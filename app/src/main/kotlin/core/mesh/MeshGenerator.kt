package core.mesh

import com.gigapi.math.vector.IntVector3
import com.gigapi.mesh.RawMeshData
import core.chunk.ChunkData

interface MeshGenerator {
    fun createMesh(
        chunkMap: Map<IntVector3, ChunkData>,
        chunkData: ChunkData
    ): RawMeshData
}
