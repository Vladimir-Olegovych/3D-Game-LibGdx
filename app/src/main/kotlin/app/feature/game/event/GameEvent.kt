package app.feature.game.event

import com.artemis.World
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.gigapi.math.vector.IntVector3
import com.gigapi.screens.mesh.MeshData
import com.gigapi.screens.mesh.RawMeshData
import core.chunk.ChunkData

sealed class GameEvent {
    class OnCreateChunkData(val chunkEntityId: Int, val chunkData: ChunkData): GameEvent()
    class OnCreateChunkMeshData(val chunkEntityId: Int, val meshData: MeshData): GameEvent()
    class OnCreateChunkRigidBody(val chunkEntityId: Int, val chunkData: ChunkData): GameEvent()
    class OnCreateMeshRigidBody(
        val entityId: Int,
        val position: Vector3,
        val rawMeshData: RawMeshData,
        val mass: Float = 1F,
        val fixedXZ: Boolean = false
    ): GameEvent()

    class OnRemoveChunkData(val chunkEntityId: Int): GameEvent()
    class OnRemoveChunkMeshData(val chunkEntityId: Int): GameEvent()
    class OnRemoveRigidBody(val entityId: Int): GameEvent()
    class OnRigidBodyTransformUpdate(val entityId: Int, val transform: Matrix4): GameEvent()
    class OnApplyForce(val entityId: Int, val force: Vector3): GameEvent()

    class LoadAdditionalChunksRequest(val world: World, val playerPosition: IntVector3): GameEvent()
    object GameWorldStarted: GameEvent()
}