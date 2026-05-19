package core.assets

enum class SkinID {

    BLOCK;

    val skin = "images/${name.lowercase()}/${name.lowercase()}.skin"
    val atlas = "images/${name.lowercase()}/${name.lowercase()}.atlas"
}