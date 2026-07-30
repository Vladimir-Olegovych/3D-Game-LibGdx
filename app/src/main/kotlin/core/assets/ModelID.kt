package core.assets

enum class ModelID {

    NULL,
    CAR,
    STONE,
    SPHERE,
    FIREYARETZIRESP;

    val filePathObj = "models/${name.lowercase()}.obj"
    val filePathMlt = "models/${name.lowercase()}.mtl"
}