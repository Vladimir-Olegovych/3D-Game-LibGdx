package com.gigcreator.core.congifs

import com.fasterxml.jackson.annotation.JsonProperty

class ServerData(
    @get:JsonProperty("worldName") @param:JsonProperty("worldName")
    val worldName: String = "",
    @get:JsonProperty("serverName") @param:JsonProperty("serverName")
    val serverName: String = "",
    @get:JsonProperty("serverPort") @param:JsonProperty("serverPort")
    val serverPort: Int = 0,
    @get:JsonProperty("worldSeed") @param:JsonProperty("worldSeed")
    val worldSeed: Int = 0,
    @get:JsonProperty("chunkSize") @param:JsonProperty("chunkSize")
    val chunkSize: Int = 16,
    @get:JsonProperty("chunkHeight") @param:JsonProperty("chunkHeight")
    val chunkHeight: Int = 16,
    @get:JsonProperty("radiusXZ") @param:JsonProperty("radiusXZ")
    var radiusXZ: Int = 16,
    @get:JsonProperty("radiusY") @param:JsonProperty("radiusY")
    var radiusY: Int = 10
)