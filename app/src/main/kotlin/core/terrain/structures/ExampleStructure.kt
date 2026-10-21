package core.terrain.structures

import core.blocks.BlockType
import core.terrain.biome.models.BiomeType
import core.terrain.level.WorldStructure
import core.terrain.structures.builder.StructureBuilder

/**
 * Коробка с настраиваемой высотой и шириной.
 * Строится вокруг центральной оси X/Z (симметрично), основание — на блоке поверхности.
 */
class ExampleStructure : WorldStructure() {

    override val id: String = "ex_structure"
    private val width: Int = 201
    private val height: Int = 200
    private val wallBlock: BlockType = BlockType.STONE
    private val fillBlock: BlockType? = null
    private val hollow: Boolean = false
    override val cellSize: Int = 8
    override val chance: Float = 0.004f
    override val biomes = setOf(BiomeType.FOREST, BiomeType.SPRUCE_FOREST, BiomeType.DESERT)

    init {
        require(width > 0) { "width must be > 0, got $width" }
        require(height > 0) { "height must be > 0, got $height" }
        require(width % 2 == 1) { "width must be odd for symmetric placement, got $width" }
    }

    override val radius: Int = width / 2 + 1
    override val above: Int = height + 1

    override fun build(b: StructureBuilder) {
        val half = width / 2

        buildClipped(
            b,
            -half, half,
            0, height - 1,
            -half, half
        )
    }

    override fun buildClipped(
        b: StructureBuilder,
        minX: Int, maxX: Int,
        minY: Int, maxY: Int,
        minZ: Int, maxZ: Int
    ) {
        val half = width / 2

        val x0 = maxOf(-half, minX)
        val x1 = minOf(half, maxX)
        val y0 = maxOf(0, minY)
        val y1 = minOf(height - 1, maxY)
        val z0 = maxOf(-half, minZ)
        val z1 = minOf(half, maxZ)

        if (x0 > x1 || y0 > y1 || z0 > z1) return

        for (x in x0..x1) {
            for (z in z0..z1) {
                val sideWall =
                    x == -half || x == half ||
                            z == -half || z == half

                for (y in y0..y1) {
                    val block = when {
                        !hollow -> wallBlock
                        y == 0 || y == height - 1 -> wallBlock
                        sideWall -> wallBlock
                        else -> fillBlock
                    }

                    if (block != null) {
                        b.set(x, y, z, block)
                    }
                }
            }
        }
    }
}