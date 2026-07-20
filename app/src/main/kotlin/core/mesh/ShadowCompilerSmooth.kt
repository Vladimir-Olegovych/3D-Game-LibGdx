package core.mesh

object ShadowCompilerSmooth {

    fun computeFaceShadow(
        bx: Int,
        by: Int,
        bz: Int,
        nx: Float,
        ny: Float,
        nz: Float,
        sampleShadow: (Int, Int, Int) -> Float
    ): FloatArray {
        val quadVertices = when {
            nz == -1F -> arrayOf(
                intArrayOf(1, 1, 0),
                intArrayOf(1, 0, 0),
                intArrayOf(0, 0, 0),
                intArrayOf(0, 1, 0)
            )
            nz == 1F -> arrayOf(
                intArrayOf(0, 1, 1),
                intArrayOf(0, 0, 1),
                intArrayOf(1, 0, 1),
                intArrayOf(1, 1, 1)
            )
            nx == -1F -> arrayOf(
                intArrayOf(0, 1, 0),
                intArrayOf(0, 0, 0),
                intArrayOf(0, 0, 1),
                intArrayOf(0, 1, 1)
            )
            nx == 1F -> arrayOf(
                intArrayOf(1, 1, 1),
                intArrayOf(1, 0, 1),
                intArrayOf(1, 0, 0),
                intArrayOf(1, 1, 0)
            )
            ny == -1F -> arrayOf(
                intArrayOf(0, 0, 0),
                intArrayOf(1, 0, 0),
                intArrayOf(1, 0, 1),
                intArrayOf(0, 0, 1)
            )
            ny == 1F -> arrayOf(
                intArrayOf(0, 1, 1),
                intArrayOf(1, 1, 1),
                intArrayOf(1, 1, 0),
                intArrayOf(0, 1, 0)
            )
            else -> error("Invalid normal")
        }

        return FloatArray(4) { i ->
            val corner = quadVertices[i]
            sampleVertexShadow(
                bx = bx,
                by = by,
                bz = bz,
                corner = corner,
                nx = nx,
                ny = ny,
                nz = nz,
                sampleShadow = sampleShadow
            )
        }
    }

    private fun sampleVertexShadow(
        bx: Int,
        by: Int,
        bz: Int,
        corner: IntArray,
        nx: Float,
        ny: Float,
        nz: Float,
        sampleShadow: (Int, Int, Int) -> Float
    ): Float {
        val cx = bx + corner[0]
        val cy = by + corner[1]
        val cz = bz + corner[2]

        return when {
            nx > 0f -> average4(
                sampleShadow(cx, cy - 1, cz - 1),
                sampleShadow(cx, cy - 1, cz),
                sampleShadow(cx, cy, cz - 1),
                sampleShadow(cx, cy, cz)
            )

            nx < 0f -> average4(
                sampleShadow(cx - 1, cy - 1, cz - 1),
                sampleShadow(cx - 1, cy - 1, cz),
                sampleShadow(cx - 1, cy, cz - 1),
                sampleShadow(cx - 1, cy, cz)
            )

            ny > 0f -> average4(
                sampleShadow(cx - 1, cy, cz - 1),
                sampleShadow(cx - 1, cy, cz),
                sampleShadow(cx, cy, cz - 1),
                sampleShadow(cx, cy, cz)
            )

            ny < 0f -> average4(
                sampleShadow(cx - 1, cy - 1, cz - 1),
                sampleShadow(cx - 1, cy - 1, cz),
                sampleShadow(cx, cy - 1, cz - 1),
                sampleShadow(cx, cy - 1, cz)
            )

            nz > 0f -> average4(
                sampleShadow(cx - 1, cy - 1, cz),
                sampleShadow(cx - 1, cy, cz),
                sampleShadow(cx, cy - 1, cz),
                sampleShadow(cx, cy, cz)
            )

            else -> average4(
                sampleShadow(cx - 1, cy - 1, cz - 1),
                sampleShadow(cx - 1, cy, cz - 1),
                sampleShadow(cx, cy - 1, cz - 1),
                sampleShadow(cx, cy, cz - 1)
            )
        }
    }

    private fun average4(a: Float, b: Float, c: Float, d: Float): Float {
        return (a + b + c + d) * 0.25f
    }
}
