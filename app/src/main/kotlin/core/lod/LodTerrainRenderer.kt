package core.lod

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.gigapi.effects.DisposableEffect
import com.gigapi.effects.LaunchedEffect
import com.gigapi.general.GContext
import core.shaders.ShaderTypes
import core.terrain.TerrainGenerator
import core.terrain.biome.models.BiomeType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

object LodConfig {
    const val TILE_CELLS = 48
    const val BASE_CELL = 4
    const val RING = 3

    const val FOG_DISTANCE = 10_000f

    const val MAX_PENDING = 3
    const val MAX_UPLOADS_PER_FRAME = 3

    const val HEIGHT_OFFSET = 1f

    val LEVELS: Int = run {
        var n = 1
        while (RING * tileBlocks(n - 1) < FOG_DISTANCE) n++
        n
    }

    fun cellBlocks(level: Int) = BASE_CELL shl level
    fun tileBlocks(level: Int) = TILE_CELLS * cellBlocks(level)

    fun bias(level: Int) = 1f + cellBlocks(level) * 0.35f

    fun fadeEnd(level: Int): Float =
        if (level >= LEVELS - 1) 2e9f else RING * tileBlocks(level).toFloat()

    fun fadeStart(level: Int): Float =
        if (level >= LEVELS - 1) 1e9f else fadeEnd(level) * 0.78f

    fun skirtDepth(level: Int) = 2f + cellBlocks(level) * 1.25f
}

class LodTerrainRenderer : LaunchedEffect, DisposableEffect {

    private class TileData(
        val key: Long,
        val level: Int,
        val vertices: FloatArray,
        val indices: ShortArray,
        val minY: Float,
        val maxY: Float
    )

    private class Tile(
        val mesh: Mesh,
        val centerY: Float,
        val halfY: Float
    )

    private class Candidate(
        val level: Int,
        val tx: Int,
        val tz: Int,
        val key: Long,
        val dist2: Float
    )

    private lateinit var terrain: TerrainGenerator
    private lateinit var sh: ShaderProgram

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val tiles = HashMap<Long, Tile>()
    private val pending = HashSet<Long>()
    private val retryAt = HashMap<Long, Int>()
    private val ready = ConcurrentLinkedQueue<TileData>()
    private val failed = ConcurrentLinkedQueue<Long>()
    private val candidates = ArrayList<Candidate>()

    private var shader: ShaderProgram? = null
    private var frame = 0

    override fun launch(gContext: GContext) {
        sh = gContext.getObject(ShaderTypes.LOD_SHADER)
        terrain = gContext.getObject()
    }

    override fun dispose() {
        scope.cancel()
        tiles.values.forEach { it.mesh.dispose() }
        tiles.clear()
        pending.clear()
        ready.clear()
        shader?.dispose()
        shader = null
    }

    // ------------------------------------------------------------------ render

    fun render(
        camera: PerspectiveCamera,
        fogColor: FloatArray,
        dayPhase: Float,
        maxShadowThreshold: Float,
        nightBlueFilter: Float
    ) {
        if (!::terrain.isInitialized) return

        frame++

        val camX = camera.position.x
        val camZ = camera.position.z

        updateStreaming(camX, camZ)

        Gdx.gl.glDisable(GL20.GL_CULL_FACE)

        sh.bind()
        sh.setUniformMatrix("modelViewProjection", camera.combined)
        sh.setUniformf("viewPosition", camera.position)
        sh.setUniformf("u_fogRadius", camera.far)
        sh.setUniformf("fogColor", fogColor[0], fogColor[1], fogColor[2])
        sh.setUniformf("u_dayPhase", dayPhase)
        sh.setUniformf("u_maxShadowThreshold", maxShadowThreshold)
        sh.setUniformf("u_nightBlueFilter", nightBlueFilter)

        for (level in 0 until LodConfig.LEVELS) {
            val tileSize = LodConfig.tileBlocks(level).toFloat()
            val half = tileSize * 0.5f

            val px = floor(camX / tileSize).toInt()
            val pz = floor(camZ / tileSize).toInt()

            sh.setUniformf("u_bias", LodConfig.bias(level))
            sh.setUniformf("u_fadeStart", LodConfig.fadeStart(level))
            sh.setUniformf("u_fadeEnd", LodConfig.fadeEnd(level))

            for (tz in pz - LodConfig.RING..pz + LodConfig.RING) {
                for (tx in px - LodConfig.RING..px + LodConfig.RING) {
                    val tile = tiles[key(level, tx, tz)] ?: continue

                    val ox = tx * tileSize
                    val oz = tz * tileSize

                    if (level > 0 && coveredByFiner(level, tx, tz, ox, oz, tileSize, camX, camZ)) {
                        continue
                    }

                    if (!camera.frustum.boundsInFrustum(
                            ox + half,
                            tile.centerY,
                            oz + half,
                            half,
                            tile.halfY,
                            half
                        )
                    ) {
                        continue
                    }

                    sh.setUniformf("u_origin", ox, 0f, oz)
                    tile.mesh.render(sh, GL20.GL_TRIANGLES)
                }
            }
        }

        Gdx.gl.glEnable(GL20.GL_CULL_FACE)
    }

    private fun coveredByFiner(
        level: Int,
        tx: Int,
        tz: Int,
        ox: Float,
        oz: Float,
        size: Float,
        camX: Float,
        camZ: Float
    ): Boolean {
        val reach = LodConfig.fadeStart(level - 1)

        val farthest = max(
            max(abs(ox - camX), abs(ox + size - camX)),
            max(abs(oz - camZ), abs(oz + size - camZ))
        )

        if (farthest > reach) return false

        return childrenReady(level, tx, tz)
    }

    private fun childrenReady(level: Int, tx: Int, tz: Int): Boolean {
        val l = level - 1
        val x = tx * 2
        val z = tz * 2

        return tiles.containsKey(key(l, x, z)) &&
                tiles.containsKey(key(l, x + 1, z)) &&
                tiles.containsKey(key(l, x, z + 1)) &&
                tiles.containsKey(key(l, x + 1, z + 1))
    }

    // ------------------------------------------------------------------ streaming

    private fun updateStreaming(camX: Float, camZ: Float) {
        while (true) {
            val k = failed.poll() ?: break
            pending.remove(k)
            retryAt[k] = frame + 300
        }

        var uploads = 0

        while (uploads < LodConfig.MAX_UPLOADS_PER_FRAME) {
            val data = ready.poll() ?: break
            pending.remove(data.key)

            val level = data.level
            val tx = unpackX(data.key)
            val tz = unpackZ(data.key)

            if (isOutOfRange(level, tx, tz, camX, camZ, margin = 1)) continue

            val mesh = Mesh(
                true,
                data.vertices.size / FLOATS,
                data.indices.size,
                VertexAttribute.Position(),
                VertexAttribute.Normal(),
                VertexAttribute.ColorPacked()
            )

            mesh.setVertices(data.vertices)
            mesh.setIndices(data.indices)

            val bias = LodConfig.bias(level)
            val minY = data.minY - bias
            val maxY = data.maxY

            tiles.put(
                data.key,
                Tile(
                    mesh,
                    (minY + maxY) * 0.5f,
                    (maxY - minY) * 0.5f
                )
            )?.mesh?.dispose()

            uploads++
        }

        val it = tiles.entries.iterator()

        while (it.hasNext()) {
            val e = it.next()
            val level = levelOf(e.key)

            if (isOutOfRange(level, unpackX(e.key), unpackZ(e.key), camX, camZ, margin = 1)) {
                e.value.mesh.dispose()
                it.remove()
            }
        }

        if (frame % 6 != 0 || pending.size >= LodConfig.MAX_PENDING) return

        candidates.clear()

        for (level in 0 until LodConfig.LEVELS) {
            val tileSize = LodConfig.tileBlocks(level).toFloat()
            val px = floor(camX / tileSize).toInt()
            val pz = floor(camZ / tileSize).toInt()

            for (tz in pz - LodConfig.RING..pz + LodConfig.RING) {
                for (tx in px - LodConfig.RING..px + LodConfig.RING) {
                    val k = key(level, tx, tz)

                    if (tiles.containsKey(k) || pending.contains(k)) continue
                    if ((retryAt[k] ?: 0) > frame) continue

                    val dx = (tx + 0.5f) * tileSize - camX
                    val dz = (tz + 0.5f) * tileSize - camZ

                    candidates.add(Candidate(level, tx, tz, k, dx * dx + dz * dz))
                }
            }
        }

        candidates.sortBy { it.dist2 }

        var i = 0

        while (i < candidates.size && pending.size < LodConfig.MAX_PENDING) {
            val c = candidates[i++]
            pending.add(c.key)

            scope.launch {
                try {
                    ready.add(buildTile(c.key, c.level, c.tx, c.tz))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    e.printStackTrace()
                    failed.add(c.key)
                }
            }
        }
    }

    private fun isOutOfRange(
        level: Int,
        tx: Int,
        tz: Int,
        camX: Float,
        camZ: Float,
        margin: Int
    ): Boolean {
        val tileSize = LodConfig.tileBlocks(level).toFloat()
        val px = floor(camX / tileSize).toInt()
        val pz = floor(camZ / tileSize).toInt()
        val r = LodConfig.RING + margin

        return abs(tx - px) > r || abs(tz - pz) > r
    }

    // ------------------------------------------------------------------ tile generation

    private fun buildTile(key: Long, level: Int, tx: Int, tz: Int): TileData {
        val n = LodConfig.TILE_CELLS
        val cellI = LodConfig.cellBlocks(level)
        val cell = cellI.toFloat()

        val baseX = tx * n * cellI
        val baseZ = tz * n * cellI

        // Высоты и биомы для клеток 0..n-1 плюс соседние клетки по всем сторонам.
        val stride = n + 2
        val heights = FloatArray(stride * stride)
        val biomes = arrayOfNulls<BiomeType>(stride * stride)

        for (j in -1 until n + 1) {
            for (i in -1 until n + 1) {
                val s = terrain.sampleSurface(baseX + i * cellI, baseZ + j * cellI)
                val idx = (j + 1) * stride + (i + 1)

                heights[idx] = s.height.toFloat()
                biomes[idx] = s.biome
            }
        }

        fun height(i: Int, j: Int): Float = heights[(j + 1) * stride + (i + 1)]
        fun biome(i: Int, j: Int): BiomeType = biomes[(j + 1) * stride + (i + 1)]!!

        // Верхняя грань + до 4 боковых граней на каждый столбец.
        val maxFaces = n * n * 5
        val v = FloatArray(maxFaces * 4 * FLOATS)
        val indices = ShortArray(maxFaces * 6)

        var p = 0
        var q = 0
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        val yOff = LodConfig.HEIGHT_OFFSET

        fun vertex(
            x: Float, y: Float, z: Float,
            nx: Float, ny: Float, nz: Float,
            color: Float
        ) {
            v[p++] = x
            v[p++] = y
            v[p++] = z

            v[p++] = nx
            v[p++] = ny
            v[p++] = nz

            v[p++] = color

            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }

        fun quad(
            x0: Float, y0: Float, z0: Float,
            x1: Float, y1: Float, z1: Float,
            x2: Float, y2: Float, z2: Float,
            x3: Float, y3: Float, z3: Float,
            nx: Float, ny: Float, nz: Float,
            color: Float
        ) {
            val first = p / FLOATS

            vertex(x0, y0, z0, nx, ny, nz, color)
            vertex(x1, y1, z1, nx, ny, nz, color)
            vertex(x2, y2, z2, nx, ny, nz, color)
            vertex(x3, y3, z3, nx, ny, nz, color)

            indices[q++] = first.toShort()
            indices[q++] = (first + 1).toShort()
            indices[q++] = (first + 2).toShort()

            indices[q++] = first.toShort()
            indices[q++] = (first + 2).toShort()
            indices[q++] = (first + 3).toShort()
        }

        for (j in 0 until n) {
            for (i in 0 until n) {
                val h = height(i, j)
                val topY = h + yOff

                val hx = max(abs(h - height(i - 1, j)), abs(h - height(i + 1, j)))
                val hz = max(abs(h - height(i, j - 1)), abs(h - height(i, j + 1)))
                val slope = max(hx, hz) / cell

                val worldX = baseX + i * cellI
                val worldZ = baseZ + j * cellI
                val color = colorOf(
                    biome = biome(i, j),
                    h = h,
                    slope = slope,
                    worldX = worldX,
                    worldZ = worldZ
                )

                val x0 = i * cell
                val x1 = (i + 1) * cell
                val z0 = j * cell
                val z1 = (j + 1) * cell

                // Верхняя грань блока.
                quad(
                    x0, topY, z0,
                    x1, topY, z0,
                    x1, topY, z1,
                    x0, topY, z1,
                    0f, 1f, 0f,
                    color
                )

                // Стенка к более низкому соседу слева.
                val hl = height(i - 1, j)
                if (hl < h) {
                    val bottom = hl + yOff

                    quad(
                        x0, topY, z0,
                        x0, topY, z1,
                        x0, bottom, z1,
                        x0, bottom, z0,
                        -1f, 0f, 0f,
                        color
                    )
                }

                // Стенка справа.
                val hr = height(i + 1, j)
                if (hr < h) {
                    val bottom = hr + yOff

                    quad(
                        x1, topY, z0,
                        x1, bottom, z0,
                        x1, bottom, z1,
                        x1, topY, z1,
                        1f, 0f, 0f,
                        color
                    )
                }

                // Стенка сзади.
                val hb = height(i, j - 1)
                if (hb < h) {
                    val bottom = hb + yOff

                    quad(
                        x0, topY, z0,
                        x0, bottom, z0,
                        x1, bottom, z0,
                        x1, topY, z0,
                        0f, 0f, -1f,
                        color
                    )
                }

                // Стенка спереди.
                val hf = height(i, j + 1)
                if (hf < h) {
                    val bottom = hf + yOff

                    quad(
                        x0, topY, z1,
                        x1, topY, z1,
                        x1, bottom, z1,
                        x0, bottom, z1,
                        0f, 0f, 1f,
                        color
                    )
                }
            }
        }

        return TileData(
            key,
            level,
            v.copyOf(p),
            indices.copyOf(q),
            minY,
            maxY
        )
    }

    private fun colorOf(
        biome: BiomeType,
        h: Float,
        slope: Float,
        worldX: Int,
        worldZ: Int
    ): Float {
        val variation = hashNoise(worldX, worldZ)
        val heightTint = ((h - TerrainGenerator.WORLD_SURFACE) / 90f).coerceIn(-1.0, 1.0)
        val rockiness = (slope - 0.55f).coerceIn(0f, 1f)

        var r: Float
        var g: Float
        var b: Float

        when (biome) {
            BiomeType.FOREST -> {
                r = 46f / 255f
                g = 204f / 255f
                b = 113f / 255f
            }

            BiomeType.SPRUCE_FOREST -> {
                r = 46f / 255f * 0.9f
                g = 204f / 255f * 0.9f
                b = 113f / 255f * 0.9f
            }

            BiomeType.DESERT -> {
                r = 237f / 255f
                g = 220f / 255f
                b = 184f / 255f
            }

            /*
            BiomeType.CANYON -> {
                r = 155f / 255f
                g = 187f / 255f
                b = 189f / 255f
            }

             */

            BiomeType.MOUNTAINS -> {
                val snow = ((h - SNOW_LINE) / 25f).coerceIn(0f, 1f)
                val base = (155f / 255f) + (187f / 255f) * snow

                r = base
                g = base
                b = base + 0.01f * snow
            }

            else -> {
                r = 0.50f
                g = 0.50f
                b = 0.50f
            }
        }

        // Плавное высотное затемнение/осветление.
        val heightShift = heightTint * 0.07f
        r += heightShift.toFloat()
        g += heightShift.toFloat()
        b += heightShift.toFloat()

        // Каменистые склоны становятся серее и темнее.
        r += (0.44f - r) * rockiness * 0.55f
        g += (0.43f - g) * rockiness * 0.55f
        b += (0.40f - b) * rockiness * 0.55f

        // Мелкая вариация, чтобы поверхность не выглядела пластиковой.
        val noise = (variation - 0.5f) * 0.055f
        r += noise
        g += noise
        b += noise

        return Color.toFloatBits(
            r.coerceIn(0f, 1f),
            g.coerceIn(0f, 1f),
            b.coerceIn(0f, 1f),
            1f
        )
    }

    private fun hashNoise(x: Int, z: Int): Float {
        var h = x * 374761393 + z * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)

        return (h and 0xFFFF).toFloat() / 65535f
    }

    // ------------------------------------------------------------------ keys

    private fun key(level: Int, tx: Int, tz: Int): Long =
        (level.toLong() shl 56) or
                ((tx.toLong() and MASK) shl 28) or
                (tz.toLong() and MASK)

    private fun levelOf(key: Long): Int = (key ushr 56).toInt()

    private fun unpackX(key: Long): Int = signExtend((key shr 28) and MASK)
    private fun unpackZ(key: Long): Int = signExtend(key and MASK)

    private fun signExtend(v: Long): Int = ((v shl 36) shr 36).toInt()

    private companion object {
        const val FLOATS = 7
        const val MASK = 0xFFFFFFFL

        val SNOW_LINE = TerrainGenerator.WORLD_SURFACE.toFloat() + 70f
    }
}