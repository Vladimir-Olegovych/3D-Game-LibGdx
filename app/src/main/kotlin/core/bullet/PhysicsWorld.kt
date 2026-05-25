package core.bullet

import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.physics.bullet.collision.btCollisionDispatcher
import com.badlogic.gdx.physics.bullet.collision.btDbvtBroadphase
import com.badlogic.gdx.physics.bullet.collision.btDefaultCollisionConfiguration
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld
import com.badlogic.gdx.physics.bullet.dynamics.btSequentialImpulseConstraintSolver
import com.gigapi.core.effects.DisposableEffect
import com.gigapi.core.effects.LaunchedEffect
import com.gigapi.general.Context

class PhysicsWorld: LaunchedEffect, DisposableEffect {
    private val collisionConfig = btDefaultCollisionConfiguration()
    private val dispatcher = btCollisionDispatcher(collisionConfig)
    private val broadphase = btDbvtBroadphase()
    private val solver = btSequentialImpulseConstraintSolver()
    val world = btDiscreteDynamicsWorld(dispatcher, broadphase, solver, collisionConfig)


    override fun launch(context: Context) {
        world.gravity = Vector3(0f, -10f, 0f)
        world.forceUpdateAllAabbs = false
    }

    fun update(deltaTime: Float) {
        world.stepSimulation(deltaTime, 1, deltaTime)
    }

    override fun dispose() {
        world.dispose()
        solver.dispose()
        broadphase.dispose()
        dispatcher.dispose()
        collisionConfig.dispose()
    }

}