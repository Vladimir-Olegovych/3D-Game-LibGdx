package core.terrain.structures

import core.blocks.BlockType
import core.terrain.biome.models.BiomeType
import core.terrain.level.WorldStructure
import core.terrain.structures.builder.Place
import core.terrain.structures.builder.StructureBuilder
import kotlin.math.sqrt
import kotlin.random.Random

class SpruceTreeStructure : WorldStructure() {

    override val id = "spruce_tree"
    override val cellSize = 5
    override val chance = 0.8f
    override val radius = 4
    override val above = 46
    override val biomes = setOf(BiomeType.SPRUCE_FOREST)

    override fun build(b: StructureBuilder) {
        generate(b, StructureClip.ALL)
    }

    override fun buildClipped(
        b: StructureBuilder,
        minX: Int, maxX: Int,
        minY: Int, maxY: Int,
        minZ: Int, maxZ: Int
    ) {
        generate(
            b,
            StructureClip(minX, maxX, minY, maxY, minZ, maxZ)
        )
    }

    private fun generate(b: StructureBuilder, clip: StructureClip) {
        val random = b.random
        val treeHeight = random.nextInt(32, 42)

        // В этом цикле random не используется, поэтому его можно обрезать.
        for (py in 1..treeHeight) {
            if (clip.contains(0, py, 0)) {
                b.set(0, py, 0, BlockType.OAK_WOOD, Place.ALWAYS)
            }
        }

        if (clip.contains(0, treeHeight + 1, 0)) {
            b.set(0, treeHeight + 1, 0, BlockType.LEAVES)
        }

        val leafStart = treeHeight / 3
        val maxRadius = if (treeHeight >= 12) 3 else 2
        val leafSpan = (treeHeight - 1 - leafStart).coerceAtLeast(1)

        for (y in leafStart until treeHeight) {
            val t = (y - leafStart).toFloat() / leafSpan

            var radius = (maxRadius * (1f - t) + 1f)
                .toInt()
                .coerceIn(1, maxRadius)

            if ((treeHeight - y) % 2 == 0) {
                radius = (radius + 1).coerceAtMost(maxRadius)
            }

            placeLeafRing(b, random, clip, y + 1, radius)
        }
    }

    private fun placeLeafRing(
        b: StructureBuilder,
        random: Random,
        clip: StructureClip,
        y: Int,
        radius: Int
    ) {
        for (x in -radius..radius) {
            for (z in -radius..radius) {
                if (x == 0 && z == 0) continue

                val distance = sqrt((x * x + z * z).toDouble())
                if (distance > radius + 0.3) continue

                if (
                    distance > radius - 0.5 &&
                    random.nextFloat() > 0.7f
                ) {
                    continue
                }

                if (clip.contains(x, y, z)) {
                    b.set(x, y, z, BlockType.LEAVES)
                }
            }
        }
    }
}