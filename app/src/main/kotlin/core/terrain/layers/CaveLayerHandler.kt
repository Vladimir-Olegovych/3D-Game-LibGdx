package core.terrain.layers

import com.gigapi.math.vector.IntVector3
import core.blocks.BlockType
import core.chunk.ChunkData
import core.terrain.BlockLayerHandler
import core.terrain.TerrainGenerator.Companion.CAVE_LEVEL
import core.terrain.TerrainGenerator.Companion.CAVE_THRESHOLD

class CaveLayerHandler: BlockLayerHandler() {

    override fun handling(
        chunkData: ChunkData,
        localPosition: IntVector3,
        worldPosition: IntVector3,
        heightNoice: Pair<Float, Int>
    ) {
        val heightNoice = heightNoice.first
        if (worldPosition.y >= CAVE_LEVEL || !isCave(heightNoice)) return
        chunkData.setBlockByLocal(BlockType.AIR, localPosition)
    }

    companion object {
        fun isCave(noiseValue: Float): Boolean {
            return noiseValue > CAVE_THRESHOLD
        }
    }

}