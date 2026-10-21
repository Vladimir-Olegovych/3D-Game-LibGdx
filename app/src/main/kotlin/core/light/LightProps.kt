package core.light

import core.blocks.BlockType

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
}

// Пример настройки (один раз при старте):
// LightProps.set(BlockType.LEAVES, opacity = 1)
// LightProps.set(BlockType.TORCH, opacity = 0, emission = 14)   // если у тебя есть такой блок