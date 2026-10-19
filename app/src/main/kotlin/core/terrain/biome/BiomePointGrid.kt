package core.terrain.biome

import com.gigapi.math.vector.IntVector3
import core.chunk.ChunkWorldUpdater
import core.terrain.biome.models.BiomeType
import kotlin.math.sqrt

class BiomePointGrid(
    private val selector: BiomeSelector
) {
    companion object {
        const val STEP_ADD = 16
        const val POINT_RADIUS = ChunkWorldUpdater.DRAW_RADIUS_X
        const val POINT_WORLD_STEP = ChunkWorldUpdater.CHUNK_SIZE * STEP_ADD

        private const val JITTER = 0.1f
        private const val HASH_SEED = (20 * 20) + 20 + 41
    }

    fun getBiomeAt(
        points: Map<IntVector3, BiomeType>,
        worldX: Int,
        worldZ: Int
    ): BiomeType {
        val cellX = floorDiv(worldX, POINT_WORLD_STEP)
        val cellZ = floorDiv(worldZ, POINT_WORLD_STEP)

        var bestBiome: BiomeType? = null
        var bestDistance = Float.MAX_VALUE

        for (gx in -1..1) {
            for (gz in -1..1) {
                val cx = cellX + gx
                val cz = cellZ + gz

                val baseX = cx * POINT_WORLD_STEP + POINT_WORLD_STEP / 2
                val baseZ = cz * POINT_WORLD_STEP + POINT_WORLD_STEP / 2

                val pointX = baseX + jitterX(cx, cz)
                val pointZ = baseZ + jitterZ(cx, cz)

                val dx = (worldX - pointX).toFloat()
                val dz = (worldZ - pointZ).toFloat()
                val distance = sqrt(dx * dx + dz * dz)

                val biome = points[IntVector3(baseX, 0, baseZ)]

                if (biome != null && distance < bestDistance) {
                    bestDistance = distance
                    bestBiome = biome
                }
            }
        }

        return bestBiome ?: BiomeType.FOREST
    }

    fun preparePointsAround(position: IntVector3): Map<IntVector3, BiomeType> {
        val result = HashMap<IntVector3, BiomeType>(16 * 16)

        val centerCellX = floorDiv(position.x, POINT_WORLD_STEP)
        val centerCellZ = floorDiv(position.z, POINT_WORLD_STEP)

        val halfGrid = POINT_RADIUS / 2

        for (gx in -halfGrid until halfGrid) {
            for (gz in -halfGrid until halfGrid) {
                val cellX = centerCellX + gx
                val cellZ = centerCellZ + gz

                val pointX = cellX * POINT_WORLD_STEP + POINT_WORLD_STEP / 2
                val pointZ = cellZ * POINT_WORLD_STEP + POINT_WORLD_STEP / 2

                val point = IntVector3(pointX, 0, pointZ)
                result[point] = selector.getBiomeAt(pointX, pointZ)
            }
        }

        return result
    }

    private fun jitterX(cellX: Int, cellZ: Int): Int {
        val noise = hashNormalized(cellX, cellZ, HASH_SEED)
        return ((noise - 0.5f) * POINT_WORLD_STEP * JITTER).toInt()
    }

    private fun jitterZ(cellX: Int, cellZ: Int): Int {
        val noise = hashNormalized(cellX, cellZ, HASH_SEED + 7919)
        return ((noise - 0.5f) * POINT_WORLD_STEP * JITTER).toInt()
    }

    private fun hashNormalized(x: Int, z: Int, seed: Int): Int {
        var h = seed
        h = h xor (x * 0x27d4eb2d)
        h = (h shl 13) xor h
        h = h xor (z * 0x27d4eb2d)
        h = (h shl 17) xor h
        h *= 0x9E3779B1.toInt()
        h = h xor (h ushr 16)
        return h and 0x7FFFFFFF
    }

    private fun floorDiv(value: Int, divisor: Int): Int {
        val q = value / divisor
        val r = value % divisor
        return if (r != 0 && ((r < 0) != (divisor < 0))) q - 1 else q
    }
}