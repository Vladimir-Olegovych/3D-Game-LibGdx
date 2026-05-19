package app.feature.game

import com.artemis.BaseSystem
import com.artemis.WorldConfiguration
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g3d.utils.FirstPersonCameraController
import com.gigapi.artemis.world.ArtemisWorld
import com.gigapi.eventbus.EventBus
import com.gigapi.general.Context
import com.gigapi.screens.fragment.Fragment
import core.artemis.disposeALL
import core.defaults.CameraTypes
import core.defaults.DefaultWorldSetupManager
import core.navigation.Navigation

class GameFragment(
    private val navigation: Navigation.Game,
    private val context: Context
): Fragment() {

    private val gameContext = Context()
    private lateinit var eventBus: EventBus
    private lateinit var camera: PerspectiveCamera
    private lateinit var artemisWorld: ArtemisWorld

    override fun onCreate() {
        gameContext.addContext(context)
        DefaultWorldSetupManager.launch(gameContext)
        gameContext.launch()

        val controller = gameContext.getObject<FirstPersonCameraController>()
        camera = gameContext.getObject(CameraTypes.GL_3D)
        eventBus = gameContext.getObject()

        Gdx.input.isCursorCatched = true
        Gdx.input.inputProcessor = controller

        val configuration = WorldConfiguration()
        for ((key, value) in gameContext.objectMap) {
            val anObject = value.anObject
            if (anObject is BaseSystem){
                eventBus.registerHandler(anObject)
                configuration.setSystem(anObject)
                continue
            }

            val customKey = key.customKey
            if(customKey != null) {
                configuration.register(customKey, anObject)
            } else {
                configuration.register(anObject)
            }
        }
        configuration.isAlwaysDelayComponentRemoval = false
        artemisWorld = ArtemisWorld(configuration)
    }

    override fun onRender(deltaTime: Float) {
        eventBus.process()
        artemisWorld.delta = deltaTime
        artemisWorld.process()
    }

    override fun onResize(width: Int, height: Int) {
        camera.viewportWidth = width.toFloat()
        camera.viewportHeight = height.toFloat()
        camera.update()
    }

    override fun onResume() {
        Gdx.input.isCursorCatched = true
    }

    override fun onPause() {
        Gdx.input.isCursorCatched = false
    }

    override fun onDestroy() {
        Gdx.input.inputProcessor = null
        Gdx.input.isCursorCatched = false
        artemisWorld.disposeALL()
        eventBus.clear()
        gameContext.removeContext(context)
        gameContext.dispose()
    }
}