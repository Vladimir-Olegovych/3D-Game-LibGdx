package core.animator

import com.badlogic.gdx.graphics.GLTexture
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.gigapi.mesh.SubMeshRenderData

class AnimatedPart(
    val subMesh: SubMeshRenderData,
    val rest: FloatArray,
    val pivot: Vector3
)

class AnimationContext(
    val body: AnimatedPart,
    val leftHand: AnimatedPart,
    val rightHand: AnimatedPart,
    val head: AnimatedPart,
    val rightLeg: AnimatedPart,
    val leftLeg: AnimatedPart,
    val rightHandTip: Vector3
) {
    companion object {
        private const val BLOCK_HAND_SCALE = 0.75f
        private const val TOOL_HAND_SCALE = 0.95f
        private const val BLOCK_LOCAL_PITCH = -25f
        private const val BLOCK_LOCAL_YAW = 45f
        private const val BLOCK_LOCAL_ROLL = -20f
        private const val TOOL_LOCAL_PITCH = 45f
        private const val TOOL_LOCAL_YAW = 90f
        private const val TOOL_LOCAL_ROLL = 45f

        fun captureRest(mesh: Mesh): FloatArray {
            val floatsPerVertex = mesh.vertexSize / 4
            val vertices = FloatArray(mesh.numVertices * floatsPerVertex)
            mesh.getVertices(vertices)
            return vertices
        }

        fun computePivot(vertices: FloatArray, mesh: Mesh, useMaxY: Boolean): Vector3 {
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

    var bodyYaw = 0f
    var lookYaw = 0f
    var lookPitch = 0f
    var rightArmOverridden = false

    var rightHandItem: Mesh? = null
        private set
    var rightHandItemTexture: GLTexture? = null
        private set
    private var rightHandItemRest: FloatArray? = null
    private var ownsRightHandItem = false
    private var rightHandItemIsTool = false

    private val workVertices = FloatArray(
        maxOf(
            body.rest.size,
            leftHand.rest.size,
            rightHand.rest.size,
            head.rest.size,
            rightLeg.rest.size,
            leftLeg.rest.size
        )
    )
    private var itemWorkVertices = FloatArray(0)
    private val transformMatrix = Matrix4()
    private val negPivot = Vector3()

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

    fun applyPart(
        part: AnimatedPart,
        localPitchDegrees: Float,
        localYawDegrees: Float = 0f,
        worldYaw: Float = bodyYaw,
        localOffsetX: Float = 0f,
        localOffsetY: Float = 0f,
        localOffsetZ: Float = 0f
    ) {
        val mesh = part.subMesh.mesh
        val rest = part.rest
        System.arraycopy(rest, 0, workVertices, 0, rest.size)
        mesh.setVertices(workVertices, 0, rest.size)

        val hasLocal = localPitchDegrees != 0f || localYawDegrees != 0f ||
            localOffsetX != 0f || localOffsetY != 0f || localOffsetZ != 0f
        if (!hasLocal && worldYaw == 0f) return

        val pivot = part.pivot
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
                .translate(localOffsetX, localOffsetY, localOffsetZ)
                .translate(negPivot)
        }
        mesh.transform(transformMatrix)
    }

    fun applyRightHandItem(
        localPitchDegrees: Float,
        localYawDegrees: Float = 0f,
        worldYaw: Float = bodyYaw,
        localOffsetX: Float = 0f,
        localOffsetY: Float = 0f,
        localOffsetZ: Float = 0f
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
        val pivot = rightHand.pivot

        transformMatrix.idt()
        if (worldYaw != 0f) {
            transformMatrix.rotate(Vector3.Y, worldYaw)
        }
        negPivot.set(-pivot.x, -pivot.y, -pivot.z + 0.2f)
        transformMatrix
            .translate(pivot)
            .rotate(Vector3.Y, localYawDegrees)
            .rotate(Vector3.X, localPitchDegrees)
            .translate(localOffsetX, localOffsetY, localOffsetZ)
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
}
