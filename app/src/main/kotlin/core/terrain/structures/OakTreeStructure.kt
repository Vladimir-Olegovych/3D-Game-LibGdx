package core.terrain.structures

import com.gigapi.math.vector.IntVector3
import core.blocks.BlockType
import core.chunk.ChunkData
import core.terrain.level.StructureGenerator
import kotlin.math.sqrt
import kotlin.random.Random

class OakTreeStructure: StructureGenerator() {

    override fun handling(
        seed: Int,
        chunkData: ChunkData,
        localPosition: IntVector3,
        worldPosition: IntVector3,
        heightNoice: Pair<Float, Int>
    ): Boolean {
        val heightNoice = heightNoice.second
        val blockType = chunkData.getBlockByLocal(localPosition)
        if (heightNoice != worldPosition.y || blockType != BlockType.GRASS) return false
        val random = Random(
            seed + worldPosition.x * 31 + worldPosition.y * 7919 + worldPosition.z * 104729
        )

        if (random.nextFloat() > 0.02f) return false
        generateTree(chunkData, localPosition, worldPosition, random)
        return true
    }

    private fun generateTree(
        chunkData: ChunkData,
        localPosition: IntVector3,
        worldPosition: IntVector3,
        random: Random,
    ) {
        val treeHeight = random.nextInt(5, 9)

        val trunkOffset = random.nextInt(-1, 1)
        for (y in 0 until treeHeight) {
            val offsetX = if (y > treeHeight / 2) trunkOffset else 0
            val offsetZ = if (y > treeHeight / 2) trunkOffset else 0

            chunkData.setBlockPending(
                BlockType.OAK_WOOD,
                offset = IntVector3(offsetX, y + 1, offsetZ),
                localPosition = localPosition,
                worldPosition = worldPosition
            )
        }

        val leafRadius = when (treeHeight) {
            in 5..6 -> 2
            in 7..8 -> 3
            else -> 2
        }

        val leafStart = treeHeight - 2

        for (y in leafStart until treeHeight + 2) {
            val radiusAtLevel = when {
                y == treeHeight + 1 -> 1
                y == treeHeight -> 2
                y >= treeHeight - 1 -> leafRadius
                else -> leafRadius - 1
            }

            for (x in -radiusAtLevel..radiusAtLevel) {
                for (z in -radiusAtLevel..radiusAtLevel) {
                    val distance = sqrt((x * x + z * z).toDouble())
                    if (distance > radiusAtLevel) continue

                    if (random.nextFloat() > 0.85f && distance > 0) continue

                    val leafType = if (distance > radiusAtLevel - 0.5) {
                        if (random.nextFloat() > 0.3f) BlockType.LEAVES else null
                    } else {
                        BlockType.LEAVES
                    }

                    leafType?.let {
                        chunkData.setBlockPending(
                            it,
                            offset = IntVector3(x, y + 1, z),
                            localPosition = localPosition,
                            worldPosition = worldPosition
                        )
                    }
                }
            }
        }

        val branchesCount = random.nextInt(1, 3)
        repeat(branchesCount) {
            val branchHeight = random.nextInt(treeHeight / 2, treeHeight - 1)
            val branchDirection = random.nextInt(0, 4)
            val branchLength = random.nextInt(2, 4)

            var xOffset = 0
            var zOffset = 0

            when (branchDirection) {
                0 -> xOffset = 1
                1 -> xOffset = -1
                2 -> zOffset = 1
                3 -> zOffset = -1
            }

            for (i in 1..branchLength) {
                val posX = xOffset * i
                val posZ = zOffset * i

                chunkData.setBlockPending(
                    BlockType.OAK_WOOD,
                    offset = IntVector3(posX, branchHeight + i/2, posZ),
                    localPosition = localPosition,
                    worldPosition = worldPosition
                )

                if (i == branchLength) {
                    for (dx in -1..1) {
                        for (dz in -1..1) {
                            if (dx == 0 && dz == 0) continue
                            if (random.nextFloat() > 0.4f) continue

                            chunkData.setBlockPending(
                                BlockType.LEAVES,
                                offset = IntVector3(posX + dx, branchHeight + i/2, posZ + dz),
                                localPosition = localPosition,
                                worldPosition = worldPosition
                            )
                        }
                    }
                }
            }
        }
    }
}