package core.terrain

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
import core.terrain.biome.BiomePointGrid
import core.terrain.biome.BiomeGenerator
import core.terrain.biome.BiomeSelector
import core.terrain.biome.biomes.DesertBiomeGenerator
import core.terrain.biome.biomes.ForestBiomeGenerator
import core.terrain.biome.biomes.MountainBiomeGenerator
import core.terrain.biome.biomes.SpruceBiomeGenerator
import core.terrain.biome.models.BiomeType
import core.terrain.layers.CaveLayerHandler
import core.terrain.level.StructurePlacer
import core.terrain.structures.CactusStructure
import core.terrain.structures.OakTreeStructure
import core.terrain.structures.SpruceTreeStructure
import math.noice.FastNoise
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.roundToInt

/** Результат дешёвой выборки поверхности для LOD (без пещер/деревьев). */
class LodSample(val height: Int, val biome: BiomeType)

class TerrainGenerator : LaunchedEffect {

    companion object {
        const val UNDERGROUND_HEIGHT = 80
        const val CAVE_THRESHOLD = 0.025f
        const val CAVE_LEVEL = -UNDERGROUND_HEIGHT
        const val WORLD_HEIGHT = 300
        const val WORLD_SURFACE = WORLD_HEIGHT / 1.5

        /** Запас над поверхностью (деревья и т.п.): выше этого чанк гарантированно пустой. */
        const val SKY_MARGIN = 40

        /** Размер ячейки (в блоках) для кэша биомных точек LOD. */
        const val LOD_POINT_CELL = 1024

        /**
         * Пропуск генерации чанков высоко над поверхностью.
         * ВАЖНО: работает, только если blendedNoise.second — это ВЫСОТА ПОВЕРХНОСТИ В МИРОВЫХ БЛОКАХ.
         * Если там другое значение — выключи.
         */
        var skySkipEnabled = true
    }

    private class ColumnInfo(
        val biomes: Array<BiomePointGrid.BiomeData?>,
        val noise: Array<Pair<Float, Int>?>,
        val maxSurface: Int
    )

    private lateinit var noiseWarp: DomainWarping2D
    private lateinit var biomePointGrid: BiomePointGrid
    private lateinit var biomeGenerators: Map<BiomeType, BiomeGenerator>
    private lateinit var structurePlacer: StructurePlacer

    /** Кэш на колонку чанков (x,z): шум/биомы не зависят от Y, раньше считались 17 раз. */
    private val columnCache = ConcurrentHashMap<Long, ColumnInfo>()
    private val lodPointsCache = ConcurrentHashMap<Long, Map<IntVector3, BiomeType>>()

    var worldSeedFromServer: Int? = null

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

        biomeGenerators = mapOf(
            BiomeType.FOREST to gContext.getObject<ForestBiomeGenerator>(),
            BiomeType.SPRUCE_FOREST to gContext.getObject<SpruceBiomeGenerator>(),
            BiomeType.DESERT to gContext.getObject<DesertBiomeGenerator>(),
            BiomeType.MOUNTAINS to gContext.getObject<MountainBiomeGenerator>()
        )

        val noise = gContext.getObject<PerlinNoise>(NoiceTypes.PERLIN_WORLD)
        noiseWarp = getNoiseDomainWarping(noise.asGenerator())
        biomePointGrid = gContext.getObject()

        structurePlacer = StructurePlacer(
            seed = worldSeed,
            structures = listOf(
                OakTreeStructure(), SpruceTreeStructure(), CactusStructure()
            ),
            surface = ::sampleSurface,
            worldTopY = WORLD_HEIGHT * 2
        )
    }
    /** Над чанком (cx,cy,cz) гарантированно только небо (всё выше поверхности + запас). */
    fun isSkyOpenAbove(cx: Int, cy: Int, cz: Int, chunkHeight: Int): Boolean {
        val col = columnCache[packKey(cx, cz)] ?: return false
        return (cy + 1) * chunkHeight > col.maxSurface + SKY_MARGIN
    }
    fun isTerrainSkyOpenAbove(
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

        val surfaceY =
            column.noise[localZ * chunkWidth + localX]?.second
                ?: return null

        // Первый мировой Y непосредственно над чанком.
        val aboveY = (cy + 1) * chunkHeight

        return aboveY > surfaceY
    }
    fun generateBiomePoints(position: IntVector3) =
        biomePointGrid.getPointsAround(position)


    fun generateChunkData(chunkData: ChunkData, points: Map<IntVector3, BiomeType>) {
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
                    val noise = column.noise[i] ?: continue
                    biomeGenerators[biomeData.currentBiome]?.process(chunkData, biomeData, x, z, noise)
                }
            }
        }

        structurePlacer.apply(chunkData)
    }
    private fun computeColumn(
        cx: Int, cz: Int, w: Int,
        points: Map<IntVector3, BiomeType>
    ): ColumnInfo {
        val biomes = arrayOfNulls<BiomePointGrid.BiomeData>(w * w)
        val noises = arrayOfNulls<Pair<Float, Int>>(w * w)
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
                noises[i] = blended
                maxSurface = max(maxSurface, blended.second)
            }
        }
        return ColumnInfo(biomes, noises, maxSurface)
    }

    private fun blendSurface(
        biomeData: BiomePointGrid.BiomeData,
        warpX: Int,
        warpZ: Int
    ): Pair<Float, Int> {
        var terrainSum = 0.0
        var surfaceSum = 0.0
        var weightSum = 0.0

        for ((biome, weight) in biomeData.weights) {
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

        val biomeData = biomePointGrid.getBiomeAt(lodPoints(wx, wz), warpX, warpZ)
        val blended = blendSurface(biomeData, warpX, warpZ)
        return LodSample(blended.second, biomeData.currentBiome)
    }

    private fun lodPoints(wx: Int, wz: Int): Map<IntVector3, BiomeType> {
        val cx = Math.floorDiv(wx, LOD_POINT_CELL)
        val cz = Math.floorDiv(wz, LOD_POINT_CELL)
        if (lodPointsCache.size > 4096) lodPointsCache.clear()
        return lodPointsCache.computeIfAbsent(packKey(cx, cz)) {
            generateBiomePoints(
                IntVector3(cx * LOD_POINT_CELL + LOD_POINT_CELL / 2, 0, cz * LOD_POINT_CELL + LOD_POINT_CELL / 2)
            )
        }
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