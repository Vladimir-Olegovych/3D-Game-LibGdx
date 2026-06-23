package core.terrain.structures

import com.gigapi.math.vector.IntVector3
import core.blocks.BlockType
import core.chunk.ChunkData
import core.terrain.TerrainGenerator
import core.terrain.level.StructureGenerator
import kotlin.random.Random

class TestStructure: StructureGenerator() {

    override fun handling(
        seed: Int,
        chunkData: ChunkData,
        localPosition: IntVector3,
        worldPosition: IntVector3,
        heightNoice: Pair<Float, Int>
    ): Boolean {
        if (worldPosition.y < TerrainGenerator.WORLD_HEIGHT / 1.5) return false
        val blockType = chunkData.getBlockByLocal(localPosition)
        if (blockType != BlockType.AIR) return false
        val random = Random(
            seed + worldPosition.x * 31 + worldPosition.y * 7919 + worldPosition.z * 104729
        )
        if (random.nextFloat() > 0.0002f) return false
        generateTest(chunkData, localPosition, worldPosition, random)
        return true
    }

    private fun generateTest(
        chunkData: ChunkData,
        localPosition: IntVector3,
        worldPosition: IntVector3,
        random: Random,
    ) {
        val length = random.nextInt(3, 6)
        val rotation = random.nextInt(4)

        fun rotate(x: Int, z: Int): Pair<Int, Int> {
            return when (rotation) {
                0 -> x to z
                1 -> -z to x
                2 -> -x to -z
                3 -> z to -x
                else -> x to z
            }
        }

        for (i in 0 .. length) {
            val (rx, rz) = rotate(i, 0)
            chunkData.setBlockPending(
                BlockType.SAND,
                offset = IntVector3(rx, 0, rz),
                localPosition = localPosition,
                worldPosition = worldPosition
            )
        }

        for (z in -1 .. 1) {
            val (rx, rz) = rotate(length, z)
            chunkData.setBlockPending(
                BlockType.SAND,
                offset = IntVector3(rx, 0, rz),
                localPosition = localPosition,
                worldPosition = worldPosition
            )
        }
    }
}