package app.feature.game.ecs.components

import com.artemis.Component
import com.gigapi.core.effects.DisposableEffect
import core.bullet.PhysicalData

class PhysicalComponent: DisposableEffect, Component() {

    var physicalData: PhysicalData? = null

    override fun dispose() {
        physicalData?.dispose()
        physicalData = null
    }
    
}