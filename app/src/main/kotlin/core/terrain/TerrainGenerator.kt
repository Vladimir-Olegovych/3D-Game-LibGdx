package core.terrain

import com.gigapi.core.effects.LaunchedEffect
import com.gigapi.general.Context
import core.chunk.ChunkData
import core.terrain.biome.ForestBiomeGenerator

class TerrainGenerator: LaunchedEffect {

    private lateinit var defaultForestBiomeGenerator: ForestBiomeGenerator

    override fun launch(context: Context) {
        defaultForestBiomeGenerator = context.getObject()
    }

    fun generateChunkData(chunkData: ChunkData) {
        for (x in 0 until chunkData.chunkWidth) {
            for (z in 0 until chunkData.chunkWidth) {
                defaultForestBiomeGenerator.generator.processChunkColumn(chunkData, x, z, null)
            }
        }
    }

}