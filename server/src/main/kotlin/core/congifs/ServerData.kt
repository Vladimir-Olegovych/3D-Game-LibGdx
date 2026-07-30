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
)