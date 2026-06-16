package core.noice

import com.gigapi.math.noice.NoiceGenerator
import com.gigapi.math.noice.PerlinNoise
import com.gigapi.math.noice.models.NoiceUtils
import com.gigapi.math.noice.models.NoiseSettings
import math.noice.FastNoise
import kotlin.math.pow

fun PerlinNoise.asGenerator(): NoiceGenerator {
    val perlinNoise = this
    return object : NoiceGenerator {
        override fun noice(x: Int, y: Int, noiceSettings: NoiseSettings): Float {
            return noice(x.toFloat() ,y.toFloat(), noiceSettings)
        }

        override fun noice(x: Float, y: Float, noiceSettings: NoiseSettings): Float {
            return perlinNoise.octavePerlin2D(x, y, noiceSettings)
        }

        override fun noice(x: Int, y: Int, z: Int, noiceSettings: NoiseSettings): Float {
            return noice(x.toFloat() ,y.toFloat(), z.toFloat(), noiceSettings)
        }

        override fun noice(x: Float, y: Float, z: Float, noiceSettings: NoiseSettings): Float {
            return perlinNoise.octavePerlin3D(x, y, z, noiceSettings)
        }
    }
}

fun FastNoise.asGenerator(): NoiceGenerator {
    val fastNoise = this
    return object : NoiceGenerator {
        override fun noice(x: Int, y: Int, noiceSettings: NoiseSettings): Float {
            return noice(x.toFloat() ,y.toFloat(), noiceSettings)
        }

        override fun noice(x: Float, y: Float, noiceSettings: NoiseSettings): Float {
            fastNoise.SetFrequency(noiceSettings.noiseZoom)
            fastNoise.SetFractalOctaves(noiceSettings.octaves)
            fastNoise.SetFractalGain(noiceSettings.persistance)

            var value = fastNoise.GetCubicFractal(x, y)

            value = (value + 1f) / 2f
            value = NoiceUtils.redistribution(value, noiceSettings)
            value = value.pow(noiceSettings.exponent)
            value = value * 2f - 1f

            return value
        }

        override fun noice(x: Int, y: Int, z: Int, noiceSettings: NoiseSettings): Float {
            return noice(x.toFloat() ,y.toFloat(), z.toFloat(), noiceSettings)
        }

        override fun noice(x: Float, y: Float, z: Float, noiceSettings: NoiseSettings): Float {
            fastNoise.SetFrequency(noiceSettings.noiseZoom)
            fastNoise.SetFractalOctaves(noiceSettings.octaves)
            fastNoise.SetFractalGain(noiceSettings.persistance)

            var value = fastNoise.GetCubicFractal(x, y, z)

            value = (value + 1f) / 2f
            value = NoiceUtils.redistribution(value, noiceSettings)
            value = value.pow(noiceSettings.exponent)
            value = value * 2f - 1f

            return value
        }
    }
}