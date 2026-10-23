package core.terrain.structures.trees

import core.blocks.types.BlockType
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
        b.fillBox(0, 1, 0, 0, 5, 0, BlockType.CACTUS, Place.ALWAYS)
    }
}