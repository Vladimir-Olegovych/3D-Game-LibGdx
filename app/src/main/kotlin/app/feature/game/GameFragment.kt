package app.feature.game

import app.feature.game.ecs.systems.*
import app.feature.game.event.EventBusTypes
import app.feature.game.event.UiEvent
import com.artemis.WorldConfiguration
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Vector3
import com.gigapi.artemis.world.ArtemisWorld
import com.gigapi.eventbus.EventBus
import com.gigapi.eventbus.annotation.BusEvent
import com.gigapi.fragment.Fragment
import com.gigapi.general.GContext
import com.gigapi.sounds.MusicPlayer
import com.gigapi.viewport.UnfairViewport
import core.artemis.disposeALL
import core.defaults.CameraTypes
import core.defaults.DefaultWorldSetupManager
import core.navigation.Navigation
import core.terrain.TerrainGenerator
import core.viewport.ViewportTypes

class GameFragment(
    private val navigation: Navigation.Game,
    private val onMenuScreen: () -> Unit,
    private val context: GContext
): Fragment() {

    private val gameContext = GContext()
    private lateinit var eventBus: EventBus
    private lateinit var viewport: UnfairViewport
    private lateinit var camera: PerspectiveCamera
    private lateinit var artemisWorld: ArtemisWorld
    private lateinit var inputMultiplexer: InputMultiplexer

    @BusEvent
    fun onMenuScreen(event: UiEvent.OnMenuScreen) {
        onMenuScreen.invoke()
    }

    override fun onCreate() {
        gameContext.addContext(context)
        gameContext.setObject(dialogManager)
        DefaultWorldSetupManager.launch(gameContext)
        gameContext.launch()

        inputMultiplexer = gameContext.getObject<InputMultiplexer>()
        viewport = gameContext.getObject(ViewportTypes.UNFAIR)
        camera = gameContext.getObject(CameraTypes.GL_3D)
        eventBus = gameContext.getObject(EventBusTypes.MAIN_EVENT_BUS)
        camera.position.set(Vector3(0f, TerrainGenerator.WORLD_SURFACE.toFloat(), 0f))

        val musicPlayer = gameContext.getObject<MusicPlayer>()
        musicPlayer.setVolume(0.5F)
        //musicPlayer.play(MusicID.MUSIC_1, true)

        eventBus.registerHandler(this)
        Gdx.input.isCursorCatched = true
        Gdx.input.inputProcessor = inputMultiplexer

        val configuration = WorldConfiguration()
        for ((key, value) in gameContext.objectMap) {
            val anObject = value.anObject
            val customKey = key.customKey
            if(customKey != null) {
                configuration.register(customKey, anObject)
            } else {
                configuration.register(anObject)
            }
        }

        arrayOf(
            WorldSystem(),
            TimeSystem(),
            PlayerSystem(),
            MoveSystem(),
            ChunkSystem(),
            PhysicSystem(),
            DrawSystem(),
            UISystem()
        ).forEach { system ->
            eventBus.registerHandler(system)
            configuration.setSystem(system)
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
        viewport.update(width, height, true)
    }

    override fun onDestroy() {
        Gdx.input.inputProcessor = null
        Gdx.input.isCursorCatched = false

        inputMultiplexer.clear()
        artemisWorld.disposeALL()
        eventBus.clear()
        gameContext.getObject<EventBus>(EventBusTypes.PHYSICS_EVENT_BUS).process()
        gameContext.removeContext(context)
        gameContext.dispose()
    }
}