package core.terrain.biome

import com.gigapi.effects.DisposableEffect
import com.gigapi.effects.LaunchedEffect
import com.gigapi.general.GContext
import com.gigapi.math.vector.IntVector3
import core.chunk.ChunkWorldUpdater
import core.terrain.biome.models.BiomeType
import java.util.*
import kotlin.math.sqrt

/** Неизменяемое окно биомов вокруг ячейки: плоский массив вместо Map<IntVector3, BiomeType>. */
class BiomePoints internal constructor(
    private val minCellX: Int,
    private val minCellZ: Int,
    private val size: Int,
    private val biomes: Array<BiomeType>
) {
    fun biomeAtCell(cellX: Int, cellZ: Int): BiomeType? {
        val ix = cellX - minCellX
        val iz = cellZ - minCellZ
        if (ix < 0 || iz < 0 || ix >= size || iz >= size) return null
        return biomes[iz * size + ix]
    }
}

/** Ограниченный потокобезопасный LRU: память не растёт, ничего не нужно чистить вручную. */
internal class LruCache<K : Any, V : Any>(private val capacity: Int) {
    private val map = object : LinkedHashMap<K, V>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>) = size > capacity
    }

    /** Тяжёлое вычисление идёт вне блокировки; при гонке побеждает первый записавший. */
    inline fun getOrCompute(key: K, compute: () -> V): V {
        synchronized(this) { get(key) }?.let { return it }
        val value = compute()
        return synchronized(this) { putIfAbsentLocked(key, value) }
    }

    fun get(key: K): V? = map[key]

    fun putIfAbsentLocked(key: K, value: V): V = map[key] ?: value.also { map[key] = it }

    fun clear() = synchronized(this) { map.clear() }
}

class BiomePointGrid : LaunchedEffect, DisposableEffect {

    companion object {
        const val STEP_ADD = 32
        const val POINT_RADIUS = ChunkWorldUpdater.DRAW_RADIUS_XZ / 2
        const val POINT_WORLD_STEP = ChunkWorldUpdater.CHUNK_SIZE * STEP_ADD
        const val BLEND_DISTANCE = POINT_WORLD_STEP * 0.4

        private const val HALF_GRID = POINT_RADIUS / 2
        private const val GRID_SIZE = HALF_GRID * 2

        /**
         * Точка влияет, только если dist < minDist + BLEND_DISTANCE (<= ~1.11 шага),
         * а центр ячейки на расстоянии 2 шагов уже дальше. Радиус 2 исчерпывающий.
         */
        private const val SEARCH_RADIUS = 2

        private const val WINDOW_CACHE_SIZE = 256

        private val ALL_BIOMES: Array<BiomeType> = BiomeType.entries.toTypedArray()
    }

    private lateinit var selector: BiomeSelector
    private val windows = LruCache<Long, BiomePoints>(WINDOW_CACHE_SIZE)

    override fun launch(gContext: GContext) {
        selector = gContext.getObject()
    }

    override fun dispose() = windows.clear()

    data class BiomeData(
        val currentBiome: BiomeType,
        val weights: Map<BiomeType, Float>
    )

    fun getBiomeAt(points: BiomePoints, worldX: Int, worldZ: Int): BiomeData {
        val cellX = Math.floorDiv(worldX, POINT_WORLD_STEP)
        val cellZ = Math.floorDiv(worldZ, POINT_WORLD_STEP)

        // Ближайшая точка каждого биома — по квадрату расстояния, sqrt только на финале.
        val nearestSq = DoubleArray(ALL_BIOMES.size) { Double.MAX_VALUE }

        for (cz in cellZ - SEARCH_RADIUS..cellZ + SEARCH_RADIUS) {
            for (cx in cellX - SEARCH_RADIUS..cellX + SEARCH_RADIUS) {
                val biome = points.biomeAtCell(cx, cz) ?: continue
                val dx = worldX.toDouble() - (cx * POINT_WORLD_STEP + POINT_WORLD_STEP / 2)
                val dz = worldZ.toDouble() - (cz * POINT_WORLD_STEP + POINT_WORLD_STEP / 2)
                val d2 = dx * dx + dz * dz
                if (d2 < nearestSq[biome.ordinal]) nearestSq[biome.ordinal] = d2
            }
        }

        var nearestOrdinal = -1
        for (o in nearestSq.indices) {
            if (nearestSq[o] != Double.MAX_VALUE &&
                (nearestOrdinal < 0 || nearestSq[o] < nearestSq[nearestOrdinal])
            ) nearestOrdinal = o
        }
        if (nearestOrdinal < 0) {
            return BiomeData(BiomeType.FOREST, mapOf(BiomeType.FOREST to 1f))
        }

        val nearestBiome = ALL_BIOMES[nearestOrdinal]
        val minDistance = sqrt(nearestSq[nearestOrdinal])

        val raw = DoubleArray(ALL_BIOMES.size)
        var total = 0.0
        var count = 0
        for (o in nearestSq.indices) {
            if (nearestSq[o] == Double.MAX_VALUE) continue
            val t = ((sqrt(nearestSq[o]) - minDistance) / BLEND_DISTANCE).coerceIn(0.0, 1.0)
            val w = 1.0 - t * t * (3.0 - 2.0 * t)
            if (w > 0.0) {
                raw[o] = w
                total += w
                count++
            }
        }

        // Типичный случай (внутри одного биома) — без лишних аллокаций.
        if (count == 1) return BiomeData(nearestBiome, mapOf(nearestBiome to 1f))

        val weights = EnumMap<BiomeType, Float>(BiomeType::class.java)
        for (o in raw.indices) if (raw[o] > 0.0) weights[ALL_BIOMES[o]] = (raw[o] / total).toFloat()
        return BiomeData(nearestBiome, weights)
    }

    fun getPointsAround(position: IntVector3): BiomePoints = getPointsAround(position.x, position.z)

    fun getPointsAround(worldX: Int, worldZ: Int): BiomePoints {
        val centerCellX = Math.floorDiv(worldX, POINT_WORLD_STEP)
        val centerCellZ = Math.floorDiv(worldZ, POINT_WORLD_STEP)
        val key = (centerCellX.toLong() shl 32) or (centerCellZ.toLong() and 0xFFFFFFFFL)

        // Все чанки одной ячейки делят одно окно — раньше оно строилось на каждый чанк заново.
        return windows.getOrCompute(key) {
            val minX = centerCellX - HALF_GRID
            val minZ = centerCellZ - HALF_GRID
            val biomes = Array(GRID_SIZE * GRID_SIZE) { idx ->
                val cellX = minX + idx % GRID_SIZE
                val cellZ = minZ + idx / GRID_SIZE
                selector.getBiomeAt(
                    cellX * POINT_WORLD_STEP + POINT_WORLD_STEP / 2,
                    cellZ * POINT_WORLD_STEP + POINT_WORLD_STEP / 2
                )
            }
            BiomePoints(minX, minZ, GRID_SIZE, biomes)
        }
    }
}