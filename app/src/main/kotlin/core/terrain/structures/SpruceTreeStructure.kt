package core.terrain.structures

import com.gigapi.math.vector.IntVector3
import core.blocks.BlockType
import core.chunk.ChunkData
import core.terrain.level.StructureGenerator
import kotlin.math.sqrt
import kotlin.random.Random

class SpruceTreeStructure : StructureGenerator() {

    override fun handling(
        seed: Int,
        chunkData: ChunkData,
        localPosition: IntVector3,
        worldPosition: IntVector3,
        heightNoice: Pair<Float, Int>
    ): Boolean {
        val surfaceY = heightNoice.second
        val blockType = chunkData.getBlockByLocal(localPosition)
        if (surfaceY != worldPosition.y || blockType != BlockType.GRASS) return false

        val random = Random(
            seed + worldPosition.x * 31 + worldPosition.y * 7919 + worldPosition.z * 104729
        )

        if (random.nextFloat() > 0.04f) return false
        generateTree(chunkData, localPosition, worldPosition, random)
        return true
    }

    private fun generateTree(
        chunkData: ChunkData,
        localPosition: IntVector3,
        worldPosition: IntVector3,
        random: Random,
    ) {
        val treeHeight = random.nextInt(32, 42)

        for (y in 0 until treeHeight) {
            chunkData.setBlockPending(
                BlockType.OAK_WOOD,
                offset = IntVector3(0, y + 1, 0),
                localPosition = localPosition,
                worldPosition = worldPosition
            )
        }

        // Tip leaf above the trunk.
        chunkData.setBlockPending(
            BlockType.LEAVES,
            offset = IntVector3(0, treeHeight + 1, 0),
            localPosition = localPosition,
            worldPosition = worldPosition
        )

        val leafStart = treeHeight / 3
        val maxRadius = if (treeHeight >= 12) 3 else 2
        val leafSpan = (treeHeight - 1 - leafStart).coerceAtLeast(1)

        for (y in leafStart until treeHeight) {
            val t = (y - leafStart).toFloat() / leafSpan
            // Wider rings at the base of the canopy, tapering to 1 at the tip.
            var radius = (maxRadius * (1f - t) + 1f).toInt().coerceIn(1, maxRadius)
            // Alternate ring widths for a spruce look.
            if ((treeHeight - y) % 2 == 0) radius = (radius + 1).coerceAtMost(maxRadius)

            placeLeafRing(
                chunkData = chunkData,
                localPosition = localPosition,
                worldPosition = worldPosition,
                random = random,
                y = y + 1,
                radius = radius,
                keepTrunk = true
            )
        }
    }

    private fun placeLeafRing(
        chunkData: ChunkData,
        localPosition: IntVector3,
        worldPosition: IntVector3,
        random: Random,
        y: Int,
        radius: Int,
        keepTrunk: Boolean,
    ) {
        for (x in -radius..radius) {
            for (z in -radius..radius) {
                if (keepTrunk && x == 0 && z == 0) continue

                val distance = sqrt((x * x + z * z).toDouble())
                if (distance > radius + 0.3) continue

                // Thin the outer edge a bit so rings don't look like solid discs.
                if (distance > radius - 0.5 && random.nextFloat() > 0.7f) continue

                chunkData.setBlockPending(
                    BlockType.LEAVES,
                    offset = IntVector3(x, y, z),
                    localPosition = localPosition,
                    worldPosition = worldPosition
                )
            }
        }
    }
}
