package com.gigcreator.core.app

import com.gigapi.coruntines.DeltaUpdater
import com.gigapi.eventbus.EventBus
import com.gigapi.general.GContext
import com.gigapi.kryo.GameServer
import com.gigcreator.core.defaults.ServerDefaults
import com.gigcreator.registerAllEvents
import kotlinx.coroutines.Dispatchers

class ServerApplication: DeltaUpdater(1 / 60F, Dispatchers.IO) {

    private val gContext = GContext()

    private lateinit var eventBus: EventBus

    override fun create() {
        ServerDefaults.launch(gContext)
        eventBus = gContext.getObject()
        val server = gContext.getObject<GameServer>()
        server.prepare()
        server.start(5551) { it.registerAllEvents() }
    }

    override fun update(deltaTime: Float) {
        eventBus.process()
    }

    override fun dispose() {
        gContext.dispose()
    }
}