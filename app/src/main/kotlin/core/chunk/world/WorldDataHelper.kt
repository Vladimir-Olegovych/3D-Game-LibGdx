package core.chunk.world

import com.badlogic.gdx.math.Vector3
import com.gigapi.math.vector.IntVector3
import com.gigapi.mesh.MeshData
import core.chunk.ChunkData
import core.chunk.ChunkStatus
import core.chunk.ChunkWorldUpdater
import kotlin.math.floor

object WorldDataHelper {

    fun chunkPositionFromBlockCoords(worldBlockPosition: IntVector3): IntVector3 {
        val chunkSize = ChunkWorldUpdater.CHUNK_SIZE
        val chunkHeight = ChunkWorldUpdater.CHUNK_HEIGHT

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
        val chunkSize = ChunkWorldUpdater.CHUNK_SIZE
        val chunkHeight = ChunkWorldUpdater.CHUNK_HEIGHT

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
        val chunkDrawingRangeX = ChunkWorldUpdater.DRAW_RADIUS_X
        val chunkDrawingRangeY = ChunkWorldUpdater.DRAW_RADIUS_Y

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
        val chunkDrawingRangeX = ChunkWorldUpdater.DRAW_RADIUS_X
        val chunkDrawingRangeY = ChunkWorldUpdater.DRAW_RADIUS_Y

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

    fun hasValidMesh(chunkMap: Map<IntVector3, MeshData>, pos: IntVector3): Boolean =
        chunkMap[pos]?.mesh != null

    fun needsMesh(chunkMap: Map<IntVector3, MeshData>, pos: IntVector3): Boolean =
        !hasValidMesh(chunkMap, pos)

    fun needsData(chunkDataMap: Map<IntVector3, ChunkData>, pos: IntVector3): Boolean {
        val data = chunkDataMap[pos] ?: return true
        return data.status == ChunkStatus.GENERATION
    }

    fun getUnneededChunks(
        chunkMap: Map<IntVector3, MeshData>,
        allChunkPositionsNeeded: List<IntVector3>
    ): List<IntVector3> {
        return chunkMap.keys
            .filter { pos -> pos !in allChunkPositionsNeeded && hasValidMesh(chunkMap, pos) }
            .toList()
    }

    fun selectPositionsToCreate(
        chunkMap: Map<IntVector3, MeshData>,
        allChunkPositionsNeeded: List<IntVector3>,
        playerPosition: IntVector3
    ): List<IntVector3> {
        return allChunkPositionsNeeded
            .filter { pos -> needsMesh(chunkMap, pos) }
            .sortedBy { pos -> IntVector3.dst(playerPosition, pos) }
            .toList()
    }

    fun selectDataPositionsToCreate(
        chunkDataMap: Map<IntVector3, ChunkData>,
        allChunkDataPositionsNeeded: List<IntVector3>,
        playerPosition: IntVector3
    ): List<IntVector3> {
        return allChunkDataPositionsNeeded
            .filter { pos -> needsData(chunkDataMap, pos) }
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
        if (localBlockPosition.x == ChunkWorldUpdater.CHUNK_SIZE - 1) {
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
        if (localBlockPosition.y == ChunkWorldUpdater.CHUNK_HEIGHT - 1) {
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
        if (localBlockPosition.z == ChunkWorldUpdater.CHUNK_SIZE - 1) {
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
                localBlockPosition.x == ChunkWorldUpdater.CHUNK_SIZE - 1 ||
                localBlockPosition.y == 0 ||
                localBlockPosition.y == ChunkWorldUpdater.CHUNK_HEIGHT - 1 ||
                localBlockPosition.z == 0 ||
                localBlockPosition.z == ChunkWorldUpdater.CHUNK_SIZE - 1
    }
}