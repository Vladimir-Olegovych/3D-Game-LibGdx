package core.settings

import com.fasterxml.jackson.annotation.JsonProperty

class SettingsSO(
    @get:JsonProperty("isLodEnabled") @param:JsonProperty("isLodEnabled")
    var isLodEnabled: Boolean = false,
    @get:JsonProperty("lodDrawRadius") @param:JsonProperty("lodDrawRadius")
    var lodDrawRadius: Float = 10_000f,
    @get:JsonProperty("drawRadiusXZ") @param:JsonProperty("drawRadiusXZ")
    var drawRadiusXZ: Int = 16,
    @get:JsonProperty("drawRadiusY") @param:JsonProperty("drawRadiusY")
    var drawRadiusY: Int = 8,
)