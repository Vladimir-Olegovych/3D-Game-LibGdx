package core.terrain.structures

import core.blocks.BlockType
import core.terrain.biome.models.BiomeType
import core.terrain.level.WorldStructure
import core.terrain.structures.builder.StructureBuilder

class RockStructure : WorldStructure() {

    override val id = "rock"
    override val cellSize = 6
    override val chance = 0.0008f

    override val radius = 10

    override val below = radius / 2 + 1
    override val above = radius / 2 + 1

    override val biomes = setOf(
        BiomeType.FOREST,
        BiomeType.SPRUCE_FOREST,
        BiomeType.DESERT
    )

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
        val rockRadius = random.nextInt(1, radius - 1)

        for (x in -rockRadius..rockRadius) {
            for (y in -rockRadius..rockRadius) {
                for (z in -rockRadius..rockRadius) {
                    // Вызывается даже для позиций вне текущего чанка.
                    val noise = random.nextDouble() * 0.3

                    val py = y + 1
                    if (!clip.contains(x, py, z)) continue

                    val distanceSquared = x * x + y * y + z * z
                    val noisyRadius = rockRadius + noise

                    if (distanceSquared <= noisyRadius * noisyRadius) {
                        b.set(x, py, z, BlockType.STONE)
                    }
                }
            }
        }
    }
}