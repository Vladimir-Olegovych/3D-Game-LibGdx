package core.terrain.layers

import com.gigapi.math.vector.IntVector3
import core.blocks.BlockType
import core.chunk.ChunkData
import core.terrain.BlockLayerHandler
import core.terrain.TerrainGenerator

class ShadowLayerHandler: BlockLayerHandler() {

    override fun handling(
        chunkData: ChunkData,
        localPosition: IntVector3,
        worldPosition: IntVector3,
        heightNoice: Pair<Float, Int>
    ) {
        if (worldPosition.y <= TerrainGenerator.CAVE_LEVEL) return
        val heightNoice = heightNoice.second
        if (worldPosition.y <= heightNoice) return
        chunkData.setDefaultShadowValue(1f, localPosition)
    }


}