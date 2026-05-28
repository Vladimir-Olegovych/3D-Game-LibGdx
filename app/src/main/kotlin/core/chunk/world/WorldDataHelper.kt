package core.chunk.world

import com.gigapi.math.vector.IntVector3
import com.gigapi.screens.mesh.MeshData
import core.chunk.ChunkData
import core.chunk.ChunkManager

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
        val chunkDrawingRangeX = ChunkManager.DRAW_RADIUS_X
        val chunkDrawingRangeY = ChunkManager.DRAW_RADIUS_Y

        val centerChunk = chunkPositionFromBlockCoords(playerPosition)
        val chunkPositionsToCreate = mutableListOf<IntVector3>()

        val centerX = centerChunk.x
        val centerY = centerChunk.y
        val centerZ = centerChunk.z

        for (dx in -chunkDrawingRangeX..chunkDrawingRangeX) {
            for (dy in -chunkDrawingRangeY..chunkDrawingRangeY) {
                for (dz in -chunkDrawingRangeX..chunkDrawingRangeX) {
                    val radiusSquaredXZ = chunkDrawingRangeX * chunkDrawingRangeX
                    val radiusSquaredY = chunkDrawingRangeY * chunkDrawingRangeY

                    if (dx * dx / radiusSquaredXZ.toDouble() +
                        dy * dy / radiusSquaredY.toDouble() +
                        dz * dz / radiusSquaredXZ.toDouble() <= 1.0) {

                        val chunkPos = IntVector3(
                            centerX + dx,
                            centerY + dy,
                            centerZ + dz
                        )
                        chunkPositionsToCreate.add(chunkPos)
                    }
                }
            }
        }

        return chunkPositionsToCreate
    }

    fun getDataPositionsAroundPlayer(
        playerPosition: IntVector3
    ): List<IntVector3> {
        val chunkDrawingRangeX = ChunkManager.DRAW_RADIUS_X
        val chunkDrawingRangeY = ChunkManager.DRAW_RADIUS_Y

        val centerChunk = chunkPositionFromBlockCoords(playerPosition)
        val chunkPositionsToCreate = mutableListOf<IntVector3>()

        val radiusXZ = chunkDrawingRangeX + 1
        val radiusY = chunkDrawingRangeY + 1

        val centerX = centerChunk.x
        val centerY = centerChunk.y
        val centerZ = centerChunk.z

        for (dx in -radiusXZ..radiusXZ) {
            for (dy in -radiusY..radiusY) {
                for (dz in -radiusXZ..radiusXZ) {
                    val radiusSquaredXZ = radiusXZ * radiusXZ
                    val radiusSquaredY = radiusY * radiusY

                    if (dx * dx / radiusSquaredXZ.toDouble() +
                        dy * dy / radiusSquaredY.toDouble() +
                        dz * dz / radiusSquaredXZ.toDouble() <= 1.0) {

                        val chunkPos = IntVector3(
                            centerX + dx,
                            centerY + dy,
                            centerZ + dz
                        )
                        chunkPositionsToCreate.add(chunkPos)
                    }
                }
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
                pos !in allChunkDataPositionsNeeded
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