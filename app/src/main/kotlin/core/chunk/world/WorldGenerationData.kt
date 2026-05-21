package core.chunk.world

import com.gigapi.math.vector.IntVector3

class WorldGenerationData(
    val chunkPositionsToCreate: List<IntVector3>,
    val chunkDataPositionsToCreate: List<IntVector3>,
    val chunkPositionsToRemove: List<IntVector3>,
    val chunkDataToRemove: List<IntVector3>
)