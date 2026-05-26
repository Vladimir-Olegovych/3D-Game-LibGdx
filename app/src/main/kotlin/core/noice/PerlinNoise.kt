package core.noice

import core.noice.models.NoiceUtils
import core.noice.models.NoiseSettings
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sign
import kotlin.random.Random

class PerlinNoise(val seed: Int = 0) {

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
            return perlinNoise2D(x * settings.noiseZoom, z * settings.noiseZoom)
        }

        var value = 0f
        var amplitude = 1f
        var frequency = settings.noiseZoom
        var maxAmplitude = 0f

        repeat(settings.octaves) {
            value += perlinNoise2D(x * frequency, z * frequency) * amplitude
            maxAmplitude += amplitude
            amplitude *= settings.persistance
            frequency *= 2f
        }

        val normalized = value / maxAmplitude

        val absVal = abs(normalized) * settings.redistributionModifier
        val transformed = absVal.pow(settings.exponent)

        return normalized.sign * transformed
    }

    fun perlinNoise2D(x: Float, y: Float): Float {
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

    fun octavePerlin3D(x: Float, y: Float, z: Float, settings: NoiseSettings): Float {
        if (settings.octaves <= 0) {
            return perlinNoise3D(x * settings.noiseZoom, y * settings.noiseZoom, z * settings.noiseZoom)
        }

        var value = 0f
        var amplitude = 1f
        var frequency = settings.noiseZoom
        var maxAmplitude = 0f

        repeat(settings.octaves) {
            value += perlinNoise3D(x * frequency, y * frequency, z * frequency) * amplitude
            maxAmplitude += amplitude
            amplitude *= settings.persistance
            frequency *= 2f
        }

        val normalized = value / maxAmplitude
        val absVal = abs(normalized) * settings.redistributionModifier
        val transformed = absVal.pow(settings.exponent)

        return normalized.sign * transformed
    }

    fun perlinNoise3D(x: Float, y: Float, z: Float): Float {
        val xi = floor(x).toInt() and 255
        val yi = floor(y).toInt() and 255
        val zi = floor(z).toInt() and 255

        val xf = x - floor(x)
        val yf = y - floor(y)
        val zf = z - floor(z)

        val u = NoiceUtils.fade(xf)
        val v = NoiceUtils.fade(yf)
        val w = NoiceUtils.fade(zf)

        val aaa = permutation[permutation[permutation[xi] + yi] + zi]
        val aab = permutation[permutation[permutation[xi] + yi] + zi + 1]
        val aba = permutation[permutation[permutation[xi] + yi + 1] + zi]
        val abb = permutation[permutation[permutation[xi] + yi + 1] + zi + 1]
        val baa = permutation[permutation[permutation[xi + 1] + yi] + zi]
        val bab = permutation[permutation[permutation[xi + 1] + yi] + zi + 1]
        val bba = permutation[permutation[permutation[xi + 1] + yi + 1] + zi]
        val bbb = permutation[permutation[permutation[xi + 1] + yi + 1] + zi + 1]

        val gradAAA = NoiceUtils.grad3D(aaa, xf, yf, zf)
        val gradAAB = NoiceUtils.grad3D(aab, xf, yf, zf - 1)
        val gradABA = NoiceUtils.grad3D(aba, xf, yf - 1, zf)
        val gradABB = NoiceUtils.grad3D(abb, xf, yf - 1, zf - 1)
        val gradBAA = NoiceUtils.grad3D(baa, xf - 1, yf, zf)
        val gradBAB = NoiceUtils.grad3D(bab, xf - 1, yf, zf - 1)
        val gradBBA = NoiceUtils.grad3D(bba, xf - 1, yf - 1, zf)
        val gradBBB = NoiceUtils.grad3D(bbb, xf - 1, yf - 1, zf - 1)

        val lerpX1 = NoiceUtils.lerp(gradAAA, gradBAA, u)
        val lerpX2 = NoiceUtils.lerp(gradABA, gradBBA, u)
        val lerpX3 = NoiceUtils.lerp(gradAAB, gradBAB, u)
        val lerpX4 = NoiceUtils.lerp(gradABB, gradBBB, u)
        val lerpY1 = NoiceUtils.lerp(lerpX1, lerpX2, v)
        val lerpY2 = NoiceUtils.lerp(lerpX3, lerpX4, v)

        return NoiceUtils.lerp(lerpY1, lerpY2, w)
    }

}