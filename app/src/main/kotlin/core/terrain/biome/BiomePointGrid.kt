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
    private val biomes: Array<BiomeType>,
    private val sitesX: IntArray,
    private val sitesZ: IntArray
) {
    /** Индекс ячейки в окне или -1; позиции точек посчитаны один раз при построении окна. */
    internal fun indexOf(cellX: Int, cellZ: Int): Int {
        val ix = cellX - minCellX
        val iz = cellZ - minCellZ
        if (ix < 0 || iz < 0 || ix >= size || iz >= size) return -1
        return iz * size + ix
    }

    internal fun biomeAt(index: Int): BiomeType = biomes[index]
    internal fun siteXAt(index: Int): Int = sitesX[index]
    internal fun siteZAt(index: Int): Int = sitesZ[index]

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
        /** Шаг сетки в чанках. Было 96 (1536 блоков) — биомы получались огромными. */
        const val STEP_ADD = 48
        const val POINT_WORLD_STEP = ChunkWorldUpdater.CHUNK_SIZE * STEP_ADD
        const val BLEND_DISTANCE = POINT_WORLD_STEP * 0.4

        /**
         * Окно побольше: биомный варп (до ~700 блоков) + SEARCH_RADIUS + позиция игрока
         * не должны выходить за пределы окна, иначе чанки и LOD увидят разные биомы.
         */
        private const val HALF_GRID = 6
        private const val GRID_SIZE = HALF_GRID * 2

        /** Насколько точка биома смещается от центра ячейки (доля шага): рвёт «квадратную» сетку. */
        private const val SITE_JITTER = 0.35

        /**
         * Точка влияет, только если dist < minDist + BLEND_DISTANCE (<= ~1.11 шага),
         * а центр ячейки на расстоянии 2 шагов уже дальше. Радиус 2 исчерпывающий.
         */
        private const val SEARCH_RADIUS = 2

        private const val WINDOW_CACHE_SIZE = 1024

        private val ALL_BIOMES: Array<BiomeType> = BiomeType.entries.toTypedArray()
    }

    private lateinit var selector: BiomeSelector
    private val windows = LruCache<Long, BiomePoints>(WINDOW_CACHE_SIZE)

    override fun launch(gContext: GContext) {
        selector = gContext.getObject()
    }

    override fun dispose() = windows.clear()

    private fun hash(cx: Int, cz: Int, salt: Int): Int {
        var h = selector.seed * 0x45d9f3b + cx * 374761393 + cz * 668265263 + salt * 0x27d4eb2d
        h = (h xor (h ushr 13)) * 1274126177
        return h xor (h ushr 16)
    }

    private fun jitter(h: Int): Double = ((h ushr 8) and 0xFFFF) / 65535.0 - 0.5

    /** Мировая позиция точки биома ячейки (детерминированный джиттер от сида). */
    fun siteX(cx: Int, cz: Int): Int =
        cx * POINT_WORLD_STEP + POINT_WORLD_STEP / 2 +
                (jitter(hash(cx, cz, 1)) * 2.0 * SITE_JITTER * POINT_WORLD_STEP).toInt()

    fun siteZ(cx: Int, cz: Int): Int =
        cz * POINT_WORLD_STEP + POINT_WORLD_STEP / 2 +
                (jitter(hash(cx, cz, 2)) * 2.0 * SITE_JITTER * POINT_WORLD_STEP).toInt()

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
                val idx = points.indexOf(cx, cz)
                if (idx < 0) continue
                val biome = points.biomeAt(idx)
                val dx = worldX.toDouble() - points.siteXAt(idx)
                val dz = worldZ.toDouble() - points.siteZAt(idx)
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
            val n = GRID_SIZE * GRID_SIZE
            val sx = IntArray(n)
            val sz = IntArray(n)
            val biomes = Array(n) { idx ->
                val cellX = minX + idx % GRID_SIZE
                val cellZ = minZ + idx / GRID_SIZE
                sx[idx] = siteX(cellX, cellZ)
                sz[idx] = siteZ(cellX, cellZ)
                selector.getBiomeAt(sx[idx], sz[idx])
            }
            BiomePoints(minX, minZ, GRID_SIZE, biomes, sx, sz)
        }
    }
}