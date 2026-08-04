package core.animator

import com.badlogic.gdx.graphics.GLTexture
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.math.Vector3
import com.gigapi.mesh.BlenderRenderData
import core.animator.scripts.AttackAnimationScript
import core.animator.scripts.IdleAnimationScript
import core.animator.scripts.RunAnimationScript
import kotlin.math.abs
import kotlin.math.sign

class ModelAnimator(
    blenderRenderData: BlenderRenderData
) {
    companion object {
        const val ANIM_IDLE = "idle"
        const val ANIM_MOVE = "move"
        const val ANIM_ATTACK = "attack"

        private const val HEAD_PITCH_DOWN_MAX = -45f
        private const val HEAD_PITCH_UP_MAX = 89f
        private const val HEAD_BODY_YAW_LIMIT = 45f
        private const val BODY_TURN_SPEED = 360f
    }

    private val context: AnimationContext
    private val idleScript: IdleAnimationScript
    private val runScript: RunAnimationScript
    private val attackScript: AttackAnimationScript

    private var locomotionScript: AnimationScript
    private var attacking = false
    private var lookInitialized = false

    init {
        val bodyMesh = blenderRenderData.subMeshes[0]
        val leftHandMesh = blenderRenderData.subMeshes[1]
        val rightHandMesh = blenderRenderData.subMeshes[2]
        val headMesh = blenderRenderData.subMeshes[3]
        val rightLegMesh = blenderRenderData.subMeshes[4]
        val leftLegMesh = blenderRenderData.subMeshes[5]

        val bodyRest = AnimationContext.captureRest(bodyMesh.mesh)
        val leftHandRest = AnimationContext.captureRest(leftHandMesh.mesh)
        val rightHandRest = AnimationContext.captureRest(rightHandMesh.mesh)
        val headRest = AnimationContext.captureRest(headMesh.mesh)
        val rightLegRest = AnimationContext.captureRest(rightLegMesh.mesh)
        val leftLegRest = AnimationContext.captureRest(leftLegMesh.mesh)

        context = AnimationContext(
            body = AnimatedPart(bodyMesh, bodyRest, Vector3()),
            leftHand = AnimatedPart(
                leftHandMesh,
                leftHandRest,
                AnimationContext.computePivot(leftHandRest, leftHandMesh.mesh, useMaxY = true)
            ),
            rightHand = AnimatedPart(
                rightHandMesh,
                rightHandRest,
                AnimationContext.computePivot(rightHandRest, rightHandMesh.mesh, useMaxY = true)
            ),
            head = AnimatedPart(
                headMesh,
                headRest,
                AnimationContext.computePivot(headRest, headMesh.mesh, useMaxY = false)
            ),
            rightLeg = AnimatedPart(
                rightLegMesh,
                rightLegRest,
                AnimationContext.computePivot(rightLegRest, rightLegMesh.mesh, useMaxY = true)
            ),
            leftLeg = AnimatedPart(
                leftLegMesh,
                leftLegRest,
                AnimationContext.computePivot(leftLegRest, leftLegMesh.mesh, useMaxY = true)
            ),
            rightHandTip = AnimationContext.computePivot(rightHandRest, rightHandMesh.mesh, useMaxY = false)
        )

        idleScript = IdleAnimationScript(context)
        runScript = RunAnimationScript(context)
        attackScript = AttackAnimationScript(context)
        locomotionScript = idleScript
        locomotionScript.onStart()
    }

    fun playAnimation(name: String) {
        if (name == ANIM_ATTACK) {
            setAttacking(true)
            return
        }

        val next = when (name) {
            ANIM_MOVE -> runScript
            else -> idleScript
        }
        if (locomotionScript === next) return

        if (next === runScript) {
            context.bodyYaw = context.lookYaw
        }
        locomotionScript = next
        locomotionScript.onStart()
    }

    fun setAttacking(value: Boolean) {
        if (attacking == value) return
        attacking = value
        if (value) {
            attackScript.onStart()
        } else {
            attackScript.onStop()
        }
    }

    fun isAttacking(): Boolean = attacking

    fun getCurrentAnimation(): String = when (locomotionScript) {
        is RunAnimationScript -> ANIM_MOVE
        else -> ANIM_IDLE
    }

    fun setLookDirection(yaw: Float, pitch: Float) {
        context.lookYaw = yaw
        context.lookPitch = pitch
        if (!lookInitialized) {
            context.bodyYaw = yaw
            lookInitialized = true
        }
    }

    fun setRightHandItem(
        mesh: Mesh?,
        texture: GLTexture? = null,
        ownsMesh: Boolean = true,
        isTool: Boolean = false
    ) {
        context.setRightHandItem(mesh, texture, ownsMesh, isTool)
    }

    fun getRightHandItemMesh(): Mesh? = context.rightHandItem

    fun getRightHandItemTexture(): GLTexture? = context.rightHandItemTexture

    fun update(deltaTime: Float) {
        updateBodyYaw(deltaTime)
        locomotionScript.update(deltaTime)
        if (attacking) {
            attackScript.update(deltaTime)
        }
        updateHead()
        updateBody()
    }

    private fun updateBodyYaw(deltaTime: Float) {
        if (locomotionScript is RunAnimationScript || attacking) {
            context.bodyYaw = context.lookYaw
            return
        }

        val diff = shortestAngleDelta(context.bodyYaw, context.lookYaw)
        if (abs(diff) <= HEAD_BODY_YAW_LIMIT) return

        val targetBodyYaw = context.lookYaw - sign(diff) * HEAD_BODY_YAW_LIMIT
        val toTarget = shortestAngleDelta(context.bodyYaw, targetBodyYaw)
        val step = BODY_TURN_SPEED * deltaTime
        context.bodyYaw += toTarget.coerceIn(-step, step)
    }

    private fun updateHead() {
        val clampedPitch = context.lookPitch.coerceIn(HEAD_PITCH_DOWN_MAX, HEAD_PITCH_UP_MAX)
        context.applyPart(context.head, -clampedPitch, worldYaw = context.lookYaw)
    }

    private fun updateBody() {
        context.applyPart(context.body, 0f, worldYaw = context.bodyYaw)
    }

    private fun shortestAngleDelta(from: Float, to: Float): Float {
        var delta = (to - from) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return delta
    }
}
