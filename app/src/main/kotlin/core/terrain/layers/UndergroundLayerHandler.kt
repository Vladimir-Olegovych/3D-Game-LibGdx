package core.terrain.layers

import com.gigapi.math.vector.IntVector3
import core.blocks.BlockType
import core.chunk.ChunkData
import core.terrain.BlockLayerHandler
import core.terrain.TerrainGenerator.Companion.UNDERGROUND_HEIGHT
import kotlin.random.Random

class UndergroundLayerHandler(
    private val seed: Int,
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

        val random = Random(seed + worldPosition.x.toLong() * 31L + worldPosition.z.toLong() * 71L)
        val undergroundHeight = random.nextInt(
            UNDERGROUND_HEIGHT, (UNDERGROUND_HEIGHT + UNDERGROUND_HEIGHT / 1.5).toInt()
        )

        if (worldPosition.y <= undergroundHeight) {
            chunkData.setBlockByLocal(undergroundBlockType, localPosition)
        }
    }


}