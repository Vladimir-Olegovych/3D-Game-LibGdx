package core.terrain.biome

import core.terrain.biome.models.BiomeType
import math.noice.FastNoise
import kotlin.random.Random

class BiomeSelector(val seed: Int) {

    companion object {
        const val TEMPERATURE_INDEX = 0
        const val WETNESS_INDEX = 1
    }

    private val noises: Array<FastNoise>

    init {
        val random = Random(seed)
        val serviceSeeds = IntArray(8) { random.nextInt() }
        noises = arrayOf(
            FastNoise(serviceSeeds[TEMPERATURE_INDEX]).apply {
                SetFrequency(0.003f)
                SetFractalOctaves(1)
                SetFractalLacunarity(2.0f)
                SetFractalGain(0.5f)
            },
            FastNoise(serviceSeeds[WETNESS_INDEX]).apply {
                SetFrequency(0.002f)
                SetFractalOctaves(1)
                SetFractalLacunarity(2.0f)
                SetFractalGain(0.5f)
            }
        )
    }

    private val biomes: Array<BiomeType> = BiomeType.entries.toTypedArray()

    /** Центры диапазонов считаются один раз, а не на каждый вызов. */
    private val centers: Array<DoubleArray> = Array(biomes.size) { b ->
        val config = biomes[b].config
        DoubleArray(config.size) { i -> ((config[i].start + config[i].end) / 2f).toDouble() }
    }

    fun getBiomeAt(worldX: Int, worldZ: Int): BiomeType {
        val x = worldX.toFloat()
        val z = worldZ.toFloat()

        // Шум считается один раз на ось, а не один раз на (биом × ось).
        val temperature = noises[TEMPERATURE_INDEX].GetPerlinFractal(x, z).toDouble()
        val wetness = noises[WETNESS_INDEX].GetPerlinFractal(x, z).toDouble()

        var best = BiomeType.FOREST
        var bestDist = Double.MAX_VALUE
        for (b in biomes.indices) {
            val c = centers[b]
            var d = 0.0
            if (c.size > TEMPERATURE_INDEX) {
                val diff = temperature - c[TEMPERATURE_INDEX]; d += diff * diff
            }
            if (c.size > WETNESS_INDEX) {
                val diff = wetness - c[WETNESS_INDEX]; d += diff * diff
            }
            if (d < bestDist) {
                bestDist = d
                best = biomes[b]
            }
        }
        return best
    }
}