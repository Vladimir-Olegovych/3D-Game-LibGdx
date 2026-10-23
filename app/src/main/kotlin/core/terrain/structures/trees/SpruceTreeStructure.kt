package core.terrain.structures.trees

import core.blocks.types.BlockType
import core.terrain.biome.models.BiomeType
import core.terrain.level.WorldStructure
import core.terrain.structures.builder.Place
import core.terrain.structures.builder.StructureBuilder
import kotlin.math.sqrt

class SpruceTreeStructure : WorldStructure() {

    override val id = "spruce_tree"
    override val cellSize = 5
    override val chance = 0.8f
    override val radius = 4
    override val above = 46
    override val biomes = setOf(BiomeType.SPRUCE_FOREST)

    override fun build(b: StructureBuilder) {
        val random = b.random
        val treeHeight = random.nextInt(32, 42)

        b.fillBox(0, 1, 0, 0, treeHeight, 0, BlockType.OAK_WOOD, Place.ALWAYS)
        b.set(0, treeHeight + 1, 0, BlockType.LEAVES)

        val leafStart = treeHeight / 3
        val maxRadius = if (treeHeight >= 12) 3 else 2
        val leafSpan = (treeHeight - 1 - leafStart).coerceAtLeast(1)

        for (y in leafStart until treeHeight) {
            val t = (y - leafStart).toFloat() / leafSpan
            var r = (maxRadius * (1f - t) + 1f).toInt().coerceIn(1, maxRadius)
            if ((treeHeight - y) % 2 == 0) r = (r + 1).coerceAtMost(maxRadius)
            placeLeafRing(b, y + 1, r)
        }
    }

    private fun placeLeafRing(b: StructureBuilder, y: Int, r: Int) {
        val random = b.random
        for (x in -r..r) for (z in -r..r) {
            if (x == 0 && z == 0) continue
            val distance = sqrt((x * x + z * z).toDouble())
            if (distance > r + 0.3) continue
            if (distance > r - 0.5 && random.nextFloat() > 0.7f) continue
            b.set(x, y, z, BlockType.LEAVES)
        }
    }
}