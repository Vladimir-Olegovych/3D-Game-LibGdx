package core.defaults

import app.feature.game.dialogs.InventoryDialog
import app.feature.game.dialogs.PauseDialog
import app.feature.game.event.EventBusTypes
import app.feature.game.ui.AimUI
import app.feature.game.ui.FpsUI
import app.feature.game.ui.InventoryUI
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.scenes.scene2d.Stage
import com.gigapi.effects.LaunchedEffect
import com.gigapi.eventbus.EventBus
import com.gigapi.general.Context
import com.gigapi.sounds.MusicPlayer
import core.bullet.world.PhysicsWorldUpdater
import core.chunk.ChunkWorldUpdater
import core.controls.PlayerInputProcessor
import core.controls.UiInputProcessor
import core.items.InventoryManager
import core.renderers.SunRenderer
import core.terrain.TerrainGenerator

object DefaultWorldSetupManager: LaunchedEffect {

    override fun launch(context: Context) {
        val playerInputProcessor = PlayerInputProcessor()
        context.setObject(playerInputProcessor)
        //---
        val uiInputProcessor = UiInputProcessor()
        context.setObject(uiInputProcessor)
        //---
        val inputMultiplexer = context.getObject<InputMultiplexer>()
        inputMultiplexer.addProcessor(context.getObject<Stage>())
        inputMultiplexer.addProcessor(playerInputProcessor)
        inputMultiplexer.addProcessor(uiInputProcessor)
        //---
        val musicPlayer = MusicPlayer(context.getObject())
        context.setObject(musicPlayer)
        //---
        context.setObject(SunRenderer())
        //---
        context.setObject(EventBusTypes.MAIN_EVENT_BUS, EventBus())
        context.setObject(EventBusTypes.CHUNK_EVENT_BUS, EventBus())
        context.setObject(EventBusTypes.PHYSICS_EVENT_BUS, EventBus())
        //Dialogs
        context.setObject(PauseDialog())
        context.setObject(InventoryDialog())
        //UI
        context.setObject(AimUI())
        context.setObject(FpsUI())
        context.setObject(InventoryUI())
        //---
        context.setObject(PhysicsWorldUpdater())
        //---
        context.setObject(ChunkWorldUpdater())
        //---
        context.setObject(TerrainGenerator())
        //---
        context.setObject(InventoryManager())
    }

}