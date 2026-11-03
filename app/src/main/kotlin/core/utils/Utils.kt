package core.utils

fun String.safeName(): String {
    return this.lowercase()
        .trim()
        .replace(Regex("\\s+"), "_")
        .replace(Regex("[\\\\/:*?\"<>|]"), "")
        .replace(Regex("_+"), "_")
        .trim('_', '.')
}