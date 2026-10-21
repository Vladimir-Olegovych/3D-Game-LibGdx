package core.mesh

object LightMesher {

    /** @return 8 float: [sky0, block0, sky1, block1, sky2, block2, sky3, block3], каждое 0..1 */
    fun computeFaceLight(
        bx: Int, by: Int, bz: Int,
        nx: Float, ny: Float, nz: Float,
        lightAt: (Int, Int, Int) -> Int   // packed (sky shl 4 | block) или -1 если непрозрачно/нет данных
    ): FloatArray {
        val sn = nx.toInt(); val sy = ny.toInt(); val sz = nz.toInt()
        val (r, u) = tangents(nx, ny, nz)

        // g[(dr+1)*3 + (du+1)]
        val g = IntArray(9)
        for (dr in -1..1) for (du in -1..1) {
            g[(dr + 1) * 3 + (du + 1)] = lightAt(
                bx + sn + r.first * dr + u.first * du,
                by + sy + r.second * dr + u.second * du,
                bz + sz + r.third * dr + u.third * du
            )
        }
        val out = FloatArray(8)
        val yFace = ny != 0f
        // знаки (sr, su) вершин v0..v3 — как в computeFaceAO
        val sr = if (yFace) intArrayOf(-1, 1, 1, -1) else intArrayOf(-1, -1, 1, 1)
        val su = if (yFace) intArrayOf(-1, -1, 1, 1) else intArrayOf(1, -1, -1, 1)
        for (i in 0 until 4) {
            vertex(
                g[4],                              // центр (0,0)
                g[(sr[i] + 1) * 3 + 1],            // боковая по r
                g[1 * 3 + (su[i] + 1)],            // боковая по u
                g[(sr[i] + 1) * 3 + (su[i] + 1)],  // угол
                out, i * 2
            )
        }
        return out
    }

    private fun vertex(c: Int, s1: Int, s2: Int, k: Int, out: FloatArray, o: Int) {
        var sky = 0; var blk = 0; var n = 0
        if (c >= 0) { sky += c ushr 4; blk += c and 15; n++ }
        if (s1 >= 0) { sky += s1 ushr 4; blk += s1 and 15; n++ }
        if (s2 >= 0) { sky += s2 ushr 4; blk += s2 and 15; n++ }
        if ((s1 >= 0 || s2 >= 0) && k >= 0) { sky += k ushr 4; blk += k and 15; n++ }  // угол при двух стенах = утечка
        if (n == 0) { out[o] = 0f; out[o + 1] = 0f }
        else { out[o] = sky / (n * 15f); out[o + 1] = blk / (n * 15f) }
    }

    private fun tangents(nx: Float, ny: Float, nz: Float)
            : Pair<Triple<Int, Int, Int>, Triple<Int, Int, Int>> = when {
        nz == -1f -> Pair(Triple(-1, 0, 0), Triple(0, 1, 0))
        nz == 1f -> Pair(Triple(1, 0, 0), Triple(0, 1, 0))
        nx == -1f -> Pair(Triple(0, 0, 1), Triple(0, 1, 0))
        nx == 1f -> Pair(Triple(0, 0, -1), Triple(0, 1, 0))
        ny == -1f -> Pair(Triple(1, 0, 0), Triple(0, 0, 1))
        ny == 1f -> Pair(Triple(1, 0, 0), Triple(0, 0, -1))
        else -> Pair(Triple(1, 0, 0), Triple(0, 1, 0))
    }
}