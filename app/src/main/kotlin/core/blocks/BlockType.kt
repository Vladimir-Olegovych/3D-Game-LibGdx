package core.blocks

enum class BlockType {
    NOTHING,
    AIR,
    GRASS,
    DIRT,
    STONE,
    OAK_WOOD,
    OAK_WOODEN_PLANKS,
    LEAVES,
    SAND,
    CACTUS;

    companion object {
        fun fromName(name: String): BlockType? {
            entries.forEach { bt -> if (bt.name.equals(name, ignoreCase = true)) return bt }
            return null
        }
        fun toByte(blockType: BlockType): Byte {
            val index = entries.indexOf(blockType)
            return if (index >= 0) (index + 1).toByte() else 0
        }

        fun fromByte(id: Byte): BlockType {
            val index = id.toInt() - 1
            return if (index >= 0 && index < entries.size) entries[index] else entries[0]
        }
    }
}
