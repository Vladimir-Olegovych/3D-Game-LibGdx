package core.chunk.world

import com.gigapi.math.vector.IntVector3

class WorldGenerationData(
    val playerPosition: IntVector3,
    val allChunkDataPositionsAround: List<IntVector3>,
    val allMeshDataPositionsAround: List<IntVector3>,
)