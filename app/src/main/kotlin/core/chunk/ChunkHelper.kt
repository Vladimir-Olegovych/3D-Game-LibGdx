package core.chunk

import com.gigapi.math.vector.IntVector3

object ChunkHelper {
    fun chunkPositionFromBlockCoords(worldBlockPosition: IntVector3): IntVector3 {
        val chunkSize = ChunkManager.CHUNK_SIZE
        val chunkHeight = ChunkManager.CHUNK_HEIGHT

        return IntVector3(
            x = (worldBlockPosition.x.toFloat() / chunkSize).toInt() * chunkSize,
            y = (worldBlockPosition.y.toFloat() / chunkHeight).toInt() * chunkHeight,
            z = (worldBlockPosition.z.toFloat() / chunkSize).toInt() * chunkSize
        )
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

                var y = -chunkHeight
                while (y >= playerPosition.y - chunkHeight * 3) {
                    chunkPos = chunkPositionFromBlockCoords(IntVector3(x, y, z))
                    chunkPositionsToCreate.add(chunkPos)
                    y -= chunkHeight
                }
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

                var y = -chunkHeight
                while (y >= playerPosition.y - chunkHeight * 3) {
                    chunkPos = chunkPositionFromBlockCoords(IntVector3(x, y, z))
                    chunkPositionsToCreate.add(chunkPos)
                    y -= chunkHeight
                }
            }
        }

        return chunkPositionsToCreate
    }
}