package core.mesh

object ShadowCompilerAO {

    fun vertexAO(side1: Boolean, side2: Boolean, corner: Boolean): Float {
        val blocked = (if (side1) 1 else 0) +
                (if (side2) 1 else 0) +
                (if (corner) 1 else 0)
        return when (blocked) {
            0    -> 1.00f
            1    -> 0.75f
            2    -> 0.55f
            else -> 0.40f
        }
    }

    fun computeFaceAO(
        bx: Int, by: Int, bz: Int,
        nx: Float, ny: Float, nz: Float,
        blockExists: (Int, Int, Int) -> Boolean
    ): FloatArray {
        val sn = nx.toInt()
        val sy = ny.toInt()
        val sz = nz.toInt()
        val (r, u) = tangents(nx, ny, nz)

        fun b(dr: Int, du: Int): Boolean = blockExists(
            bx + sn + r.first  * dr + u.first  * du,
            by + sy + r.second * dr + u.second * du,
            bz + sz + r.third  * dr + u.third  * du
        )

        val rPos = b( 1,  0)
        val rNeg = b(-1,  0)
        val uPos = b( 0,  1)
        val uNeg = b( 0, -1)
        val rNeg_uNeg = b(-1, -1)
        val rPos_uNeg = b( 1, -1)
        val rNeg_uPos = b(-1,  1)
        val rPos_uPos = b( 1,  1)

        return when {
            nz == -1f -> floatArrayOf(
                vertexAO(rNeg, uPos, rNeg_uPos),  // v0=(1,1,0)
                vertexAO(rNeg, uNeg, rNeg_uNeg),  // v1=(1,0,0)
                vertexAO(rPos, uNeg, rPos_uNeg),  // v2=(0,0,0)
                vertexAO(rPos, uPos, rPos_uPos)   // v3=(0,1,0)
            )

            nz == 1f -> floatArrayOf(
                vertexAO(rNeg, uPos, rNeg_uPos),  // v0=(0,1,1)
                vertexAO(rNeg, uNeg, rNeg_uNeg),  // v1=(0,0,1)
                vertexAO(rPos, uNeg, rPos_uNeg),  // v2=(1,0,1)
                vertexAO(rPos, uPos, rPos_uPos)   // v3=(1,1,1)
            )

            nx == -1f -> floatArrayOf(
                vertexAO(rNeg, uPos, rNeg_uPos),  // v0=(0,1,0)
                vertexAO(rNeg, uNeg, rNeg_uNeg),  // v1=(0,0,0)
                vertexAO(rPos, uNeg, rPos_uNeg),  // v2=(0,0,1)
                vertexAO(rPos, uPos, rPos_uPos)   // v3=(0,1,1)
            )

            nx == 1f -> floatArrayOf(
                vertexAO(rNeg, uPos, rNeg_uPos),  // v0=(1,1,1)
                vertexAO(rNeg, uNeg, rNeg_uNeg),  // v1=(1,0,1)
                vertexAO(rPos, uNeg, rPos_uNeg),  // v2=(1,0,0)
                vertexAO(rPos, uPos, rPos_uPos)   // v3=(1,1,0)
            )

            ny == -1f -> floatArrayOf(
                vertexAO(rNeg, uNeg, rNeg_uNeg),  // v0=(0,0,0)
                vertexAO(rPos, uNeg, rPos_uNeg),  // v1=(1,0,0)
                vertexAO(rPos, uPos, rPos_uPos),  // v2=(1,0,1)
                vertexAO(rNeg, uPos, rNeg_uPos)   // v3=(0,0,1)
            )

            ny == 1f -> floatArrayOf(
                vertexAO(rNeg, uNeg, rNeg_uNeg),  // v0=(0,1,1)
                vertexAO(rPos, uNeg, rPos_uNeg),  // v1=(1,1,1)
                vertexAO(rPos, uPos, rPos_uPos),  // v2=(1,1,0)
                vertexAO(rNeg, uPos, rNeg_uPos)   // v3=(0,1,0)
            )

            else -> floatArrayOf(1f, 1f, 1f, 1f)
        }
    }

    private fun tangents(nx: Float, ny: Float, nz: Float)
            : Pair<Triple<Int,Int,Int>, Triple<Int,Int,Int>> = when {
        nz == -1f -> Pair(Triple(-1, 0, 0), Triple(0, 1, 0))
        nz ==  1f -> Pair(Triple( 1, 0, 0), Triple(0, 1, 0))
        nx == -1f -> Pair(Triple( 0, 0, 1), Triple(0, 1, 0))
        nx ==  1f -> Pair(Triple( 0, 0,-1), Triple(0, 1, 0))
        ny == -1f -> Pair(Triple( 1, 0, 0), Triple(0, 0, 1))
        ny ==  1f -> Pair(Triple( 1, 0, 0), Triple(0, 0,-1))
        else      -> Pair(Triple(1, 0, 0),  Triple(0, 1, 0))
    }
}