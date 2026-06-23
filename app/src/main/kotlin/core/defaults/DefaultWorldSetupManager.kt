package core.defaults

import app.feature.game.ecs.systems.*
import app.feature.game.event.EventBusTypes
import com.badlogic.gdx.InputMultiplexer
import com.gigapi.core.effects.LaunchedEffect
import com.gigapi.eventbus.EventBus
import com.gigapi.general.Context
import com.gigapi.screens.sounds.MusicPlayer
import core.bullet.world.PhysicsWorldUpdater
import core.chunk.ChunkWorldUpdater
import core.controls.PlayerInputProcessor
import core.controls.ProcessorIndex
import core.renderers.SunRenderer
import core.terrain.TerrainGenerator

object DefaultWorldSetupManager: LaunchedEffect {

    override fun launch(context: Context) {
        val inputMultiplexer = context.getObject<InputMultiplexer>()
        val playerInputProcessor = PlayerInputProcessor()
        inputMultiplexer.addProcessor(ProcessorIndex.PLAYER_INPUT, playerInputProcessor)
        context.setObject(playerInputProcessor)
        //---
        val musicPlayer = MusicPlayer(context.getObject())
        context.setObject(musicPlayer)
        //---
        context.setObject(SunRenderer())
        //---
        context.setObject(EventBusTypes.MAIN_EVENT_BUS, EventBus())
        context.setObject(EventBusTypes.CHUNK_EVENT_BUS, EventBus())
        context.setObject(EventBusTypes.PHYSICS_EVENT_BUS, EventBus())
        //---
        context.setObject(PhysicsWorldUpdater())
        //---
        context.setObject(ChunkWorldUpdater())
        //Systems
        context.setObject(WorldSystem())
        context.setObject(PlayerSystem())
        context.setObject(DrawSystem())
        context.setObject(ChunkSystem())
        context.setObject(PhysicSystem())
        context.setObject(MoveSystem())
        //---
        context.setObject(TerrainGenerator())
    }

}