package core.terrain.biome

import math.noice.FastNoise
import kotlin.random.Random

class WarpedPoint(val x: Int, val z: Int)

/**
 * Многослойный domain warp ТОЛЬКО для поиска биома (поверхность считается по своему, мягкому варпу).
 *
 * Слои применяются последовательно, каждый следующий берёт уже искажённые координаты —
 * из-за этого границы биомов не просто «дрожат», а закручиваются в извилистые рукава и заливы.
 */
class BiomeWarp(seed: Int) {

    companion object {
        // амплитуды в блоках (реальный сдвиг ~ половина от значения: Perlin редко выходит к ±1)
        const val BIG_AMPLITUDE = 520f
        const val MID_AMPLITUDE = 150f
        const val SMALL_AMPLITUDE = 30f

        const val BIG_FREQUENCY = 0.0011f
        const val MID_FREQUENCY = 0.005f
        const val SMALL_FREQUENCY = 0.02f
    }

    private val random = Random(seed xor 0x5EED1234)

    private fun noise(frequency: Float, octaves: Int) = FastNoise(random.nextInt()).apply {
        SetFrequency(frequency)
        SetFractalOctaves(octaves)
        SetFractalLacunarity(2.0f)
        SetFractalGain(0.5f)
    }

    private val bigX = noise(BIG_FREQUENCY, 2)
    private val bigZ = noise(BIG_FREQUENCY, 2)
    private val midX = noise(MID_FREQUENCY, 2)
    private val midZ = noise(MID_FREQUENCY, 2)
    private val smallX = noise(SMALL_FREQUENCY, 1)
    private val smallZ = noise(SMALL_FREQUENCY, 1)

    fun warp(worldX: Int, worldZ: Int): WarpedPoint {
        var x = worldX.toFloat()
        var z = worldZ.toFloat()

        val x1 = x + bigX.GetPerlinFractal(x, z) * BIG_AMPLITUDE
        val z1 = z + bigZ.GetPerlinFractal(x, z) * BIG_AMPLITUDE
        x = x1; z = z1

        val x2 = x + midX.GetPerlinFractal(x, z) * MID_AMPLITUDE
        val z2 = z + midZ.GetPerlinFractal(x, z) * MID_AMPLITUDE
        x = x2; z = z2

        val x3 = x + smallX.GetPerlinFractal(x, z) * SMALL_AMPLITUDE
        val z3 = z + smallZ.GetPerlinFractal(x, z) * SMALL_AMPLITUDE

        return WarpedPoint(x3.toInt(), z3.toInt())
    }
}