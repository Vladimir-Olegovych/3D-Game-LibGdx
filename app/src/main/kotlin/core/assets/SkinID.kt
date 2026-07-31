package core.assets

enum class SkinID {

    ITEM,
    BLOCK,
    BUTTON;

    val skin = "images/${name.lowercase()}/${name.lowercase()}.skin"
    val atlas = "images/${name.lowercase()}/${name.lowercase()}.atlas"
}