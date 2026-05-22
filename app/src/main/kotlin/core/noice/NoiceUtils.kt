package core.noice

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

object NoiceUtils {
    fun grad(hash: Int, x: Float, y: Float): Float {
        val h = hash and 15
        val u = if (h < 8) x else y
        val v = if (h < 4) y else if (h == 12 || h == 14) x else 0f
        return if ((h and 1) == 0) u else -u + if ((h and 2) == 0) v else -v
    }

    fun fade(t: Float): Float {
        return t * t * t * (t * (t * 6f - 15f) + 10f)
    }

    fun lerp(a: Float, b: Float, t: Float): Float {
        return a + t * (b - a)
    }

    fun sign(x: Float): Float = if (x >= 0) 1f else -1f

    fun remapValue(value: Float, initialMin: Float, initialMax: Float, outputMin: Float, outputMax: Float): Float {
        return outputMin + (value - initialMin) * (outputMax - outputMin) / (initialMax - initialMin)
    }

    fun remapValue01(value: Float, outputMin: Float, outputMax: Float): Float {
        return outputMin + value * (outputMax - outputMin)
    }

    fun remapValue01ToInt(value: Float, outputMin: Float, outputMax: Float): Int {
        return remapValue01(value, outputMin, outputMax).roundToInt()
    }

    fun redistribution(noise: Float, settings: NoiseSettings): Float {
        val scaled = noise * settings.redistributionModifier
        return sign(scaled) * abs(scaled).pow(settings.exponent)
    }
}