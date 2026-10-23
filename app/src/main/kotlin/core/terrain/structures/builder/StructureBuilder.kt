package core.terrain.structures.builder

import core.blocks.types.BlockType
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
    val anchorX: Int, val anchorY: Int, val anchorZ: Int,
    val random: Random,
    val biome: BiomeType,
    /** Вес маски 0..1 в точке привязки (1 если маски нет): можно менять густоту к центру пятна */
    val mask: Float = 1f
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
        put(lx, ly, lz, block, mode)
    }

    fun fillBox(
        dx1: Int, dy1: Int, dz1: Int, dx2: Int, dy2: Int, dz2: Int,
        block: BlockType, mode: Place = Place.ONLY_AIR
    ) = forLocal(dx1, dy1, dz1, dx2, dy2, dz2) { lx, ly, lz -> put(lx, ly, lz, block, mode) }

    /** Только оболочка бокса. */
    fun hollowBox(
        dx1: Int, dy1: Int, dz1: Int, dx2: Int, dy2: Int, dz2: Int,
        block: BlockType, mode: Place = Place.ONLY_AIR
    ) {
        fillBox(dx1, dy1, dz1, dx2, dy1, dz2, block, mode)   // низ
        fillBox(dx1, dy2, dz1, dx2, dy2, dz2, block, mode)   // верх
        fillBox(dx1, dy1, dz1, dx1, dy2, dz2, block, mode)   // -X
        fillBox(dx2, dy1, dz1, dx2, dy2, dz2, block, mode)   // +X
        fillBox(dx1, dy1, dz1, dx2, dy2, dz1, block, mode)   // -Z
        fillBox(dx1, dy1, dz2, dx2, dy2, dz2, block, mode)   // +Z
    }

    fun fillSphere(r: Int, block: BlockType, mode: Place = Place.ONLY_AIR) =
        forLocal(-r, -r, -r, r, r, r) { lx, ly, lz ->
            val x = lx + minX - anchorX; val y = ly + minY - anchorY; val z = lz + minZ - anchorZ
            if (x * x + y * y + z * z <= r * r) put(lx, ly, lz, block, mode)
        }

    /** Горизонтальный диск радиуса r на высоте dy. */
    fun fillDisc(r: Int, dy: Int, block: BlockType, mode: Place = Place.ONLY_AIR) =
        forLocal(-r, dy, -r, r, dy, r) { lx, ly, lz ->
            val x = lx + minX - anchorX; val z = lz + minZ - anchorZ
            if (x * x + z * z <= r * r) put(lx, ly, lz, block, mode)
        }

    private fun put(lx: Int, ly: Int, lz: Int, block: BlockType, mode: Place) {
        if (mode == Place.ONLY_AIR && chunk.getBlockByLocal(lx, ly, lz) != BlockType.AIR) return
        chunk.setBlockByLocal(block, lx, ly, lz)
    }
    /** Обход колонок (x, z) прямоугольника, обрезанного по чанку. Координаты относительно якоря. */
    fun columns(dx1: Int, dz1: Int, dx2: Int, dz2: Int, action: (Int, Int) -> Unit) {
        val x1 = maxOf(minOf(dx1, dx2), minX - anchorX)
        val x2 = minOf(maxOf(dx1, dx2), minX + w - 1 - anchorX)
        val z1 = maxOf(minOf(dz1, dz2), minZ - anchorZ)
        val z2 = minOf(maxOf(dz1, dz2), minZ + w - 1 - anchorZ)
        for (x in x1..x2) for (z in z1..z2) action(x, z)
    }

    /** Колонка (dx, dz) от dy1 до dy2: blockAt(y) вернул null, значит блок пропускается. */
    fun column(
        dx: Int, dz: Int, dy1: Int, dy2: Int,
        mode: Place = Place.ONLY_AIR,
        blockAt: (Int) -> BlockType?
    ) {
        val lx = anchorX + dx - minX
        val lz = anchorZ + dz - minZ
        if (lx !in 0 until w || lz !in 0 until w) return
        val y1 = maxOf(dy1, minY - anchorY)
        val y2 = minOf(dy2, minY + h - 1 - anchorY)
        for (y in y1..y2) {
            val block = blockAt(y) ?: continue
            put(lx, y + anchorY - minY, lz, block, mode)
        }
    }
    /** Обрезает бокс (в координатах структуры) по чанку и обходит локальные координаты. */
    private inline fun forLocal(
        dx1: Int, dy1: Int, dz1: Int, dx2: Int, dy2: Int, dz2: Int,
        action: (Int, Int, Int) -> Unit
    ) {
        val x1 = maxOf(minOf(dx1, dx2) + anchorX - minX, 0)
        val x2 = minOf(maxOf(dx1, dx2) + anchorX - minX, w - 1)
        val y1 = maxOf(minOf(dy1, dy2) + anchorY - minY, 0)
        val y2 = minOf(maxOf(dy1, dy2) + anchorY - minY, h - 1)
        val z1 = maxOf(minOf(dz1, dz2) + anchorZ - minZ, 0)
        val z2 = minOf(maxOf(dz1, dz2) + anchorZ - minZ, w - 1)
        if (x1 > x2 || y1 > y2 || z1 > z2) return
        for (x in x1..x2) for (y in y1..y2) for (z in z1..z2) action(x, y, z)
    }
}