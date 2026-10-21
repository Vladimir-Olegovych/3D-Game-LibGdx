package core.terrain.structures

import core.blocks.BlockType
import core.terrain.biome.models.BiomeType
import core.terrain.level.WorldStructure
import core.terrain.structures.builder.Place
import core.terrain.structures.builder.StructureBuilder

class CactusStructure : WorldStructure() {

    override val id = "cactus"
    override val cellSize = 6
    override val chance = 0.15f
    override val radius = 0
    override val above = 5
    override val biomes = setOf(BiomeType.DESERT)

    override fun build(b: StructureBuilder) {
        val height = 5

        for (y in 1..height) {
            b.set(0, y, 0, BlockType.CACTUS, Place.ALWAYS)
        }
    }
}