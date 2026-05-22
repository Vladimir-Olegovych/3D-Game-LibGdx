package core.terrain

import com.gigapi.math.vector.IntVector3
import core.chunk.ChunkData

abstract class BlockLayerHandler {

    private var next: BlockLayerHandler? = null

    fun setNext(handler: BlockLayerHandler): BlockLayerHandler {
        this.next = handler
        return handler
    }

    fun handle(
        chunkData: ChunkData,
        localPosition: IntVector3,
        worldPosition: IntVector3,
        surfaceHeightNoise: Int
    ) {
        handling(chunkData, localPosition, worldPosition, surfaceHeightNoise)
        next?.handle(chunkData, localPosition, worldPosition, surfaceHeightNoise)
    }

    protected abstract fun handling(
        chunkData: ChunkData,
        localPosition: IntVector3,
        worldPosition: IntVector3,
        surfaceHeightNoise: Int
    )
}