package core.assets

enum class MusicID {
    MUSIC_1,
    MUSIC_2;

    val filePath = "music/${name.lowercase()}.mp3"
}