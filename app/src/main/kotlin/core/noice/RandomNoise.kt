package core.noice

import com.gigapi.math.vector.IntVector3

class RandomNoise(val seed: Int) {

    fun randomize(intVector3: IntVector3): Boolean {
        return randomize(intVector3.x.toFloat(), intVector3.y.toFloat(), intVector3.z.toFloat())
    }

    fun randomize(x: Float, y: Float, z: Float): Boolean {
        val ix = x.toRawBits()
        val iy = y.toRawBits()
        val iz = z.toRawBits()

        var hash = seed
        hash = hash * 31 + ix
        hash = hash * 31 + iy
        hash = hash * 31 + iz

        hash = hash xor (hash ushr 16)
        hash *= 0x85ebca6b.toInt()
        hash = hash xor (hash ushr 13)
        hash *= 0xc2b2ae35.toInt()
        hash = hash xor (hash ushr 16)

        return hash < 0
    }
}