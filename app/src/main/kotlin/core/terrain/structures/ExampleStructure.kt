package core.terrain.structures

import core.blocks.types.BlockType
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

    override val radius: Int = width / 2 + 1
    override val above: Int = height + 1

    override fun build(b: StructureBuilder) {
        val half = width / 2
        if (hollow) b.hollowBox(-half, 0, -half, half, height - 1, half, wallBlock)
        else        b.fillBox(-half, 0, -half, half, height - 1, half, wallBlock)
        fillBlock?.let { b.fillBox(-half + 1, 1, -half + 1, half - 1, height - 2, half - 1, it) }
    }
}