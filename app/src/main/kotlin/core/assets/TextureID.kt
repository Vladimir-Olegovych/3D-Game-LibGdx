package core.assets

enum class TextureID(type: String) {

    PLAYER("jpg");

    val filePath = "textures/${name.lowercase()}.$type"
}