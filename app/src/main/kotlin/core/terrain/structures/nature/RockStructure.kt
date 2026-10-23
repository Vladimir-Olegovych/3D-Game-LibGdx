package core.terrain.structures.nature

import core.blocks.types.BlockType
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
    override val biomes = setOf(BiomeType.FOREST, BiomeType.SPRUCE_FOREST, BiomeType.DESERT)

    override fun build(b: StructureBuilder) {
        val random = b.random
        val rockRadius = random.nextInt(1, radius - 1)

        for (x in -rockRadius..rockRadius) for (y in -rockRadius..rockRadius) for (z in -rockRadius..rockRadius) {
            val noise = random.nextDouble() * 0.3
            val noisyRadius = rockRadius + noise
            if (x * x + y * y + z * z <= noisyRadius * noisyRadius) {
                b.set(x, y + 1, z, BlockType.STONE)
            }
        }
    }
}