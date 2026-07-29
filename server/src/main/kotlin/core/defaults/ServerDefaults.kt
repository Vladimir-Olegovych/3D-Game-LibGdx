package com.gigcreator.core.defaults

import com.gigapi.effects.LaunchedEffect
import com.gigapi.eventbus.EventBus
import com.gigapi.general.GContext
import com.gigapi.kryo.GameServer
import com.gigcreator.core.app.ServerAcceptor

object ServerDefaults: LaunchedEffect {
    override fun launch(gContext: GContext) {
        val eventBus = EventBus()
        gContext.setObject(eventBus)
        //---
        val serverAcceptor = ServerAcceptor(eventBus)
        gContext.setObject(serverAcceptor)
        //---
        val server = GameServer(1024)
        server.addListener(serverAcceptor)
        gContext.setObject(server)
    }
}