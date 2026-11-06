package core.terrain.layers

import com.gigapi.math.vector.IntVector3
import core.blocks.types.BlockType
import core.chunk.ChunkData
import core.math.LayerNoise.fbm2
import core.math.LayerNoise.noise2
import core.terrain.BlockLayerHandler
import core.terrain.TerrainGenerator.Companion.UNDERGROUND_HEIGHT
import kotlin.math.max
import kotlin.math.min

class SurfaceLayerHandler(
    private val surfaceBlockType: BlockType = BlockType.GRASS,
    private val underSurfaceBlockType: BlockType = BlockType.DIRT,
    private val dirtPatchThreshold: Float = 0.95f,
    private val gravelThreshold: Float = 0.95f,
    private val snowFringeThreshold: Float = 0.65f,
    private val snowSurfaceHeight: Int? = null,
    private val sandSurfaceHeight: Int? = null,
    private val subsoilBlockType: BlockType = BlockType.STONE,
    private val seed: Int
) : BlockLayerHandler() {

    private enum class Top { SURFACE, DIRT_PATCH, GRAVEL, SAND, SNOW_FRINGE, SNOW }

    private companion object {
        const val S_THICK = 101
        const val S_SNOW = 113
        const val S_SNOW_PATCH = 127
        const val S_SNOW_DEPTH = 131
        const val S_SAND = 137
        const val S_SAND_DEPTH = 149
        const val S_DIRT_PATCH = 151
        const val S_GRAVEL = 163

        const val SNOW_LINE_JITTER = 10f   // +-5 блоков
        const val SNOW_FRINGE = 3          // ширина зоны пятнистого снега под линией
        const val SAND_LINE_JITTER = 4f    // +-2 блока
    }

    override fun handling(
        chunkData: ChunkData,
        localPosition: IntVector3,
        worldPosition: IntVector3,
        heightNoice: Pair<Float, Int>
    ) {
        val surfaceY = heightNoice.second
        val y = worldPosition.y
        if (y > surfaceY) return

        val depth = surfaceY - y
        val soilMax = max(UNDERGROUND_HEIGHT, 1)
        if (depth >= soilMax) return            // глубже — работает подземный слой

        val x = worldPosition.x
        val z = worldPosition.z

        // толщина почвы 2..4, плавно меняется по карте
        val thickness = min(soilMax, 2 + (fbm2(x / 48f, z / 48f, S_THICK + seed) * 3f).toInt())

        val top = classify(x, z, surfaceY)

        val block = when {
            depth >= thickness -> subsoilBlockType
            depth == 0 -> topBlock(top)
            else -> underBlock(top, depth, thickness, x, z)
        }
        chunkData.setBlockByLocal(block, localPosition)
    }

    fun topBlockAt(x: Int, z: Int, surfaceY: Int): BlockType = topBlock(classify(x, z, surfaceY))
    // ----------------------------------------------------------- классификация столбца

    private fun classify(x: Int, z: Int, surfaceY: Int): Top {
        // снег
        if (snowSurfaceHeight != null) {
            val line = snowSurfaceHeight + ((fbm2(x / 30f, z / 30f, S_SNOW + seed) - snowFringeThreshold) * SNOW_LINE_JITTER).toInt()
            if (surfaceY > line) return Top.SNOW
            if (surfaceY > line - SNOW_FRINGE && noise2(x / 4f, z / 4f, S_SNOW_PATCH + seed) > snowFringeThreshold) {
                return Top.SNOW_FRINGE
            }
        }
        // пляж
        if (sandSurfaceHeight != null) {
            val line = sandSurfaceHeight + ((fbm2(x / 20f, z / 20f, S_SAND + seed) - 0.5f) * SAND_LINE_JITTER).toInt()
            if (surfaceY <= line) return Top.SAND
        }
        // пятна на траве
        if (noise2(x / 7f, z / 7f, S_GRAVEL + seed) > gravelThreshold) return Top.GRAVEL
        if (noise2(x / 5f, z / 5f, S_DIRT_PATCH + seed) > dirtPatchThreshold) return Top.DIRT_PATCH
        return Top.SURFACE
    }

    private fun topBlock(top: Top): BlockType = when (top) {
        Top.SURFACE -> surfaceBlockType
        Top.DIRT_PATCH -> underSurfaceBlockType
        Top.GRAVEL -> BlockType.GRAVEL
        Top.SAND -> BlockType.SAND
        Top.SNOW_FRINGE -> BlockType.SNOW_DIRT
        Top.SNOW -> BlockType.SNOW
    }

    private fun underBlock(top: Top, depth: Int, thickness: Int, x: Int, z: Int): BlockType = when (top) {
        // песок 2..3 блока
        Top.SAND ->
            if (depth < 2 + (noise2(x / 6f, z / 6f, S_SAND_DEPTH + seed) * 2f).toInt()) BlockType.SAND
            else underSurfaceBlockType
        // снег 1..2 блока
        Top.SNOW ->
            if (depth < 1 + (noise2(x / 6f, z / 6f, S_SNOW_DEPTH + seed) * 2f).toInt()) BlockType.SNOW
            else underSurfaceBlockType
        else -> underSurfaceBlockType
    }
}