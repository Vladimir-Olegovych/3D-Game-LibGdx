package core.mesh.model

import core.blocks.types.BlockShape


class ModelQuad(val corners: IntArray /* 4 угла * xyz */, val dir: Int)

object BlockModels {
    private fun q(dir: Int, vararg c: Int) = ModelQuad(c, dir)

    /** Квад + он же с обратной стороны (порядок углов обратный → другой winding, текстура зеркалится) */
    private fun twoSided(dir: Int, vararg c: Int) = listOf(
        q(dir, *c),
        q(dir, c[9], c[10], c[11], c[6], c[7], c[8], c[3], c[4], c[5], c[0], c[1], c[2])
    )

    // dir = 2 (+Y), чтобы шейдер освещал диагонали равномерно как верх
    val CROSS: List<ModelQuad> =
        twoSided(2, 0,1,0,  0,0,0,  1,0,1,  1,1,1) +
                twoSided(2, 0,1,1,  0,0,1,  1,0,0,  1,1,0)

    fun of(shape: BlockShape): List<ModelQuad>? = when (shape) {
        BlockShape.CROSS -> CROSS
        else -> null
    }
}