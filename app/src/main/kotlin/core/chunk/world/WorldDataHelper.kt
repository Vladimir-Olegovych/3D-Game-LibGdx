package core.chunk.world

import com.gigapi.math.vector.IntVector3
import core.chunk.ChunkData
import core.chunk.ChunkManager
import core.mesh.MeshData

object WorldDataHelper {
    fun chunkPositionFromBlockCoords(worldBlockPosition: IntVector3): IntVector3 {
        val chunkSize = ChunkManager.CHUNK_SIZE
        val chunkHeight = ChunkManager.CHUNK_HEIGHT

        return IntVector3(
            x = floorDiv(worldBlockPosition.x, chunkSize),
            y = floorDiv(worldBlockPosition.y, chunkHeight),
            z = floorDiv(worldBlockPosition.z, chunkSize)
        )
    }

    fun floorDiv(a: Int, b: Int): Int = when {
        a >= 0 -> a / b
        else -> (a - b + 1) / b
    }

    fun getChunkPositionsAroundPlayer(
        playerPosition: IntVector3
    ): List<IntVector3> {
        val chunkSize = ChunkManager.CHUNK_SIZE
        val chunkHeight = ChunkManager.CHUNK_HEIGHT
        val chunkDrawingRange = ChunkManager.DRAW_RADIUS


        val startX = playerPosition.x - chunkDrawingRange * chunkSize
        val startZ = playerPosition.z - chunkDrawingRange * chunkSize
        val endX = playerPosition.x + chunkDrawingRange * chunkSize
        val endZ = playerPosition.z + chunkDrawingRange * chunkSize

        val chunkPositionsToCreate = mutableListOf<IntVector3>()

        for (x in startX..endX step chunkSize) {
            for (z in startZ..endZ step chunkSize) {
                var chunkPos = chunkPositionFromBlockCoords(IntVector3(x, 0, z))
                chunkPositionsToCreate.add(chunkPos)

                /*
                var y = -chunkHeight
                while (y >= playerPosition.y - chunkHeight * 3) {
                    chunkPos = chunkPositionFromBlockCoords(IntVector3(x, y, z))
                    chunkPositionsToCreate.add(chunkPos)
                    y -= chunkHeight
                }
                 */
            }
        }

        return chunkPositionsToCreate
    }
    fun getDataPositionsAroundPlayer(
        playerPosition: IntVector3
    ): List<IntVector3> {
        val chunkSize = ChunkManager.CHUNK_SIZE
        val chunkHeight = ChunkManager.CHUNK_HEIGHT
        val chunkDrawingRange = ChunkManager.DRAW_RADIUS


        val startX = playerPosition.x - (chunkDrawingRange + 1) * chunkSize
        val startZ = playerPosition.z - (chunkDrawingRange + 1) * chunkSize
        val endX = playerPosition.x + (chunkDrawingRange + 1) * chunkSize
        val endZ = playerPosition.z + (chunkDrawingRange + 1) * chunkSize

        val chunkPositionsToCreate = mutableListOf<IntVector3>()

        for (x in startX..endX step chunkSize) {
            for (z in startZ..endZ step chunkSize) {
                var chunkPos = chunkPositionFromBlockCoords(IntVector3(x, 0, z))
                chunkPositionsToCreate.add(chunkPos)
                /*
                var y = -chunkHeight
                while (y >= playerPosition.y - chunkHeight * 3) {
                    chunkPos = chunkPositionFromBlockCoords(IntVector3(x, y, z))
                    chunkPositionsToCreate.add(chunkPos)
                    y -= chunkHeight
                }
                 */
            }
        }

        return chunkPositionsToCreate
    }

    fun getUnneededData(
        chunkDataMap: Map<IntVector3, ChunkData>,
        allChunkDataPositionsNeeded: List<IntVector3>
    ): List<IntVector3> {
        return chunkDataMap.keys
            .filter { pos ->
                pos !in allChunkDataPositionsNeeded &&
                        chunkDataMap[pos]?.modified == false
            }
            .toList()
    }

    fun getUnneededChunks(
        chunkMap: Map<IntVector3, MeshData>,
        allChunkPositionsNeeded: List<IntVector3>
    ): List<IntVector3> {
        return chunkMap.keys
            .filter { pos -> pos !in allChunkPositionsNeeded }
            .toList()
    }

    fun selectPositionsToCreate(
        chunkMap: Map<IntVector3, MeshData>,
        allChunkPositionsNeeded: List<IntVector3>,
        playerPosition: IntVector3
    ): List<IntVector3> {
        return allChunkPositionsNeeded
            .filter { pos -> pos !in chunkMap }
            .sortedBy { pos -> IntVector3.dst(playerPosition, pos) }
            .toList()
    }

    fun selectDataPositionsToCreate(
        chunkDataMap: Map<IntVector3, ChunkData>,
        allChunkDataPositionsNeeded: List<IntVector3>,
        playerPosition: IntVector3
    ): List<IntVector3> {
        return allChunkDataPositionsNeeded
            .filter { pos -> pos !in chunkDataMap }
            .sortedBy { pos -> IntVector3.dst(playerPosition, pos) }
            .toList()
    }
}