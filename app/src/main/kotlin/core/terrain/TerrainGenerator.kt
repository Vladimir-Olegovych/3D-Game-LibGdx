package core.terrain

import com.gigapi.core.effects.LaunchedEffect
import com.gigapi.general.Context
import core.chunk.ChunkData
import core.noice.DomainWarping
import core.noice.NoiseSettings
import core.noice.PerlinNoise
import core.terrain.biome.BiomeGenerator
import core.terrain.layers.SurfaceLayerHandler
import core.terrain.layers.UndergroundLayerHandler

class TerrainGenerator: LaunchedEffect {

    private lateinit var defaultForestBiomeGenerator: BiomeGenerator
    private lateinit var noice: PerlinNoise
    private lateinit var noices: NoiseSettings

    override fun launch(context: Context) {
        val noice = PerlinNoise(100)
        val domainXConfig = NoiseSettings(
            noiseZoom = 0.005f,
            octaves = 1,
            persistance = 0.5f,
            redistributionModifier = 1.0f,
            exponent = 1f
        )

        val domainYConfig = NoiseSettings(
            noiseZoom = 0.005f,
            octaves = 1,
            persistance = 0.5f,
            redistributionModifier = 1.0f,
            exponent = 1f
        )

        val biomeConfig = NoiseSettings(
            noiseZoom = 0.01f,       // Средняя ширина гор
            octaves = 5,
            persistance = 0.5f,
            redistributionModifier = 1.6f,  // Умеренный сдвиг
            exponent = 1.6f          // Между линейной и квадратичной
        )
        this.noice = noice
        this.noices = biomeConfig
        val domainWarping = DomainWarping(noice, domainXConfig, domainYConfig)
        val startLayerHandler = SurfaceLayerHandler()
        startLayerHandler.setNext(UndergroundLayerHandler())
        defaultForestBiomeGenerator = BiomeGenerator(domainWarping, biomeConfig, startLayerHandler)
    }


    fun generateChunkData(chunkData: ChunkData) {
        for (x in 0 until chunkData.chunkWidth) {
            for (z in 0 until chunkData.chunkWidth) {
                defaultForestBiomeGenerator.processChunkColumn(chunkData, x, z, null)
            }
        }
    }

}