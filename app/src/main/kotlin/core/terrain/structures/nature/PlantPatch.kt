package core.terrain.structures.nature

import com.gigapi.math.noice.SpawnMask
import com.gigapi.math.noice.models.NoiseSettings
import core.blocks.types.BlockType
import core.terrain.TerrainGenerator.Companion.SURFACE_HEIGHT
import core.terrain.biome.models.BiomeType
import core.terrain.level.WorldStructure
import core.terrain.structures.builder.Place
import core.terrain.structures.builder.StructureBuilder

class PlantPatch(
    override val id: String,
    override val biomes: Set<BiomeType>?,
    override val chance: Float,
    override val mask: SpawnMask? = null,
    private val heightThreshold: Int? = null,
    private val plants: List<BlockType>
) : WorldStructure() {
    override val cellSize = 1
    override val radius = 0
    override val above = 1

    override fun canSpawn(surfaceY: Int, biome: BiomeType): Boolean {
        if (heightThreshold == null) return super.canSpawn(surfaceY, biome)
        return surfaceY < heightThreshold && super.canSpawn(surfaceY, biome)
    }

    override fun build(b: StructureBuilder) {
        val type = if (plants.size == 1) plants[0] else plants[b.random.nextInt(plants.size)]
        b.set(0, 1, 0, type, Place.ONLY_AIR)
    }

    companion object {
        val wheatNoise = NoiseSettings(
            noiseZoom = 0.01f,
            octaves = 0,
            persistance = 0.5f,
            redistributionModifier = 1.0f,
            exponent = 1f
        )

        val structures = listOf(
            PlantPatch(
                id = "wheat",
                biomes = setOf(BiomeType.FOREST),
                chance = 0.8f,
                heightThreshold = SURFACE_HEIGHT,
                mask = SpawnMask.noise(wheatNoise, min = 0.1f, max = 0.5f),
                plants = listOf(BlockType.WHEAT_PLANT_1)
            ),
            PlantPatch(
                id = "grass",
                biomes = setOf(BiomeType.FOREST, BiomeType.SPRUCE_FOREST),
                chance = 0.3f,
                plants = listOf(BlockType.GRASS_PLANT_1, BlockType.GRASS_PLANT_2)
            ),
        )
    }
}
