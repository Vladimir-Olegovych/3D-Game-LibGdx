package core.chunk

import com.gigapi.math.vector.IntVector3
import core.blocks.BlockType
import core.chunk.world.WorldDataHelper
import java.util.concurrent.ConcurrentHashMap

/**
 * Cross-chunk structure writes go here, keyed by target chunk.
 * Applied before first mesh; already-drawn targets are marked dirty for batched remesh.
 */
class WorldPendingBlocks {

    private val pendingByChunk = ConcurrentHashMap<IntVector3, ConcurrentHashMap<Int, BlockType>>()

    @Volatile
    private var chunkDataMap: ConcurrentHashMap<IntVector3, ChunkData>? = null


    fun bind(
        chunkDataMap: ConcurrentHashMap<IntVector3, ChunkData>
    ) {
        this.chunkDataMap = chunkDataMap
    }

    fun applyChunk(chunkData: ChunkData) {
        val chunkKey = chunkData.position
        val map = pendingByChunk[chunkKey]?: return
        for ((index, block) in map) {
            chunkData.setBlockByIndex(block, index)
        }
    }

    fun deleteChunk(chunkPosition: IntVector3){
        pendingByChunk.remove(chunkPosition)
    }

    fun offer(worldX: Int, worldY: Int, worldZ: Int, blockType: BlockType) {
        val cx = WorldDataHelper.floorDiv(worldX, ChunkWorldUpdater.CHUNK_SIZE)
        val cy = WorldDataHelper.floorDiv(worldY, ChunkWorldUpdater.CHUNK_HEIGHT)
        val cz = WorldDataHelper.floorDiv(worldZ, ChunkWorldUpdater.CHUNK_SIZE)

        val lx = worldX - cx * ChunkWorldUpdater.CHUNK_SIZE
        val ly = worldY - cy * ChunkWorldUpdater.CHUNK_HEIGHT
        val lz = worldZ - cz * ChunkWorldUpdater.CHUNK_SIZE
        val chunkKey = IntVector3(cx, cy, cz)
        val index = localIndex(lx, ly, lz)

        var map  = pendingByChunk[chunkKey]
        if (map == null) {
            map = ConcurrentHashMap()
            pendingByChunk[chunkKey] = map
        }

        map[index] = blockType
    }


    fun clear() {
        pendingByChunk.clear()
    }

    companion object {

        fun localIndex(x: Int, y: Int, z: Int): Int {
            val w = ChunkWorldUpdater.CHUNK_SIZE
            val h = ChunkWorldUpdater.CHUNK_HEIGHT
            return x * h * w + y * w + z
        }
    }
}