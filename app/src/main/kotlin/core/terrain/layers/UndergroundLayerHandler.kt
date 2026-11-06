package core.terrain.layers

import com.gigapi.math.vector.IntVector3
import core.blocks.types.BlockType
import core.chunk.ChunkData
import core.terrain.BlockLayerHandler
import core.terrain.TerrainGenerator.Companion.UNDERGROUND_HEIGHT
import kotlin.math.floor

/**
 * Слои считаются по глубине под поверхностью: depth = surfaceY - y (0 = верхний блок).
 *
 *  depth < UNDERGROUND_HEIGHT  : почва. Не трогаем верх (depth < 2), в земле иногда
 *                                встречаются карманы гравия и глины (заменяем только DIRT).
 *  depth >= UNDERGROUND_HEIGHT : подземный слой. Камень + осадочные породы:
 *                                меловые пласты, линзы глины, гравийные карманы.
 *                                Чем глубже, тем меньше осадков, совсем глубоко только камень.
 *
 * ВАЖНО: обработчик должен идти ПОСЛЕ слоя, который кладёт землю/траву/песок/снег
 * (почвенная часть читает текущий блок).
 */
class UndergroundLayerHandler(
    private val undergroundBlockType: BlockType = BlockType.STONE,
    private val seed: Int
) : BlockLayerHandler() {

    private companion object {
        const val SUBSOIL_TOP_SKIP = 2          // верхние блоки не трогаем (трава/снег/песок)
        const val SEDIMENT_DEPTH = 56           // глубже UNDERGROUND_HEIGHT + это — чистый камень
        const val CHALK_MIN_OFFSET = 3          // мел начинается не сразу под почвой
        const val CHALK_BAND = 7                // толщина слоя в блоках
        const val CHALK_CHANCE = 0.30f          // доля слоёв, которые меловые

        const val SEED_GRAVEL = 11
        const val SEED_CLAY = 23
        const val SEED_WARP = 37
        const val SEED_CHALK = 41
        const val SEED_SOIL_GRAVEL = 53
        const val SEED_SOIL_CLAY = 67
    }

    override fun handling(
        chunkData: ChunkData,
        localPosition: IntVector3,
        worldPosition: IntVector3,
        heightNoice: Pair<Float, Int>
    ) {
        val surfaceY = heightNoice.second
        val y = worldPosition.y
        if (y > surfaceY) return

        val depth = surfaceY - y
        val x = worldPosition.x
        val z = worldPosition.z

        if (depth < UNDERGROUND_HEIGHT) {
            handleSoil(chunkData, localPosition, x, y, z, depth)
        } else {
            val block = pickUnderground(x, y, z, depth - UNDERGROUND_HEIGHT)
            chunkData.setBlockByLocal(block, localPosition)
        }
    }

    private fun handleSoil(
        chunk: ChunkData, local: IntVector3,
        x: Int, y: Int, z: Int, depth: Int
    ) {
        if (depth < SUBSOIL_TOP_SKIP) return
        if (chunk.getBlockByLocal(local.x, local.y, local.z) != BlockType.DIRT) return

        if (noise(x / 9f, y / 5f, z / 9f, SEED_SOIL_GRAVEL) > 0.80f) {
            chunk.setBlockByLocal(BlockType.GRAVEL, local)
        } else if (noise(x / 11f, y / 4f, z / 11f, SEED_SOIL_CLAY) > 0.82f) {
            chunk.setBlockByLocal(BlockType.CLAY, local)
        }
    }

    // ------------------------------------------------------ подземный слой

    private fun pickUnderground(x: Int, y: Int, z: Int, below: Int): BlockType {
        if (below > SEDIMENT_DEPTH) return undergroundBlockType   // глубже осадков нет

        val fade = below / SEDIMENT_DEPTH.toFloat() * 0.15f

        if (noise(x / 12f, y / 8f, z / 12f, SEED_GRAVEL) > 0.78f + fade) return BlockType.GRAVEL

        if (noise(x / 16f, y / 5f, z / 16f, SEED_CLAY) > 0.80f + fade) return BlockType.CLAY

        if (below >= CHALK_MIN_OFFSET) {
            val warp = (noise(x / 40f, 0.5f, z / 40f, SEED_WARP) - 0.5f) * 16f
            val layer = Math.floorDiv(y + warp.toInt(), CHALK_BAND)
            if (hash(layer, 0, 0, SEED_CHALK) < CHALK_CHANCE) return BlockType.CHALK
        }

        return undergroundBlockType
    }


    private fun hash(x: Int, y: Int, z: Int, s: Int): Float {
        var h = x * 374761393 + y * 668265263 + z * 1274126177 + (s + seed) * 144665
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFFFF) / 16777216f
    }

    private fun noise(x: Float, y: Float, z: Float, s: Int): Float {
        val x0 = floor(x).toInt(); val y0 = floor(y).toInt(); val z0 = floor(z).toInt()
        val fx = smooth(x - x0); val fy = smooth(y - y0); val fz = smooth(z - z0)

        val c000 = hash(x0, y0, z0, s);         val c100 = hash(x0 + 1, y0, z0, s)
        val c010 = hash(x0, y0 + 1, z0, s);     val c110 = hash(x0 + 1, y0 + 1, z0, s)
        val c001 = hash(x0, y0, z0 + 1, s);     val c101 = hash(x0 + 1, y0, z0 + 1, s)
        val c011 = hash(x0, y0 + 1, z0 + 1, s); val c111 = hash(x0 + 1, y0 + 1, z0 + 1, s)

        val x00 = lerp(c000, c100, fx); val x10 = lerp(c010, c110, fx)
        val x01 = lerp(c001, c101, fx); val x11 = lerp(c011, c111, fx)
        return lerp(lerp(x00, x10, fy), lerp(x01, x11, fy), fz)
    }

    private fun smooth(t: Float) = t * t * (3f - 2f * t)
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}