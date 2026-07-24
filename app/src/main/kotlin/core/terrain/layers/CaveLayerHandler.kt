package core.terrain.layers

import com.gigapi.math.vector.IntVector3
import core.blocks.BlockType
import core.chunk.ChunkData
import core.terrain.BlockLayerHandler
import core.terrain.TerrainGenerator.Companion.CAVE_LEVEL
import core.terrain.TerrainGenerator.Companion.CAVE_THRESHOLD
import math.noice.FastNoise

class CaveLayerHandler(
    private val caveNoise: FastNoise,
) : BlockLayerHandler() {

    override fun handling(
        chunkData: ChunkData,
        localPosition: IntVector3,
        worldPosition: IntVector3,
        heightNoice: Pair<Float, Int>
    ) {
        if (worldPosition.y >= CAVE_LEVEL || worldPosition.y >= heightNoice.second) return
        if (!isCave(worldPosition.x, worldPosition.y, worldPosition.z)) return

        chunkData.setBlockByLocal(BlockType.AIR, localPosition)
    }

    private fun isCave(x: Int, y: Int, z: Int): Boolean {
        val fx = x.toFloat()
        val fy = y.toFloat()
        val fz = z.toFloat()

        val n1 = caveNoise.GetSimplex(fx, fy, fz)
        val n1sq = n1 * n1
        if (n1sq >= CAVE_THRESHOLD) return false

        val n2 = caveNoise.GetSimplex(fx + CAVE_NOISE_OFFSET, fy + CAVE_NOISE_OFFSET, fz + CAVE_NOISE_OFFSET)
        return n1sq + n2 * n2 < CAVE_THRESHOLD
    }

    companion object {
        private const val CAVE_NOISE_OFFSET = 10_000f
        const val CAVE_NOISE_FREQUENCY = 0.015f
    }
}
