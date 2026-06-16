package core.terrain

import com.gigapi.core.effects.LaunchedEffect
import com.gigapi.general.Context
import com.gigapi.math.noice.PerlinNoise
import com.gigapi.math.noice.RandomNoise
import core.chunk.ChunkData
import core.noice.NoiceTypes
import core.terrain.biome.BiomeGenerator
import core.terrain.biome.BiomeSelector
import core.terrain.biome.biomes.DesertBiomeGenerator
import core.terrain.biome.biomes.ForestBiomeGenerator
import core.terrain.biome.biomes.MountainBiomeGenerator
import core.terrain.biome.models.BiomeType
import math.noice.FastNoise
import kotlin.random.Random
import kotlin.to

class TerrainGenerator: LaunchedEffect {

    private lateinit var biomeSelector: BiomeSelector
    private lateinit var biomeGenerators: Map<BiomeType, BiomeGenerator>

    override fun launch(context: Context) {
        val worldSeed = Random.nextInt()
        context.setObject(NoiceTypes.PERLIN_WORLD, PerlinNoise(worldSeed))
        context.setObject(NoiceTypes.FAST_PERLIN, FastNoise(worldSeed))
        context.setObject(NoiceTypes.RANDOM_WORLD, RandomNoise(worldSeed))

        context.setObject(BiomeSelector(worldSeed))

        context.setObject(ForestBiomeGenerator())
        context.setObject(DesertBiomeGenerator())
        context.setObject(MountainBiomeGenerator())

        biomeGenerators = mapOf(
            BiomeType.FOREST    to context.getObject<ForestBiomeGenerator>(),
            BiomeType.DESERT    to context.getObject<DesertBiomeGenerator>(),
            BiomeType.MOUNTAINS to context.getObject<MountainBiomeGenerator>()
        )

        biomeSelector = context.getObject()
    }

    fun generateChunkData(chunkData: ChunkData) {
        for (x in 0 until chunkData.chunkWidth) {
            for (z in 0 until chunkData.chunkWidth) {
                generateColumn(chunkData, x, z)
            }
        }
    }

    private fun generateColumn(chunkData: ChunkData, localX: Int, localZ: Int) {
        val worldX = chunkData.position.x * chunkData.chunkWidth + localX
        val worldZ = chunkData.position.z * chunkData.chunkWidth + localZ

        val biome = biomeSelector.getBiomeAt(worldX, worldZ)

        val generator = biomeGenerators[biome]

        val (terrain, surface) = generator?.computeSurfaceNoise(worldX, worldZ)
            ?: return

        val surfaceNoise = Pair(terrain, surface)

        generator.process(chunkData, localX, localZ, surfaceNoise)
    }

    companion object {
        const val CAVE_THRESHOLD = 0.2F
        const val CAVE_LEVEL = -120
        const val UNDERGROUND_HEIGHT = 80
        const val WORLD_HEIGHT = 300
        const val WORLD_SURFACE = WORLD_HEIGHT / 2
    }

}