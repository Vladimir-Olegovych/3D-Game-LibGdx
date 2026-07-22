package core.chunk

import com.gigapi.math.vector.IntVector3
import core.blocks.BlockType

class ShadowUpdater {

    fun updateShadow(
        chunkMap: Map<IntVector3, ChunkData>,
        chunkData: ChunkData,
        updateChunksBelow: Boolean = false
    ): Set<IntVector3> {
        val updatedChunks = mutableSetOf<IntVector3>()
        var currentChunk = chunkData

        while (true) {
            if (updateChunkShadow(chunkMap, currentChunk)) {
                updatedChunks.add(currentChunk.position)
            }
            if (!updateChunksBelow) break

            val currentPos = currentChunk.position
            currentChunk = chunkMap[IntVector3(currentPos.x, currentPos.y - 1, currentPos.z)] ?: break
        }

        return updatedChunks
    }

    private fun updateChunkShadow(
        chunkMap: Map<IntVector3, ChunkData>,
        chunkData: ChunkData
    ): Boolean {
        val w = chunkData.chunkWidth
        val h = chunkData.chunkHeight
        val chunkPos = chunkData.position
        var changed = false

        for (x in 0 until w) {
            for (z in 0 until w) {
                var skyVisible = isSkyVisibleAbove(chunkMap, chunkPos, x, z)

                for (y in (h - 1) downTo 0) {
                    val shadow = if (skyVisible) 1f else 0.5f
                    if (chunkData.getDefaultShadowValue(x, y, z) != shadow) {
                        chunkData.setDefaultShadowValue(shadow, x, y, z)
                        changed = true
                    }
                    if (isOpaque(chunkData.getBlockByLocal(x, y, z))) {
                        skyVisible = false
                    }
                }
            }
        }

        return changed
    }

    private fun isSkyVisibleAbove(
        chunkMap: Map<IntVector3, ChunkData>,
        chunkPos: IntVector3,
        localX: Int,
        localZ: Int
    ): Boolean {
        var yChunk = chunkPos.y + 1
        while (true) {
            val aboveChunk = chunkMap[IntVector3(chunkPos.x, yChunk, chunkPos.z)] ?: return true
            for (y in (aboveChunk.chunkHeight - 1) downTo 0) {
                if (isOpaque(aboveChunk.getBlockByLocal(localX, y, localZ))) {
                    return false
                }
            }
            yChunk++
        }
    }

    private fun isOpaque(blockType: BlockType): Boolean {
        return blockType != BlockType.AIR && blockType != BlockType.NOTHING
    }
}