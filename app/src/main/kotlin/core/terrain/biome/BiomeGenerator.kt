package core.terrain.biome

import com.gigapi.math.vector.IntVector3
import core.blocks.BlockType
import core.chunk.ChunkData
import core.chunk.ChunkManager
import core.noice.DomainWarping
import core.noice.NoiceUtils
import core.noice.NoiseSettings
import core.terrain.BlockLayerHandler

class BiomeGenerator(
    private val domainWarping: DomainWarping,
    private val biomeNoiseSettings: NoiseSettings,
    private val startLayerHandler: BlockLayerHandler
) {

    fun processChunkColumn(
        chunkData: ChunkData, x: Int, z: Int, terrainHeightNoise: Int?
    ) {
        val worldPosition = IntVector3(
            x = chunkData.position.x * chunkData.chunkWidth + x,
            z = chunkData.position.z * chunkData.chunkWidth + z
        )

        val ground = getSurfaceHeightNoise(worldPosition.x, worldPosition.z, ChunkManager.WORLD_HEIGHT)

        for (y in 0 until chunkData.chunkHeight) {
            worldPosition.y = chunkData.position.y * chunkData.chunkHeight + y
            val localPosition = IntVector3(x, y, z)
            startLayerHandler.handle(chunkData, localPosition, worldPosition, ground)
        }
    }

    fun getSurfaceHeightNoise(x: Int, z: Int, height: Int): Int {
        var terrainHeight = domainWarping.generateDomainNoise(x, z, biomeNoiseSettings)
        terrainHeight = NoiceUtils.redistribution(terrainHeight, biomeNoiseSettings)
        val surfaceHeight = NoiceUtils.remapValue01ToInt(terrainHeight, 0f, height.toFloat())
        return surfaceHeight + (height / 2)
    }

}