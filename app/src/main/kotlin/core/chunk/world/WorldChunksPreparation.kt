package core.chunk.world

import com.gigapi.math.vector.IntVector3

class WorldChunksPreparation(
    val playerPosition: IntVector3,
    val toCreateMeshDates: List<IntVector3>,
    val toCreateChunkDates: List<IntVector3>
)