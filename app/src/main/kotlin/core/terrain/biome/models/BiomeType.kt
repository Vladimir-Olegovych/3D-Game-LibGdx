package core.terrain.biome.models

enum class BiomeType(val configs: Array<BiomeConfig>) {
    DESERT(
        arrayOf(
            BiomeConfig(start = 0.4f, end = 1.0f),  //Temperature
            BiomeConfig(start = -1f, end = 0.05f)  //Wetness
        )
    ),
    FOREST(
        arrayOf(
            BiomeConfig(start = 0f, end = 0.2f),  //Temperature
            BiomeConfig(start = 0.2f, end = 0.4f) //Wetness
        )
    ),
    MOUNTAINS(
        arrayOf(
            BiomeConfig(start = 0.2f, end = 0.4f),//Temperature
            BiomeConfig(start = 0.2f, end = 0.6f) //Wetness
        )
    )
}