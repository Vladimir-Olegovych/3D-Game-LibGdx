package core.blocks

enum class BlockType(val id: Byte, val regionName: String) {
    NOTHING(0, ""),
    AIR(1, ""),
    GRASS(2, "green_grass_top"),
    DIRT(3, "dirt_soil_brown"),
    STONE(4, "stone_ground_grey"),
    WOOD(5, "dark_wood_planks"),
    LEAVES(6, "sand_smooth"),
    SAND(7, "white_sand");

    companion object {
        private val map = entries.associateBy(BlockType::id)
        fun fromByte(id: Byte): BlockType = map[id] ?: NOTHING
    }
}
