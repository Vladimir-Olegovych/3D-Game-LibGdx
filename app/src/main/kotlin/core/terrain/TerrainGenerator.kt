package core.terrain

import com.gigapi.effects.LaunchedEffect
import com.gigapi.general.Context
import com.gigapi.math.noice.NoiceGenerator
import com.gigapi.math.noice.PerlinNoise
import com.gigapi.math.noice.RandomNoise
import com.gigapi.math.noice.domain.DomainWarping2D
import com.gigapi.math.noice.models.NoiseSettings
import core.chunk.ChunkData
import core.noice.NoiceTypes
import core.noice.asGenerator
import core.terrain.biome.BiomeGenerator
import core.terrain.biome.BiomeSelector
import core.terrain.biome.biomes.DesertBiomeGenerator
import core.terrain.biome.biomes.ForestBiomeGenerator
import core.terrain.biome.biomes.MountainBiomeGenerator
import core.terrain.biome.models.BiomeType
import core.terrain.level.StructureSelector
import math.noice.FastNoise
import kotlin.random.Random

class TerrainGenerator: LaunchedEffect {

    private lateinit var noiseWarp: DomainWarping2D
    private lateinit var biomeSelector: BiomeSelector
    private lateinit var structureSelector: StructureSelector
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
        val noise = context.getObject<PerlinNoise>(NoiceTypes.PERLIN_WORLD)
        noiseWarp = getNoiseDomainWarping(noise.asGenerator())
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

        val offset = noiseWarp.generateDomainOffset(worldX, worldZ)

        val warpX = worldX + offset.x * 8.0f
        val warpZ = worldZ + offset.y * 8.0f

        val biome = biomeSelector.getBiomeAt(warpX.toInt(), warpZ.toInt())
        val generator = biomeGenerators[biome] ?: return

        val (terrain, surface) = generator.computeSurfaceNoise(
            warpX.toInt(),
            warpZ.toInt()
        )

        generator.process(chunkData, localX, localZ, Pair(terrain, surface))
    }

    private fun getNoiseDomainWarping(noiceGenerator: NoiceGenerator): DomainWarping2D {
        val domainXNoiseSettings= NoiseSettings(
            noiseZoom = 0.005f,
            octaves = 1,
            persistance = 0.5f,
            redistributionModifier = 1.0f,
            exponent = 1f
        )
        val domainZNoiseSettings = NoiseSettings(
            noiseZoom = 0.004f,
            octaves = 1,
            persistance = 0.5f,
            redistributionModifier = 1.0f,
            exponent = 1f
        )

        return DomainWarping2D(
            noiceGenerator = noiceGenerator,
            noiseDomainX = domainXNoiseSettings,
            noiseDomainY = domainZNoiseSettings,
            amplitudeX = 20,
            amplitudeY = 20
        )
    }

    companion object {
        const val CAVE_THRESHOLD = 0.2F
        const val CAVE_LEVEL = -120
        const val UNDERGROUND_HEIGHT = 80
        const val WORLD_HEIGHT = 300
        const val WORLD_SURFACE = WORLD_HEIGHT / 1.5
    }

}