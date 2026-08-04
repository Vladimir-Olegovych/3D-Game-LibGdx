package core.animator.scripts

import core.animator.AnimationContext
import core.animator.AnimationScript
import kotlin.math.sin

class RunAnimationScript(
    context: AnimationContext
) : AnimationScript(context) {

    companion object {
        private const val SWING_ANGLE = 25f
        private const val SPEED = 8f
    }

    override fun update(deltaTime: Float) {
        time += deltaTime * SPEED
        val angle = sin(time.toDouble()).toFloat() * SWING_ANGLE

        if (!context.rightArmOverridden) {
            context.applyPart(context.rightHand, angle)
            context.applyRightHandItem(angle)
        }
        context.applyPart(context.leftHand, -angle)
        context.applyPart(context.rightLeg, -angle)
        context.applyPart(context.leftLeg, angle)
    }
}
