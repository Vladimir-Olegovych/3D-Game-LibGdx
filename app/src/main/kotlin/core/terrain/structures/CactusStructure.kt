package core.terrain.structures

import com.gigapi.math.vector.IntVector3
import core.blocks.BlockType
import core.chunk.ChunkData
import core.terrain.TerrainGenerator
import core.terrain.level.StructureGenerator
import kotlin.math.sqrt
import kotlin.random.Random

class CactusStructure: StructureGenerator() {

    override fun handling(
        seed: Int,
        chunkData: ChunkData,
        localPosition: IntVector3,
        worldPosition: IntVector3,
        heightNoice: Pair<Float, Int>
    ): Boolean {
        val heightNoice = heightNoice.second
        val blockType = chunkData.getBlockByLocal(localPosition)
        if (heightNoice != worldPosition.y || blockType != BlockType.SAND) return false
        val random = Random(
            seed + worldPosition.x * 31 + worldPosition.y * 7919 + worldPosition.z * 104729
        )

        if (random.nextFloat() > 0.004f) return false
        generateCactus(chunkData, localPosition, worldPosition, random)
        return true
    }

    private fun generateCactus(
        chunkData: ChunkData,
        localPosition: IntVector3,
        worldPosition: IntVector3,
        random: Random,
    ) {
        val length = random.nextInt(4, 5)
        for (y in 0 .. length) {
            chunkData.setBlockPending(
                BlockType.CACTUS,
                offset = IntVector3(0, y + 1, 0),
                localPosition = localPosition,
                worldPosition = worldPosition
            )
        }
    }
}