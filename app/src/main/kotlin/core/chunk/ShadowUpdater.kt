package core.chunk

import com.gigapi.math.vector.IntVector3
import core.blocks.BlockType

class ShadowUpdater {

    fun updateShadow(
        chunkMap: Map<IntVector3, ChunkData>,
        chunkData: ChunkData
    ) {
        val w = chunkData.chunkWidth
        val h = chunkData.chunkHeight
        val chunkPos = chunkData.position

        for (x in 0 until w) {
            for (z in 0 until w) {
                var skyVisible = isSkyVisibleAbove(chunkMap, chunkPos, x, z)

                for (y in (h - 1) downTo 0) {
                    chunkData.setDefaultShadowValue(if (skyVisible) 1f else 0f, x, y, z)
                    if (isOpaque(chunkData.getBlockByLocal(x, y, z))) {
                        skyVisible = false
                    }
                }
            }
        }
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