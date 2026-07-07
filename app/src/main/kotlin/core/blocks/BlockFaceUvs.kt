package core.blocks

import com.badlogic.gdx.math.Vector2

data class BlockFaceUvs(
    val up: Array<Vector2>,
    val side: Array<Vector2>,
    val down: Array<Vector2>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BlockFaceUvs

        if (!up.contentEquals(other.up)) return false
        if (!side.contentEquals(other.side)) return false
        if (!down.contentEquals(other.down)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = up.contentHashCode()
        result = 31 * result + side.contentHashCode()
        result = 31 * result + down.contentHashCode()
        return result
    }
}