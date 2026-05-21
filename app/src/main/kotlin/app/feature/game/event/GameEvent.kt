package app.feature.game.event

import com.artemis.World
import com.gigapi.math.vector.IntVector3
import core.chunk.ChunkData
import core.mesh.MeshData

sealed class GameEvent {
    class OnCreateChunkData(val chunkEntityId: Int, val chunkData: ChunkData)
    class OnCreateChunkMeshData(val chunkEntityId: Int, val meshData: MeshData)
    class OnCreateChunkRigidBody(val chunkEntityId: Int, val chunkData: ChunkData)

    class OnRemoveChunkData(val chunkEntityId: Int)
    class OnRemoveChunkMeshData(val chunkEntityId: Int)
    class OnRemoveChunkRigidBody(val chunkEntityId: Int)

    class LoadAdditionalChunksRequest(val world: World, val playerPosition: IntVector3)

}