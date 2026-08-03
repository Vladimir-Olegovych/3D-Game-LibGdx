package core.assets

enum class ModelID {

    NULL,
    M_PLAYER_MODEL,
    CAR,
    STONE,
    SPHERE,
    FIREYARETZIRESP;

    val filePathObj = "models/${name.lowercase()}.obj"
    val filePathMlt = "models/${name.lowercase()}.mtl"
}