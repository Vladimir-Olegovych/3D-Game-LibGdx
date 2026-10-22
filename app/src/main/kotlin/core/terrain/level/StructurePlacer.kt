package core.terrain.level

import com.gigapi.effects.DisposableEffect
import core.chunk.ChunkData
import core.terrain.LodSample
import core.terrain.biome.models.BiomeType
import core.terrain.structures.builder.StructureBuilder
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class StructurePlacer(
    private val seed: Int,
    private val structures: List<WorldStructure>,
    /** Поверхность в любой мировой колонке, независимо от чанков (TerrainGenerator::sampleSurface). */
    private val surface: (Int, Int) -> LodSample,
    /** Выше этой отметки не бывает ни рельефа, ни структур — чанк пропускается сразу. */
    private val worldTopY: Int
): DisposableEffect {
    private class Placed(val x: Int, val y: Int, val z: Int, val seed: Long, val biome: BiomeType)

    private val NONE = Placed(0, 0, 0, 0, BiomeType.FOREST)

    private val caches = Array(structures.size) { ConcurrentHashMap<Long, Placed>() }

    fun apply(chunk: ChunkData) {
        val w = chunk.chunkWidth
        val h = chunk.chunkHeight
        val minX = chunk.position.x * w
        val minZ = chunk.position.z * w
        val minY = chunk.position.y * h
        val maxX = minX + w - 1
        val maxZ = minZ + w - 1
        val maxY = minY + h - 1
        if (minY > worldTopY) return

        for (i in structures.indices) {
            val s = structures[i]
            val cs = s.cellSize
            val cx0 = Math.floorDiv(minX - s.radius, cs)
            val cx1 = Math.floorDiv(maxX + s.radius, cs)
            val cz0 = Math.floorDiv(minZ - s.radius, cs)
            val cz1 = Math.floorDiv(maxZ + s.radius, cs)

            // Порядок обхода фиксирован => результат одинаков в любом чанке.
            for (cz in cz0..cz1) for (cx in cx0..cx1) {
                val p = place(i, s, cx, cz) ?: continue
                if (p.x < minX - s.radius || p.x > maxX + s.radius) continue
                if (p.z < minZ - s.radius || p.z > maxZ + s.radius) continue
                if (p.y - s.below > maxY || p.y + s.above < minY) continue

                val builder = StructureBuilder(
                    chunk, p.x, p.y, p.z,
                    Random(p.seed xor (p.x.toLong() shl 32) xor (p.y.toLong() shl 16) xor p.z.toLong()),
                    p.biome
                )

                s.buildClipped(
                    b = builder,
                    minX = minX - p.x,
                    maxX = maxX - p.x,
                    minY = minY - p.y,
                    maxY = maxY - p.y,
                    minZ = minZ - p.z,
                    maxZ = maxZ - p.z
                )
            }
        }
    }

    /** Освобождение памяти при выгрузке мира / по таймеру. */
    fun clearCache() = caches.forEach { it.clear() }

    private fun place(index: Int, s: WorldStructure, cx: Int, cz: Int): Placed? {
        val cache = caches[index]
        if (cache.size > 50_000) cache.clear()
        val key = (cx.toLong() shl 32) or (cz.toLong() and 0xFFFFFFFFL)
        val p = cache.computeIfAbsent(key) { compute(s, cx, cz) ?: NONE }
        return if (p === NONE) null else p
    }

    private fun compute(s: WorldStructure, cx: Int, cz: Int): Placed? {
        val hash = hash(seed.toLong() xor s.id.hashCode().toLong(), cx, cz)

        val roll = ((hash ushr 40) and 0xFFFFFF).toInt() / 16777216f
        if (roll >= s.chance) return null

        val ax = cx * s.cellSize + (((hash ushr 8) and 0x7FFFFFFF) % s.cellSize).toInt()
        val az = cz * s.cellSize + (((hash ushr 24) and 0x7FFFFFFF) % s.cellSize).toInt()

        val sample = surface(ax, az)
        s.biomes?.let { if (sample.biome !in it) return null }
        if (!s.canSpawn(sample.height, sample.biome)) return null

        return Placed(ax, sample.height, az, splitMix(hash), sample.biome)
    }

    private fun hash(base: Long, a: Int, b: Int): Long =
        splitMix(splitMix(splitMix(base) + a) + b)

    private fun splitMix(x: Long): Long {
        var z = x + -7046029254386353131L
        z = (z xor (z ushr 30)) * -4658895280553007687L
        z = (z xor (z ushr 27)) * -7723592293110705685L
        return z xor (z ushr 31)
    }

    override fun dispose() {
        clearCache()
    }
}