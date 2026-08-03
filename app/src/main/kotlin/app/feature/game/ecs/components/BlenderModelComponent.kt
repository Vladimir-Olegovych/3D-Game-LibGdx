package app.feature.game.ecs.components

import com.artemis.Component
import com.gigapi.effects.DisposableEffect
import com.gigapi.mesh.BlenderRenderData

class BlenderModelComponent: DisposableEffect, Component() {

    val ignoreMeshDrawing = ArrayList<Int>()
    var ignoreDrawingAll = false
    var blenderRenderData: BlenderRenderData? = null

    override fun dispose() {
        blenderRenderData?.subMeshes?.forEach {
            it.dispose()
        }
        blenderRenderData = null
    }
}