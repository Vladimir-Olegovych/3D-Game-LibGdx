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

    private val pendingByChunk = ConcurrentHashMap<Long, ConcurrentHashMap<Int, BlockType>>()
    val dirtyMeshChunks = ConcurrentHashMap.newKeySet<IntVector3Key>()

    @Volatile
    private var chunkDataMap: ConcurrentHashMap<IntVector3, ChunkData>? = null

    @Volatile
    private var isMeshDrawn: ((IntVector3) -> Boolean)? = null

    fun bind(
        chunkDataMap: ConcurrentHashMap<IntVector3, ChunkData>,
        isMeshDrawn: (IntVector3) -> Boolean,
    ) {
        this.chunkDataMap = chunkDataMap
        this.isMeshDrawn = isMeshDrawn
    }

    fun offer(worldX: Int, worldY: Int, worldZ: Int, blockType: BlockType) {
        val cx = WorldDataHelper.floorDiv(worldX, ChunkWorldUpdater.CHUNK_SIZE)
        val cy = WorldDataHelper.floorDiv(worldY, ChunkWorldUpdater.CHUNK_HEIGHT)
        val cz = WorldDataHelper.floorDiv(worldZ, ChunkWorldUpdater.CHUNK_SIZE)

        val lx = worldX - cx * ChunkWorldUpdater.CHUNK_SIZE
        val ly = worldY - cy * ChunkWorldUpdater.CHUNK_HEIGHT
        val lz = worldZ - cz * ChunkWorldUpdater.CHUNK_SIZE
        val index = localIndex(lx, ly, lz)
        val chunkKey = packChunk(cx, cy, cz)

        val map = chunkDataMap
        val drawn = isMeshDrawn
        if (map != null) {
            val existing = map[IntVector3(cx, cy, cz)]
            if (existing != null && existing.status != ChunkStatus.GENERATION) {
                existing.setBlockByIndex(blockType, index)
                markDirtyIfDrawn(existing.position, drawn)
                return
            }
        }

        pendingByChunk
            .getOrPut(chunkKey) { ConcurrentHashMap() }[index] = blockType

        // Race: target became CREATED while we queued — flush now.
        if (map != null) {
            val existing = map[IntVector3(cx, cy, cz)]
            if (existing != null && existing.status != ChunkStatus.GENERATION) {
                applyTo(existing)
                markDirtyIfDrawn(existing.position, drawn)
            }
        }
    }

    fun applyTo(chunkData: ChunkData) {
        val key = packChunk(chunkData.position.x, chunkData.position.y, chunkData.position.z)
        val pending = pendingByChunk.remove(key) ?: return
        for ((index, blockType) in pending) {
            chunkData.setBlockByIndex(blockType, index)
        }
    }

    fun discardChunk(position: IntVector3) {
        pendingByChunk.remove(packChunk(position.x, position.y, position.z))
        dirtyMeshChunks.remove(IntVector3Key(position.x, position.y, position.z))
    }

    fun clear() {
        pendingByChunk.clear()
        dirtyMeshChunks.clear()
    }

    private fun markDirtyIfDrawn(position: IntVector3, drawn: ((IntVector3) -> Boolean)?) {
        if (drawn?.invoke(position) == true) {
            dirtyMeshChunks.add(IntVector3Key(position.x, position.y, position.z))
        }
    }

    companion object {
        fun packChunk(x: Int, y: Int, z: Int): Long {
            return (x.toLong() and 0x1FFFFF) or
                ((y.toLong() and 0x1FFFFF) shl 21) or
                ((z.toLong() and 0x1FFFFF) shl 42)
        }

        fun localIndex(x: Int, y: Int, z: Int): Int {
            val w = ChunkWorldUpdater.CHUNK_SIZE
            val h = ChunkWorldUpdater.CHUNK_HEIGHT
            return x * h * w + y * w + z
        }
    }
}

/** Immutable chunk-pos key for concurrent dirty sets (avoids mutable IntVector3 map-key bugs). */
data class IntVector3Key(val x: Int, val y: Int, val z: Int)
