package core.terrain.biome.models

enum class BiomeType(
    val id: Byte,
    val config: Array<BiomeConfig?>
) {
    DESERT(
        id = 0,
        config = arrayOf(
            BiomeConfig(start = 0.4f, end = 1.0f),   // Temperature
            BiomeConfig(start = -1f, end = 0.05f),   // Wetness
            null                                     // Fissure
        )
    ),
    FOREST(
        id = 1,
        config = arrayOf(
            BiomeConfig(start = 0f, end = 0.2f),
            BiomeConfig(start = 0.2f, end = 0.4f),
            null
        )
    ),
    SPRUCE_FOREST(
        id = 2,
        config = arrayOf(
            BiomeConfig(start = -0.5f, end = 0.4f),
            BiomeConfig(start = 0.2f, end = 0.7f),
            null
        )
    ),
    CANYON(
        id = 4,
        config = arrayOf(
            BiomeConfig(start = -1.0f, end = 1.0f),
            BiomeConfig(start = -1.0f, end = 1.0f),
            BiomeConfig(start = 0.2f, end = 1.0f)
        )
    ),
    MOUNTAINS(
        id = 3,
        config = arrayOf(
            BiomeConfig(start = 0.2f, end = 0.4f),
            BiomeConfig(start = 0.2f, end = 0.6f),
            null
        )
    );

    val centers: FloatArray = FloatArray(config.size) { i ->
        val c = config[i] ?: return@FloatArray 0f
        (c.start + c.end) / 2f
    }

    companion object {
        private val map = entries.associateBy(BiomeType::id)

        fun fromByte(id: Byte): BiomeType =
            map[id] ?: FOREST
    }
}