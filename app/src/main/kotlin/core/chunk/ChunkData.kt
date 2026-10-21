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
        private val AIR_BYTE: Byte = BlockType.toByte(BlockType.AIR)

        fun create(
            position: IntVector3,
            chunkWidth: Int,
            chunkHeight: Int
        ): ChunkData {
            val blocks = ByteArray(chunkWidth * chunkHeight * chunkWidth) { AIR_BYTE }
            return ChunkData(null, position, chunkWidth, chunkHeight, blocks)
        }
    }

    /** Количество блоков AIR. Поддерживается инкрементально в set*, после массовой записи в [blocks] вызови [recount]. */
    var airCount: Int = 0
        private set

    init {
        recount()
    }

    /** Чанк целиком из воздуха — меш не нужен. */
    val isEmpty: Boolean get() = airCount == blocks.size

    /** В чанке нет воздуха вообще (полностью забит). */
    val isFull: Boolean get() = airCount == 0

    /** Пересчитать счётчик воздуха. Дёшево (4096 байт), вызывать после генерации. */
    fun recount() {
        var c = 0
        for (b in blocks) if (b == AIR_BYTE) c++
        airCount = c
    }

    fun isAllBlock(blockType: BlockType): Boolean {
        val expected = BlockType.toByte(blockType)
        for (b in blocks) if (b != expected) return false
        return true
    }

    fun hasNonAirBlock(): Boolean = airCount < blocks.size

    private fun putByte(index: Int, newByte: Byte) {
        val old = blocks[index]
        if (old == newByte) return
        if (old == AIR_BYTE) airCount-- else if (newByte == AIR_BYTE) airCount++
        blocks[index] = newByte
    }

    fun setBlockByIndex(blockType: BlockType, index: Int) {
        putByte(index, BlockType.toByte(blockType))
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
        setBlockByLocal(blockType, lx, ly, lz)
    }

    fun setBlockByLocal(blockType: BlockType, x: Int, y: Int, z: Int): Boolean {
        if (x < 0 || y < 0 || z < 0 || x > chunkWidth - 1 || z > chunkWidth - 1 || y > chunkHeight - 1) return false
        putByte(getIndex(x, y, z), BlockType.toByte(blockType))
        return true
    }

    fun getBlockByLocal(localPosition: IntVector3): BlockType {
        return getBlockByLocal(localPosition.x, localPosition.y, localPosition.z)
    }

    fun getBlockByLocal(x: Int, y: Int, z: Int): BlockType {
        return BlockType.fromByte(blocks[getIndex(x, y, z)])
    }

    fun getIndex(x: Int, y: Int, z: Int): Int {
        return x * chunkHeight * chunkWidth + y * chunkWidth + z
    }
}
