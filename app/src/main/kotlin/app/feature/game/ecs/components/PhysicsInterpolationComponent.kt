package app.feature.game.ecs.components

import com.artemis.Component
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import core.defaults.WorldConstants

class PhysicsInterpolationComponent : Component() {
    // Буфер снапшотов (старые -> новые)
    val positions = Array(CAPACITY) { Vector3() }
    val rotations = Array(CAPACITY) { Quaternion() }
    val times = LongArray(CAPACITY)
    var count = 0

    val renderPos = Vector3()
    val renderRot = Quaternion()

    var interval = DEFAULT_INTERVAL
    var lastRawNanos = 0L

    fun push(pos: Vector3, rot: Quaternion, nanos: Long) {
        if (count == CAPACITY) dropFirst(1)
        positions[count].set(pos)
        rotations[count].set(rot)
        times[count] = nanos
        count++
    }

    fun dropFirst(n: Int) {
        val remain = count - n
        for (i in 0 until remain) {
            positions[i].set(positions[i + n])
            rotations[i].set(rotations[i + n])
            times[i] = times[i + n]
        }
        count = remain
    }

    fun clear() {
        count = 0
    }

    companion object {
        const val CAPACITY = 8
        const val DEFAULT_INTERVAL = WorldConstants.PHYSICS_DELTA
        const val MIN_INTERVAL = 1f / 120f
        const val MAX_INTERVAL = 0.25f
        const val INTERVAL_SMOOTHING = 0.2f
        const val TIME_SMOOTHING = 0.1f
        const val RENDER_DELAY_STEPS = 1.5f
        const val SNAP_DISTANCE = 32f
    }
}