package core.chunk

import com.gigapi.math.vector.IntVector3
import core.blocks.BlockType

data class ShadowUpdateResult(
    val changedChunks: Set<IntVector3>,
    val remeshNeighbors: Set<IntVector3>,
)

class ShadowUpdater {

    fun updateShadow(
        chunkMap: Map<IntVector3, ChunkData>,
        chunkData: ChunkData,
        updateChunksBelow: Boolean = false
    ): ShadowUpdateResult {
        val changedChunks = mutableSetOf<IntVector3>()
        val remeshNeighbors = mutableSetOf<IntVector3>()
        var currentChunk = chunkData

        while (true) {
            val chunkResult = updateChunkShadow(chunkMap, currentChunk)
            if (chunkResult.changed) {
                changedChunks.add(currentChunk.position)
            }
            remeshNeighbors.addAll(chunkResult.remeshNeighbors)
            if (!updateChunksBelow) break

            val currentPos = currentChunk.position
            currentChunk = chunkMap[IntVector3(currentPos.x, currentPos.y - 1, currentPos.z)] ?: break
        }

        return ShadowUpdateResult(changedChunks, remeshNeighbors)
    }

    private data class ChunkShadowResult(
        val changed: Boolean,
        val remeshNeighbors: Set<IntVector3>,
    )

    private fun updateChunkShadow(
        chunkMap: Map<IntVector3, ChunkData>,
        chunkData: ChunkData
    ): ChunkShadowResult {
        val w = chunkData.chunkWidth
        val h = chunkData.chunkHeight
        val chunkPos = chunkData.position
        var changed = false
        val borderFacesChanged = mutableSetOf<ChunkBorderFace>()

        for (x in 0 until w) {
            for (z in 0 until w) {
                var skyVisible = isSkyVisibleAbove(chunkMap, chunkPos, x, z)

                for (y in (h - 1) downTo 0) {
                    val shadow = if (skyVisible) 1f else 0.5f
                    if (chunkData.getDefaultShadowValue(x, y, z) != shadow) {
                        chunkData.setDefaultShadowValue(shadow, x, y, z)
                        changed = true
                        borderFacesChanged.addAll(borderFacesForCell(x, y, z, w, h))
                    }
                    if (isOpaque(chunkData.getBlockByLocal(x, y, z))) {
                        skyVisible = false
                    }

                }
            }
        }

        val remeshNeighbors = borderFacesChanged.mapTo(mutableSetOf()) { face ->
            face.neighborPosition(chunkPos)
        }
        return ChunkShadowResult(changed, remeshNeighbors)
    }

    private fun borderFacesForCell(x: Int, y: Int, z: Int, w: Int, h: Int): Set<ChunkBorderFace> {
        val faces = mutableSetOf<ChunkBorderFace>()
        if (x == 0) faces.add(ChunkBorderFace.WEST)
        if (x == w - 1) faces.add(ChunkBorderFace.EAST)
        if (y == 0) faces.add(ChunkBorderFace.BOTTOM)
        if (y == h - 1) faces.add(ChunkBorderFace.TOP)
        if (z == 0) faces.add(ChunkBorderFace.NORTH)
        if (z == w - 1) faces.add(ChunkBorderFace.SOUTH)
        return faces
    }

    private enum class ChunkBorderFace {
        WEST, EAST, BOTTOM, TOP, NORTH, SOUTH;

        fun neighborPosition(chunkPos: IntVector3): IntVector3 = when (this) {
            WEST -> IntVector3(chunkPos.x - 1, chunkPos.y, chunkPos.z)
            EAST -> IntVector3(chunkPos.x + 1, chunkPos.y, chunkPos.z)
            BOTTOM -> IntVector3(chunkPos.x, chunkPos.y - 1, chunkPos.z)
            TOP -> IntVector3(chunkPos.x, chunkPos.y + 1, chunkPos.z)
            NORTH -> IntVector3(chunkPos.x, chunkPos.y, chunkPos.z - 1)
            SOUTH -> IntVector3(chunkPos.x, chunkPos.y, chunkPos.z + 1)
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

    companion object {
        fun isOpaque(blockType: BlockType): Boolean {
            return blockType != BlockType.AIR && blockType != BlockType.NOTHING
        }
    }
}