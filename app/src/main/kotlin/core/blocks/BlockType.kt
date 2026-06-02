package core.blocks

enum class BlockType(val id: Byte) {
    NOTHING(0),
    AIR(1),
    GRASS(2),
    DIRT(3),
    STONE(4);

    companion object {
        private val map = entries.associateBy(BlockType::id)
        fun fromByte(id: Byte): BlockType = map[id] ?: NOTHING
    }
}
