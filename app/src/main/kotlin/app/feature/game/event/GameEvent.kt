package app.feature.game.event

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.gigapi.math.vector.IntVector3
import com.gigapi.screens.mesh.MeshData
import com.gigapi.screens.mesh.RawMeshData
import core.chunk.ChunkData
import core.chunk.world.WorldGenerationData

sealed class GameEvent {

    class OnUpdateChunkData(val chunkEntityId: Int, val chunkData: ChunkData): GameEvent()
    class OnUpdateChunkMeshData(val chunkEntityId: Int, val meshData: MeshData): GameEvent()
    class OnCreateChunkTransform(val chunkEntityId: Int, val transform: Matrix4): GameEvent()
    class OnCreateChunkMeshData(val chunkEntityId: Int, val meshData: MeshData): GameEvent()
    class OnCreateChunkRigidBody(val chunkEntityId: Int, val chunkData: ChunkData): GameEvent()
    class OnCreateMeshRigidBody(
        val entityId: Int,
        val position: Vector3,
        val rawMeshData: RawMeshData,
        val mass: Float = 1F,
        val friction: Float = 0.5f,
        val restitution: Float = 0.5f,
        val activationState: Int? = null,
        val fixedXZ: Boolean = false
    ): GameEvent()

    class OnRemoveChunkData(val chunkEntityId: Int): GameEvent()
    class OnRemoveChunkMeshData(val chunkEntityId: Int): GameEvent()
    class OnRemoveRigidBody(val entityId: Int): GameEvent()
    class OnRigidBodyTransformUpdate(val entityId: Int, val transform: Matrix4): GameEvent()
    class OnApplyLinearForce(val entityId: Int, val ignoreYLinear: Boolean, val force: Vector3): GameEvent()
    class OnApplyForce(val entityId: Int, val force: Vector3): GameEvent()

    class OnRayCastRequest(
        val requestId: Long,
        val from: Vector3,
        val direction: Vector3,
        val maxDistance: Float = 10f
    ) : GameEvent()

    class OnRayCastResult(
        val requestId: Long,
        val hasHit: Boolean,
        val direction: Vector3,
        val hitPoint: Vector3,
        val hitNormal: Vector3,
        val hitEntityId: Int? = null
    ) : GameEvent()
}