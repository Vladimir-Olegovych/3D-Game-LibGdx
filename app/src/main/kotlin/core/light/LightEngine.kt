package core.light

import core.chunk.ChunkData

class LightEngine(
    private val w: Int,
    private val h: Int,
    /** Доступ к загруженным чанкам (потокобезопасная map). */
    private val chunkAt: (cx: Int, cy: Int, cz: Int) -> ChunkData?,
    /** true, если над этим чанком гарантированно только открытое небо (см. TerrainGenerator.isSkyOpenAbove). */
    private val isSkyOpenAbove: (cx: Int, cy: Int, cz: Int) -> Boolean
) {
    private companion object {
        const val M = 0x1FFFFFL
        val DX = intArrayOf(1, -1, 0, 0, 0, 0)
        val DY = intArrayOf(0, 0, 1, -1, 0, 0)   // индекс 3 = вниз
        val DZ = intArrayOf(0, 0, 0, 0, 1, -1)
        const val DOWN = 3
    }

    private class Queue(initialCapacity: Int = 2048) {
        private var positions = LongArray(initialCapacity.coerceAtLeast(1))
        private var levels = IntArray(positions.size)

        private var head = 0
        private var tail = 0

        var size = 0
            private set

        val isNotEmpty: Boolean
            get() = size > 0

        val firstPosition: Long
            get() {
                check(size > 0)
                return positions[head]
            }

        val firstLevel: Int
            get() {
                check(size > 0)
                return levels[head]
            }

        fun push(position: Long, level: Int) {
            if (size == positions.size) grow()

            positions[tail] = position
            levels[tail] = level

            tail++
            if (tail == positions.size) tail = 0

            size++
        }

        fun removeFirst() {
            check(size > 0)

            head++
            if (head == positions.size) head = 0

            size--

            if (size == 0) {
                head = 0
                tail = 0
            }
        }

        fun clear() {
            head = 0
            tail = 0
            size = 0
        }

        private fun grow() {
            val oldCapacity = positions.size
            check(oldCapacity <= Int.MAX_VALUE / 2) {
                "Light queue capacity overflow: $oldCapacity"
            }

            val newPositions = LongArray(oldCapacity * 2)
            val newLevels = IntArray(oldCapacity * 2)

            val firstPart = minOf(size, oldCapacity - head)

            positions.copyInto(newPositions, 0, head, head + firstPart)
            levels.copyInto(newLevels, 0, head, head + firstPart)

            val secondPart = size - firstPart
            if (secondPart > 0) {
                positions.copyInto(newPositions, firstPart, 0, secondPart)
                levels.copyInto(newLevels, firstPart, 0, secondPart)
            }

            positions = newPositions
            levels = newLevels
            head = 0
            tail = size
        }
    }

    private val lock = Any()
    private val addQ = Queue()
    private val remQ = Queue()
    private val dirty = HashSet<ChunkData>()
    private var pending: ChunkData? = null

    private var cCx = Int.MIN_VALUE
    private var cCy = 0
    private var cCz = 0
    private var cChunk: ChunkData? = null

    // ───────────────────────── публичный API ─────────────────────────

    /**
     * Вызывать ПОСЛЕ генерации чанка, ДО построения его меша.
     * Возвращает чанки, которым нужен ремеш (включая сам чанк и затронутых соседей).
     */
    fun onChunkLoaded(chunk: ChunkData): List<ChunkData> = synchronized(lock) {
        begin(chunk)
        val lightChunkData = chunk.light
        val light: ByteArray

        if (lightChunkData == null) {
            light = ByteArray(chunk.blocks.size)
            chunk.light = light
        } else {
            light = lightChunkData
        }
        dirty.add(chunk)

        val cx = chunk.position.x
        val cy = chunk.position.y
        val cz = chunk.position.z
        val bx = cx * w
        val by = cy * h
        val bz = cz * w

        // ── небо (первым: fill перезаписывает байт целиком) ──
        val aboveLit = chunkAt(cx, cy + 1, cz)?.light != null
        if (!aboveLit && isSkyOpenAbove(cx, cy, cz)) {
            if (chunk.isEmpty) {
                java.util.Arrays.fill(light, 0xF0.toByte())
                for (lx in 0 until w) for (ly in 0 until h) for (lz in 0 until w) {
                    if (lx == 0 || lx == w - 1 || ly == 0 || ly == h - 1 || lz == 0 || lz == w - 1)
                        addQ.push(pack(bx + lx, by + ly, bz + lz), 15)
                }
            } else {
                for (lx in 0 until w) for (lz in 0 until w) {
                    for (ly in h - 1 downTo 0) {
                        val idx = chunk.getIndex(lx, ly, lz)
                        val op = LightProps.opacity(chunk.blocks[idx])
                        if (op >= 15) break
                        val level = if (op == 0) 15 else 15 - op
                        light[idx] = ((light[idx].toInt() and 0x0F) or (level shl 4)).toByte()
                        addQ.push(pack(bx + lx, by + ly, bz + lz), level)
                        if (op > 0) break
                    }
                }
            }
        }
        pullNeighbors(chunk, sky = true)
        spread(addQ, sky = true)
        addQ.clear()

        // ── блочный свет ──
        if (!chunk.isEmpty) {
            val blocks = chunk.blocks
            for (i in blocks.indices) {
                val em = LightProps.emission(blocks[i])
                if (em > 0) {
                    light[i] = ((light[i].toInt() and 0xF0) or em).toByte()
                    val z = i % w
                    val y = (i / w) % h
                    val x = i / (w * h)
                    addQ.push(pack(bx + x, by + y, bz + z), em)
                }
            }
        }
        pullNeighbors(chunk, sky = false)
        spread(addQ, sky = false)
        addQ.clear()

        flush()
    }

    /**
     * Вызывать ПОСЛЕ того, как блок в chunk.blocks уже заменён (сломан/поставлен).
     * Возвращает чанки для ремеша (включая сам чанк).
     */
    fun onBlockChanged(chunk: ChunkData, lx: Int, ly: Int, lz: Int): List<ChunkData> = synchronized(lock) {
        if (chunk.light == null) return@synchronized emptyList()
        begin(null)

        val idx = chunk.getIndex(lx, ly, lz)
        val newBlock = chunk.blocks[idx]
        val newOp = LightProps.opacity(newBlock)
        val newEm = LightProps.emission(newBlock)
        val wx = chunk.position.x * w + lx
        val wy = chunk.position.y * h + ly
        val wz = chunk.position.z * w + lz
        val pos = pack(wx, wy, wz)

        markDirty(chunk, lx, ly, lz)

        for (sky in booleanArrayOf(false, true)) {
            val old = get(chunk, idx, sky)
            val base = if (newOp >= 15 || sky) 0 else newEm
            put(chunk, lx, ly, lz, idx, base, sky)

            if (old > base) {
                remQ.push(pos, old)
                unspread(sky)
            }
            if (newOp < 15) {
                if (base > 0) addQ.push(pos, base)
                for (d in 0 until 6) {
                    val nx = wx + DX[d]; val ny = wy + DY[d]; val nz = wz + DZ[d]
                    val ch = lookup(nx, ny, nz) ?: continue
                    val l = get(ch, ch.getIndex(nx - cCx * w, ny - cCy * h, nz - cCz * w), sky)
                    if (l > 0) addQ.push(pack(nx, ny, nz), l)
                }
            }
            spread(addQ, sky)
            addQ.clear()
        }
        flush()
    }

    // ───────────────────────── BFS ─────────────────────────

    private fun spread(q: Queue, sky: Boolean) {
        while (q.isNotEmpty) {
            val p = q.firstPosition
            val l = q.firstLevel
            q.removeFirst()
            val x = ux(p); val y = uy(p); val z = uz(p)
            for (d in 0 until 6) {
                val nx = x + DX[d]; val ny = y + DY[d]; val nz = z + DZ[d]
                val ch = lookup(nx, ny, nz) ?: continue
                val lx = nx - cCx * w; val ly = ny - cCy * h; val lz = nz - cCz * w
                val idx = ch.getIndex(lx, ly, lz)
                val op = LightProps.opacity(ch.blocks[idx])
                if (op >= 15) continue
                val nl = if (sky && d == DOWN && l == 15 && op == 0) 15 else l - maxOf(1, op)
                if (nl <= 0 || nl <= get(ch, idx, sky)) continue
                put(ch, lx, ly, lz, idx, nl, sky)
                q.push(pack(nx, ny, nz), nl)
            }
        }
    }

    private fun unspread(sky: Boolean) {
        val q = remQ
        while (q.isNotEmpty) {
            val p = q.firstPosition
            val l = q.firstLevel
            q.removeFirst()
            val x = ux(p); val y = uy(p); val z = uz(p)
            for (d in 0 until 6) {
                val nx = x + DX[d]; val ny = y + DY[d]; val nz = z + DZ[d]
                val ch = lookup(nx, ny, nz) ?: continue
                val lx = nx - cCx * w; val ly = ny - cCy * h; val lz = nz - cCz * w
                val idx = ch.getIndex(lx, ly, lz)
                val nl = get(ch, idx, sky)
                if (nl == 0) continue
                val np = pack(nx, ny, nz)
                if (nl < l || (sky && d == DOWN && l == 15)) {
                    // зависел от удаляемого света
                    val em = if (sky) 0 else LightProps.emission(ch.blocks[idx])
                    put(ch, lx, ly, lz, idx, em, sky)
                    q.push(np, nl)
                    if (em > 0) addQ.push(np, em)    // факел внутри зоны удаления не теряем
                } else {
                    addQ.push(np, nl)                // независимый источник — пере-распространим
                }
            }
        }
        q.clear()
    }

    /** Кладёт в очередь клетки соседних чанков, прилегающие к нашему, — BFS сам затечёт внутрь. */
    private fun pullNeighbors(chunk: ChunkData, sky: Boolean) {
        val cx = chunk.position.x; val cy = chunk.position.y; val cz = chunk.position.z
        for (d in 0 until 6) {
            val nc = chunkAt(cx + DX[d], cy + DY[d], cz + DZ[d]) ?: continue
            val arr = nc.light ?: continue
            val xr = if (DX[d] == 1) 0..0 else if (DX[d] == -1) (w - 1)..(w - 1) else 0 until w
            val yr = if (DY[d] == 1) 0..0 else if (DY[d] == -1) (h - 1)..(h - 1) else 0 until h
            val zr = if (DZ[d] == 1) 0..0 else if (DZ[d] == -1) (w - 1)..(w - 1) else 0 until w
            val bx = nc.position.x * w; val by = nc.position.y * h; val bz = nc.position.z * w
            for (lx in xr) for (ly in yr) for (lz in zr) {
                val v = arr[nc.getIndex(lx, ly, lz)].toInt() and 0xFF
                val level = if (sky) v ushr 4 else v and 15
                if (level > 0) addQ.push(pack(bx + lx, by + ly, bz + lz), level)
            }
        }
    }

    // ───────────────────────── утилиты ─────────────────────────

    private fun begin(p: ChunkData?) {
        dirty.clear(); addQ.clear(); remQ.clear()
        pending = p
        cCx = Int.MIN_VALUE; cChunk = null
    }

    private fun flush(): List<ChunkData> {
        val r = ArrayList(dirty)
        dirty.clear()
        pending = null
        cChunk = null; cCx = Int.MIN_VALUE
        return r
    }

    /** Чанк по мировым координатам; только если он уже просчитан. Кэширует последний. */
    private fun lookup(wx: Int, wy: Int, wz: Int): ChunkData? {
        val cx = Math.floorDiv(wx, w)
        val cy = Math.floorDiv(wy, h)
        val cz = Math.floorDiv(wz, w)
        if (cx != cCx || cy != cCy || cz != cCz) {
            cCx = cx; cCy = cy; cCz = cz
            val p = pending
            val c = if (p != null && p.position.x == cx && p.position.y == cy && p.position.z == cz) p
            else chunkAt(cx, cy, cz)
            cChunk = if (c?.light != null) c else null
        }
        return cChunk
    }

    private fun get(ch: ChunkData, idx: Int, sky: Boolean): Int {
        val v = ch.light!![idx].toInt() and 0xFF
        return if (sky) v ushr 4 else v and 15
    }

    private fun put(ch: ChunkData, lx: Int, ly: Int, lz: Int, idx: Int, level: Int, sky: Boolean) {
        val arr = ch.light!!
        val v = arr[idx].toInt() and 0xFF
        arr[idx] = (if (sky) (v and 0x0F) or (level shl 4) else (v and 0xF0) or level).toByte()
        markDirty(ch, lx, ly, lz)
    }

    /** Чанк менялся → ремеш. Если клетка на границе, меши соседей (их вершины семплят наш свет) тоже. */
    private fun markDirty(ch: ChunkData, lx: Int, ly: Int, lz: Int) {
        dirty.add(ch)
        val ox = if (lx == 0) -1 else if (lx == w - 1) 1 else 0
        val oy = if (ly == 0) -1 else if (ly == h - 1) 1 else 0
        val oz = if (lz == 0) -1 else if (lz == w - 1) 1 else 0
        if (ox == 0 && oy == 0 && oz == 0) return
        for (a in 0..1) for (b in 0..1) for (c in 0..1) {
            val dx = if (a == 0) 0 else ox
            val dy = if (b == 0) 0 else oy
            val dz = if (c == 0) 0 else oz
            if (dx == 0 && dy == 0 && dz == 0) continue
            chunkAt(ch.position.x + dx, ch.position.y + dy, ch.position.z + dz)?.let { dirty.add(it) }
        }
    }

    private fun pack(x: Int, y: Int, z: Int): Long =
        ((x.toLong() and M) shl 42) or ((y.toLong() and M) shl 21) or (z.toLong() and M)
    private fun ux(p: Long) = ((p ushr 42).toInt() shl 11) shr 11
    private fun uy(p: Long) = ((p ushr 21).toInt() shl 11) shr 11
    private fun uz(p: Long) = (p.toInt() shl 11) shr 11
}