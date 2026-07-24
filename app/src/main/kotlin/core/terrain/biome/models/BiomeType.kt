package core.terrain.biome.models

enum class BiomeType(val id: Byte, val config: Array<BiomeConfig>) {
    DESERT(
        id = 0,
        config = arrayOf(
            BiomeConfig(start = 0.4f, end = 1.0f),  //Temperature
            BiomeConfig(start = -1f, end = 0.05f)  //Wetness
        )
    ),
    FOREST(
        id = 1,
        config = arrayOf(
            BiomeConfig(start = 0f, end = 0.2f),  //Temperature
            BiomeConfig(start = 0.2f, end = 0.4f) //Wetness
        )
    ),
    SPRUCE_FOREST(
        id = 2,
        config = arrayOf(
            BiomeConfig(start = -0.5f, end = 0.4f),  //Temperature
            BiomeConfig(start = 0.2f, end = 0.7f) //Wetness
        )
    ),
    MOUNTAINS(
        id = 3,
        config = arrayOf(
            BiomeConfig(start = 0.2f, end = 0.4f),//Temperature
            BiomeConfig(start = 0.2f, end = 0.6f) //Wetness
        )
    );

    companion object {
        private val map = BiomeType.entries.associateBy(BiomeType::id)
        fun fromByte(id: Byte): BiomeType = map[id] ?: FOREST
    }
}