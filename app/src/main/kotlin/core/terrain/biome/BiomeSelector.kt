package core.terrain.biome

import core.terrain.biome.models.BiomeType
import math.noice.FastNoise
import kotlin.random.Random

class BiomeSelector(
    val seed: Int,
    private val noiseFrequencies: FloatArray = floatArrayOf(
        0.003f, // Temperature
        0.002f, // Wetness
        0.004f  // Fissure
    )
) {

    private val noises: Array<FastNoise>

    init {
        require(noiseFrequencies.isNotEmpty()) {
            "At least one noise frequency is required"
        }

        val random = Random(seed)

        noises = Array(noiseFrequencies.size) { index ->
            FastNoise(random.nextInt()).apply {
                SetFrequency(noiseFrequencies[index])
                SetFractalOctaves(1)
                SetFractalLacunarity(2.0f)
                SetFractalGain(0.5f)
            }
        }
    }

    private val biomes: Array<BiomeType> = BiomeType.entries.toTypedArray()

    fun getBiomeAt(worldX: Int, worldZ: Int): BiomeType {
        val x = worldX.toFloat()
        val z = worldZ.toFloat()

        val values = FloatArray(noises.size) { index ->
            noises[index].GetPerlinFractal(x, z)
        }

        var best = BiomeType.FOREST
        var bestDistance = Double.MAX_VALUE

        for (biome in biomes) {
            val distance = distanceTo(biome, values)

            if (distance < bestDistance) {
                bestDistance = distance
                best = biome
            }
        }

        return best
    }

    private fun distanceTo(
        biome: BiomeType,
        values: FloatArray
    ): Double {
        var distance = 0.0

        for (i in biome.centers.indices) {
            if (i >= values.size) break

            val diff = values[i] - biome.centers[i]
            distance += diff * diff
        }

        return distance
    }
}