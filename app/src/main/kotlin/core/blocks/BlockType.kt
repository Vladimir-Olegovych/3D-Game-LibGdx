package core.blocks

enum class BlockType(val id: Byte) {
    NOTHING(0),
    AIR(1),
    GRASS(2),
    DIRT(3),
    STONE(4),
    WOOD(5),
    LEAVES(6),
    SAND(7),
    CACTUS(8);

    companion object {
        private val map = entries.associateBy(BlockType::id)
        fun fromByte(id: Byte): BlockType = map[id] ?: NOTHING
    }
}
