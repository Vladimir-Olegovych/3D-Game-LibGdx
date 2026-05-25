package core.terrain.biome

import com.gigapi.core.effects.LaunchedEffect
import com.gigapi.general.Context
import core.noice.DomainWarping
import core.noice.NoiseSettings
import core.noice.PerlinNoise
import core.terrain.layers.SurfaceLayerHandler
import core.terrain.layers.UndergroundLayerHandler

class ForestBiomeGenerator: LaunchedEffect {

    lateinit var generator: BiomeGenerator

    override fun launch(context: Context) {
        val noice = context.getObject<PerlinNoise>()
        val domainXNoiseSettings= NoiseSettings(
            noiseZoom = 0.005f,
            octaves = 1,
            persistance = 0.5f,
            redistributionModifier = 1.0f,
            exponent = 1f
        )

        val domainYNoiseSettings = NoiseSettings(
            noiseZoom = 0.005f,
            octaves = 1,
            persistance = 0.5f,
            redistributionModifier = 1.0f,
            exponent = 1f
        )

        val biomeNoiseSettings = NoiseSettings(
            noiseZoom = 0.01f,
            octaves = 5,
            persistance = 0.5f,
            redistributionModifier = 1.6f,
            exponent = 1.6f
        )
        val domainWarping = DomainWarping(noice, domainXNoiseSettings, domainYNoiseSettings)
        val startLayerHandler = SurfaceLayerHandler()
        startLayerHandler
            .setNext(UndergroundLayerHandler())
            //.setNext()

        generator = BiomeGenerator(domainWarping, biomeNoiseSettings, startLayerHandler)
    }

}