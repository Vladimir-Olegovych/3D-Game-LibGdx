package core.noice.models

data class NoiseSettings(
    val noiseZoom: Float,
    val octaves: Int,
    val persistance: Float,
    val redistributionModifier: Float,
    val exponent: Float
)