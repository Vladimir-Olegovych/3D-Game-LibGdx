package core.mesh

import com.gigapi.math.vector.IntVector3
import core.chunk.ChunkData

class ChunkShadowSampler {

    fun sampleDefaultShadow(
        currentChunk: ChunkData,
        chunkMap: Map<IntVector3, ChunkData>,
        x: Int,
        y: Int,
        z: Int,
        chunkWidth: Int,
        chunkHeight: Int
    ): Float {
        val fallback = currentChunk.getDefaultShadowValue(
            x.coerceIn(0, chunkWidth - 1),
            y.coerceIn(0, chunkHeight - 1),
            z.coerceIn(0, chunkWidth - 1)
        )

        if (x in 0 until chunkWidth && y in 0 until chunkHeight && z in 0 until chunkWidth) {
            return currentChunk.getDefaultShadowValue(x, y, z)
        }

        var chunkOffX = 0
        var chunkOffY = 0
        var chunkOffZ = 0
        var localX = x
        var localY = y
        var localZ = z

        when {
            x < 0 -> {
                chunkOffX = -1
                localX = x + chunkWidth
            }
            x >= chunkWidth -> {
                chunkOffX = 1
                localX = x - chunkWidth
            }
        }

        when {
            y < 0 -> {
                chunkOffY = -1
                localY = y + chunkHeight
            }
            y >= chunkHeight -> {
                chunkOffY = 1
                localY = y - chunkHeight
            }
        }

        when {
            z < 0 -> {
                chunkOffZ = -1
                localZ = z + chunkWidth
            }
            z >= chunkWidth -> {
                chunkOffZ = 1
                localZ = z - chunkWidth
            }
        }

        val neighborChunkPos = IntVector3(
            currentChunk.position.x + chunkOffX,
            currentChunk.position.y + chunkOffY,
            currentChunk.position.z + chunkOffZ
        )

        val neighborChunk = chunkMap[neighborChunkPos] ?: return fallback
        if (localY !in 0 until chunkHeight) return fallback

        return neighborChunk.getDefaultShadowValue(localX, localY, localZ)
    }
}
