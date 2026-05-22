package core.noice

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sign
import kotlin.random.Random

class PerlinNoise(private val seed: Int = 0) {

    private val permutation = IntArray(512).apply {
        val p = IntArray(256) { it }

        val random = Random(seed)
        for (i in 255 downTo 1) {
            val j = random.nextInt(i + 1)
            val temp = p[i]
            p[i] = p[j]
            p[j] = temp
        }

        for (i in 0 until 512) {
            this[i] = p[i % 256]
        }
    }

    fun octavePerlin2D(x: Float, z: Float, settings: NoiseSettings): Float {
        if (settings.octaves <= 0) {
            return perlinNoise(x * settings.noiseZoom, z * settings.noiseZoom)
        }

        var value = 0f
        var amplitude = 1f
        var frequency = settings.noiseZoom
        var maxAmplitude = 0f

        repeat(settings.octaves) {
            value += perlinNoise(x * frequency, z * frequency) * amplitude
            maxAmplitude += amplitude
            amplitude *= settings.persistance
            frequency *= 2f
        }

        val normalized = value / maxAmplitude

        val absVal = abs(normalized) * settings.redistributionModifier
        val transformed = absVal.pow(settings.exponent.toFloat())

        return normalized.sign * transformed
    }

    fun perlinNoise(x: Float, y: Float): Float {
        val xi = floor(x).toInt() and 255
        val yi = floor(y).toInt() and 255

        val xf = x - floor(x)
        val yf = y - floor(y)

        val u = NoiceUtils.fade(xf)
        val v = NoiceUtils.fade(yf)

        val aa = permutation[permutation[xi] + yi]
        val ab = permutation[permutation[xi] + yi + 1]
        val ba = permutation[permutation[xi + 1] + yi]
        val bb = permutation[permutation[xi + 1] + yi + 1]

        val gradAA = NoiceUtils.grad(aa, xf, yf)
        val gradAB = NoiceUtils.grad(ab, xf, yf - 1)
        val gradBA = NoiceUtils.grad(ba, xf - 1, yf)
        val gradBB = NoiceUtils.grad(bb, xf - 1, yf - 1)

        val lerpX1 = NoiceUtils.lerp(gradAA, gradBA, u)
        val lerpX2 = NoiceUtils.lerp(gradAB, gradBB, u)

        return NoiceUtils.lerp(lerpX1, lerpX2, v)
    }


}