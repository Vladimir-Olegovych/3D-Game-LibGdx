package core.terrain.layers

import com.gigapi.math.vector.IntVector3
import core.blocks.BlockType
import core.chunk.ChunkData
import core.chunk.ChunkManager
import core.terrain.BlockLayerHandler
import kotlin.math.roundToInt

class SurfaceLayerHandler(
    private val surfaceBlockType: BlockType = BlockType.GRASS,
    private val underSurfaceBlockType: BlockType = BlockType.DIRT,
    private val surfaceLevel: Int = 3
): BlockLayerHandler() {

    override fun handling(
        chunkData: ChunkData,
        localPosition: IntVector3,
        worldPosition: IntVector3,
        surfaceHeightNoise: Int
    ) {
        val block = when (worldPosition.y) {
            in surfaceHeightNoise - surfaceLevel..<surfaceHeightNoise -> underSurfaceBlockType
            surfaceHeightNoise -> surfaceBlockType
            else -> BlockType.AIR
        }
        chunkData.setBlockByLocal(block, localPosition)
    }


}