package core.mesh

import com.gigapi.math.vector.IntVector3
import core.blocks.BlockType
import core.chunk.ChunkData

/**
 * Fast block / shadow lookup for one chunk and its 26 neighbours.
 */
class ChunkBlockAccess(
    private val chunk: ChunkData,
    chunkMap: Map<IntVector3, ChunkData>,
    val width: Int,
    val height: Int,
) {
    private val chunkMap: Map<IntVector3, ChunkData> = chunkMap

    fun isSolid(x: Int, y: Int, z: Int): Boolean = when (getBlock(x, y, z)) {
        BlockType.AIR, BlockType.NOTHING -> false
        else -> true
    }

    fun blockExists(x: Int, y: Int, z: Int): Boolean = isSolid(x, y, z)

    fun getBlock(x: Int, y: Int, z: Int): BlockType {
        if (x in 0 until width && y in 0 until height && z in 0 until width) {
            return chunk.getBlockByLocal(x, y, z)
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
                localX = x + width
            }
            x >= width -> {
                chunkOffX = 1
                localX = x - width
            }
        }
        when {
            y < 0 -> {
                chunkOffY = -1
                localY = y + height
            }
            y >= height -> {
                chunkOffY = 1
                localY = y - height
            }
        }
        when {
            z < 0 -> {
                chunkOffZ = -1
                localZ = z + width
            }
            z >= width -> {
                chunkOffZ = 1
                localZ = z - width
            }
        }

        val neighborChunkPos = IntVector3(
            chunk.position.x + chunkOffX,
            chunk.position.y + chunkOffY,
            chunk.position.z + chunkOffZ,
        )
        val neighborChunk = chunkMap[neighborChunkPos]
        return if (neighborChunk != null && localY in 0 until height) {
            neighborChunk.getBlockByLocal(localX, localY, localZ)
        } else {
            BlockType.NOTHING
        }
    }

    fun getShadow(x: Int, y: Int, z: Int): Float {
        if (x in 0 until width && y in 0 until height && z in 0 until width) {
            return chunk.getDefaultShadowValue(x, y, z)
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
                localX = x + width
            }
            x >= width -> {
                chunkOffX = 1
                localX = x - width
            }
        }
        when {
            y < 0 -> {
                chunkOffY = -1
                localY = y + height
            }
            y >= height -> {
                chunkOffY = 1
                localY = y - height
            }
        }
        when {
            z < 0 -> {
                chunkOffZ = -1
                localZ = z + width
            }
            z >= width -> {
                chunkOffZ = 1
                localZ = z - width
            }
        }

        val neighborChunkPos = IntVector3(
            chunk.position.x + chunkOffX,
            chunk.position.y + chunkOffY,
            chunk.position.z + chunkOffZ,
        )
        val neighborChunk = chunkMap[neighborChunkPos]
        return if (neighborChunk != null && localY in 0 until height) {
            neighborChunk.getDefaultShadowValue(localX, localY, localZ)
        } else {
            1f
        }
    }
}
