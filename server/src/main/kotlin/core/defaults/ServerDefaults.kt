package com.gigcreator.core.defaults

import com.fasterxml.jackson.databind.ObjectMapper
import com.gigapi.effects.LaunchedEffect
import com.gigapi.eventbus.EventBus
import com.gigapi.general.GContext
import com.gigapi.kryo.GameServer

object ServerDefaults: LaunchedEffect {
    override fun launch(gContext: GContext) {
        gContext.setObject(ObjectMapper())
        //---
        val eventBus = EventBus()
        gContext.setObject(eventBus)
        //---
        val server = GameServer(1024)
        gContext.setObject(server)
    }
}