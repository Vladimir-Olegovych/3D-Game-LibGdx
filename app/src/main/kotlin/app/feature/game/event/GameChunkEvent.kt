package app.feature.game.event

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.gigapi.mesh.MeshData
import core.chunk.world.WorldChunksPreparation

sealed class GameChunkEvent {
    class OnRequireChunkEntityIds(val worldChunksPreparation: WorldChunksPreparation)
    class OnUpdateChunkMeshData(val chunkEntityId: Int, val meshData: MeshData): GameChunkEvent()
    class OnCreateChunkTransform(val chunkEntityId: Int, val transform: Matrix4): GameChunkEvent()
    class OnCreateChunkMeshData(val chunkEntityId: Int, val meshData: MeshData): GameChunkEvent()

    class OnRemoveChunkData(val chunkEntityId: Int): GameChunkEvent()
    class OnRemoveChunkMeshData(val chunkEntityId: Int): GameChunkEvent()

    class GameWorldStarted(val position: Vector3): ChunkEvent()
}