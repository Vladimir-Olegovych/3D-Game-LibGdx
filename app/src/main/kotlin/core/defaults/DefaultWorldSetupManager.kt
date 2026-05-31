package core.defaults

import app.feature.game.ecs.systems.CameraSystem
import app.feature.game.ecs.systems.ChunkSystem
import app.feature.game.ecs.systems.DrawSystem
import app.feature.game.ecs.systems.PhysicSystem
import app.feature.game.ecs.systems.WorldSystem
import app.feature.game.event.EventBusTypes
import com.gigapi.core.effects.LaunchedEffect
import com.gigapi.eventbus.EventBus
import com.gigapi.general.Context
import core.bullet.PhysicsWorldUpdater
import core.chunk.ChunkManager
import core.noice.models.NoiceTypes
import core.noice.PerlinNoise
import core.noice.RandomNoise
import core.renderers.SunRenderer
import core.terrain.TerrainGenerator
import core.terrain.biome.ForestBiomeGenerator

object DefaultWorldSetupManager: LaunchedEffect {

    override fun launch(context: Context) {
        //---
        context.setObject(SunRenderer())
        //---
        context.setObject(EventBusTypes.MAIN_EVENT_BUS, EventBus())
        context.setObject(EventBusTypes.PHYSICS_EVENT_BUS, EventBus())
        //---
        context.setObject(PhysicsWorldUpdater())
        //---
        context.setObject(ChunkManager())
        //Systems
        context.setObject(WorldSystem())
        context.setObject(CameraSystem())
        context.setObject(DrawSystem())
        context.setObject(ChunkSystem())
        context.setObject(PhysicSystem())
        //---
        context.setObject(NoiceTypes.PERLIN_WORLD, PerlinNoise(0))
        context.setObject(NoiceTypes.RANDOM_WORLD, RandomNoise(0))
        //---
        context.setObject(ForestBiomeGenerator())
        //---
        context.setObject(TerrainGenerator())
    }

}