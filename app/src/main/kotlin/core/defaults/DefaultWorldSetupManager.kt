package core.defaults

import app.feature.game.ecs.states.ClientNetworkState
import app.feature.game.ecs.states.OutboundNetworkState
import app.feature.game.ecs.states.RemotePlayerRegistry
import app.feature.game.ecs.states.TimeState
import app.feature.game.event.EventBusTypes
import app.feature.game.ui.AimUI
import app.feature.game.ui.DataUI
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.scenes.scene2d.Stage
import com.gigapi.effects.LaunchedEffect
import com.gigapi.eventbus.EventBus
import com.gigapi.general.GContext
import com.gigapi.kryo.GameClient
import com.gigapi.sounds.MusicPlayer
import core.bullet.world.PhysicsWorldUpdater
import core.chunk.ChunkWorldUpdater
import core.client.ClientAcceptor
import core.controls.PInputProcessor
import core.controls.UInputProcessor
import core.crafting.CraftManager
import core.items.ItemManager
import core.network.NetworkStateUpdater
import core.renderers.SkyPlanetRenderer
import core.renderers.StarRenderer
import core.terrain.TerrainGenerator

object DefaultWorldSetupManager: LaunchedEffect {

    override fun launch(gContext: GContext) {
        //---
        val mainEventBus = EventBus()
        gContext.setObject(EventBusTypes.MAIN_EVENT_BUS, mainEventBus)
        gContext.setObject(EventBusTypes.CHUNK_EVENT_BUS, EventBus())
        gContext.setObject(EventBusTypes.PHYSICS_EVENT_BUS, EventBus())
        //---
        val gameClient = gContext.getObject<GameClient>()
        gameClient.prepare()
        gameClient.addListener(ClientAcceptor(mainEventBus))
        //---
        val playerInputProcessor = PInputProcessor()
        gContext.setObject(playerInputProcessor)
        //---
        val uInputProcessor = UInputProcessor()
        gContext.setObject(uInputProcessor)
        //---
        val inputMultiplexer = gContext.getObject<InputMultiplexer>()
        inputMultiplexer.addProcessor(gContext.getObject<Stage>())
        inputMultiplexer.addProcessor(playerInputProcessor)
        inputMultiplexer.addProcessor(uInputProcessor)
        //---
        val musicPlayer = MusicPlayer(gContext.getObject())
        gContext.setObject(musicPlayer)
        //---
        gContext.setObject(SkyPlanetRenderer())
        gContext.setObject(StarRenderer())
        //---
        gContext.setObject(TimeState())
        //UI
        gContext.setObject(AimUI())
        gContext.setObject(DataUI())
        //---
        gContext.setObject(PhysicsWorldUpdater())
        //---
        gContext.setObject(ChunkWorldUpdater())
        //---
        gContext.setObject(ClientNetworkState())
        gContext.setObject(OutboundNetworkState())
        gContext.setObject(NetworkStateUpdater())
        gContext.setObject(RemotePlayerRegistry())
        //---
        gContext.setObject(TerrainGenerator())
        //---
        gContext.setObject(ItemManager())
        //---
        gContext.setObject(CraftManager())
    }

}