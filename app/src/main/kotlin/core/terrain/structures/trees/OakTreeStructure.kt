package core.terrain.structures.trees

import core.blocks.types.BlockType
import core.terrain.biome.models.BiomeType
import core.terrain.level.WorldStructure
import core.terrain.structures.builder.Place
import core.terrain.structures.builder.StructureBuilder
import kotlin.math.sqrt

class OakTreeStructure : WorldStructure() {

    override val id = "oak_tree"
    override val cellSize = 6
    override val chance = 0.7f
    override val radius = 5
    override val above = 12
    override val biomes = setOf(BiomeType.FOREST)

    override fun build(b: StructureBuilder) {
        val random = b.random
        val treeHeight = random.nextInt(5, 9)
        val trunkOffset = random.nextInt(-1, 1)

        for (y in 0 until treeHeight) {
            val o = if (y > treeHeight / 2) trunkOffset else 0
            b.set(o, y + 1, o, BlockType.OAK_WOOD, Place.ALWAYS)
        }

        val leafRadius = when (treeHeight) {
            in 5..6 -> 2
            in 7..8 -> 3
            else -> 2
        }

        for (y in (treeHeight - 2) until treeHeight + 2) {
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

                    val leaf = if (distance > radiusAtLevel - 0.5) {
                        if (random.nextFloat() > 0.3f) BlockType.LEAVES else null
                    } else BlockType.LEAVES

                    if (leaf != null) b.set(x, y + 1, z, leaf)
                }
            }
        }

        repeat(random.nextInt(1, 3)) {
            val branchHeight = random.nextInt(treeHeight / 2, treeHeight - 1)
            val dir = random.nextInt(0, 4)
            val len = random.nextInt(2, 4)
            val dx = when (dir) { 0 -> 1; 1 -> -1; else -> 0 }
            val dz = when (dir) { 2 -> 1; 3 -> -1; else -> 0 }

            for (i in 1..len) {
                val px = dx * i
                val py = branchHeight + i / 2
                val pz = dz * i
                b.set(px, py, pz, BlockType.OAK_WOOD, Place.ALWAYS)

                if (i == len) {
                    for (ox in -1..1) for (oz in -1..1) {
                        if (ox == 0 && oz == 0) continue
                        if (random.nextFloat() > 0.4f) continue
                        b.set(px + ox, py, pz + oz, BlockType.LEAVES)
                    }
                }
            }
        }
    }
}