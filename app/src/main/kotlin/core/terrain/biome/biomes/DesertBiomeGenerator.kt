package core.terrain.biome.biomes

import com.gigapi.effects.LaunchedEffect
import com.gigapi.general.Context
import com.gigapi.math.noice.NoiceGenerator
import com.gigapi.math.noice.PerlinNoise
import com.gigapi.math.noice.domain.DomainWarping2D
import com.gigapi.math.noice.models.NoiceUtils
import com.gigapi.math.noice.models.NoiseSettings
import com.gigapi.math.vector.IntVector3
import core.blocks.BlockType
import core.chunk.ChunkData
import core.noice.NoiceTypes
import core.noice.asGenerator
import core.terrain.TerrainGenerator
import core.terrain.biome.BiomeGenerator
import core.terrain.layers.ShadowLayerHandler
import core.terrain.layers.SurfaceLayerHandler
import core.terrain.layers.UndergroundLayerHandler

class DesertBiomeGenerator : LaunchedEffect, BiomeGenerator() {

    private val baseNoiseSettings = NoiseSettings(
        noiseZoom = 0.005f,
        octaves = 5,
        persistance = 0.5f,
        redistributionModifier = 1.6f,
        exponent = 1.8f
    )

    private lateinit var baseDomainWarping: DomainWarping2D
    override val startLayerHandler = SurfaceLayerHandler(
        surfaceBlockType = BlockType.SAND, underSurfaceBlockType = BlockType.SAND
    )

    override fun launch(context: Context) {
        val perlinNoise = context.getObject<PerlinNoise>(NoiceTypes.PERLIN_WORLD)
        val noiceGenerator = perlinNoise.asGenerator()

        baseDomainWarping = getSurfaceDomainWarping(noiceGenerator)

        startLayerHandler
            .setNext(ShadowLayerHandler())
            .setNext(UndergroundLayerHandler())
    }

    override fun computeSurfaceNoise(worldX: Int, worldZ: Int): Pair<Float, Int> {
        val height = TerrainGenerator.WORLD_HEIGHT
        var terrainHeight = baseDomainWarping.generateDomainNoise(worldX, worldZ, baseNoiseSettings)
        terrainHeight = NoiceUtils.redistribution(terrainHeight, baseNoiseSettings)
        val surfaceHeight = NoiceUtils.remapValue01ToInt(terrainHeight, 0f, height.toFloat())
        return Pair(terrainHeight, surfaceHeight + (height / 2))
    }

    override fun process(chunkData: ChunkData, x: Int, z: Int, surfaceNoise: Pair<Float, Int>) {
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

    override fun process(chunkData: ChunkData, x: Int, z: Int) {
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