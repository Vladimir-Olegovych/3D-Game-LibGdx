package app.feature.game.ecs.components

import com.artemis.Component
import com.gigapi.mesh.BlenderRenderData

class BlenderModelComponent: Component() {
    val ignoreMeshDrawing = ArrayList<Int>()
    var ignoreDrawingAll = false
    var blenderRenderData: BlenderRenderData? = null
}