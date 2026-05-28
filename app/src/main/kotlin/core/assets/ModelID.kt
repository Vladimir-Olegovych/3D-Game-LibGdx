package core.assets

enum class ModelID {

    CUBE,
    COCK,
    SPHERE,
    CAPSULE;

    val filePath = "models/${name.lowercase()}.obj"
}