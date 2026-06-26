package core.terrain.layers

import com.gigapi.math.vector.IntVector3
import core.chunk.ChunkData
import core.terrain.BlockLayerHandler
import core.terrain.level.StructureGenerator
import kotlin.random.Random

class StructureLayerHandler(
    private val seed: Int,
    private val structureList: List<StructureGenerator>
): BlockLayerHandler() {

    private val random = Random(seed)
    private val structureSeeds = Array(structureList.size) { random.nextInt() }

    override fun handling(
        chunkData: ChunkData,
        localPosition: IntVector3,
        worldPosition: IntVector3,
        heightNoice: Pair<Float, Int>
    ) {
        structureList.forEachIndexed { index, generator ->
            val hasStructure = generator.handling(
                structureSeeds[index],
                chunkData,
                localPosition,
                worldPosition,
                heightNoice
            )
            if (hasStructure) return
        }
    }


}