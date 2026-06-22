package core.terrain.layers

import com.gigapi.math.vector.IntVector3
import core.blocks.BlockType
import core.chunk.ChunkData
import core.terrain.BlockLayerHandler
import core.terrain.level.StructureGenerator

class StructureLayerHandler(
    private val structureList: List<StructureGenerator>
): BlockLayerHandler() {

    override fun handling(
        chunkData: ChunkData,
        localPosition: IntVector3,
        worldPosition: IntVector3,
        heightNoice: Pair<Float, Int>
    ) {
        structureList.forEach { it.handling(chunkData, localPosition, worldPosition, heightNoice) }
    }


}