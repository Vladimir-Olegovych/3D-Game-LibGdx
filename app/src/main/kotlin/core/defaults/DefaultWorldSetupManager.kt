package core.defaults

import app.feature.game.dialogs.InventoryDialog
import app.feature.game.dialogs.PauseDialog
import app.feature.game.event.EventBusTypes
import app.feature.game.ui.AimUI
import app.feature.game.ui.DataUI
import app.feature.game.ui.InventoryUI
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.scenes.scene2d.Stage
import com.gigapi.effects.LaunchedEffect
import com.gigapi.eventbus.EventBus
import com.gigapi.general.GContext
import com.gigapi.sounds.MusicPlayer
import core.bullet.world.PhysicsWorldUpdater
import core.chunk.ChunkWorldUpdater
import core.controls.PlayerInputProcessor
import core.controls.UiInputProcessor
import core.items.InventoryManager
import core.renderers.StarRenderer
import core.renderers.SkyPlanetRenderer
import core.terrain.TerrainGenerator
import app.feature.game.ecs.states.RemotePlayerRegistry
import core.network.ClientNetworkState
import core.network.NetworkOutboundState
import core.network.NetworkStateUpdater
import app.feature.game.ecs.states.TimeState
import com.gigapi.kryo.GameClient
import core.client.ClientAcceptor

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
        val playerInputProcessor = PlayerInputProcessor()
        gContext.setObject(playerInputProcessor)
        //---
        val uiInputProcessor = UiInputProcessor()
        gContext.setObject(uiInputProcessor)
        //---
        val inputMultiplexer = gContext.getObject<InputMultiplexer>()
        inputMultiplexer.addProcessor(gContext.getObject<Stage>())
        inputMultiplexer.addProcessor(playerInputProcessor)
        inputMultiplexer.addProcessor(uiInputProcessor)
        //---
        val musicPlayer = MusicPlayer(gContext.getObject())
        gContext.setObject(musicPlayer)
        //---
        gContext.setObject(SkyPlanetRenderer())
        gContext.setObject(StarRenderer())
        //---
        gContext.setObject(TimeState())
        //Dialogs
        gContext.setObject(PauseDialog())
        gContext.setObject(InventoryDialog())
        //UI
        gContext.setObject(AimUI())
        gContext.setObject(DataUI())
        gContext.setObject(InventoryUI())
        //---
        gContext.setObject(PhysicsWorldUpdater())
        //---
        gContext.setObject(ChunkWorldUpdater())
        //---
        gContext.setObject(ClientNetworkState())
        gContext.setObject(NetworkOutboundState())
        gContext.setObject(NetworkStateUpdater())
        gContext.setObject(RemotePlayerRegistry())
        //---
        gContext.setObject(TerrainGenerator())
        //---
        gContext.setObject(InventoryManager())
    }

}