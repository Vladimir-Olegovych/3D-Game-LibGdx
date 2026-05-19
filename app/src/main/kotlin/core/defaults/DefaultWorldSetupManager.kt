package core.defaults

import app.feature.game.ecs.systems.CameraSystem
import app.feature.game.ecs.systems.ChunkSystem
import app.feature.game.ecs.systems.DrawSystem
import app.feature.game.ecs.systems.PhysicSystem
import com.badlogic.gdx.physics.bullet.Bullet
import com.gigapi.core.effects.LaunchedEffect
import com.gigapi.eventbus.EventBus
import com.gigapi.general.Context
import core.bullet.PhysicsWorld
import core.chunk.ChunkManager

object DefaultWorldSetupManager: LaunchedEffect {

    override fun launch(context: Context) {
        Bullet.init()
        context.setObject(PhysicsWorld())
        context.setObject(EventBus())
        //---
        context.setObject(ChunkManager())
        //Systems
        context.setObject(CameraSystem())
        context.setObject(DrawSystem())
        context.setObject(ChunkSystem())
        context.setObject(PhysicSystem())
    }

}