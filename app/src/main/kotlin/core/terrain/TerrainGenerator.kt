package core.terrain

import com.badlogic.gdx.math.Vector2
import com.gigapi.effects.LaunchedEffect
import com.gigapi.general.GContext
import com.gigapi.math.noice.NoiceGenerator
import com.gigapi.math.noice.PerlinNoise
import com.gigapi.math.noice.RandomNoise
import com.gigapi.math.noice.domain.DomainWarping2D
import com.gigapi.math.noice.models.NoiseSettings
import com.gigapi.math.vector.IntVector3
import core.chunk.ChunkData
import core.noice.NoiceTypes
import core.noice.asGenerator
import core.terrain.biome.BiomeGenerator
import core.terrain.biome.BiomePointGrid
import core.terrain.biome.BiomeSelector
import core.terrain.biome.biomes.DesertBiomeGenerator
import core.terrain.biome.biomes.ForestBiomeGenerator
import core.terrain.biome.biomes.MountainBiomeGenerator
import core.terrain.biome.biomes.SpruceBiomeGenerator
import core.terrain.biome.models.BiomeType
import core.terrain.layers.CaveLayerHandler
import math.noice.FastNoise
import kotlin.math.roundToInt

class TerrainGenerator: LaunchedEffect {

    companion object {
        const val UNDERGROUND_HEIGHT = 80
        const val CAVE_THRESHOLD = 0.025f
        const val CAVE_LEVEL = -UNDERGROUND_HEIGHT
        const val WORLD_HEIGHT = 300
        const val WORLD_SURFACE = WORLD_HEIGHT / 1.5
    }

    private lateinit var noiseWarp: DomainWarping2D
    private lateinit var biomePointGrid: BiomePointGrid
    private lateinit var biomeGenerators: Map<BiomeType, BiomeGenerator>

    var worldSeedFromServer: Int? = null

    override fun launch(gContext: GContext) {
        val worldSeed = worldSeedFromServer?: error("worldSeedFromServer is null")
        gContext.setObject(NoiceTypes.PERLIN_WORLD, PerlinNoise(worldSeed))
        gContext.setObject(NoiceTypes.FAST_PERLIN, FastNoise(worldSeed))
        gContext.setObject(NoiceTypes.FAST_CAVE, FastNoise(worldSeed).apply {
            SetFrequency(CaveLayerHandler.CAVE_NOISE_FREQUENCY)
        })
        gContext.setObject(NoiceTypes.RANDOM_WORLD, RandomNoise(worldSeed))

        gContext.setObject(BiomeSelector(worldSeed))
        gContext.setObject(BiomePointGrid())

        gContext.setObject(ForestBiomeGenerator())
        gContext.setObject(SpruceBiomeGenerator())
        gContext.setObject(DesertBiomeGenerator())
        gContext.setObject(MountainBiomeGenerator())

        biomeGenerators = mapOf(
            BiomeType.FOREST        to gContext.getObject<ForestBiomeGenerator>(),
            BiomeType.SPRUCE_FOREST to gContext.getObject<SpruceBiomeGenerator>(),
            BiomeType.DESERT        to gContext.getObject<DesertBiomeGenerator>(),
            BiomeType.MOUNTAINS     to gContext.getObject<MountainBiomeGenerator>()
        )

        val noise = gContext.getObject<PerlinNoise>(NoiceTypes.PERLIN_WORLD)
        noiseWarp = getNoiseDomainWarping(noise.asGenerator())
        biomePointGrid = gContext.getObject()
    }

    fun generateBiomePoints(position: IntVector3) =
        biomePointGrid.getPointsAround(position)

    fun generateChunkData(chunkData: ChunkData, points: Map<IntVector3, BiomeType>) {
        val w = chunkData.chunkWidth

        val offsets = Array(w * w) { i ->
            val lx = i % w; val lz = i / w
            val wx = chunkData.position.x * w + lx
            val wz = chunkData.position.z * w + lz
            noiseWarp.generateDomainOffset(wx, wz)
        }

        val biomeCache = HashMap<Long, BiomePointGrid.BiomeData>()

        for (x in 0 until w) {
            for (z in 0 until w) {
                generateColumn(chunkData, x, z, points, offsets, biomeCache)
            }
        }
    }

    private fun generateColumn(
        chunkData: ChunkData,
        localX: Int,
        localZ: Int,
        points: Map<IntVector3, BiomeType>,
        offsets: Array<Vector2>,
        biomeCache: HashMap<Long, BiomePointGrid.BiomeData>
    ) {
        val w = chunkData.chunkWidth
        val worldX = chunkData.position.x * w + localX
        val worldZ = chunkData.position.z * w + localZ

        val offset = offsets[localZ * w + localX]
        val warpX = worldX + offset.x * 10.0f
        val warpZ = worldZ + offset.y * 8.0f

        val biomeKey = packBiomeKey(warpX.toInt(), warpZ.toInt())
        val biomeData = biomeCache.getOrPut(biomeKey) {
            biomePointGrid.getBiomeAt(points, warpX.toInt(), warpZ.toInt())
        }

        val generator = biomeGenerators[biomeData.currentBiome] ?: return

        var terrainSum = 0.0
        var surfaceSum = 0.0
        var weightSum = 0.0

        for ((biome, weight) in biomeData.weights) {
            val biomeGenerator = biomeGenerators[biome]
                ?: error("No biome generator for $biome")

            val noise = biomeGenerator.computeSurfaceNoise(
                warpX.toInt(),
                warpZ.toInt()
            )

            terrainSum += noise.first.toDouble() * weight
            surfaceSum += noise.second.toDouble() * weight
            weightSum += weight
        }

        check(weightSum > 0.0)

        val blendedNoise = Pair(
            (terrainSum / weightSum).toFloat(),
            (surfaceSum / weightSum).roundToInt()
        )

        generator.process(
            chunkData,
            biomeData,
            localX,
            localZ,
            blendedNoise
        )
    }

    private fun packBiomeKey(x: Int, z: Int): Long =
        (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)

    private fun getNoiseDomainWarping(noiceGenerator: NoiceGenerator): DomainWarping2D {
        val domainXNoiseSettings= NoiseSettings(
            noiseZoom = 0.009f,
            octaves = 1,
            persistance = 0.5f,
            redistributionModifier = 1.0f,
            exponent = 1f
        )
        val domainZNoiseSettings = NoiseSettings(
            noiseZoom = 0.008f,
            octaves = 1,
            persistance = 0.5f,
            redistributionModifier = 1.0f,
            exponent = 1f
        )

        return DomainWarping2D(
            noiceGenerator = noiceGenerator,
            noiseDomainX = domainXNoiseSettings,
            noiseDomainY = domainZNoiseSettings,
            amplitudeX = 40,
            amplitudeY = 20
        )
    }

}