package core.mesh.model

import core.blocks.types.BlockShape

/**
 * @param corners 4 угла * xyz (целые). Для fine-квадов — в 1/FINE_SCALE блока.
 * @param dir     направление для освещения в шейдере (0..5)
 * @param fine    true -> позиции в 1/8 блока, UV из [uv] в 1/16 тайла
 * @param uv      4 вершины * (u,v) в 1/16 тайла, только для fine
 * @param texDir  из какой грани брать регион текстуры (0 = side, 2 = up, 3 = down)
 */
class ModelQuad(
    val corners: IntArray,
    val dir: Int,
    val fine: Boolean = false,
    val uv: IntArray? = null,   // fine: пиксели тайла 0..UV_SCALE
    val texDir: Int = 0,
    val halfXZ: Boolean = false // fine: сдвиг x,z на пол-шага сетки (1/16 блока)
)

object BlockModels {
    const val HALF_FLAG = 16
    const val UV_SCALE = 256
    const val FINE_SCALE = 8      // позиции fine-квадов: 1/8 блока (макс. 29*8+8 = 240 <= 255)
    const val FINE_FLAG = 8       // бит в a_Pos.w

    private fun q(dir: Int, vararg c: Int) = ModelQuad(c, dir)

    private fun twoSided(dir: Int, vararg c: Int) = listOf(
        q(dir, *c),
        q(dir, c[9], c[10], c[11], c[6], c[7], c[8], c[3], c[4], c[5], c[0], c[1], c[2])
    )

    val CROSS: List<ModelQuad> =
        twoSided(2, 0,1,0,  0,0,0,  1,0,1,  1,1,1) +
                twoSided(2, 0,1,1,  0,0,1,  1,0,0,  1,1,0)

    // ---------------------------------------------------------------- TORCH

    /** Единичные углы граней, порядок как UNIT в GreedyChunkMesher (0:+X 1:-X 2:+Y 3:-Y 4:+Z 5:-Z). */
    private val UNIT = arrayOf(
        intArrayOf(1,1,1,  1,0,1,  1,0,0,  1,1,0),
        intArrayOf(0,1,0,  0,0,0,  0,0,1,  0,1,1),
        intArrayOf(0,1,1,  1,1,1,  1,1,0,  0,1,0),
        intArrayOf(0,0,0,  1,0,0,  1,0,1,  0,0,1),
        intArrayOf(0,1,1,  0,0,1,  1,0,1,  1,1,1),
        intArrayOf(1,1,0,  1,0,0,  0,0,0,  0,1,0),
    )

    private val TORCH_SIDE = intArrayOf(57, 48, 72, 128)
    private val TORCH_TOP  = intArrayOf(57, 48, 72, 64)

    private fun box(
        x0: Int, y0: Int, z0: Int, x1: Int, y1: Int, z1: Int,
        halfXZ: Boolean,
        region: (Int) -> IntArray?          // null = грань не создаётся
    ): List<ModelQuad> = (0 until 6).mapNotNull { d ->
        val r = region(d) ?: return@mapNotNull null
        val u = UNIT[d]
        val c = IntArray(12)
        for (i in 0 until 4) {
            c[i * 3]     = if (u[i * 3]     == 0) x0 else x1
            c[i * 3 + 1] = if (u[i * 3 + 1] == 0) y0 else y1
            c[i * 3 + 2] = if (u[i * 3 + 2] == 0) z0 else z1
        }
        val u0 = r[0] * 2 + 1; val v0 = r[1] * 2 + 1
        val u1 = r[2] * 2 - 1; val v1 = r[3] * 2 - 1
        val uv = intArrayOf(u0, v0,  u0, v1,  u1, v1,  u1, v0)
        ModelQuad(c, d, fine = true, uv = uv, texDir = d, halfXZ = halfXZ)
    }

    val TORCH: List<ModelQuad> = box(3, 0, 3, 4, 5, 4, halfXZ = true) { d ->
        when (d) { 2 -> TORCH_TOP; 3 -> null; else -> TORCH_SIDE }
    }

    fun of(shape: BlockShape): List<ModelQuad>? = when (shape) {
        BlockShape.CROSS -> CROSS
        BlockShape.TORCH -> TORCH
        else -> null
    }
}