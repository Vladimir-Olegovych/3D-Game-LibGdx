package app.feature.game.event

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.gigapi.mesh.RawMeshData
import core.chunk.ChunkData

sealed class PhysicsEvent {
    class OnUpdateChunkData(val chunkEntityId: Int, val chunkData: ChunkData): PhysicsEvent()
    class OnCreateChunkRigidBody(val chunkEntityId: Int, val chunkData: ChunkData): PhysicsEvent()
    class OnRemoveRigidBody(val entityId: Int): PhysicsEvent()
    class OnRigidBodyTransformUpdate(val entityId: Int, val transform: Matrix4): PhysicsEvent()
    class OnApplyLinearForce(val entityId: Int, val ignoreYLinear: Boolean, val force: Vector3): PhysicsEvent()
    class OnApplyForce(val entityId: Int, val force: Vector3): PhysicsEvent()

    class OnCreateMeshRigidBody(
        val entityId: Int,
        val position: Vector3,
        val rawMeshData: RawMeshData,
        val mass: Float = 1F,
        val friction: Float = 0.5f,
        val restitution: Float = 0.5f,
        val activationState: Int? = null,
        val fixedXZ: Boolean = false
    ): PhysicsEvent()

    class OnRayCastRequest(
        val requestId: Long,
        val requestOwner: Int,
        val from: Vector3,
        val direction: Vector3,
        val maxDistance: Float = 10f
    ) : PhysicsEvent()

    class OnRayCastResult(
        val requestId: Long,
        val requestOwner: Int,
        val hasHit: Boolean,
        val direction: Vector3,
        val hitPoint: Vector3,
        val hitNormal: Vector3,
        val hitEntityId: Int? = null
    ) : PhysicsEvent()

}