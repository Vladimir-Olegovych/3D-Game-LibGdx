package core.terrain.layers

import com.gigapi.math.vector.IntVector3
import core.blocks.BlockType
import core.chunk.ChunkData
import core.chunk.ChunkManager
import core.terrain.BlockLayerHandler

class WaterLayerHandler: BlockLayerHandler() {

    override fun handling(
        chunkData: ChunkData,
        localPosition: IntVector3,
        worldPosition: IntVector3,
        heightNoice: Pair<Float, Int>
    ) {

        //chunkData.setBlockByLocal(block, localPosition)
    }


}