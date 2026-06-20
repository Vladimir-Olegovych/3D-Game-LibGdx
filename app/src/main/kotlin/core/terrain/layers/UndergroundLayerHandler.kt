package core.terrain.layers

import com.gigapi.math.vector.IntVector3
import core.blocks.BlockType
import core.chunk.ChunkData
import core.terrain.BlockLayerHandler
import core.terrain.TerrainGenerator.Companion.UNDERGROUND_HEIGHT

class UndergroundLayerHandler(
    private val undergroundBlockType: BlockType = BlockType.STONE
): BlockLayerHandler() {
    override fun handling(
        chunkData: ChunkData,
        localPosition: IntVector3,
        worldPosition: IntVector3,
        heightNoice: Pair<Float, Int>
    ) {
        val heightNoice = heightNoice.second
        if(worldPosition.y > heightNoice) return

        if (worldPosition.y <= UNDERGROUND_HEIGHT) {
            chunkData.setBlockByLocal(undergroundBlockType, localPosition)
        }
    }


}