package com.gigcreator.core.defaults

import com.fasterxml.jackson.databind.ObjectMapper
import com.gigapi.effects.LaunchedEffect
import com.gigapi.eventbus.EventBus
import com.gigapi.general.GContext
import com.gigapi.kryo.GameServer
import com.gigcreator.ecs.states.ServerPlayerRegistry

object ServerDefaults: LaunchedEffect {
    override fun launch(gContext: GContext) {
        gContext.setObject(ObjectMapper())
        gContext.setObject(EventBus())
        gContext.setObject(ServerPlayerRegistry())
        gContext.setObject(GameServer(1024))
    }
}