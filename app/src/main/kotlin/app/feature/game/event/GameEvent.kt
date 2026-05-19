package app.feature.game.event

import core.chunk.ChunkData
import core.mesh.MeshData

sealed class GameEvent {
    class OnCreateChunkData(val chunkEntityId: Int, val chunkData: ChunkData)
    class OnCreateChunkMeshData(val chunkEntityId: Int, val meshData: MeshData)
}