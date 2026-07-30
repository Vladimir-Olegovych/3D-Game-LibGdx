package com.gigcreator.core.app

import com.artemis.WorldConfiguration
import com.gigapi.artemis.world.ArtemisWorld
import com.gigapi.coruntines.DeltaUpdater
import com.gigapi.eventbus.EventBus
import com.gigapi.general.GContext
import com.gigapi.kryo.GameServer
import com.gigcreator.core.congifs.ServerData
import com.gigcreator.core.defaults.ServerDefaults
import com.gigcreator.ecs.systems.ConnectionSystem
import com.gigcreator.ecs.systems.NetworkRelaySystem
import com.gigcreator.ecs.systems.SendSystem
import com.gigcreator.registerAllEvents
import kotlinx.coroutines.Dispatchers

class ServerApplication(
    private val serverData: ServerData
): DeltaUpdater(1 / 60F, Dispatchers.Default) {

    private val gContext = GContext()

    private lateinit var artemisWorld: ArtemisWorld
    private lateinit var eventBus: EventBus

    override fun create() {
        ServerDefaults.launch(gContext)
        gContext.setObject(serverData)
        gContext.launch()
        eventBus = gContext.getObject()

        val configuration = WorldConfiguration()
        for ((key, value) in gContext.objectMap) {
            val anObject = value.anObject
            val customKey = key.customKey
            if(customKey != null) {
                configuration.register(customKey, anObject)
            } else {
                configuration.register(anObject)
            }
        }

        arrayOf(
            ConnectionSystem(),
            NetworkRelaySystem(),
            SendSystem(),
        ).forEach { system ->
            eventBus.registerHandler(system)
            configuration.setSystem(system)
        }

        configuration.isAlwaysDelayComponentRemoval = false
        artemisWorld = ArtemisWorld(configuration)

        val server = gContext.getObject<GameServer>()
        server.prepare()
        server.addListener(ServerAcceptor(eventBus))
        server.start(serverData.serverPort) { it.registerAllEvents() }
    }

    override fun update(deltaTime: Float) {
        eventBus.process()
        artemisWorld.delta = deltaTime
        artemisWorld.process()
    }

    override fun dispose() {
        gContext.dispose()
    }
}