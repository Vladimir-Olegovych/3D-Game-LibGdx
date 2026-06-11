package core.chunk.world

import com.badlogic.gdx.math.Vector3
import com.gigapi.math.vector.IntVector3
import core.chunk.ChunkDataManager
import kotlin.math.floor

object ChunkHelper {

    fun getLocalPosition(worldPos: IntVector3, chunkPos: IntVector3): IntVector3 {
        val chunkSize = ChunkDataManager.CHUNK_SIZE
        val chunkHeight = ChunkDataManager.CHUNK_HEIGHT

        return IntVector3(
            x = worldPos.x - chunkPos.x * chunkSize,
            y = worldPos.y - chunkPos.y * chunkHeight,
            z = worldPos.z - chunkPos.z * chunkSize
        )
    }

    fun getBlockPositionFromWorldPosition(position: Vector3): IntVector3 {
        val chunkSize = ChunkDataManager.CHUNK_SIZE
        val chunkHeight = ChunkDataManager.CHUNK_HEIGHT

        // floor вместо toInt() — корректно для отрицательных координат
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