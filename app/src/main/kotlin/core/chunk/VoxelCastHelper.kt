package core.chunk

import app.feature.game.event.ChunkEvent
import app.feature.game.event.EventBusTypes
import com.badlogic.gdx.math.Vector3
import com.gigapi.eventbus.EventBus
import com.gigapi.eventbus.annotation.BusEvent
import com.gigapi.general.GContext
import com.gigapi.math.vector.IntVector3
import core.blocks.types.BlockType
import core.bullet.raycast.VoxelHit
import core.chunk.ChunkData.Companion.isSolid
import core.chunk.ChunkWorldUpdater.Companion.CHUNK_HEIGHT
import core.chunk.ChunkWorldUpdater.Companion.CHUNK_SIZE
import core.defaults.CastTypes
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt

class VoxelCastHelper(
    private val gContext: GContext,
    private val pendingChunkPositions: ConcurrentHashMap.KeySetView<IntVector3, Boolean>,
    private val createdChunkDataMap: ConcurrentHashMap<IntVector3, ChunkData>,
) {
    private val mainEventBus = gContext.getObject<EventBus>(EventBusTypes.MAIN_EVENT_BUS)

    private fun cellAt(x: Int, y: Int, z: Int): VoxelHit? {
        val chunkPosition = IntVector3(
            Math.floorDiv(x, CHUNK_SIZE),
            Math.floorDiv(y, CHUNK_HEIGHT),
            Math.floorDiv(z, CHUNK_SIZE)
        )
        if (pendingChunkPositions.contains(chunkPosition)) return null
        val chunk = createdChunkDataMap[chunkPosition] ?: return null
        val entityId = chunk.entityId ?: return null

        val lx = Math.floorMod(x, CHUNK_SIZE)
        val ly = Math.floorMod(y, CHUNK_HEIGHT)
        val lz = Math.floorMod(z, CHUNK_SIZE)

        return VoxelHit(
            chunkPosition = chunkPosition,
            chunkEntityId = entityId,
            blockPosition = IntVector3(lx, ly, lz),
            block = chunk.getBlockByLocal(lx, ly, lz)
        )
    }

    private fun raycastVoxels(
        origin: Vector3,
        direction: Vector3,
        maxDistance: Float,
        forPlace: Boolean
    ): VoxelHit? {
        val len = sqrt(
            direction.x * direction.x + direction.y * direction.y + direction.z * direction.z
        )
        if (len < 1e-6f) return null
        val dx = direction.x / len
        val dy = direction.y / len
        val dz = direction.z / len

        var x = floor(origin.x).toInt()
        var y = floor(origin.y).toInt()
        var z = floor(origin.z).toInt()

        val stepX = if (dx > 0f) 1 else -1
        val stepY = if (dy > 0f) 1 else -1
        val stepZ = if (dz > 0f) 1 else -1

        val inf = Float.MAX_VALUE
        val tDeltaX = if (dx != 0f) abs(1f / dx) else inf
        val tDeltaY = if (dy != 0f) abs(1f / dy) else inf
        val tDeltaZ = if (dz != 0f) abs(1f / dz) else inf

        var tMaxX = when {
            dx > 0f -> (x + 1 - origin.x) * tDeltaX
            dx < 0f -> (origin.x - x) * tDeltaX
            else -> inf
        }
        var tMaxY = when {
            dy > 0f -> (y + 1 - origin.y) * tDeltaY
            dy < 0f -> (origin.y - y) * tDeltaY
            else -> inf
        }
        var tMaxZ = when {
            dz > 0f -> (z + 1 - origin.z) * tDeltaZ
            dz < 0f -> (origin.z - z) * tDeltaZ
            else -> inf
        }

        var px = x
        var py = y
        var pz = z
        var hasPrev = false

        val maxSteps = (maxDistance * 3f).toInt() + 3
        for (i in 0 until maxSteps) {
            val cell = cellAt(x, y, z) ?: return null
            if (isSolid(cell.block)) {
                if (!forPlace) return cell
                return if (hasPrev) cellAt(px, py, pz) else null
            }
            px = x; py = y; pz = z
            hasPrev = true

            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                if (tMaxX > maxDistance) return null
                x += stepX; tMaxX += tDeltaX
            } else if (tMaxY < tMaxZ) {
                if (tMaxY > maxDistance) return null
                y += stepY; tMaxY += tDeltaY
            } else {
                if (tMaxZ > maxDistance) return null
                z += stepZ; tMaxZ += tDeltaZ
            }
        }
        return null
    }

    @BusEvent
    fun onVoxelCastRequest(event: ChunkEvent.OnRayCastRequest) {
        val id = event.requestId
        val isPlace = id == CastTypes.Chunk.CHUNK_PLACE_RAY_CAST
        val isDig = id == CastTypes.Chunk.CHUNK_DIG_RAY_CAST
        val isSelect = id == CastTypes.Chunk.BLOCK_SELECT_RAY_CAST
        if (!isPlace && !isDig && !isSelect) return

        val hit = raycastVoxels(event.from, event.direction, event.maxDistance, isPlace)

        if (hit == null) {
            if (!isPlace) {
                mainEventBus.sendEvent(ChunkEvent.OnRayCastResult(
                    requestId = id,
                    requestOwner = event.requestOwner,
                    chunkEntityId = -1,
                    blockToSet = BlockType.AIR,
                    blockToRemove = BlockType.AIR,
                    chunkPosition = IntVector3(),
                    blockPosition = IntVector3()
                ))
            }
            return
        }

        mainEventBus.sendEvent(ChunkEvent.OnRayCastResult(
            requestId = id,
            requestOwner = event.requestOwner,
            chunkEntityId = hit.chunkEntityId,
            blockToSet = if (isPlace) BlockType.NOTHING else BlockType.AIR,
            blockToRemove = hit.block,
            chunkPosition = hit.chunkPosition,
            blockPosition = hit.blockPosition
        ))
    }
}