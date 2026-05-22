package core.noice

data class NoiseSettings(
    val noiseZoom: Float,
    val octaves: Int,
    val persistance: Float,
    val redistributionModifier: Float,
    val exponent: Float
)