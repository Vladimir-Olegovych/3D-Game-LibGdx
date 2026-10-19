package core.chunk

import com.gigapi.math.vector.IntVector3
import core.blocks.BlockType

class ChunkData(
    var entityId: Int? = null,
    var position: IntVector3,
    val chunkWidth: Int,
    val chunkHeight: Int,
    val blocks: ByteArray
) {

    companion object {
        fun create(
            position: IntVector3,
            chunkWidth: Int,
            chunkHeight: Int
        ): ChunkData {
            val blocks = ByteArray(chunkWidth * chunkHeight * chunkWidth) { BlockType.toByte(BlockType.AIR) }
            return ChunkData(null, position, chunkWidth, chunkHeight, blocks)
        }
    }

    fun isAllBlock(blockType: BlockType): Boolean {
        blocks.forEach { if (blockType != BlockType.fromByte(it)) return false }
        return true
    }

    fun hasNonAirBlock(): Boolean {
        for (id in blocks) {
            if (BlockType.fromByte(id) != BlockType.AIR) return true
        }
        return false
    }

    fun setBlockByIndex(blockType: BlockType, index: Int){
        blocks[index] = BlockType.toByte(blockType)
    }

    fun setBlockByLocal(blockType: BlockType, localPosition: IntVector3): Boolean {
        return setBlockByLocal(blockType, localPosition.x, localPosition.y, localPosition.z)
    }

    fun setBlockPending(
        blockType: BlockType,
        offset: IntVector3,
        localPosition: IntVector3,
        worldPosition: IntVector3
    ) {
        val lx = localPosition.x + offset.x
        val ly = localPosition.y + offset.y
        val lz = localPosition.z + offset.z
        if (setBlockByLocal(blockType, lx, ly, lz)) return
    }

    fun setBlockByLocal(blockType: BlockType, x: Int, y: Int, z: Int): Boolean {
        if (x < 0 || y < 0 || z < 0 || x > chunkWidth - 1 || z > chunkWidth - 1 || y > chunkHeight - 1) return false
        val index: Int = getIndex(x, y, z)
        if (index < 0 || index >= blocks.size) return false
        blocks[index] = BlockType.toByte(blockType)
        return true
    }

    fun getBlockByLocal(localPosition: IntVector3): BlockType {
        return getBlockByLocal(localPosition.x, localPosition.y, localPosition.z)
    }

    fun getBlockByLocal(x: Int, y: Int, z: Int): BlockType {
        val index = getIndex(x, y, z)
        return BlockType.fromByte(blocks[index])
    }

    fun getIndex(x: Int, y: Int, z: Int): Int {
        return x * chunkHeight * chunkWidth + y * chunkWidth + z
    }
}
