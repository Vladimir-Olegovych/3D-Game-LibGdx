package core.chunk

import com.gigapi.math.vector.IntVector3
import core.blocks.BlockType

class ChunkData(
    val position: IntVector3,
    val chunkWidth: Int,
    val chunkHeight: Int,
    val blocks: ByteArray,
    val shadows: ByteArray,
) {
    var status = ChunkStatus.GENERATION

    @Volatile
    var worldPending: WorldPendingBlocks? = null

    companion object {
        const val SHADOW_MAX: Byte = 30
        const val SHADOW_MIN: Byte = 0

        fun floatToShadowByte(value: Float): Byte {
            val clamped = value.coerceIn(0f, 1f)
            val intValue = (clamped * (SHADOW_MAX - SHADOW_MIN) + SHADOW_MIN).toInt()
            return intValue.coerceIn(SHADOW_MIN.toInt(), SHADOW_MAX.toInt()).toByte()
        }

        fun shadowByteToFloat(byteValue: Byte): Float {
            val normalized = (byteValue.toInt() - SHADOW_MIN).toFloat() / (SHADOW_MAX - SHADOW_MIN)
            return normalized.coerceIn(0f, 1f)
        }

        fun create(
            position: IntVector3,
            chunkWidth: Int,
            chunkHeight: Int
        ): ChunkData {
            val blocks = ByteArray(chunkWidth * chunkHeight * chunkWidth) { BlockType.AIR.id }
            val shadows = ByteArray(chunkWidth * chunkHeight * chunkWidth) { 15 }
            return ChunkData(position, chunkWidth, chunkHeight, blocks, shadows)
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
        blocks[index] = blockType.id
        if (ShadowUpdater.isOpaque(blockType)) {
            shadows[index] = 15
        }
    }

    fun setBlockByLocal(blockType: BlockType, localPosition: IntVector3): Boolean {
        return setBlockByLocal(blockType, localPosition.x, localPosition.y, localPosition.z)
    }

    /**
     * Writes [blockType] at local+offset. If outside this chunk, routes to [worldPending]
     * for the target chunk (apply-before-mesh / dirty remesh).
     */
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

        worldPending?.offer(
            worldPosition.x + offset.x,
            worldPosition.y + offset.y,
            worldPosition.z + offset.z,
            blockType
        )
    }

    fun setBlockByLocal(blockType: BlockType, x: Int, y: Int, z: Int): Boolean {
        if (x < 0 || y < 0 || z < 0 || x > chunkWidth - 1 || z > chunkWidth - 1 || y > chunkHeight - 1) return false
        val index: Int = getIndex(x, y, z)
        if (index < 0 || index >= blocks.size) return false
        blocks[index] = blockType.id
        if (ShadowUpdater.isOpaque(blockType)) {
            shadows[index] = 15
        }
        return true
    }

    fun getBlockByLocal(localPosition: IntVector3): BlockType {
        return getBlockByLocal(localPosition.x, localPosition.y, localPosition.z)
    }

    fun getBlockByLocal(x: Int, y: Int, z: Int): BlockType {
        val index = getIndex(x, y, z)
        return BlockType.fromByte(blocks[index])
    }

    fun setDefaultShadowValue(value: Float, localPosition: IntVector3): Boolean {
        return setDefaultShadowValue(value, localPosition.x, localPosition.y, localPosition.z)
    }

    fun setDefaultShadowValue(value: Float, x: Int, y: Int, z: Int): Boolean {
        if (x > chunkWidth - 1 || z > chunkWidth - 1 || y > chunkHeight - 1) return false
        val index = getIndex(x, y, z)
        if (index < 0 || index >= blocks.size) return false
        shadows[index] = floatToShadowByte(value)
        return true
    }

    fun setDefaultShadowValueRaw(value: Byte, localPosition: IntVector3): Boolean {
        return setDefaultShadowValueRaw(value, localPosition.x, localPosition.y, localPosition.z)
    }

    fun setDefaultShadowValueRaw(value: Byte, x: Int, y: Int, z: Int): Boolean {
        val index = getIndex(x, y, z)
        shadows[index] = value.coerceIn(SHADOW_MIN, SHADOW_MAX)
        return true
    }

    fun getDefaultShadowValue(localPosition: IntVector3): Float {
        return getDefaultShadowValue(localPosition.x, localPosition.y, localPosition.z)
    }

    fun getDefaultShadowValue(x: Int, y: Int, z: Int): Float {
        val index = getIndex(x, y, z)
        return shadowByteToFloat(shadows[index])
    }

    fun getDefaultShadowValueRaw(localPosition: IntVector3): Byte {
        return getDefaultShadowValueRaw(localPosition.x, localPosition.y, localPosition.z)
    }

    fun getDefaultShadowValueRaw(x: Int, y: Int, z: Int): Byte {
        val index = getIndex(x, y, z)
        return shadows[index]
    }

    fun getShadowMapValue(localPosition: IntVector3): Float {
        return getShadowMapValue(localPosition.x, localPosition.y, localPosition.z)
    }

    fun getShadowMapValue(x: Int, y: Int, z: Int): Float {
        val index = getIndex(x, y, z)
        return shadowByteToFloat(shadows[index])
    }

    fun getShadowMapValueRaw(localPosition: IntVector3): Byte {
        return getShadowMapValueRaw(localPosition.x, localPosition.y, localPosition.z)
    }

    fun getShadowMapValueRaw(x: Int, y: Int, z: Int): Byte {
        val index = getIndex(x, y, z)
        return shadows[index]
    }

    fun getIndex(x: Int, y: Int, z: Int): Int {
        return x * chunkHeight * chunkWidth + y * chunkWidth + z
    }
}
