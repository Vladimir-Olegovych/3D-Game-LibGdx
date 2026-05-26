package core.terrain.biome

import com.gigapi.math.vector.IntVector3
import core.chunk.ChunkData
import core.noice.domain.DomainWarping2D
import core.noice.domain.DomainWarping3D
import core.noice.models.NoiceUtils
import core.noice.models.NoiseSettings
import core.terrain.BlockLayerHandler
import core.terrain.TerrainGenerator
import core.terrain.TerrainGenerator.Companion.CAVE_LEVEL

class BiomeGenerator(
    private val surfaceDomainWarping2D: DomainWarping2D,
    private val caveDomainWarping3D: DomainWarping3D,
    private val biomeNoiseSettings: NoiseSettings,
    private val caveNoiseSettings: NoiseSettings,
    private val startLayerHandler: BlockLayerHandler
) {

    fun processChunkColumn(
        chunkData: ChunkData, x: Int, z: Int, terrainHeightNoise: Int?
    ) {
        val worldPosition = IntVector3(
            x = chunkData.position.x * chunkData.chunkWidth + x,
            z = chunkData.position.z * chunkData.chunkWidth + z
        )
        var surfaceNoice: Pair<Float, Int>? = null

        for (y in 0 until chunkData.chunkHeight) {
            worldPosition.y = chunkData.position.y * chunkData.chunkHeight + y

            if (worldPosition.y >= CAVE_LEVEL && surfaceNoice == null) {
                surfaceNoice = getSurfaceHeightNoise(worldPosition.x, worldPosition.z)
            } else if (worldPosition.y < CAVE_LEVEL) {
                surfaceNoice = null
            }

            val localPosition = IntVector3(x, y, z)
            startLayerHandler.handle(
                chunkData,
                localPosition,
                worldPosition,
                surfaceNoice?: getCaveHeightNoice(worldPosition.x, worldPosition.y, worldPosition.z)
            )
        }
    }

    fun getCaveHeightNoice(x: Int, y: Int, z: Int) : Pair<Float, Int> {
        var terrainHeight = caveDomainWarping3D.generateDomainNoise(x, y, z, caveNoiseSettings)
        //terrainHeight = NoiceUtils.redistribution(terrainHeight, biomeNoiseSettings)
        return Pair(terrainHeight, 0)
    }

    fun getSurfaceHeightNoise(x: Int, z: Int): Pair<Float, Int> {
        val height = TerrainGenerator.WORLD_HEIGHT
        var terrainHeight = surfaceDomainWarping2D.generateDomainNoise(x, z, biomeNoiseSettings)
        terrainHeight = NoiceUtils.redistribution(terrainHeight, biomeNoiseSettings)
        val surfaceHeight = NoiceUtils.remapValue01ToInt(terrainHeight, 0f, height.toFloat())
        return Pair(terrainHeight, surfaceHeight + (height / 2))
    }

}