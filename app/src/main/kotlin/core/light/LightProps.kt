package core.light

import core.blocks.types.BlockType

/**
 * opacity: 0 = прозрачный, 1..14 = ослабляет свет дополнительно (листва, вода), 15 = непрозрачный.
 * emission: 0..15, яркость источника.
 * По умолчанию всё непрозрачное, кроме AIR — настрой в старте игры.
 */
object LightProps {
    private val opacity = ByteArray(256) { 15 }
    private val emission = ByteArray(256)

    init { set(BlockType.AIR, 0) }

    fun set(type: BlockType, opacity: Int, emission: Int = 0) {
        val i = BlockType.toByte(type).toInt() and 0xFF
        this.opacity[i] = opacity.toByte()
        this.emission[i] = emission.toByte()
    }

    fun opacity(b: Byte): Int = opacity[b.toInt() and 0xFF].toInt()
    fun emission(b: Byte): Int = emission[b.toInt() and 0xFF].toInt()

    init {

        set(BlockType.LEAVES, opacity = 1)
        set(BlockType.GRASS_PLANT_1, opacity = 1)
        set(BlockType.GRASS_PLANT_2, opacity = 1)
        set(BlockType.WHEAT_PLANT_1, opacity = 1)
        //LightProps.set(BlockType.TORCH, opacity = 0, emission = 14)
        set(BlockType.OAK_WOODEN_PLANKS, opacity = 0, emission = 14)
    }
}