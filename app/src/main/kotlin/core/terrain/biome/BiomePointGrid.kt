package core.terrain.biome

import com.gigapi.effects.LaunchedEffect
import com.gigapi.general.GContext
import com.gigapi.math.vector.IntVector3
import core.chunk.ChunkWorldUpdater
import core.terrain.biome.models.BiomeType
import kotlin.math.sqrt

class BiomePointGrid: LaunchedEffect {

    companion object {
        const val STEP_ADD = 32
        const val POINT_RADIUS = ChunkWorldUpdater.DRAW_RADIUS_X / 2
        const val POINT_WORLD_STEP = ChunkWorldUpdater.CHUNK_SIZE * STEP_ADD
        const val BLEND_DISTANCE = POINT_WORLD_STEP * 0.4
    }

    private lateinit var selector: BiomeSelector

    override fun launch(gContext: GContext) {
        selector = gContext.getObject()
    }

    data class BiomeData(
        val currentBiome: BiomeType,
        val weights: Map<BiomeType, Float>
    )

    fun getBiomeAt(
        points: Map<IntVector3, BiomeType>,
        worldX: Int,
        worldZ: Int
    ): BiomeData {
        val nearestByBiome = HashMap<BiomeType, Double>()

        for ((point, biome) in points) {
            val dx = worldX.toDouble() - point.x.toDouble()
            val dz = worldZ.toDouble() - point.z.toDouble()
            val distance = sqrt(dx * dx + dz * dz)

            val previous = nearestByBiome[biome]

            if (previous == null || distance < previous) {
                nearestByBiome[biome] = distance
            }
        }

        val nearest = nearestByBiome.minByOrNull { it.value }
            ?: return BiomeData(
                currentBiome = BiomeType.FOREST,
                weights = mapOf(BiomeType.FOREST to 1f)
            )

        val minDistance = nearest.value
        val rawWeights = HashMap<BiomeType, Double>()
        var totalWeight = 0.0

        for ((biome, distance) in nearestByBiome) {
            val t = (
                    (distance - minDistance) / BLEND_DISTANCE
                    ).coerceIn(0.0, 1.0)

            val weight = 1.0 - t * t * (3.0 - 2.0 * t)

            if (weight > 0.0) {
                rawWeights[biome] = weight
                totalWeight += weight
            }
        }

        return BiomeData(
            currentBiome = nearest.key,
            weights = rawWeights.mapValues { (_, weight) ->
                (weight / totalWeight).toFloat()
            }
        )
    }

    fun getPointsAround(position: IntVector3): Map<IntVector3, BiomeType> {
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

    private fun floorDiv(value: Int, divisor: Int): Int {
        val q = value / divisor
        val r = value % divisor
        return if (r != 0 && ((r < 0) != (divisor < 0))) q - 1 else q
    }
}