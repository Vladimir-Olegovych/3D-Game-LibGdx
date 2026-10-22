package core.mesh.generators

import com.badlogic.gdx.graphics.Mesh
import com.gigapi.effects.LaunchedEffect
import com.gigapi.general.GContext
import com.gigapi.math.vector.IntVector3
import com.gigapi.mesh.RawMeshData
import core.blocks.BlockDataManager
import core.blocks.BlockType
import core.chunk.ChunkData
import core.mesh.light.LightMesher
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.max
import core.mesh.generators.ChunkMesher.Neighborhood
import core.mesh.light.ShadowCompilerAO
import java.util.Arrays

/**
 * Binary greedy mesher + упакованная вершина (16 байт вместо 68).
 *
 * Вершина = 4 слова по 4 байта (хранятся как Float через Float.fromBits):
 *   a_Pos    ubyte4          : x, y, z (локально в чанке), w = направление 0..5 (+X -X +Y -Y +Z -Z)
 *   a_Tile   ubyte4          : localU, localV, repeatU, repeatV     (для fract() в шейдере)
 *   a_Shade  ubyte4 normalized: ao, sky, block, 0
 *   a_Origin ushort2 normalized: origin тайла в атласе (u, v)
 * Размер тайла в атласе одинаков для всех блоков -> uniform u_tileSize.
 *
 * Индексы: сначала непрозрачные квады, потом cutout (generateAllSides: листва и т.п.).
 * Граница хранится в ChunkMeshSplit, DrawSystem рисует диапазоны двумя проходами.
 */
class GreedyChunkMesher: LaunchedEffect, MeshGenerator {

    companion object {
        const val STRIDE = 4 // float'ов на вершину (16 байт)

        /** Размер тайла в атласе (с отступом), одинаковый для всех граней. Читается DrawSystem. */
        @Volatile @JvmField var tileSizeU = 0f
        @Volatile @JvmField var tileSizeV = 0f

        /** Выключи, чтобы сравнить картинку (все квады станут 1x1). */
        @JvmField var merge = true

        /** Индекс, с которого начинаются cutout-треугольники в этом RawMeshData (-1 если неизвестно). */
        fun splitOf(raw: RawMeshData): Int = ChunkMeshSplit.splitOf(raw)

        private val TYPES = BlockType.values()
        private val DIRS = ChunkMesher.directions

        private fun f(x: Int, y: Int, z: Int) = floatArrayOf(x.toFloat(), y.toFloat(), z.toFloat())

        /** Порядок как в DIRS: 0:+X 1:-X 2:+Y 3:-Y 4:+Z 5:-Z. UV вершин: v0=(l,t) v1=(l,b) v2=(r,b) v3=(r,t). */
        private val UNIT: Array<Array<FloatArray>> = arrayOf(
            arrayOf(f(1, 1, 1), f(1, 0, 1), f(1, 0, 0), f(1, 1, 0)), // +X
            arrayOf(f(0, 1, 0), f(0, 0, 0), f(0, 0, 1), f(0, 1, 1)), // -X
            arrayOf(f(0, 1, 1), f(1, 1, 1), f(1, 1, 0), f(0, 1, 0)), // +Y
            arrayOf(f(0, 0, 0), f(1, 0, 0), f(1, 0, 1), f(0, 0, 1)), // -Y
            arrayOf(f(0, 1, 1), f(0, 0, 1), f(1, 0, 1), f(1, 1, 1)), // +Z
            arrayOf(f(1, 1, 0), f(1, 0, 0), f(0, 0, 0), f(0, 1, 0)), // -Z
        )

        /** Упаковка 4 байт в float. Старший байт обязан быть < 0x7F, иначе получится NaN-паттерн. */
        private fun pack(b0: Int, b1: Int, b2: Int, b3: Int): Float {
            val top = b3 and 255
            check((top and 0x7F) != 0x7F) { "packed word would be a NaN pattern" }
            return Float.fromBits((b0 and 255) or ((b1 and 255) shl 8) or ((b2 and 255) shl 16) or (top shl 24))
        }

        private fun q8(v: Float): Int = Math.round(v * 255f).coerceIn(0, 255)
    }

    private lateinit var blockDataManager: BlockDataManager

    override fun launch(gContext: GContext) {
        blockDataManager = gContext.getObject()
    }

    override fun createMesh(chunkMap: Map<IntVector3, ChunkData>, chunkData: ChunkData): RawMeshData {
        if (chunkData.isEmpty) return RawMeshData(FloatArray(0), ShortArray(0))
        return mesh(chunkData, Neighborhood(chunkData, chunkMap))
    }

    internal fun mesh(chunk: ChunkData, nb: Neighborhood): RawMeshData = Run(chunk, nb).build()

    // ------------------------------------------------------------------ helpers

    private class FloatBuf(cap: Int) {
        var a = FloatArray(cap)
        var n = 0
        fun add(v: Float) {
            if (n == a.size) a = a.copyOf(n * 2)
            a[n++] = v
        }
        fun toArray(): FloatArray = a.copyOf(n)
    }

    private class ShortBuf(cap: Int) {
        var a = ShortArray(cap)
        var n = 0
        fun add(v: Int) {
            if (n == a.size) a = a.copyOf(n * 2)
            a[n++] = v.toShort()
        }
    }

    private class TileInfo(
        /** origin тайла в атласе, упакован как 2 x ushort normalized */
        val originWord: Float,
        val standard: Boolean,
        /** локальные UV 0/1 для 4 вершин (для квада 1x1) */
        val loc: FloatArray
    )

    // ------------------------------------------------------------------ main work

    private inner class Run(val chunk: ChunkData, val nb: Neighborhood) {
        val w = chunk.chunkWidth
        val h = chunk.chunkHeight
        val sz = intArrayOf(w, h, w)
        val volume = w * h * w
        val textureMap = blockDataManager.getBlockTextureDataMap()

        val occludes = BooleanArray(TYPES.size)
        val drawable = BooleanArray(TYPES.size)
        val special = BooleanArray(TYPES.size)   // generateAllSides -> cutout

        val occ = Array(3) { a -> IntArray(sz[(a + 1) % 3] * sz[(a + 2) % 3]) }
        val drw = Array(3) { a -> IntArray(sz[(a + 1) % 3] * sz[(a + 2) % 3]) }

        val ids = IntArray(volume)
        val specials = ArrayList<Int>()

        val gBlock = IntArray(volume)
        val gAo = FloatArray(volume)
        val gSky = FloatArray(volume)
        val gBlk = FloatArray(volume)

        var unitFaces = 0L
        var quads = 0L

        val vb = FloatBuf(4096)
        val ibOpaque = ShortBuf(2048)
        val ibCutout = ShortBuf(256)

        val c = IntArray(3)
        val e = IntArray(3)
        val aoS = FloatArray(4)
        val ltS = FloatArray(8)
        val locS = FloatArray(8)
        val tiles = arrayOfNulls<TileInfo>(6 * TYPES.size)

        val blockExists: (Int, Int, Int) -> Boolean = { x, y, z ->
            when (nb.block(x, y, z)) {
                BlockType.AIR, BlockType.NOTHING -> false
                else -> true
            }
        }
        val lightAt: (Int, Int, Int) -> Int = { x, y, z -> nb.light(x, y, z) }

        init {
            require(w <= 30 && h <= 30) { "bitmask mesher supports chunk size <= 30" }
            for (t in TYPES) {
                if (t == BlockType.AIR || t == BlockType.NOTHING) continue
                val td = textureMap[t]
                val allSides = td?.generateAllSides == true
                occludes[t.ordinal] = !allSides
                drawable[t.ordinal] = td != null && !allSides
                special[t.ordinal] = td != null && allSides
            }
        }

        fun build(): RawMeshData {
            fillMasks()
            for (d in 0 until 6) {
                collectFaces(d)
                greedy(d)
            }
            emitSpecials()

            val idx = ShortArray(ibOpaque.n + ibCutout.n)
            System.arraycopy(ibOpaque.a, 0, idx, 0, ibOpaque.n)
            System.arraycopy(ibCutout.a, 0, idx, ibOpaque.n, ibCutout.n)
            val raw = RawMeshData(vb.toArray(), idx)
            ChunkMeshSplit.putRaw(raw, ibOpaque.n)
            return raw
        }

        private fun col(a: Int, x: Int, y: Int, z: Int): Int = when (a) {
            0 -> y * w + z
            1 -> z * w + x
            else -> x * h + y
        }

        private fun fillMasks() {
            for (x in 0 until w) for (y in 0 until h) for (z in 0 until w) {
                val o = chunk.getBlockByLocal(x, y, z).ordinal
                val idx = (x * h + y) * w + z
                ids[idx] = o
                if (occludes[o]) {
                    occ[0][col(0, x, y, z)] = occ[0][col(0, x, y, z)] or (1 shl (x + 1))
                    occ[1][col(1, x, y, z)] = occ[1][col(1, x, y, z)] or (1 shl (y + 1))
                    occ[2][col(2, x, y, z)] = occ[2][col(2, x, y, z)] or (1 shl (z + 1))
                }
                if (drawable[o]) {
                    drw[0][col(0, x, y, z)] = drw[0][col(0, x, y, z)] or (1 shl x)
                    drw[1][col(1, x, y, z)] = drw[1][col(1, x, y, z)] or (1 shl y)
                    drw[2][col(2, x, y, z)] = drw[2][col(2, x, y, z)] or (1 shl z)
                }
                if (special[o]) specials.add(idx)
            }
            for (y in 0 until h) for (z in 0 until w) {
                val ci = col(0, 0, y, z)
                if (occludes[nb.block(-1, y, z).ordinal]) occ[0][ci] = occ[0][ci] or 1
                if (occludes[nb.block(w, y, z).ordinal]) occ[0][ci] = occ[0][ci] or (1 shl (w + 1))
            }
            for (x in 0 until w) for (z in 0 until w) {
                val ci = col(1, x, 0, z)
                if (occludes[nb.block(x, -1, z).ordinal]) occ[1][ci] = occ[1][ci] or 1
                if (occludes[nb.block(x, h, z).ordinal]) occ[1][ci] = occ[1][ci] or (1 shl (h + 1))
            }
            for (x in 0 until w) for (y in 0 until h) {
                val ci = col(2, x, y, 0)
                if (occludes[nb.block(x, y, -1).ordinal]) occ[2][ci] = occ[2][ci] or 1
                if (occludes[nb.block(x, y, w).ordinal]) occ[2][ci] = occ[2][ci] or (1 shl (w + 1))
            }
        }

        private fun collectFaces(d: Int) {
            val a = d shr 1
            val positive = (d and 1) == 0
            val u = (a + 1) % 3
            val v = (a + 2) % 3
            val n = sz[a]
            val sv = sz[v]
            val cells = sz[u] * sv
            val full = (1 shl n) - 1
            Arrays.fill(gBlock, 0, n * cells, 0)

            val occA = occ[a]
            val drwA = drw[a]
            val nrm = DIRS[d].normal

            for (cl in 0 until cells) {
                val dm = drwA[cl]
                if (dm == 0) continue
                val hidden = if (positive) (occA[cl] ushr 2) else occA[cl]
                var m = dm and hidden.inv() and full
                if (m == 0) continue
                val pu = cl / sv
                val pv = cl % sv
                while (m != 0) {
                    val i = Integer.numberOfTrailingZeros(m)
                    m = m and (m - 1)
                    c[a] = i; c[u] = pu; c[v] = pv
                    val x = c[0]; val y = c[1]; val z = c[2]
                    val o = ids[(x * h + y) * w + z]
                    unitFaces++

                    val ao = ShadowCompilerAO.computeFaceAO(x, y, z, nrm.x, nrm.y, nrm.z, blockExists)
                    val lt = LightMesher.computeFaceLight(x, y, z, nrm.x, nrm.y, nrm.z, lightAt)

                    val flat = ao[0] == ao[1] && ao[1] == ao[2] && ao[2] == ao[3] &&
                            lt[0] == lt[2] && lt[2] == lt[4] && lt[4] == lt[6] &&
                            lt[1] == lt[3] && lt[3] == lt[5] && lt[5] == lt[7]

                    if (flat && tileInfo(d, o).standard) {
                        val gi = i * cells + cl
                        gBlock[gi] = o + 1
                        gAo[gi] = ao[0]
                        gSky[gi] = lt[0]
                        gBlk[gi] = lt[1]
                    } else {
                        emitSingle(d, x, y, z, o, ao, lt)
                    }
                }
            }
        }

        private fun same(p: Int, q: Int): Boolean =
            gBlock[p] == gBlock[q] && gAo[p] == gAo[q] && gSky[p] == gSky[q] && gBlk[p] == gBlk[q]

        private fun greedy(d: Int) {
            val a = d shr 1
            val u = (a + 1) % 3
            val v = (a + 2) % 3
            val n = sz[a]
            val su = sz[u]
            val sv = sz[v]
            val cells = su * sv

            for (i in 0 until n) {
                val base = i * cells
                for (pu in 0 until su) {
                    var pv = 0
                    while (pv < sv) {
                        val idx = base + pu * sv + pv
                        if (gBlock[idx] == 0) { pv++; continue }

                        var gv = 1
                        var gu = 1
                        if (merge) {
                            while (pv + gv < sv && same(idx, idx + gv)) gv++
                            outer@ while (pu + gu < su) {
                                val row = base + (pu + gu) * sv + pv
                                for (k in 0 until gv) if (!same(idx, row + k)) break@outer
                                gu++
                            }
                        }
                        emitMerged(d, a, u, v, i, pu, pv, gu, gv, idx)

                        for (du in 0 until gu) {
                            val rowBase = base + (pu + du) * sv + pv
                            for (dv in 0 until gv) gBlock[rowBase + dv] = 0
                        }
                        pv += gv
                    }
                }
            }
        }

        private fun emitMerged(
            d: Int, a: Int, u: Int, v: Int,
            slice: Int, pu: Int, pv: Int, gu: Int, gv: Int, gi: Int
        ) {
            c[a] = slice; c[u] = pu; c[v] = pv
            e[a] = 1; e[u] = gu; e[v] = gv

            val o = gBlock[gi] - 1
            val t = tileInfo(d, o)

            val un = UNIT[d]
            val tu = abs(
                (un[3][0] - un[0][0]) * e[0] + (un[3][1] - un[0][1]) * e[1] + (un[3][2] - un[0][2]) * e[2]
            ).toInt()
            val tv = abs(
                (un[1][0] - un[0][0]) * e[0] + (un[1][1] - un[0][1]) * e[1] + (un[1][2] - un[0][2]) * e[2]
            ).toInt()

            // v0=(0,0) v1=(0,tv) v2=(tu,tv) v3=(tu,0)
            locS[0] = 0f; locS[1] = 0f
            locS[2] = 0f; locS[3] = tv.toFloat()
            locS[4] = tu.toFloat(); locS[5] = tv.toFloat()
            locS[6] = tu.toFloat(); locS[7] = 0f

            val ao = gAo[gi]; val sky = gSky[gi]; val blk = gBlk[gi]
            for (k in 0 until 4) {
                aoS[k] = ao
                ltS[k * 2] = sky
                ltS[k * 2 + 1] = blk
            }
            pushQuad(d, c[0], c[1], c[2], e[0], e[1], e[2], locS, t, tu, tv, aoS, ltS, false, false)
        }

        private fun emitSingle(d: Int, x: Int, y: Int, z: Int, o: Int, ao: FloatArray, lt: FloatArray) {
            val b0 = ao[0] * (0.2f + max(lt[0], lt[1]))
            val b1 = ao[1] * (0.2f + max(lt[2], lt[3]))
            val b2 = ao[2] * (0.2f + max(lt[4], lt[5]))
            val b3 = ao[3] * (0.2f + max(lt[6], lt[7]))
            val flip = b0 + b2 < b1 + b3
            val t = tileInfo(d, o)
            pushQuad(d, x, y, z, 1, 1, 1, t.loc, t, 1, 1, ao, lt, flip, special[o])
        }

        private fun pushQuad(
            d: Int, x: Int, y: Int, z: Int, ex: Int, ey: Int, ez: Int,
            loc: FloatArray, t: TileInfo, nU: Int, nV: Int,
            ao: FloatArray, lt: FloatArray, flip: Boolean, cutout: Boolean
        ) {
            quads++
            val base = vb.n / STRIDE
            val un = UNIT[d]
            for (i in 0 until 4) {
                val p = un[i]
                val px = x + (p[0] * ex).toInt()
                val py = y + (p[1] * ey).toInt()
                val pz = z + (p[2] * ez).toInt()
                vb.add(pack(px, py, pz, d))
                vb.add(pack(Math.round(loc[i * 2]), Math.round(loc[i * 2 + 1]), nU, nV))
                vb.add(pack(q8(ao[i]), q8(lt[i * 2]), q8(lt[i * 2 + 1]), 0))
                vb.add(t.originWord)
            }
            val ib = if (cutout) ibCutout else ibOpaque
            if (flip) {
                ib.add(base + 1); ib.add(base + 2); ib.add(base + 3)
                ib.add(base + 1); ib.add(base + 3); ib.add(base)
            } else {
                ib.add(base); ib.add(base + 1); ib.add(base + 2)
                ib.add(base); ib.add(base + 2); ib.add(base + 3)
            }
        }

        private fun tileInfo(d: Int, o: Int): TileInfo {
            val key = d * TYPES.size + o
            tiles[key]?.let { return it }

            val uvs = blockDataManager.faceUVs(DIRS[d].directionType, TYPES[o])
            var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            for (i in 0 until 4) {
                minX = minOf(minX, uvs[i].x); maxX = maxOf(maxX, uvs[i].x)
                minY = minOf(minY, uvs[i].y); maxY = maxOf(maxY, uvs[i].y)
            }
            val sx = maxX - minX
            val sy = maxY - minY

            // все тайлы атласа должны быть одного размера (у вас 128x128)
            if (tileSizeU == 0f) { tileSizeU = sx; tileSizeV = sy }
            check(abs(sx - tileSizeU) < 1e-5f && abs(sy - tileSizeV) < 1e-5f) {
                "Atlas tiles must have equal size: got $sx x $sy, expected $tileSizeU x $tileSizeV"
            }

            val loc = FloatArray(8)
            for (i in 0 until 4) {
                loc[i * 2] = if (sx > 0f) (uvs[i].x - minX) / sx else 0f
                loc[i * 2 + 1] = if (sy > 0f) (uvs[i].y - minY) / sy else 0f
            }
            val standard = uvs[0].x == uvs[1].x && uvs[2].x == uvs[3].x &&
                    uvs[0].y == uvs[3].y && uvs[1].y == uvs[2].y &&
                    uvs[0].x < uvs[3].x && uvs[0].y < uvs[1].y

            val ou = Math.round(minX * 65535f).coerceIn(0, 65535)
            val ov = Math.round(minY * 65535f).coerceIn(0, 65535)
            val word = ou or (ov shl 16)
            check(((word ushr 24) and 0x7F) != 0x7F) { "atlas origin word would be a NaN pattern" }

            val info = TileInfo(Float.fromBits(word), standard, loc)
            tiles[key] = info
            return info
        }

        // ------------------------------------------------------------ generateAllSides blocks

        private fun emitSpecials() {
            for (s in 0 until specials.size) {
                val idx = specials[s]
                val z = idx % w
                val y = (idx / w) % h
                val x = idx / (w * h)
                val o = ids[idx]
                val block = TYPES[o]
                val all = hasActiveAllSides(block, x, y, z)

                for (d in 0 until 6) {
                    val dir = DIRS[d]
                    val nbk = nb.block(x + dir.dx, y + dir.dy, z + dir.dz)
                    val render = nbk == BlockType.AIR || nbk == BlockType.NOTHING || all
                    if (!render) continue
                    unitFaces++

                    val nrm = dir.normal
                    val ao = ShadowCompilerAO.computeFaceAO(x, y, z, nrm.x, nrm.y, nrm.z, blockExists)
                    val lt = LightMesher.computeFaceLight(x, y, z, nrm.x, nrm.y, nrm.z, lightAt)
                    emitSingle(d, x, y, z, o, ao, lt)
                }
            }
        }

        private fun hasActiveAllSides(block: BlockType, x: Int, y: Int, z: Int): Boolean {
            val worldX = chunk.position.x * w + x
            val worldY = chunk.position.y * h + y
            val worldZ = chunk.position.z * w + z
            for (i in DIRS.indices) {
                val dir = DIRS[i]
                val nx = x + dir.dx
                val ny = y + dir.dy
                val nz = z + dir.dz
                val neighbor = nb.block(nx, ny, nz)
                if (neighbor != block) continue
                val nd = textureMap[neighbor] ?: continue
                if (!nd.generateAllSides) continue

                val nwx = chunk.position.x * w + nx
                val nwy = chunk.position.y * h + ny
                val nwz = chunk.position.z * w + nz
                val cmp = when {
                    nwx != worldX -> nwx.compareTo(worldX)
                    nwy != worldY -> nwy.compareTo(worldY)
                    else -> nwz.compareTo(worldZ)
                }
                if (cmp < 0) return false
            }
            return true
        }
    }
}

/**
 * Хранит границу "непрозрачные | cutout" в индексном буфере.
 * Weak-карты: записи исчезают вместе с RawMeshData / Mesh.
 */
object ChunkMeshSplit {
    private val byRaw = Collections.synchronizedMap(WeakHashMap<RawMeshData, Int>())
    private val byMesh = Collections.synchronizedMap(WeakHashMap<Mesh, Int>())

    internal fun putRaw(raw: RawMeshData, split: Int) { byRaw[raw] = split }
    fun splitOf(raw: RawMeshData): Int = byRaw[raw] ?: -1

    fun register(mesh: Mesh, split: Int) { byMesh[mesh] = split }

    /** Если границы нет — весь меш считается непрозрачным. */
    fun get(mesh: Mesh): Int = byMesh[mesh] ?: mesh.numIndices
}