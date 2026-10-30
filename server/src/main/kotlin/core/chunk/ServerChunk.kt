package com.gigcreator.core.chunk

import com.artemis.utils.IntBag

/** Упаковка 3D-координат чанка в Long (по 21 биту на ось, знаковые). */
internal object ChunkKey {
    private const val MASK = 0x1FFFFFL

    fun pack(x: Int, y: Int, z: Int): Long =
        ((x.toLong() and MASK) shl 42) or
                ((y.toLong() and MASK) shl 21) or
                (z.toLong() and MASK)
}

/**
 * Серверный чанк: НИЧЕГО не генерирует и не хранит блоков,
 * только id сущностей, которые в нём лежат, и id наблюдателей, которые его видят.
 */
internal class ServerChunk internal constructor(
    val x: Int,
    val y: Int,
    val z: Int,
) {
    internal val key: Long = ChunkKey.pack(x, y, z)

    /** Сущности (любые: игроки, мобы, предметы), находящиеся в этом чанке. */
    val entities = IntBag()

    /** Сущности-наблюдатели (с ObserverComponent), которые видят этот чанк. */
    val observers = IntBag()

    internal val isUnused: Boolean get() = entities.size() == 0 && observers.size() == 0

    override fun toString() = "ServerChunk($x, $y, $z; entities=${entities.size()}, observers=${observers.size()})"
}