package app.feature.game.ecs.systems

import com.artemis.BaseSystem
import com.artemis.annotations.Wire
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g3d.utils.FirstPersonCameraController
import core.defaults.CameraTypes

class CameraSystem: BaseSystem() {

    @Wire(name = CameraTypes.GL_3D)
    private lateinit var camera: PerspectiveCamera
    @Wire
    private lateinit var controller: FirstPersonCameraController

    override fun processSystem() {
        camera.update()
        controller.update(world.delta)
    }
}