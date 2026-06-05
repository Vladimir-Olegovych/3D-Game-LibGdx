package app.feature.game.ecs.systems

import app.feature.game.ecs.components.TransformComponent
import com.artemis.BaseSystem
import com.artemis.ComponentMapper
import com.artemis.annotations.Wire
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g3d.utils.FirstPersonCameraController
import com.badlogic.gdx.math.Vector3
import core.defaults.CameraTypes
import core.defaults.WorldConstants

class CameraSystem: BaseSystem() {

    @Wire(name = CameraTypes.GL_3D)
    private lateinit var camera: PerspectiveCamera
    @Wire
    private lateinit var controller: FirstPersonCameraController

    private lateinit var transformMapper: ComponentMapper<TransformComponent>
    private val cameraOffset = Vector3(0f, 0.9f, 0.4f)

    override fun processSystem() {
        val playerEntityId = WorldConstants.getPlayerEntityId()
        //val playerTransform = transformMapper[playerEntityId]?.transform ?: return
        //val playerPosition = Vector3()
        //playerTransform.getTranslation(playerPosition)
        //playerPosition.add(cameraOffset)
        //camera.position.set(playerPosition)
        camera.update()
        controller.update(world.delta)
    }
}