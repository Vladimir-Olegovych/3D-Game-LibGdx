package app.feature.game.ecs.systems

import app.feature.game.ecs.components.LookDirectionComponent
import app.feature.game.ecs.components.TransformComponent
import app.feature.game.event.EventBusTypes
import app.feature.game.event.PhysicsEvent
import com.artemis.BaseSystem
import com.artemis.ComponentMapper
import com.artemis.annotations.Wire
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.gigapi.eventbus.EventBus
import com.gigapi.eventbus.annotation.BusEvent
import core.blocks.types.BlockType
import core.bullet.raycast.RayCastTypes
import core.defaults.CameraTypes
import core.defaults.PlayerConstants

class BlockSelectionSystem : BaseSystem() {

    @Wire(name = EventBusTypes.PHYSICS_EVENT_BUS)
    private lateinit var physicsEventBus: EventBus
    @Wire(name = CameraTypes.GL_3D)
    private lateinit var camera: Camera
    @Wire
    private lateinit var shapeRenderer: ShapeRenderer

    private lateinit var transformMapper: ComponentMapper<TransformComponent>
    private lateinit var lookDirectionMapper: ComponentMapper<LookDirectionComponent>

    private val boxCorner = Vector3()
    private val boxScale = Vector3()
    private var hasSelection = false
    private var period = 0f

    override fun processSystem() {
        val playerId = PlayerConstants.getLocalPlayerEntityId()
        period += world.delta
        if (period >= 0.05f) {
            sendRequest(playerId)
            period = 0f
        }
        if (hasSelection) render()
    }

    private fun sendRequest(playerId: Int) {
        val transform = transformMapper[playerId]?.transform ?: return
        val look = lookDirectionMapper[playerId] ?: return

        val from = Vector3().also { transform.getTranslation(it) }
            .add(PlayerConstants.cameraOffset)
        val direction = Vector3(look.direction).nor()

        physicsEventBus.sendEvent(
            PhysicsEvent.OnRayCastRequest(
                requestId = RayCastTypes.BLOCK_SELECT_RAY_CAST,
                requestOwner = playerId,
                from = from,
                direction = direction,
                maxDistance = PlayerConstants.REACH_DISTANCE
            )
        )
    }

    @BusEvent
    fun onBlockRayCastResult(event: PhysicsEvent.OnRayCastBlockResult) {
        if (event.requestId != RayCastTypes.BLOCK_SELECT_RAY_CAST) return
        if (event.requestOwner != PlayerConstants.getLocalPlayerEntityId()) return

        if (event.blockToRemove == BlockType.AIR) {
            hasSelection = false
            return
        }

        val chunkTransform = transformMapper[event.chunkEntityId]?.transform ?: return

        boxCorner.set(
            event.blockPosition.x.toFloat(),
            event.blockPosition.y.toFloat(),
            event.blockPosition.z.toFloat()
        ).mul(chunkTransform)
        chunkTransform.getScale(boxScale)

        hasSelection = true
    }

    private fun render() {
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glLineWidth(2f)

        shapeRenderer.projectionMatrix = camera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = Color.BLACK

        val sx = boxScale.x
        val sy = boxScale.y
        val sz = boxScale.z
        shapeRenderer.box(
            boxCorner.x - EPS,
            boxCorner.y - EPS,
            boxCorner.z + sz + EPS,
            sx + EPS * 2, sy + EPS * 2, sz + EPS * 2
        )
        shapeRenderer.end()
    }

    private companion object {
        const val RESULT_TIMEOUT = 0.15f
        const val EPS = 0.0005f
    }
}