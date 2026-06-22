package core.chunk.world

import com.badlogic.gdx.math.Vector3
import com.gigapi.math.vector.IntVector3
import core.chunk.ChunkData
import core.chunk.ChunkWorldUpdater
import kotlin.math.floor

object ChunkHelper {

    fun inRange(rangeWidth: Int, rangeHeight: Int, chunkData: ChunkData, localPos: IntVector3): Boolean {
        val centerX = chunkData.chunkWidth / 2f
        val centerZ = chunkData.chunkWidth / 2f
        val centerY = chunkData.chunkHeight / 2f

        val dx = localPos.x - centerX
        val dy = localPos.y - centerY
        val dz = localPos.z - centerZ

        val horizontalDistSq = dx*dx + dz*dz
        val verticalDistSq = dy*dy
        return horizontalDistSq / (rangeWidth * rangeWidth) + verticalDistSq / (rangeHeight * rangeHeight) <= 1.0
    }

    fun getLocalPosition(worldPos: IntVector3, chunkPos: IntVector3): IntVector3 {
        val chunkSize = ChunkWorldUpdater.CHUNK_SIZE
        val chunkHeight = ChunkWorldUpdater.CHUNK_HEIGHT

        return IntVector3(
            x = worldPos.x - chunkPos.x * chunkSize,
            y = worldPos.y - chunkPos.y * chunkHeight,
            z = worldPos.z - chunkPos.z * chunkSize
        )
    }

    fun getBlockPositionFromWorldPosition(position: Vector3): IntVector3 {
        val chunkSize = ChunkWorldUpdater.CHUNK_SIZE
        val chunkHeight = ChunkWorldUpdater.CHUNK_HEIGHT

        val wx = floor(position.x).toInt()
        val wy = floor(position.y).toInt()
        val wz = floor(position.z).toInt()

        val blockPos = IntVector3(wx, wy, wz)
        val chunkPos = WorldDataHelper.getChunkPositionFromBlockCoords(blockPos)

        val localX = wx - chunkPos.x * chunkSize
        val localY = wy - chunkPos.y * chunkHeight
        val localZ = wz - chunkPos.z * chunkSize

        return IntVector3(localX, localY, localZ)
    }
}