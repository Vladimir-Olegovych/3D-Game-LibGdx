package core.chunk

import com.gigapi.math.vector.IntVector3
import core.blocks.BlockType

class ChunkData(
    val position: IntVector3,
    val chunkWidth: Int,
    val chunkHeight: Int,
    val blocksArray: Array<BlockType>
) {
    fun setBlockByLocal(blockType: BlockType, x: Int, y: Int, z: Int){
        val index: Int = x * chunkHeight * chunkWidth + y * chunkWidth + z
        blocksArray[index] = blockType
    }

    fun getBlockByLocal(x: Int, y: Int, z: Int): BlockType {
        val index: Int = x * chunkHeight * chunkWidth + y * chunkWidth + z
        return blocksArray[index]
    }

    companion object {
        fun create(
            position: IntVector3,
            chunkWidth: Int,
            chunkHeight: Int
        ): ChunkData {
            val blocksArray = Array(chunkWidth * chunkHeight * chunkWidth) {
                BlockType.AIR
            }
            return ChunkData(position, chunkWidth, chunkHeight, blocksArray)
        }
    }
}