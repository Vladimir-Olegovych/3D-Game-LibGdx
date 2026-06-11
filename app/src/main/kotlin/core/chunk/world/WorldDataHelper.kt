package core.chunk.world

import com.badlogic.gdx.math.Vector3
import com.gigapi.math.vector.IntVector3
import com.gigapi.screens.mesh.MeshData
import core.chunk.ChunkData
import core.chunk.ChunkDataManager
import kotlin.math.floor

object WorldDataHelper {
    fun chunkPositionFromBlockCoords(worldBlockPosition: IntVector3): IntVector3 {
        val chunkSize = ChunkDataManager.CHUNK_SIZE
        val chunkHeight = ChunkDataManager.CHUNK_HEIGHT

        return IntVector3(
            x = floorDiv(worldBlockPosition.x, chunkSize),
            y = floorDiv(worldBlockPosition.y, chunkHeight),
            z = floorDiv(worldBlockPosition.z, chunkSize)
        )
    }

    fun getChunkPositionFromWorldPosition(position: Vector3): IntVector3 {
        val blockPos = IntVector3(
            floor(position.x).toInt(),
            floor(position.y).toInt(),
            floor(position.z).toInt()
        )
        return getChunkPositionFromBlockCoords(blockPos)
    }

    fun getChunkPositionFromBlockCoords(blockPos: IntVector3): IntVector3 {
        val chunkSize = ChunkDataManager.CHUNK_SIZE
        val chunkHeight = ChunkDataManager.CHUNK_HEIGHT

        return IntVector3(
            x = floorDiv(blockPos.x, chunkSize),
            y = floorDiv(blockPos.y, chunkHeight),
            z = floorDiv(blockPos.z, chunkSize)
        )
    }

    fun floorDiv(a: Int, b: Int): Int {
        return when {
            b < 0 -> floorDiv(a, -b)
            a >= 0 -> a / b
            else -> (a + 1) / b - 1
        }
    }

    fun getChunkPositionsAroundPlayer(
        playerPosition: IntVector3
    ): List<IntVector3> {
        val chunkDrawingRangeX = ChunkDataManager.DRAW_RADIUS_X
        val chunkDrawingRangeY = ChunkDataManager.DRAW_RADIUS_Y

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
        val chunkDrawingRangeX = ChunkDataManager.DRAW_RADIUS_X
        val chunkDrawingRangeY = ChunkDataManager.DRAW_RADIUS_Y

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

    fun getEdgeNeighbourChunks(
        chunkData: ChunkData,
        localBlockPosition: IntVector3,
        chunkDataMap: Map<IntVector3, ChunkData>
    ): List<ChunkData> {
        val neighboursToUpdate = mutableListOf<ChunkData>()

        if (localBlockPosition.x == 0) {
            val neighbourPos = IntVector3(
                chunkData.position.x - 1,
                chunkData.position.y,
                chunkData.position.z
            )
            chunkDataMap[neighbourPos]?.let { neighboursToUpdate.add(it) }
        }
        if (localBlockPosition.x == ChunkDataManager.CHUNK_SIZE - 1) {
            val neighbourPos = IntVector3(
                chunkData.position.x + 1,
                chunkData.position.y,
                chunkData.position.z
            )
            chunkDataMap[neighbourPos]?.let { neighboursToUpdate.add(it) }
        }
        if (localBlockPosition.y == 0) {
            val neighbourPos = IntVector3(
                chunkData.position.x,
                chunkData.position.y - 1,
                chunkData.position.z
            )
            chunkDataMap[neighbourPos]?.let { neighboursToUpdate.add(it) }
        }
        if (localBlockPosition.y == ChunkDataManager.CHUNK_HEIGHT - 1) {
            val neighbourPos = IntVector3(
                chunkData.position.x,
                chunkData.position.y + 1,
                chunkData.position.z
            )
            chunkDataMap[neighbourPos]?.let { neighboursToUpdate.add(it) }
        }
        if (localBlockPosition.z == 0) {
            val neighbourPos = IntVector3(
                chunkData.position.x,
                chunkData.position.y,
                chunkData.position.z - 1
            )
            chunkDataMap[neighbourPos]?.let { neighboursToUpdate.add(it) }
        }
        if (localBlockPosition.z == ChunkDataManager.CHUNK_SIZE - 1) {
            val neighbourPos = IntVector3(
                chunkData.position.x,
                chunkData.position.y,
                chunkData.position.z + 1
            )
            chunkDataMap[neighbourPos]?.let { neighboursToUpdate.add(it) }
        }

        return neighboursToUpdate
    }

    fun isOnEdge(localBlockPosition: IntVector3): Boolean {
        return localBlockPosition.x == 0 ||
                localBlockPosition.x == ChunkDataManager.CHUNK_SIZE - 1 ||
                localBlockPosition.y == 0 ||
                localBlockPosition.y == ChunkDataManager.CHUNK_HEIGHT - 1 ||
                localBlockPosition.z == 0 ||
                localBlockPosition.z == ChunkDataManager.CHUNK_SIZE - 1
    }
}