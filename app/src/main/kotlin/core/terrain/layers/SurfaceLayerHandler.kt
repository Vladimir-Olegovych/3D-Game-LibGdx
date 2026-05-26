package core.terrain.layers

import com.gigapi.math.vector.IntVector3
import core.blocks.BlockType
import core.chunk.ChunkData
import core.terrain.BlockLayerHandler
import core.terrain.TerrainGenerator

class SurfaceLayerHandler(
    private val surfaceBlockType: BlockType = BlockType.GRASS,
    private val underSurfaceBlockType: BlockType = BlockType.DIRT
): BlockLayerHandler() {

    override fun handling(
        chunkData: ChunkData,
        localPosition: IntVector3,
        worldPosition: IntVector3,
        heightNoice: Pair<Float, Int>
    ) {
        val heightNoice = heightNoice.second
        val block = when (worldPosition.y) {
            in TerrainGenerator.UNDERGROUND_HEIGHT ..< heightNoice -> underSurfaceBlockType
            heightNoice -> surfaceBlockType
            else -> BlockType.AIR
        }
        chunkData.setBlockByLocal(block, localPosition)
    }


}