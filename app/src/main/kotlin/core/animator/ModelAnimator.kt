package core.animator

import com.badlogic.gdx.graphics.GLTexture
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.gigapi.mesh.BlenderRenderData
import com.gigapi.mesh.SubMeshRenderData
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sin

class ModelAnimator(
    private val blenderRenderData: BlenderRenderData
) {
    companion object {
        const val ANIM_IDLE = "idle"
        const val ANIM_MOVE = "move"

        private const val MOVE_SWING_ANGLE = 45f
        private const val MOVE_SPEED = 8f
        private const val IDLE_SWING_ANGLE = 8f
        private const val IDLE_SPEED = 1.5f

        private const val HEAD_PITCH_DOWN_MAX = -45f
        private const val HEAD_PITCH_UP_MAX = 89f
        private const val HEAD_BODY_YAW_LIMIT = 45f
        private const val BODY_TURN_SPEED = 360f

        // Held item: Minecraft-like third-person / view size
        private const val BLOCK_HAND_SCALE = 0.75f
        private const val TOOL_HAND_SCALE = 0.95f
        private const val BLOCK_LOCAL_PITCH = -25f
        private const val BLOCK_LOCAL_YAW = 45f
        private const val BLOCK_LOCAL_ROLL = -20f
        // ItemType textures point along +X; lean after uprighting in rest pose
        private const val TOOL_LOCAL_PITCH = 45f
        private const val TOOL_LOCAL_YAW = 90f
        private const val TOOL_LOCAL_ROLL = 45f
    }

    private var rightHandItem: Mesh? = null
    private var rightHandItemTexture: GLTexture? = null
    private var rightHandItemRest: FloatArray? = null
    private var ownsRightHandItem = false
    private var rightHandItemIsTool = false
    private var itemWorkVertices = FloatArray(0)

    private val body = blenderRenderData.subMeshes[0]
    private val leftHand = blenderRenderData.subMeshes[1]
    private val rightHand = blenderRenderData.subMeshes[2]
    private val head = blenderRenderData.subMeshes[3]
    private val rightLeg = blenderRenderData.subMeshes[4]
    private val leftLeg = blenderRenderData.subMeshes[5]

    private val bodyRest = captureRest(body.mesh)
    private val leftHandRest = captureRest(leftHand.mesh)
    private val rightHandRest = captureRest(rightHand.mesh)
    private val headRest = captureRest(head.mesh)
    private val rightLegRest = captureRest(rightLeg.mesh)
    private val leftLegRest = captureRest(leftLeg.mesh)

    private val leftHandPivot = computePivot(leftHandRest, leftHand.mesh, useMaxY = true)
    private val rightHandPivot = computePivot(rightHandRest, rightHand.mesh, useMaxY = true)
    private val rightHandTip = computePivot(rightHandRest, rightHand.mesh, useMaxY = false)
    private val headPivot = computePivot(headRest, head.mesh, useMaxY = false)
    private val rightLegPivot = computePivot(rightLegRest, rightLeg.mesh, useMaxY = true)
    private val leftLegPivot = computePivot(leftLegRest, leftLeg.mesh, useMaxY = true)

    private val workVertices = FloatArray(
        maxOf(
            bodyRest.size,
            leftHandRest.size,
            rightHandRest.size,
            headRest.size,
            rightLegRest.size,
            leftLegRest.size
        )
    )
    private val transformMatrix = Matrix4()
    private val negPivot = Vector3()

    private var animTime = 0f
    private var currentAnimation = ANIM_IDLE
    private var lookYaw = 0f
    private var lookPitch = 0f
    private var bodyYaw = 0f
    private var lookInitialized = false

    fun playAnimation(name: String) {
        if (currentAnimation == name) return
        if (name == ANIM_MOVE) {
            bodyYaw = lookYaw
        }
        currentAnimation = name
        animTime = 0f
    }

    fun getCurrentAnimation(): String = currentAnimation

    fun setLookDirection(yaw: Float, pitch: Float) {
        lookYaw = yaw
        lookPitch = pitch
        if (!lookInitialized) {
            bodyYaw = yaw
            lookInitialized = true
        }
    }

    fun setRightHandItem(
        mesh: Mesh?,
        texture: GLTexture? = null,
        ownsMesh: Boolean = true,
        isTool: Boolean = false
    ) {
        if (ownsRightHandItem) {
            rightHandItem?.dispose()
        }
        rightHandItem = mesh
        rightHandItemTexture = texture
        ownsRightHandItem = ownsMesh && mesh != null
        rightHandItemIsTool = isTool
        rightHandItemRest = mesh?.let { prepareRightHandItemRest(it, isTool) }
    }

    fun getRightHandItemMesh(): Mesh? = rightHandItem

    fun getRightHandItemTexture(): GLTexture? = rightHandItemTexture

    fun update(deltaTime: Float) {
        updateBodyYaw(deltaTime)
        when (currentAnimation) {
            ANIM_IDLE -> updateIdle(deltaTime)
            ANIM_MOVE -> updateMove(deltaTime)
            else -> updateIdle(deltaTime)
        }
        updateHead()
        updateBody()
    }

    private fun updateBodyYaw(deltaTime: Float) {
        if (currentAnimation == ANIM_MOVE) {
            bodyYaw = lookYaw
            return
        }

        val diff = shortestAngleDelta(bodyYaw, lookYaw)
        if (abs(diff) <= HEAD_BODY_YAW_LIMIT) return

        val targetBodyYaw = lookYaw - sign(diff) * HEAD_BODY_YAW_LIMIT
        val toTarget = shortestAngleDelta(bodyYaw, targetBodyYaw)
        val step = BODY_TURN_SPEED * deltaTime
        bodyYaw += toTarget.coerceIn(-step, step)
    }

    private fun updateIdle(deltaTime: Float) {
        animTime += deltaTime * IDLE_SPEED
        val angle = sin(animTime.toDouble()).toFloat() * IDLE_SWING_ANGLE

        applyPartTransform(rightHand, rightHandRest, rightHandPivot, angle, 0f, bodyYaw)
        applyRightHandItemTransform(angle, 0f, bodyYaw)
        applyPartTransform(leftHand, leftHandRest, leftHandPivot, -angle, 0f, bodyYaw)
        applyPartTransform(rightLeg, rightLegRest, rightLegPivot, 0f, 0f, bodyYaw)
        applyPartTransform(leftLeg, leftLegRest, leftLegPivot, 0f, 0f, bodyYaw)
    }

    private fun updateMove(deltaTime: Float) {
        animTime += deltaTime * MOVE_SPEED
        val angle = sin(animTime.toDouble()).toFloat() * MOVE_SWING_ANGLE

        applyPartTransform(rightHand, rightHandRest, rightHandPivot, angle, 0f, bodyYaw)
        applyRightHandItemTransform(angle, 0f, bodyYaw)
        applyPartTransform(leftHand, leftHandRest, leftHandPivot, -angle, 0f, bodyYaw)
        applyPartTransform(rightLeg, rightLegRest, rightLegPivot, -angle, 0f, bodyYaw)
        applyPartTransform(leftLeg, leftLegRest, leftLegPivot, angle, 0f, bodyYaw)
    }

    private fun updateHead() {
        val clampedPitch = lookPitch.coerceIn(HEAD_PITCH_DOWN_MAX, HEAD_PITCH_UP_MAX)
        applyPartTransform(head, headRest, headPivot, -clampedPitch, 0f, lookYaw)
    }

    private fun updateBody() {
        applyPartTransform(body, bodyRest, Vector3.Zero, 0f, 0f, bodyYaw)
    }

    private fun applyPartTransform(
        subMesh: SubMeshRenderData,
        rest: FloatArray,
        pivot: Vector3,
        localPitchDegrees: Float,
        localYawDegrees: Float,
        worldYaw: Float
    ) {
        val mesh = subMesh.mesh
        System.arraycopy(rest, 0, workVertices, 0, rest.size)
        mesh.setVertices(workVertices, 0, rest.size)

        val hasLocal = localPitchDegrees != 0f || localYawDegrees != 0f
        if (!hasLocal && worldYaw == 0f) return

        transformMatrix.idt()
        if (worldYaw != 0f) {
            transformMatrix.rotate(Vector3.Y, worldYaw)
        }
        if (hasLocal) {
            negPivot.set(-pivot.x, -pivot.y, -pivot.z)
            transformMatrix
                .translate(pivot)
                .rotate(Vector3.Y, localYawDegrees)
                .rotate(Vector3.X, localPitchDegrees)
                .translate(negPivot)
        }
        mesh.transform(transformMatrix)
    }

    private fun applyRightHandItemTransform(
        localPitchDegrees: Float,
        localYawDegrees: Float,
        worldYaw: Float
    ) {
        val mesh = rightHandItem ?: return
        val rest = rightHandItemRest ?: return

        if (itemWorkVertices.size < rest.size) {
            itemWorkVertices = FloatArray(rest.size)
        }
        System.arraycopy(rest, 0, itemWorkVertices, 0, rest.size)
        mesh.setVertices(itemWorkVertices, 0, rest.size)

        val itemPitch = if (rightHandItemIsTool) TOOL_LOCAL_PITCH else BLOCK_LOCAL_PITCH
        val itemYaw = if (rightHandItemIsTool) TOOL_LOCAL_YAW else BLOCK_LOCAL_YAW
        val itemRoll = if (rightHandItemIsTool) TOOL_LOCAL_ROLL else BLOCK_LOCAL_ROLL

        transformMatrix.idt()
        if (worldYaw != 0f) {
            transformMatrix.rotate(Vector3.Y, worldYaw)
        }
        negPivot.set(-rightHandPivot.x, -rightHandPivot.y, -rightHandPivot.z + 0.2f)

        transformMatrix
            .translate(rightHandPivot)
            .rotate(Vector3.Y, localYawDegrees)
            .rotate(Vector3.X, localPitchDegrees)
            .translate(negPivot)
            .translate(rightHandTip)
            .rotate(Vector3.Z, itemRoll)
            .rotate(Vector3.Y, itemYaw)
            .rotate(Vector3.X, itemPitch)
        mesh.transform(transformMatrix)
    }

    private fun prepareRightHandItemRest(mesh: Mesh, isTool: Boolean): FloatArray {
        val rest = captureRest(mesh)
        val stride = mesh.vertexSize / 4
        var minX = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY

        var i = 0
        while (i < rest.size) {
            val x = rest[i]
            val y = rest[i + 1]
            val z = rest[i + 2]
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
            if (z < minZ) minZ = z
            if (z > maxZ) maxZ = z
            i += stride
        }

        val centerX = (minX + maxX) * 0.5f
        val centerY = (minY + maxY) * 0.5f
        val centerZ = (minZ + maxZ) * 0.5f
        val scale = if (isTool) TOOL_HAND_SCALE else BLOCK_HAND_SCALE

        i = 0
        while (i < rest.size) {
            rest[i] = (rest[i] - centerX) * scale
            rest[i + 1] = (rest[i + 1] - centerY) * scale
            rest[i + 2] = (rest[i + 2] - centerZ) * scale
            i += stride
        }

        if (isTool) {
            // ItemType sprites point along +X; rotate +90° around Z so tip is +Y
            i = 0
            while (i < rest.size) {
                val x = rest[i]
                val y = rest[i + 1]
                rest[i] = -y
                rest[i + 1] = x
                i += stride
            }

            var gripY = Float.POSITIVE_INFINITY
            i = 1
            while (i < rest.size) {
                if (rest[i] < gripY) gripY = rest[i]
                i += stride
            }
            i = 1
            while (i < rest.size) {
                rest[i] -= gripY
                i += stride
            }
        }

        return rest
    }

    private fun shortestAngleDelta(from: Float, to: Float): Float {
        var delta = (to - from) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return delta
    }

    private fun captureRest(mesh: Mesh): FloatArray {
        val floatsPerVertex = mesh.vertexSize / 4
        val vertices = FloatArray(mesh.numVertices * floatsPerVertex)
        mesh.getVertices(vertices)
        return vertices
    }

    private fun computePivot(vertices: FloatArray, mesh: Mesh, useMaxY: Boolean): Vector3 {
        val stride = mesh.vertexSize / 4
        var minX = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY

        var i = 0
        while (i < vertices.size) {
            val x = vertices[i]
            val y = vertices[i + 1]
            val z = vertices[i + 2]
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
            if (z < minZ) minZ = z
            if (z > maxZ) maxZ = z
            i += stride
        }

        val pivotY = if (useMaxY) maxY else minY
        return Vector3((minX + maxX) * 0.5f, pivotY, (minZ + maxZ) * 0.5f)
    }
}
