package com.gigcreator.core.chunk

import com.artemis.utils.IntBag
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min


internal class ServerChunkManager(
    val chunkSize: Int = 16,
    val chunkHeight: Int = 16,
) {

    interface Listener {
        /** Чанк появился (первая сущность или первый наблюдатель). */
        fun onCreate(chunk: ServerChunk) {}

        /** Чанк опустел: нет ни сущностей, ни наблюдателей. */
        fun onDestroy(chunk: ServerChunk) {}

        /** [observer] начал видеть [chunk]; [entities] — всё, что в нём сейчас лежит. [first] — он первый зритель. */
        fun onObserve(observer: Int, chunk: ServerChunk, entities: IntBag, first: Boolean) {}

        /** [observer] перестал видеть [chunk]; [entities] — всё, что в нём лежит. [last] — зрителей не осталось. */
        fun onUnobserve(observer: Int, chunk: ServerChunk, entities: IntBag, last: Boolean) {}

        /** [entity] стала видимой для [observers] (только для тех, кто её ещё не видел). [chunk] — её текущий чанк. */
        fun onEntityShow(entity: Int, observers: IntBag, chunk: ServerChunk) {}

        /** [entity] перестала быть видимой для [observers]. [chunk] — чанк, из которого она ушла/удалена. */
        fun onEntityHide(entity: Int, observers: IntBag, chunk: ServerChunk) {}
    }

    private class View(val cx: Int, val cy: Int, val cz: Int, val radiusXZ: Int, val radiusY: Int) {
        fun contains(x: Int, y: Int, z: Int): Boolean {
            val dx = x - cx
            val dz = z - cz
            return dx * dx + dz * dz <= radiusXZ * radiusXZ && abs(y - cy) <= radiusY
        }
    }

    private val listeners = ArrayList<Listener>()
    private val chunks = HashMap<Long, ServerChunk>()
    private val views = HashMap<Int, View>()
    private var entityChunks = arrayOfNulls<ServerChunk>(1024)

    private val scratchShow = IntBag()
    private val scratchHide = IntBag()

    val chunkCount: Int get() = chunks.size

    // ------------------------------------------------------------------ listeners

    fun addListener(listener: Listener) {
        if (listener !in listeners) listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    // ------------------------------------------------------------------ coords

    fun toChunkX(worldX: Float): Int = floor(worldX / chunkSize).toInt()
    fun toChunkY(worldY: Float): Int = floor(worldY / chunkHeight).toInt()
    fun toChunkZ(worldZ: Float): Int = floor(worldZ / chunkSize).toInt()

    // ------------------------------------------------------------------ queries

    fun getChunk(x: Int, y: Int, z: Int): ServerChunk? = chunks[ChunkKey.pack(x, y, z)]

    /** Чанк, в котором сейчас лежит сущность. */
    fun getChunk(entity: Int): ServerChunk? = chunkOf(entity)

    fun contains(entity: Int): Boolean = chunkOf(entity) != null

    fun isObserver(entity: Int): Boolean = views.containsKey(entity)

    /** Наблюдатели, которые сейчас видят сущность (включая её саму, если она наблюдатель). */
    fun getObservers(entity: Int): IntBag? = chunkOf(entity)?.observers

    // ------------------------------------------------------------------ entities

    /** Поместить сущность в чанк или переместить. Если уже там — ничего не делает. */
    fun moveTo(entity: Int, cx: Int, cy: Int, cz: Int) {
        val from = chunkOf(entity)

        if (from == null) {
            val to = obtainChunk(cx, cy, cz)
            to.entities.add(entity)
            setChunkOf(entity, to)
            if (to.observers.size() > 0) {
                for (l in listeners) l.onEntityShow(entity, to.observers, to)
            }
            return
        }

        if (from.x == cx && from.y == cy && from.z == cz) return

        val to = obtainChunk(cx, cy, cz)
        from.entities.removeValue(entity)
        to.entities.add(entity)
        setChunkOf(entity, to)

        // Те, кто видел старый чанк, но не видит новый — прячем. Кто видит оба — ничего не шлём.
        scratchHide.clear()
        scratchShow.clear()
        difference(from.observers, to.observers, scratchHide)
        difference(to.observers, from.observers, scratchShow)

        if (scratchHide.size() > 0) for (l in listeners) l.onEntityHide(entity, scratchHide, from)
        if (scratchShow.size() > 0) for (l in listeners) l.onEntityShow(entity, scratchShow, to)

        release(from)
    }

    /** Убрать сущность из менеджера (удалена из мира / отключился владелец). */
    fun remove(entity: Int) {
        val chunk = chunkOf(entity) ?: return
        setChunkOf(entity, null)
        chunk.entities.removeValue(entity)
        if (chunk.observers.size() > 0) {
            for (l in listeners) l.onEntityHide(entity, chunk.observers, chunk)
        }
        release(chunk)
    }

    // ------------------------------------------------------------------ observers

    /**
     * Создать/обновить наблюдателя. Зона видимости — цилиндр:
     * по XZ радиус [radiusXZ] чанков (круг), по Y — [radiusY] чанков.
     * Вызывать каждый тик безопасно: если ничего не изменилось, выходит сразу.
     */
    fun updateObserver(observer: Int, cx: Int, cy: Int, cz: Int, radiusXZ: Int, radiusY: Int) {
        val old = views[observer]
        if (old != null && old.cx == cx && old.cy == cy && old.cz == cz &&
            old.radiusXZ == radiusXZ && old.radiusY == radiusY
        ) return

        val new = View(cx, cy, cz, radiusXZ, radiusY)
        views[observer] = new
        applyViews(observer, old, new)
    }

    fun removeObserver(observer: Int) {
        val old = views.remove(observer) ?: return
        applyViews(observer, old, null)
    }

    private fun applyViews(observer: Int, old: View?, new: View?) {
        val a = old ?: new ?: return
        val b = new ?: old ?: return

        val r = max(a.radiusXZ, b.radiusXZ)
        val ry = max(a.radiusY, b.radiusY)

        val minX = min(a.cx, b.cx) - r
        val maxX = max(a.cx, b.cx) + r
        val minY = min(a.cy, b.cy) - ry
        val maxY = max(a.cy, b.cy) + ry
        val minZ = min(a.cz, b.cz) - r
        val maxZ = max(a.cz, b.cz) + r

        for (x in minX..maxX) {
            for (z in minZ..maxZ) {
                for (y in minY..maxY) {
                    val wasIn = old?.contains(x, y, z) == true
                    val isIn = new?.contains(x, y, z) == true
                    if (wasIn == isIn) continue
                    if (isIn) observe(observer, x, y, z) else unobserve(observer, x, y, z)
                }
            }
        }
    }

    private fun observe(observer: Int, x: Int, y: Int, z: Int) {
        val chunk = obtainChunk(x, y, z)
        chunk.observers.add(observer)
        val first = chunk.observers.size() == 1
        for (l in listeners) l.onObserve(observer, chunk, chunk.entities, first)
    }

    private fun unobserve(observer: Int, x: Int, y: Int, z: Int) {
        val chunk = chunks[ChunkKey.pack(x, y, z)] ?: return
        if (!chunk.observers.removeValue(observer)) return
        val last = chunk.observers.size() == 0
        for (l in listeners) l.onUnobserve(observer, chunk, chunk.entities, last)
        release(chunk)
    }

    // ------------------------------------------------------------------ internals

    private fun obtainChunk(x: Int, y: Int, z: Int): ServerChunk {
        val key = ChunkKey.pack(x, y, z)
        chunks[key]?.let { return it }
        val chunk = ServerChunk(x, y, z)
        chunks[key] = chunk
        for (l in listeners) l.onCreate(chunk)
        return chunk
    }

    private fun release(chunk: ServerChunk) {
        if (!chunk.isUnused) return
        if (chunks.remove(chunk.key) == null) return
        for (l in listeners) l.onDestroy(chunk)
    }

    private fun chunkOf(entity: Int): ServerChunk? =
        if (entity in entityChunks.indices) entityChunks[entity] else null

    private fun setChunkOf(entity: Int, chunk: ServerChunk?) {
        if (entity >= entityChunks.size) {
            if (chunk == null) return
            entityChunks = entityChunks.copyOf(max(entity + 1, entityChunks.size * 2))
        }
        entityChunks[entity] = chunk
    }

    /** out = a \ b */
    private fun difference(a: IntBag, b: IntBag, out: IntBag) {
        for (i in 0 until a.size()) {
            val v = a[i]
            if (!b.contains(v)) out.add(v)
        }
    }
}
