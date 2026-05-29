package core.assets

enum class ModelID {

    CAR,
    SPHERE,
    FIREYARETZIRESP;

    val filePathObj = "models/${name.lowercase()}.obj"
    val filePathMlt = "models/${name.lowercase()}.mtl"
}