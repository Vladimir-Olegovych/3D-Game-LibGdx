package core.terrain.biome

import core.chunk.ChunkData
import core.terrain.BlockLayerHandler

abstract class BiomeGenerator {

    protected abstract val startLayerHandler: BlockLayerHandler

    abstract fun computeSurfaceNoise(worldX: Int, worldZ: Int): Pair<Float, Int>

    abstract fun process(chunkData: ChunkData, x: Int, z: Int, surfaceNoise: Pair<Float, Int>)

    open fun process(chunkData: ChunkData, x: Int, z: Int) {
        val worldX = chunkData.position.x * chunkData.chunkWidth + x
        val worldZ = chunkData.position.z * chunkData.chunkWidth + z
        process(chunkData, x, z, computeSurfaceNoise(worldX, worldZ))
    }
}