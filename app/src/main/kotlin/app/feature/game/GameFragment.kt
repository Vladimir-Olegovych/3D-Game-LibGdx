package app.feature.game

import app.feature.game.ecs.systems.*
import app.feature.game.event.ClientEvent
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
import com.gigapi.eventbus.annotation.EventType
import com.gigapi.fragment.Fragment
import com.gigapi.general.GContext
import com.gigapi.kryo.GameClient
import com.gigapi.sounds.MusicPlayer
import com.gigapi.viewport.UnfairViewport
import com.gigcreator.NetworkEvent
import com.gigcreator.registerAllEvents
import core.artemis.disposeALL
import core.defaults.CameraTypes
import core.defaults.DefaultWorldSetupManager
import core.navigation.Navigation
import app.feature.game.ecs.states.ClientNetworkState
import core.terrain.TerrainGenerator
import core.viewport.ViewportTypes
import app.feature.game.ecs.states.TimeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class GameFragment(
    private val navigation: Navigation.Game,
    private val onMenuScreen: () -> Unit,
    private val gContext: GContext
): Fragment() {

    private val gameGContext = GContext()
    private var artemisWorld: ArtemisWorld? = null

    private lateinit var eventBus: EventBus
    private lateinit var viewport: UnfairViewport
    private lateinit var camera: PerspectiveCamera
    private lateinit var inputMultiplexer: InputMultiplexer
    private lateinit var gameClient: GameClient

    @BusEvent
    fun onMenuScreen(event: UiEvent.OnMenuScreen) {
        onMenuScreen.invoke()
    }

    @BusEvent
    @EventType(NetworkEvent.HelloFromServer::class)
    fun networkEventReceivedHelloFromServer(received: ClientEvent.OnReceived) {
        val event = received.event as NetworkEvent.HelloFromServer
        gameGContext.getObject<ClientNetworkState>().localPlayerId = event.playerId
        gameGContext.getObject<TimeState>().apply {
            setTimeOfDay(event.timeOfDay)
            this@apply.cycleDuration = event.cycleDuration
        }
        startWorld(event)
    }

    override fun onCreate() {
        gameGContext.addContext(gContext)
        gameGContext.setObject(dialogManager)
        DefaultWorldSetupManager.launch(gameGContext)

        gameClient = gameGContext.getObject()
        inputMultiplexer = gameGContext.getObject<InputMultiplexer>()
        viewport = gameGContext.getObject(ViewportTypes.UNFAIR)
        camera = gameGContext.getObject(CameraTypes.GL_3D)
        eventBus = gameGContext.getObject(EventBusTypes.MAIN_EVENT_BUS)
        camera.position.set(Vector3(0f, TerrainGenerator.WORLD_SURFACE.toFloat(), 0f))

        val musicPlayer = gameGContext.getObject<MusicPlayer>()
        musicPlayer.setVolume(0.5F)
        //musicPlayer.play(MusicID.MUSIC_1, true)

        eventBus.registerHandler(this)
        Gdx.input.isCursorCatched = true
        Gdx.input.inputProcessor = inputMultiplexer

        gameClient.start(
            address = "127.0.0.1", port = 5551,
            custom = { it.registerAllEvents() },
            onConnectionFailed = {
                lifecycleScope.launch {
                    delay(2000L.milliseconds)
                    onMenuScreen.invoke()
                }
            }
        )
    }

    private fun startWorld(event: NetworkEvent.HelloFromServer) {
        if (artemisWorld != null) return

        val generator = gameGContext.getRawObject<TerrainGenerator>()
        generator.worldSeedFromServer = event.worldSeed

        val configuration = WorldConfiguration()
        for ((key, value) in gameGContext.objectMap) {
            val anObject = value.anObject
            val customKey = key.customKey
            if(customKey != null) {
                configuration.register(customKey, anObject)
            } else {
                configuration.register(anObject)
            }
        }

        gameGContext.launch()

        arrayOf(
            WorldSystem(),
            TimeSystem(),
            PhysicSystem(),
            PlayerSystem(),
            MoveSystem(),
            ChunkSystem(),
            NetworkSystem(),
            ModelAnimationSystem(),
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
        artemisWorld?.delta = deltaTime
        artemisWorld?.process()
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

        gameClient.dispose()
        inputMultiplexer.clear()
        artemisWorld?.disposeALL()
        artemisWorld = null
        eventBus.clear()
        gameGContext.getObject<EventBus>(EventBusTypes.PHYSICS_EVENT_BUS).process()
        gameGContext.removeContext(gContext)
        gameGContext.dispose()
    }
}