package app.feature.game.ecs.states

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class TimeState {

    var dayPhase = 1f
        private set

    var timeOfDay = 0.7f
        private set

    var cycleDuration = 600f

    var maxShadowThreshold = 0.5f
        set(value) { field = value.coerceIn(0f, 1f) }

    var nightBlueFilter = 1f
        set(value) { field = value.coerceIn(0f, 1f) }

    var celestialDistance = 800f

    private val fogColorBuffer = FloatArray(3)
    private val sunDirectionBuffer = Vector3()
    private val moonDirectionBuffer = Vector3()
    private val sunPositionBuffer = Vector3()
    private val moonPositionBuffer = Vector3()

    fun update(deltaTime: Float) {
        timeOfDay = (timeOfDay + deltaTime / cycleDuration) % 1f
        dayPhase = ((cos((timeOfDay - 0.25f) * 2f * PI.toFloat()) + 1f) / 2f)
    }

    fun setTimeOfDay(value: Float) {
        timeOfDay = ((value % 1f) + 1f) % 1f
        dayPhase = ((cos((timeOfDay - 0.25f) * 2f * PI.toFloat()) + 1f) / 2f)
    }

    fun starVisibility(): Float = ((1f - dayPhase - 0.15f) / 0.55f).coerceIn(0f, 1f)

    /** Celestial rotation around world X axis, radians. Half turn per day cycle. */
    fun skyRotation(): Float = timeOfDay * PI.toFloat()

    fun applySkyRotation(direction: Vector3): Vector3 {
        return direction.cpy().rotate(Vector3.X, MathUtils.radiansToDegrees * skyRotation())
    }

    fun getSunDirection(out: Vector3 = sunDirectionBuffer): Vector3 {
        val sunAngle = (timeOfDay - 0.25f) * 2f * PI.toFloat()
        return out.set(0f, cos(sunAngle), sin(sunAngle)).nor()
    }

    fun getMoonDirection(out: Vector3 = moonDirectionBuffer): Vector3 {
        return getSunDirection(out).scl(-1f)
    }

    fun getSunWorldPosition(cameraPosition: Vector3, out: Vector3 = sunPositionBuffer): Vector3 {
        return out.set(cameraPosition).add(getSunDirection().scl(celestialDistance))
    }

    fun getMoonWorldPosition(cameraPosition: Vector3, out: Vector3 = moonPositionBuffer): Vector3 {
        return out.set(cameraPosition).add(getMoonDirection().scl(celestialDistance))
    }

    fun celestialVisibility(direction: Vector3): Float {
        return ((direction.y - 0.05f) / 0.15f).coerceIn(0f, 1f)
    }

    fun fogColor(): FloatArray {
        val dayR = 135f / 255f
        val dayG = 206f / 255f
        val dayB = 240f / 255f

        val sunsetR = 255f / 255f
        val sunsetG = 145f / 255f
        val sunsetB = 120f / 255f

        val nightR = 10f / 255f
        val nightG = 18f / 255f
        val nightB = 45f / 255f

        val sunsetFactor = (1f - abs(dayPhase - 0.5f) * 2f).coerceIn(0f, 1f).let { it * it } * 0.5f
        val nightBlend = ((0.5f - dayPhase) * 2f).coerceIn(0f, 1f)

        var r = dayR + (sunsetR - dayR) * sunsetFactor
        var g = dayG + (sunsetG - dayG) * sunsetFactor
        var b = dayB + (sunsetB - dayB) * sunsetFactor

        r += (nightR - r) * nightBlend
        g += (nightG - g) * nightBlend
        b += (nightB - b) * nightBlend

        fogColorBuffer[0] = r
        fogColorBuffer[1] = g
        fogColorBuffer[2] = b
        return fogColorBuffer
    }
}