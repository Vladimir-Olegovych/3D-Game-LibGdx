package core.terrain

import com.gigapi.effects.DisposableEffect
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
import core.terrain.biome.BiomePoints
import core.terrain.biome.BiomeSelector
import core.terrain.biome.biomes.DesertBiomeGenerator
import core.terrain.biome.biomes.ForestBiomeGenerator
import core.terrain.biome.biomes.MountainBiomeGenerator
import core.terrain.biome.biomes.SpruceBiomeGenerator
import core.terrain.biome.models.BiomeType
import core.terrain.layers.CaveLayerHandler
import core.terrain.level.StructurePlacer
import core.terrain.structures.houses.OldHouseStructure
import core.terrain.structures.nature.PlantPatch
import core.terrain.structures.nature.RockStructure
import core.terrain.structures.trees.CactusStructure
import core.terrain.structures.trees.OakTreeStructure
import core.terrain.structures.trees.SpruceTreeStructure
import math.noice.FastNoise
import java.util.EnumMap
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.roundToInt

/** Результат дешёвой выборки поверхности для LOD (без пещер/деревьев). */
class LodSample(val height: Int, val biome: BiomeType)

class TerrainGenerator : DisposableEffect, LaunchedEffect {

    companion object {
        const val UNDERGROUND_HEIGHT = 80
        const val CAVE_THRESHOLD = 0.025f
        const val CAVE_LEVEL = -UNDERGROUND_HEIGHT
        const val WORLD_HEIGHT = 300
        const val WORLD_SURFACE = WORLD_HEIGHT / 1.5
        const val SKY_MARGIN = 40
        const val SURFACE_HEIGHT = (WORLD_SURFACE - SKY_MARGIN).toInt()

        /**
         * Пропуск генерации чанков высоко над поверхностью.
         * ВАЖНО: работает, только если blendedNoise.second — это ВЫСОТА ПОВЕРХНОСТИ В МИРОВЫХ БЛОКАХ.
         */
        var skySkipEnabled = true
    }

    /** Примитивные массивы вместо Array<Pair<Float, Int>?>: нет боксинга и в кэше меньше объектов. */
    private class ColumnInfo(
        val biomes: Array<BiomePointGrid.BiomeData?>,
        val terrain: FloatArray,
        val surface: IntArray,
        val maxSurface: Int
    )

    private lateinit var noiseWarp: DomainWarping2D
    private lateinit var biomePointGrid: BiomePointGrid
    private lateinit var biomeGenerators: Map<BiomeType, BiomeGenerator>
    private lateinit var structurePlacer: StructurePlacer

    /** Размер ограничивается через retainColumns() (скользящее окно вокруг игрока) и dispose(). */
    private val columnCache = ConcurrentHashMap<Long, ColumnInfo>()

    var worldSeedFromServer: Int? = null

    override fun dispose() {
        columnCache.clear()
    }

    override fun launch(gContext: GContext) {
        val worldSeed = worldSeedFromServer ?: error("worldSeedFromServer is null")
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

        biomeGenerators = EnumMap<BiomeType, BiomeGenerator>(BiomeType::class.java).apply {
            put(BiomeType.FOREST, gContext.getObject<ForestBiomeGenerator>())
            put(BiomeType.SPRUCE_FOREST, gContext.getObject<SpruceBiomeGenerator>())
            put(BiomeType.DESERT, gContext.getObject<DesertBiomeGenerator>())
            put(BiomeType.MOUNTAINS, gContext.getObject<MountainBiomeGenerator>())
        }

        val noise = gContext.getObject<PerlinNoise>(NoiceTypes.PERLIN_WORLD)
        val perlinNoiseGenerator = noise.asGenerator()
        noiseWarp = getNoiseDomainWarping(perlinNoiseGenerator)
        biomePointGrid = gContext.getObject()

        structurePlacer = StructurePlacer(
            seed = worldSeed,
            structures = listOf(
                *PlantPatch.structures.toTypedArray(), RockStructure(),
                OakTreeStructure(), SpruceTreeStructure(), CactusStructure(),
                OldHouseStructure()
            ),
            noise = perlinNoiseGenerator,
            surface = ::sampleSurface,
            worldTopY = WORLD_HEIGHT * 2
        )
        gContext.setObject(structurePlacer)
    }

    /** Над чанком (cx,cy,cz) гарантированно только небо (всё выше поверхности + запас). */
    fun isSkyOpenAbove(cx: Int, cy: Int, cz: Int, chunkHeight: Int): Boolean {
        val col = columnCache[packKey(cx, cz)] ?: return false
        return (cy + 1) * chunkHeight > col.maxSurface + SKY_MARGIN
    }

    fun isSkyOpenAbove(
        cx: Int,
        cy: Int,
        cz: Int,
        localX: Int,
        localZ: Int,
        chunkWidth: Int,
        chunkHeight: Int
    ): Boolean? {
        require(localX in 0 until chunkWidth)
        require(localZ in 0 until chunkWidth)

        val column = columnCache[packKey(cx, cz)] ?: return null
        val i = localZ * chunkWidth + localX
        if (column.biomes[i] == null) return null

        // Первый мировой Y непосредственно над чанком.
        return (cy + 1) * chunkHeight > column.surface[i]
    }

    fun generateBiomePoints(position: IntVector3): BiomePoints =
        biomePointGrid.getPointsAround(position)

    fun generateChunkData(chunkData: ChunkData, points: BiomePoints) {
        val w = chunkData.chunkWidth
        val cx = chunkData.position.x
        val cz = chunkData.position.z

        val column = columnCache.computeIfAbsent(packKey(cx, cz)) {
            computeColumn(cx, cz, w, points)
        }

        val chunkMinY = chunkData.position.y * chunkData.chunkHeight
        val skipTerrain = skySkipEnabled && chunkMinY > column.maxSurface + SKY_MARGIN

        if (!skipTerrain) {
            for (x in 0 until w) {
                for (z in 0 until w) {
                    val i = z * w + x
                    val biomeData = column.biomes[i] ?: continue
                    biomeGenerators[biomeData.currentBiome]
                        ?.process(chunkData, biomeData, x, z, Pair(column.terrain[i], column.surface[i]))
                }
            }
        }

        structurePlacer.apply(chunkData)
    }

    private fun computeColumn(cx: Int, cz: Int, w: Int, points: BiomePoints): ColumnInfo {
        val n = w * w
        val biomes = arrayOfNulls<BiomePointGrid.BiomeData>(n)
        val terrain = FloatArray(n)
        val surface = IntArray(n)
        var maxSurface = Int.MIN_VALUE

        for (lz in 0 until w) {
            for (lx in 0 until w) {
                val wx = cx * w + lx
                val wz = cz * w + lz
                val offset = noiseWarp.generateDomainOffset(wx, wz)
                val warpX = (wx + offset.x * 10.0f).toInt()
                val warpZ = (wz + offset.y * 8.0f).toInt()

                val biomeData = biomePointGrid.getBiomeAt(points, warpX, warpZ)
                if (biomeGenerators[biomeData.currentBiome] == null) continue

                val blended = blendSurface(biomeData, warpX, warpZ)
                val i = lz * w + lx
                biomes[i] = biomeData
                terrain[i] = blended.first
                surface[i] = blended.second
                maxSurface = max(maxSurface, blended.second)
            }
        }
        return ColumnInfo(biomes, terrain, surface, maxSurface)
    }

    private fun blendSurface(
        biomeData: BiomePointGrid.BiomeData,
        warpX: Int,
        warpZ: Int
    ): Pair<Float, Int> {
        val weights = biomeData.weights

        // Один биом (большая часть карты): смешивать нечего.
        if (weights.size == 1) {
            val biome = weights.keys.first()
            return (biomeGenerators[biome] ?: error("No biome generator for $biome"))
                .computeSurfaceNoise(warpX, warpZ)
        }

        var terrainSum = 0.0
        var surfaceSum = 0.0
        var weightSum = 0.0

        for ((biome, weight) in weights) {
            val biomeGenerator = biomeGenerators[biome] ?: error("No biome generator for $biome")
            val noise = biomeGenerator.computeSurfaceNoise(warpX, warpZ)
            terrainSum += noise.first.toDouble() * weight
            surfaceSum += noise.second.toDouble() * weight
            weightSum += weight
        }
        check(weightSum > 0.0)
        return Pair((terrainSum / weightSum).toFloat(), (surfaceSum / weightSum).roundToInt())
    }

    fun retainColumns(minCx: Int, maxCx: Int, minCz: Int, maxCz: Int) {
        columnCache.keys.removeIf { key ->
            val cx = (key shr 32).toInt()
            val cz = key.toInt()
            cx !in minCx..maxCx || cz < minCz || cz > maxCz
        }
    }

    fun sampleSurface(wx: Int, wz: Int): LodSample {
        val offset = noiseWarp.generateDomainOffset(wx, wz)
        val warpX = (wx + offset.x * 10.0f).toInt()
        val warpZ = (wz + offset.y * 8.0f).toInt()

        // Окно биомов берётся из ограниченного LRU в BiomePointGrid — отдельный кэш и clear() не нужны.
        val points = biomePointGrid.getPointsAround(wx, wz)
        val biomeData = biomePointGrid.getBiomeAt(points, warpX, warpZ)
        val blended = blendSurface(biomeData, warpX, warpZ)
        return LodSample(blended.second, biomeData.currentBiome)
    }

    private fun packKey(x: Int, z: Int): Long =
        (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)

    private fun getNoiseDomainWarping(noiceGenerator: NoiceGenerator): DomainWarping2D {
        val domainXNoiseSettings = NoiseSettings(
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