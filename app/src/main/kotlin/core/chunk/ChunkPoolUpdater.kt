package core.chunk

import com.gigapi.math.vector.IntVector3
import core.blocks.types.BlockType
import java.util.Arrays.fill
import java.util.concurrent.ConcurrentLinkedQueue

class ChunkPoolUpdater(
    private val chunkWidth: Int = ChunkWorldUpdater.CHUNK_SIZE,
    private val chunkHeight: Int = ChunkWorldUpdater.CHUNK_HEIGHT
) {
    private val chunkPool = ConcurrentLinkedQueue<ChunkData>()

    fun createChunk(position: IntVector3): ChunkData {
        val pooled = chunkPool.poll()
        return if (pooled != null) {
            pooled.entityId = null
            pooled.position = position
            pooled.light?.let { fill(it, 0) }
            fill(pooled.blocks, BlockType.toByte(BlockType.AIR))
            pooled
        } else {
            ChunkData.create(position, chunkWidth, chunkHeight)
        }
    }

    fun removeChunk(chunkData: ChunkData) {
        chunkPool.offer(chunkData)
    }

    fun clear() {
        chunkPool.clear()
    }

    fun poolSize(): Int = chunkPool.size
}