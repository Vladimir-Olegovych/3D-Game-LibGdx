package app.feature.game.event

import com.artemis.World
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.gigapi.math.vector.IntVector3
import core.chunk.ChunkData
import core.mesh.MeshData
import core.mesh.RawMeshData

sealed class GameEvent {
    class OnCreateChunkData(val chunkEntityId: Int, val chunkData: ChunkData)
    class OnCreateChunkMeshData(val chunkEntityId: Int, val meshData: MeshData)
    class OnCreateChunkRigidBody(val chunkEntityId: Int, val chunkData: ChunkData)
    class OnCreateMeshRigidBody(val entityId: Int, val position: Vector3, val rawMeshData: RawMeshData, val mass: Float = 1F)

    class OnRemoveChunkData(val chunkEntityId: Int)
    class OnRemoveChunkMeshData(val chunkEntityId: Int)
    class OnRemoveRigidBody(val entityId: Int)
    class OnRigidBodyTransformUpdate(val entityId: Int, val transform: Matrix4)

    class LoadAdditionalChunksRequest(val world: World, val playerPosition: IntVector3)

}