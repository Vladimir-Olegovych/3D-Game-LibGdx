package core.terrain.structures.builder

import core.blocks.BlockType
import core.chunk.ChunkData
import core.terrain.biome.models.BiomeType
import kotlin.random.Random

enum class Place {
    /** Только в пустое место (листва, декор) — не портит рельеф. */
    ONLY_AIR,
    /** Заменяет что угодно (стволы, стены). */
    ALWAYS
}

/**
 * Окно в один чанк. Координаты — относительно точки привязки структуры,
 * всё, что выходит за пределы этого чанка, тихо отбрасывается.
 */
class StructureBuilder(
    private val chunk: ChunkData,
    val anchorX: Int,
    val anchorY: Int,
    val anchorZ: Int,
    val random: Random,
    val biome: BiomeType
) {
    private val w = chunk.chunkWidth
    private val h = chunk.chunkHeight
    private val minX = chunk.position.x * w
    private val minY = chunk.position.y * h
    private val minZ = chunk.position.z * w

    fun set(dx: Int, dy: Int, dz: Int, block: BlockType, mode: Place = Place.ONLY_AIR) {
        val lx = anchorX + dx - minX
        val ly = anchorY + dy - minY
        val lz = anchorZ + dz - minZ
        if (lx !in 0 until w || ly !in 0 until h || lz !in 0 until w) return
        if (mode == Place.ONLY_AIR && chunk.getBlockByLocal(lx, ly, lz) != BlockType.AIR) return
        chunk.setBlockByLocal(block, lx, ly, lz)
    }

    /** Заполнение параллелепипеда (границы включительно), считает только пересечение с чанком. */
    fun fillBox(
        dx1: Int, dy1: Int, dz1: Int,
        dx2: Int, dy2: Int, dz2: Int,
        block: BlockType, mode: Place = Place.ONLY_AIR
    ) {
        val x1 = maxOf(minOf(dx1, dx2), minX - anchorX)
        val x2 = minOf(maxOf(dx1, dx2), minX + w - 1 - anchorX)
        val y1 = maxOf(minOf(dy1, dy2), minY - anchorY)
        val y2 = minOf(maxOf(dy1, dy2), minY + h - 1 - anchorY)
        val z1 = maxOf(minOf(dz1, dz2), minZ - anchorZ)
        val z2 = minOf(maxOf(dz1, dz2), minZ + w - 1 - anchorZ)
        for (x in x1..x2) for (y in y1..y2) for (z in z1..z2) set(x, y, z, block, mode)
    }
}