package core.animator.scripts

import core.animator.AnimationContext
import core.animator.AnimationScript
import kotlin.math.sin

class AttackAnimationScript(
    context: AnimationContext
) : AnimationScript(context) {

    companion object {
        private const val SPEED = 20f
        private const val READY_PITCH = -20f
        private const val THRUST_PITCH = -40f
        private const val SIDE_YAW = 0f
    }

    override fun onStart() {
        super.onStart()
        context.rightArmOverridden = true
        context.bodyYaw = context.lookYaw
    }

    override fun onStop() {
        context.rightArmOverridden = false
    }

    override fun update(deltaTime: Float) {
        time += deltaTime * SPEED
        // 0..1: pull back → stab forward → recover
        val thrust = (sin(time.toDouble()).toFloat() + 1f) * 0.5f
        val attackPitch = READY_PITCH + (THRUST_PITCH - READY_PITCH) * thrust

        context.applyPart(context.rightHand, attackPitch, SIDE_YAW)
        context.applyRightHandItem(attackPitch, SIDE_YAW)
    }
}
