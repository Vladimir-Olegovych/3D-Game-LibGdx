package app.feature.game.ecs.components

import com.artemis.Component
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody
import com.gigapi.core.effects.DisposableEffect

class PhysicalComponent: DisposableEffect, Component() {

    var body: btRigidBody? = null

    override fun dispose() {
        body?.dispose()
        body = null
    }
    
}