package core.terrain.biome.biomes

import com.gigapi.effects.LaunchedEffect
import com.gigapi.general.GContext
import com.gigapi.math.noice.NoiceGenerator
import com.gigapi.math.noice.PerlinNoise
import com.gigapi.math.noice.domain.DomainWarping2D
import com.gigapi.math.noice.models.NoiceUtils
import com.gigapi.math.noice.models.NoiseSettings
import com.gigapi.math.vector.IntVector3
import core.blocks.types.BlockType
import core.chunk.ChunkData
import core.noice.NoiceTypes
import core.noice.asGenerator
import core.terrain.TerrainGenerator
import core.terrain.biome.BiomeGenerator
import core.terrain.biome.BiomePointGrid
import core.terrain.layers.CaveLayerHandler
import core.terrain.layers.SurfaceLayerHandler
import core.terrain.layers.UndergroundLayerHandler
import math.noice.FastNoise
import kotlin.math.pow
import kotlin.random.Random

class MountainBiomeGenerator : LaunchedEffect, BiomeGenerator() {

    private val baseNoiseSettings = NoiseSettings(
        noiseZoom = 0.001f,
        octaves = 5,
        persistance = 0.1f,
        redistributionModifier = 1.4f,
        exponent = 1.8f
    )

    private lateinit var baseDomainWarping: DomainWarping2D
    override val startLayerHandler = SurfaceLayerHandler(surfaceBlockType = BlockType.STONE)

    override fun launch(gContext: GContext) {
        val perlinNoise = gContext.getObject<PerlinNoise>(NoiceTypes.PERLIN_WORLD)
        val caveNoise = gContext.getObject<FastNoise>(NoiceTypes.FAST_CAVE)
        val randomPerlin = PerlinNoise(Random(perlinNoise.seed).nextInt())
        val noiceGenerator = randomPerlin.asGenerator()

        baseDomainWarping = getSurfaceDomainWarping(noiceGenerator)

        startLayerHandler
            .setNext(UndergroundLayerHandler())
            .setNext(CaveLayerHandler(caveNoise))
    }

    override fun computeSurfaceNoise(worldX: Int, worldZ: Int): Pair<Float, Int> {
        val height = TerrainGenerator.WORLD_HEIGHT

        var terrainHeight = baseDomainWarping.generateDomainNoise(worldX, worldZ, baseNoiseSettings)
        terrainHeight = NoiceUtils.redistribution(terrainHeight, baseNoiseSettings)

        // 1. Усиливаем контраст — растягиваем верх, чтобы были острые пики
        terrainHeight = 1f - (1f - terrainHeight).pow(1.8f)  // exponent > 1 → выше горы

        // 2. Мягко поднимаем низины, НЕ обрезая пики
        val minTerrainLevel = 0.9f
        if (terrainHeight < minTerrainLevel) {
            terrainHeight = minTerrainLevel - (minTerrainLevel - terrainHeight) * 0.25f
        }

        // 3. Опционально: ridged-слой для хребтов
        val ridge = 1f - kotlin.math.abs(baseDomainWarping.generateDomainNoise(worldX, worldZ, baseNoiseSettings))
        terrainHeight = (terrainHeight * 0.75f + ridge.pow(3f) * 0.25f).coerceIn(0f, 1f)

        val baseHeight = 400
        val mountainHeight = height - baseHeight
        val surfaceHeight = (terrainHeight * mountainHeight).toInt() + baseHeight

        return Pair(terrainHeight, surfaceHeight)
    }

    override fun process(chunkData: ChunkData,
                         biomeData: BiomePointGrid.BiomeData,
                         x: Int, z: Int, surfaceNoise: Pair<Float, Int>) {
        val worldPosition = IntVector3(
            x = chunkData.position.x * chunkData.chunkWidth + x,
            z = chunkData.position.z * chunkData.chunkWidth + z
        )

        for (y in 0 until chunkData.chunkHeight) {
            worldPosition.y = chunkData.position.y * chunkData.chunkHeight + y
            val localPosition = IntVector3(x, y, z)

            startLayerHandler.handle(
                chunkData,
                localPosition,
                worldPosition,
                surfaceNoise
            )
        }
    }

    override fun process(chunkData: ChunkData,
                         biomeData: BiomePointGrid.BiomeData,
                         x: Int, z: Int) {
        val worldPosition = IntVector3(
            x = chunkData.position.x * chunkData.chunkWidth + x,
            z = chunkData.position.z * chunkData.chunkWidth + z
        )
        val surfaceNoise = computeSurfaceNoise(worldPosition.x, worldPosition.z)

        for (y in 0 until chunkData.chunkHeight) {
            worldPosition.y = chunkData.position.y * chunkData.chunkHeight + y
            val localPosition = IntVector3(x, y, z)

            startLayerHandler.handle(
                chunkData,
                localPosition,
                worldPosition,
                surfaceNoise
            )
        }
    }

    private fun getSurfaceDomainWarping(noiceGenerator: NoiceGenerator): DomainWarping2D {
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

        return DomainWarping2D(noiceGenerator, domainXNoiseSettings, domainZNoiseSettings)
    }
}