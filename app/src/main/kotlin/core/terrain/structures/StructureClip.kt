package core.terrain.structures

internal class StructureClip(
    private val minX: Int,
    private val maxX: Int,
    private val minY: Int,
    private val maxY: Int,
    private val minZ: Int,
    private val maxZ: Int
) {
    fun contains(x: Int, y: Int, z: Int): Boolean =
        x in minX..maxX &&
                y in minY..maxY &&
                z in minZ..maxZ

    companion object {
        val ALL = StructureClip(
            Int.MIN_VALUE, Int.MAX_VALUE,
            Int.MIN_VALUE, Int.MAX_VALUE,
            Int.MIN_VALUE, Int.MAX_VALUE
        )
    }
}