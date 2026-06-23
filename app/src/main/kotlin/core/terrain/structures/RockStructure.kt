package core.terrain.structures

import com.gigapi.math.vector.IntVector3
import core.blocks.BlockType
import core.chunk.ChunkData
import core.terrain.TerrainGenerator
import core.terrain.level.StructureGenerator
import kotlin.math.sqrt
import kotlin.random.Random

class RockStructure: StructureGenerator() {

    override fun handling(
        seed: Int,
        chunkData: ChunkData,
        localPosition: IntVector3,
        worldPosition: IntVector3,
        heightNoice: Pair<Float, Int>
    ): Boolean {
        val heightNoice = heightNoice.second
        if (heightNoice >= TerrainGenerator.WORLD_SURFACE) return false
        val blockType = chunkData.getBlockByLocal(localPosition)
        if (heightNoice != worldPosition.y || blockType != BlockType.GRASS) return false
        val random = Random(
            seed + worldPosition.x * 31 + worldPosition.y * 7919 + worldPosition.z * 104729
        )
        if (random.nextFloat() > 0.0002f) return false
        generateRock(chunkData, localPosition, worldPosition, random)
        return true
    }

    private fun generateRock(
        chunkData: ChunkData,
        localPosition: IntVector3,
        worldPosition: IntVector3,
        random: Random,
    ) {
        val rockRadius = random.nextInt(1, 3)

        for (x in -rockRadius..rockRadius) {
            for (y in -rockRadius..rockRadius) {
                for (z in -rockRadius..rockRadius) {
                    val distance = sqrt((x * x + y * y + z * z).toDouble())

                    val noise = random.nextDouble() * 0.3

                    if (distance <= rockRadius + noise) {
                        chunkData.setBlockPending(
                            BlockType.STONE,
                            offset = IntVector3(x, y, z),
                            localPosition = localPosition,
                            worldPosition = worldPosition
                        )
                    }
                }
            }
        }
    }
}