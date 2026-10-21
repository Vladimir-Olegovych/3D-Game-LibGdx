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
    override val cellSize = 5          // раньше 4% колонок ≈ 1 дерево на 25 блоков
    override val chance = 0.8f         // 0.8 / 25 ≈ 3.2%; подгони плотность под себя
    override val radius = 4            // maxRadius = 3 + 1 запас
    override val above = 46            // макс. высота ствола 41 + верхушка + запас
    override val biomes = setOf(BiomeType.SPRUCE_FOREST)

    override fun build(b: StructureBuilder) {
        val random = b.random
        val treeHeight = random.nextInt(32, 42)

        for (y in 0 until treeHeight) {
            b.set(0, y + 1, 0, BlockType.OAK_WOOD, Place.ALWAYS)
        }

        // Верхушка
        b.set(0, treeHeight + 1, 0, BlockType.LEAVES)

        val leafStart = treeHeight / 3
        val maxRadius = if (treeHeight >= 12) 3 else 2
        val leafSpan = (treeHeight - 1 - leafStart).coerceAtLeast(1)

        for (y in leafStart until treeHeight) {
            val t = (y - leafStart).toFloat() / leafSpan
            var radius = (maxRadius * (1f - t) + 1f).toInt().coerceIn(1, maxRadius)
            if ((treeHeight - y) % 2 == 0) radius = (radius + 1).coerceAtMost(maxRadius)

            placeLeafRing(b, random, y + 1, radius)
        }
    }

    private fun placeLeafRing(b: StructureBuilder, random: Random, y: Int, radius: Int) {
        for (x in -radius..radius) {
            for (z in -radius..radius) {
                if (x == 0 && z == 0) continue   // ствол

                val distance = sqrt((x * x + z * z).toDouble())
                if (distance > radius + 0.3) continue

                // random вызывается в том же порядке в любом чанке
                if (distance > radius - 0.5 && random.nextFloat() > 0.7f) continue

                b.set(x, y, z, BlockType.LEAVES)
            }
        }
    }
}