package core.math

import kotlin.math.floor

object LayerNoise {

    fun hash(x: Int, y: Int, z: Int, s: Int): Float {
        var h = x * 374761393 + y * 668265263 + z * 1274126177 + s * 144665
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFFFF) / 16777216f
    }

    /** 2D value noise, [0,1) */
    fun noise2(x: Float, z: Float, s: Int): Float {
        val x0 = floor(x).toInt(); val z0 = floor(z).toInt()
        val fx = smooth(x - x0); val fz = smooth(z - z0)
        val a = lerp(hash(x0, 0, z0, s),     hash(x0 + 1, 0, z0, s),     fx)
        val b = lerp(hash(x0, 0, z0 + 1, s), hash(x0 + 1, 0, z0 + 1, s), fx)
        return lerp(a, b, fz)
    }

    /** Две октавы: границы получаются не такими гладкими, как у одного шума. */
    fun fbm2(x: Float, z: Float, s: Int): Float =
        noise2(x, z, s) * 0.65f + noise2(x * 2.3f + 17f, z * 2.3f - 9f, s + 1) * 0.35f

    private fun smooth(t: Float) = t * t * (3f - 2f * t)
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}