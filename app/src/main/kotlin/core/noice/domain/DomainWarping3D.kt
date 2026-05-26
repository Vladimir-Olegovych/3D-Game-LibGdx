package core.noice.domain

import com.badlogic.gdx.math.Vector3
import com.gigapi.math.vector.IntVector3
import core.noice.PerlinNoise
import core.noice.models.NoiseSettings
import kotlin.math.roundToInt

class DomainWarping3D(
    val noise: PerlinNoise,
    val noiseDomainX: NoiseSettings,
    val noiseDomainY: NoiseSettings,
    val noiseDomainZ: NoiseSettings,
    val amplitudeX: Int = 20,
    val amplitudeY: Int = 20,
    val amplitudeZ: Int = 20
) {

    fun generateDomainNoise(x: Int, y: Int, z: Int, defaultNoiseSettings: NoiseSettings): Float {
        val domainOffset = generateDomainOffset(x.toFloat(), y.toFloat(), z.toFloat())
        return noise.octavePerlin3D(
            x + domainOffset.x,
            y + domainOffset.y,
            z + domainOffset.z,
            defaultNoiseSettings
        )
    }

    fun generateDomainOffset(x: Float, y: Float, z: Float): Vector3 {
        val offsetX = noise.octavePerlin3D(x, y, z, noiseDomainX) * amplitudeX
        val offsetY = noise.octavePerlin3D(x, y, z, noiseDomainY) * amplitudeY
        val offsetZ = noise.octavePerlin3D(x, y, z, noiseDomainZ) * amplitudeZ
        return Vector3(offsetX, offsetY, offsetZ)
    }

    fun generateDomainOffsetInt(x: Float, y: Float, z: Float): IntVector3 {
        val offset = generateDomainOffset(x, y, z)
        return IntVector3(offset.x.roundToInt(), offset.y.roundToInt(), offset.z.roundToInt())
    }
}