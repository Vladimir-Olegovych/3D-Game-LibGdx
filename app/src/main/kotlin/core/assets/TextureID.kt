package core.assets

enum class TextureID(type: String) {

    PLAYER("jpg"),

    ao_hair("png"),
    skintest("jpg"),
    ao_clothes("png"),
    normal("png"),
    rough("png"),
    diffuso("png");

    val filePath = "textures/${name.lowercase()}.$type"
}