package core.terrain.biome

import core.terrain.biome.models.BiomeType
import math.noice.FastNoise
import kotlin.math.pow
import kotlin.random.Random

class BiomeSelector(val seed: Int) {

    companion object {
        const val TEMPERATURE_INDEX = 0
        const val WETNESS_INDEX = 1
    }

    private val serviceSeeds: IntArray = IntArray(8) { Random(seed).nextInt() }

    private val noises: Array<FastNoise> = arrayOf(
        // [TEMPERATURE_INDEX]
        FastNoise(serviceSeeds[TEMPERATURE_INDEX]).apply {
            SetFrequency(0.005f)
            SetFractalOctaves(1)
            SetFractalLacunarity(2.0f)
            SetFractalGain(0.5f)
        },
        // [WETNESS_INDEX]
        FastNoise(serviceSeeds[WETNESS_INDEX]).apply {
            SetFrequency(0.004f)
            SetFractalOctaves(1)
            SetFractalLacunarity(2.0f)
            SetFractalGain(0.5f)
        }
    )

    fun getBiomeAt(worldX: Int, worldZ: Int): BiomeType {
        val x = worldX.toFloat()
        val z = worldZ.toFloat()

        return BiomeType.entries.minByOrNull { biome ->
            biome.config.indices.sumOf { i ->
                val sample = noises[i].GetPerlinFractal(x, z).toDouble()
                val center = ((biome.config[i].start + biome.config[i].end) / 2f).toDouble()
                (sample - center).pow(2)
            }
        } ?: BiomeType.FOREST
    }
}