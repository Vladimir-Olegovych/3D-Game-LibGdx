package core.noice

import com.badlogic.gdx.math.Vector2
import com.gigapi.math.vector.IntVector2
import kotlin.math.roundToInt

class DomainWarping(
    val noice: PerlinNoise,
    val noiseDomainX: NoiseSettings,
    val noiseDomainY: NoiseSettings,
    val amplitudeX: Int = 20,
    val amplitudeY: Int = 20
) {

    fun generateDomainNoise(x: Int, z: Int, defaultNoiseSettings: NoiseSettings): Float {
        val domainOffset = generateDomainOffset(x, z)
        return noice.octavePerlin2D(
            x + domainOffset.x,
            z + domainOffset.y,
            defaultNoiseSettings
        )
    }

    fun generateDomainOffset(x: Int, z: Int): Vector2 {
        val noiseX = noice.octavePerlin2D(x.toFloat(), z.toFloat(), noiseDomainX) * amplitudeX
        val noiseY = noice.octavePerlin2D(x.toFloat(), z.toFloat(), noiseDomainY) * amplitudeY
        return Vector2(noiseX, noiseY)
    }

    fun generateDomainOffsetInt(x: Int, z: Int): IntVector2 {
        val offset = generateDomainOffset(x, z)
        return IntVector2(offset.x.roundToInt(), offset.y.roundToInt())
    }
}