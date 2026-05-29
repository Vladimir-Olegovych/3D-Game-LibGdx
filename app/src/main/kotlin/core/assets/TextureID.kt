package core.assets

enum class TextureID(type: String) {

    PLAYER("jpg"),
    ao_hair("png"),
    skintest("jpg"),
    ao_clothes("png");

    val filePath = "textures/${name.lowercase()}.$type"
}