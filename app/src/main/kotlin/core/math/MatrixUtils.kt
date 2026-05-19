package core.math

import com.badlogic.gdx.math.Matrix4
import core.chunk.ChunkData

fun createMatrixForChunk(chunkData: ChunkData): Matrix4 {
    val blockSize = 1.0f
    val worldX = chunkData.position.x * chunkData.chunkWidth * blockSize
    val worldY = chunkData.position.y * chunkData.chunkHeight * blockSize
    val worldZ = chunkData.position.z * chunkData.chunkWidth * blockSize

    return Matrix4().translate(worldX, worldY, worldZ)
}