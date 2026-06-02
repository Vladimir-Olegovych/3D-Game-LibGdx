package core.chunk

import com.gigapi.math.vector.IntVector3
import core.blocks.BlockType

class ChunkData(
    val position: IntVector3,
    val chunkWidth: Int,
    val chunkHeight: Int,
    val blocks: ByteArray,
    val shadows: ByteArray
) {
    companion object {
        const val SHADOW_MIN = 0x0F.toByte()
        const val SHADOW_MAX = 0x1F.toByte()

        private fun floatToShadowByte(value: Float): Byte {
            val clamped = value.coerceIn(0f, 1f)
            val intValue = (clamped * (SHADOW_MAX - SHADOW_MIN) + SHADOW_MIN).toInt()
            return intValue.coerceIn(SHADOW_MIN.toInt(), SHADOW_MAX.toInt()).toByte()
        }

        private fun shadowByteToFloat(byteValue: Byte): Float {
            val normalized = (byteValue.toInt() - SHADOW_MIN).toFloat() / (SHADOW_MAX - SHADOW_MIN)
            return normalized.coerceIn(0f, 1f)
        }

        fun create(
            position: IntVector3,
            chunkWidth: Int,
            chunkHeight: Int
        ): ChunkData {
            val blocks = ByteArray(chunkWidth * chunkHeight * chunkWidth) { BlockType.AIR.id }
            val shadows = ByteArray(chunkWidth * chunkHeight * chunkWidth) { SHADOW_MIN }
            return ChunkData(position, chunkWidth, chunkHeight, blocks, shadows)
        }
    }

    fun isAllBlock(blockType: BlockType): Boolean {
        blocks.forEach { if (blockType != BlockType.fromByte(it)) return false }
        return true
    }

    fun setBlockByLocal(blockType: BlockType, localPosition: IntVector3){
        setBlockByLocal(blockType, localPosition.x, localPosition.y, localPosition.z)
    }

    fun setBlockByLocal(blockType: BlockType, x: Int, y: Int, z: Int){
        val index: Int = getIndex(x, y, z)
        blocks[index] = blockType.id
    }

    fun getBlockByLocal(localPosition: IntVector3): BlockType {
        return getBlockByLocal(localPosition.x, localPosition.y, localPosition.z)
    }

    fun getBlockByLocal(x: Int, y: Int, z: Int): BlockType {
        val index = getIndex(x, y, z)
        return BlockType.fromByte(blocks[index])
    }

    fun setDefaultShadowValue(value: Float, localPosition: IntVector3) {
        setDefaultShadowValue(value, localPosition.x, localPosition.y, localPosition.z)
    }

    fun setDefaultShadowValue(value: Float, x: Int, y: Int, z: Int) {
        val index = getIndex(x, y, z)
        shadows[index] = floatToShadowByte(value)
    }

    fun setDefaultShadowValueRaw(value: Byte, localPosition: IntVector3) {
        setDefaultShadowValueRaw(value, localPosition.x, localPosition.y, localPosition.z)
    }

    fun setDefaultShadowValueRaw(value: Byte, x: Int, y: Int, z: Int) {
        val index = getIndex(x, y, z)
        shadows[index] = value.coerceIn(SHADOW_MIN, SHADOW_MAX)
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