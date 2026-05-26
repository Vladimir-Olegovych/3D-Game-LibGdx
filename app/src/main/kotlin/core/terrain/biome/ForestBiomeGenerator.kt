package core.terrain.biome

import com.gigapi.core.effects.LaunchedEffect
import com.gigapi.general.Context
import core.noice.domain.DomainWarping2D
import core.noice.models.NoiceTypes
import core.noice.models.NoiseSettings
import core.noice.PerlinNoise
import core.noice.domain.DomainWarping3D
import core.terrain.layers.CaveLayerHandler
import core.terrain.layers.SurfaceLayerHandler
import core.terrain.layers.UndergroundLayerHandler

class ForestBiomeGenerator: LaunchedEffect {

    lateinit var generator: BiomeGenerator

    override fun launch(context: Context) {
        val perlinNoise = context.getObject<PerlinNoise>(NoiceTypes.PERLIN_WORLD)

        val biomeNoiseSettings = NoiseSettings(
            noiseZoom = 0.01f,
            octaves = 5,
            persistance = 0.5f,
            redistributionModifier = 1.6f,
            exponent = 1.6f
        )

        val caveNoiseSettings = NoiseSettings(
            noiseZoom = 0.04f,
            octaves = 1,
            persistance = 0.5f,
            redistributionModifier = 1.0f,
            exponent = 1.6f
        )

        val domainWarping2D = getSurfaceDomainWarping(perlinNoise)
        val domainWarping3D = getCaveDomainWarping(perlinNoise)

        val startLayerHandler = SurfaceLayerHandler()
        startLayerHandler
            .setNext(UndergroundLayerHandler(perlinNoise.seed))
            .setNext(CaveLayerHandler())

        generator = BiomeGenerator(
            surfaceDomainWarping2D = domainWarping2D,
            caveDomainWarping3D = domainWarping3D,
            biomeNoiseSettings = biomeNoiseSettings,
            caveNoiseSettings = caveNoiseSettings,
            startLayerHandler = startLayerHandler
        )
    }

    private fun getSurfaceDomainWarping(perlinNoise: PerlinNoise): DomainWarping2D {
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

        return DomainWarping2D(perlinNoise, domainXNoiseSettings, domainZNoiseSettings)
    }

    private fun getCaveDomainWarping(perlinNoise: PerlinNoise): DomainWarping3D {
        val domainXNoiseSettings= NoiseSettings(
            noiseZoom = 0.04f,
            octaves = 1,
            persistance = 0.5f,
            redistributionModifier = 1.0f,
            exponent = 1f
        )

        val domainYNoiseSettings = NoiseSettings(
            noiseZoom = 0.05f,
            octaves = 1,
            persistance = 0.5f,
            redistributionModifier = 1.0f,
            exponent = 1f
        )

        val domainZNoiseSettings = NoiseSettings(
            noiseZoom = 0.03f,
            octaves = 1,
            persistance = 0.5f,
            redistributionModifier = 1.0f,
            exponent = 1f
        )
        return DomainWarping3D(perlinNoise, domainXNoiseSettings, domainYNoiseSettings, domainZNoiseSettings)
    }

}