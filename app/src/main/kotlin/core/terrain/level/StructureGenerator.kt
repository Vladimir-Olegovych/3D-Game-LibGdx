package core.terrain.level

import com.gigapi.math.vector.IntVector3
import core.chunk.ChunkData


abstract class StructureGenerator {

    abstract fun handling(
        seed: Int,
        chunkData: ChunkData,
        localPosition: IntVector3,
        worldPosition: IntVector3,
        heightNoice: Pair<Float, Int>
    ): Boolean

}