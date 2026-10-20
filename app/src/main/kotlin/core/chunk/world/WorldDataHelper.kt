package core.chunk.world

import com.badlogic.gdx.math.Vector3
import com.gigapi.math.vector.IntVector3
import com.gigapi.mesh.MeshData
import core.blocks.BlockType
import core.chunk.ChunkData
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

        val radiusXZ = chunkDrawingRangeX
        val radiusY = chunkDrawingRangeY

        for (dx in -radiusXZ..radiusXZ) {
            for (dz in -radiusXZ..radiusXZ) {
                val horizontalDistSquared = dx * dx + dz * dz
                if (horizontalDistSquared <= radiusXZ * radiusXZ) {
                    for (dy in -radiusY..radiusY) {
                        if (dy * dy <= radiusY * radiusY) {
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
            for (dz in -radiusXZ..radiusXZ) {
                val horizontalDistSquared = dx * dx + dz * dz
                if (horizontalDistSquared <= radiusXZ * radiusXZ) {
                    for (dy in -radiusY..radiusY) {
                        if (dy * dy <= radiusY * radiusY) {
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
        }

        return chunkPositionsToCreate
    }
    fun sortChunksByDistance(
        chunks: MutableList<IntVector3>,
        playerPosition: IntVector3
    ) {
        val playerChunk = chunkPositionFromBlockCoords(playerPosition)

        chunks.sortBy { position ->
            val dx = position.x - playerChunk.x
            val dy = position.y - playerChunk.y
            val dz = position.z - playerChunk.z

            dx * dx + dy * dy + dz * dz
        }
    }

    fun isOutsideRadius(
        position: IntVector3,
        center: IntVector3,
        radiusXZ: Int,
        radiusY: Int
    ): Boolean {
        val dx = position.x - center.x
        val dy = position.y - center.y
        val dz = position.z - center.z

        val horizontalDistanceSquared = dx * dx + dz * dz

        return horizontalDistanceSquared > radiusXZ * radiusXZ ||
                dy * dy > radiusY * radiusY
    }

    fun getUnneededData(
        chunkDataMap: Map<IntVector3, ChunkData>,
        allChunkDataPositionsNeeded: List<IntVector3>
    ): List<IntVector3> {
        val neededPositions = allChunkDataPositionsNeeded.toHashSet()
        return chunkDataMap.keys
            .filter { pos ->
                pos !in neededPositions
            }
            .toList()
    }

    fun getUnneededChunks(
        chunkMap: Map<IntVector3, MeshData>,
        allChunkPositionsNeeded: List<IntVector3>
    ): List<IntVector3> {
        val neededPositions = allChunkPositionsNeeded.toHashSet()
        return chunkMap.keys
            .filter { pos -> pos !in neededPositions }
            .toList()
    }

    fun selectPositionsToCreate(
        chunkMap: Map<IntVector3, MeshData>,
        allChunkPositionsNeeded: List<IntVector3>,
        playerPosition: IntVector3
    ): List<IntVector3> {
        val playerChunkPosition = chunkPositionFromBlockCoords(playerPosition)
        return allChunkPositionsNeeded
            .filter { pos -> pos !in chunkMap }
            .sortedBy { pos ->
                val dx = pos.x - playerChunkPosition.x
                val dy = pos.y - playerChunkPosition.y
                val dz = pos.z - playerChunkPosition.z
                dx * dx + dy * dy + dz * dz
            }
            .toList()
    }

    fun selectDataPositionsToCreate(
        chunkDataMap: Map<IntVector3, ChunkData>,
        allChunkDataPositionsNeeded: List<IntVector3>,
        playerPosition: IntVector3
    ): List<IntVector3> {
        val playerChunkPosition = chunkPositionFromBlockCoords(playerPosition)
        return allChunkDataPositionsNeeded
            .filter { pos -> pos !in chunkDataMap }
            .sortedBy { pos ->
                val dx = pos.x - playerChunkPosition.x
                val dy = pos.y - playerChunkPosition.y
                val dz = pos.z - playerChunkPosition.z
                dx * dx + dy * dy + dz * dz
            }
            .toList()
    }

    fun getNeighboursPositions(chunkPos: IntVector3): Set<IntVector3> {
        val neighbors = mutableSetOf<IntVector3>()

        val centerX = chunkPos.x
        val centerY = chunkPos.y
        val centerZ = chunkPos.z

        for (dx in -1..1) {
            for (dz in -1..1) {
                if (dx == 0 && dz == 0) continue
                val neighborPos = IntVector3(
                    centerX + dx,
                    centerY,
                    centerZ + dz
                )
                neighbors.add(neighborPos)
            }
        }

        return neighbors
    }
    fun getExistingNeighboursNeedingBorderRemesh(
        chunkData: ChunkData,
        chunkMap: Map<IntVector3, ChunkData>,
        existingMeshedPositions: Set<IntVector3>,
    ): Set<IntVector3> {
        val w = chunkData.chunkWidth
        val pos = chunkData.position
        val neighbours = mutableSetOf<IntVector3>()

        val westPos = IntVector3(pos.x - 1, pos.y, pos.z)
        if (westPos in existingMeshedPositions) {
            val neighbor = chunkMap[westPos]
            if (neighbor != null && hasExposedFaceOnXBorder(neighbor, chunkData, neighborX = w - 1, chunkX = 0)) {
                neighbours.add(westPos)
            }
        }

        val eastPos = IntVector3(pos.x + 1, pos.y, pos.z)
        if (eastPos in existingMeshedPositions) {
            val neighbor = chunkMap[eastPos]
            if (neighbor != null && hasExposedFaceOnXBorder(neighbor, chunkData, neighborX = 0, chunkX = w - 1)) {
                neighbours.add(eastPos)
            }
        }

        val northPos = IntVector3(pos.x, pos.y, pos.z - 1)
        if (northPos in existingMeshedPositions) {
            val neighbor = chunkMap[northPos]
            if (neighbor != null && hasExposedFaceOnZBorder(neighbor, chunkData, neighborZ = w - 1, chunkZ = 0)) {
                neighbours.add(northPos)
            }
        }

        val southPos = IntVector3(pos.x, pos.y, pos.z + 1)
        if (southPos in existingMeshedPositions) {
            val neighbor = chunkMap[southPos]
            if (neighbor != null && hasExposedFaceOnZBorder(neighbor, chunkData, neighborZ = 0, chunkZ = w - 1)) {
                neighbours.add(southPos)
            }
        }

        return neighbours
    }

    private fun hasExposedFaceOnXBorder(
        neighbor: ChunkData,
        chunk: ChunkData,
        neighborX: Int,
        chunkX: Int,
    ): Boolean {
        val w = chunk.chunkWidth
        val h = chunk.chunkHeight
        for (y in 0 until h) {
            for (z in 0 until w) {
                if (exposesFaceToward(
                        neighbor.getBlockByLocal(neighborX, y, z),
                        chunk.getBlockByLocal(chunkX, y, z)
                    )) {
                    return true
                }
            }
        }
        return false
    }

    private fun hasExposedFaceOnYBorder(
        neighbor: ChunkData,
        chunk: ChunkData,
        neighborY: Int,
        chunkY: Int,
    ): Boolean {
        val w = chunk.chunkWidth
        val h = chunk.chunkHeight
        for (x in 0 until w) {
            for (z in 0 until w) {
                if (exposesFaceToward(
                        neighbor.getBlockByLocal(x, neighborY, z),
                        chunk.getBlockByLocal(x, chunkY, z)
                    )) {
                    return true
                }
            }
        }
        return false
    }

    private fun hasExposedFaceOnZBorder(
        neighbor: ChunkData,
        chunk: ChunkData,
        neighborZ: Int,
        chunkZ: Int,
    ): Boolean {
        val w = chunk.chunkWidth
        val h = chunk.chunkHeight
        for (x in 0 until w) {
            for (y in 0 until h) {
                if (exposesFaceToward(
                        neighbor.getBlockByLocal(x, y, neighborZ),
                        chunk.getBlockByLocal(x, y, chunkZ)
                    )) {
                    return true
                }
            }
        }
        return false
    }

    /** Neighbor renders a face into [acrossBlock] (e.g. air) — its mesh baked wrong border shadows. */
    private fun exposesFaceToward(neighborBlock: BlockType, acrossBlock: BlockType): Boolean {
        return isMeshRelevantBlock(neighborBlock) && !isMeshRelevantBlock(acrossBlock)
    }

    private fun isMeshRelevantBlock(blockType: BlockType): Boolean {
        return blockType != BlockType.AIR && blockType != BlockType.NOTHING
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
    fun hasRequiredNeighbors(
        chunkDataMap: Map<IntVector3, ChunkData>,
        position: IntVector3
    ): Boolean {
        val north = IntVector3(position.x, position.y, position.z - 1)
        val south = IntVector3(position.x, position.y, position.z + 1)
        val west  = IntVector3(position.x - 1, position.y, position.z)
        val east  = IntVector3(position.x + 1, position.y, position.z)
        val down  = IntVector3(position.x, position.y - 1, position.z)
        val up    = IntVector3(position.x, position.y + 1, position.z)

        return chunkDataMap.containsKey(north) &&
                chunkDataMap.containsKey(south) &&
                chunkDataMap.containsKey(west)  &&
                chunkDataMap.containsKey(east)  &&
                chunkDataMap.containsKey(down)  &&
                chunkDataMap.containsKey(up)
    }

    fun findSpawnPosition(chunkDataMap: Map<IntVector3, ChunkData>,
                          centerPosition: Vector3): Vector3? {
        val startChunkPos = getChunkPositionFromWorldPosition(centerPosition)
        for (y in -4 + startChunkPos.y .. 4 + startChunkPos.y) {
            val chunkPosition = IntVector3(
                startChunkPos.x,
                y,
                startChunkPos.z
            )
            val chunkData = chunkDataMap[chunkPosition] ?: continue

            for (localX in 0 until chunkData.chunkWidth) {
                for (localZ in 0 until chunkData.chunkWidth) {
                    for (localY in 0 until chunkData.chunkHeight) {
                        val block = chunkData.getBlockByLocal(localX, localY, localZ)
                        if (block != BlockType.AIR) {
                            val aboveBlock = if (localY < chunkData.chunkHeight - 1) {
                                chunkData.getBlockByLocal(localX, localY + 1, localZ)
                            } else {
                                val aboveChunkPos = IntVector3(chunkPosition.x, chunkPosition.y + 1, chunkPosition.z)
                                val aboveChunk = chunkDataMap[aboveChunkPos]
                                aboveChunk?.getBlockByLocal(localX, 0, localZ)
                            }

                            val aboveAboveBlock = if (localY < chunkData.chunkHeight - 2) {
                                chunkData.getBlockByLocal(localX, localY + 2, localZ)
                            } else if (localY == chunkData.chunkHeight - 2) {
                                val aboveChunkPos = IntVector3(chunkPosition.x, chunkPosition.y + 1, chunkPosition.z)
                                val aboveChunk = chunkDataMap[aboveChunkPos]
                                aboveChunk?.getBlockByLocal(localX, 0, localZ)
                            } else {
                                val aboveChunkPos = IntVector3(chunkPosition.x, chunkPosition.y + 1, chunkPosition.z)
                                val aboveChunk = chunkDataMap[aboveChunkPos]
                                aboveChunk?.getBlockByLocal(localX, 1, localZ)
                            }

                            if (aboveBlock == BlockType.AIR && aboveAboveBlock == BlockType.AIR) {
                                val worldX = chunkPosition.x * chunkData.chunkWidth + localX
                                val worldY = chunkPosition.y * chunkData.chunkHeight + localY + 2
                                val worldZ = chunkPosition.z * chunkData.chunkWidth + localZ

                                return Vector3(worldX.toFloat(), worldY.toFloat(), worldZ.toFloat())
                            }
                        }
                    }
                }
            }
        }

        return null
    }
}