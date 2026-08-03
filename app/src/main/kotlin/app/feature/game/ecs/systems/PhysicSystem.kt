package app.feature.game.ecs.systems

import app.feature.game.ecs.components.PhysicsInterpolationComponent
import app.feature.game.ecs.components.TransformComponent
import app.feature.game.event.GameEvent
import com.artemis.BaseSystem
import com.artemis.ComponentMapper
import com.artemis.annotations.Wire
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.IntArray
import com.gigapi.eventbus.annotation.BusEvent
import core.bullet.world.PhysicsWorldUpdater
import kotlin.math.exp

class PhysicSystem : BaseSystem() {

    @Wire
    private lateinit var physicsWorldUpdater: PhysicsWorldUpdater

    private lateinit var transformMapper: ComponentMapper<TransformComponent>
    private lateinit var physicsInterpMapper: ComponentMapper<PhysicsInterpolationComponent>

    private val tmpPos = Vector3()
    private val tmpRot = Quaternion()
    private val tmpScale = Vector3(1f, 1f, 1f)
    private val activeEntities = IntArray()

    @BusEvent
    fun onRigidBodyTransformUpdate(event: GameEvent.OnRigidBodyTransformUpdate) {
        val transformComponent = transformMapper[event.entityId] ?: return
        if (transformComponent.transform == null) {
            transformComponent.transform = Matrix4()
        }

        event.transform.getTranslation(tmpPos)
        event.transform.getRotation(tmpRot, true)

        val interp = physicsInterpMapper[event.entityId]
            ?: physicsInterpMapper.create(event.entityId)

        if (!containsEntity(event.entityId)) {
            activeEntities.add(event.entityId)
        }

        interp.pendingPos.set(tmpPos)
        interp.pendingRot.set(tmpRot)
        interp.hasPending = true

        if (!interp.hasTarget) {
            interp.toPos.set(tmpPos)
            interp.toRot.set(tmpRot)
            interp.renderPos.set(tmpPos)
            interp.renderRot.set(tmpRot)
            interp.hasTarget = true
            interp.hasPending = false
            applyRenderTransform(transformComponent.transform!!, interp)
        }
    }

    override fun initialize() {
        physicsWorldUpdater.start()
    }

    override fun processSystem() {
        for (i in 0 until activeEntities.size) {
            interpolateEntity(activeEntities[i])
        }
    }

    override fun dispose() {
        physicsWorldUpdater.stop()
    }

    private fun interpolateEntity(entityId: Int) {
        val interp = physicsInterpMapper[entityId] ?: return
        val transformComponent = transformMapper[entityId] ?: return
        val transform = transformComponent.transform
            ?: Matrix4().also { transformComponent.transform = it }

        if (interp.hasPending) {
            interp.toPos.set(interp.pendingPos)
            interp.toRot.set(interp.pendingRot)
            interp.hasPending = false

            if (interp.renderPos.dst2(interp.toPos) >
                PhysicsInterpolationComponent.SNAP_DISTANCE * PhysicsInterpolationComponent.SNAP_DISTANCE
            ) {
                interp.renderPos.set(interp.toPos)
                interp.renderRot.set(interp.toRot)
            }
        }

        if (!interp.hasTarget) return

        val alpha = (1f - exp((-PhysicsInterpolationComponent.SMOOTHING * world.delta).toDouble()))
            .toFloat()
            .coerceIn(0f, 1f)

        interp.renderPos.lerp(interp.toPos, alpha)
        interp.renderRot.slerp(interp.toRot, alpha)
        applyRenderTransform(transform, interp)
    }

    private fun applyRenderTransform(transform: Matrix4, interp: PhysicsInterpolationComponent) {
        transform.set(interp.renderPos, interp.renderRot, tmpScale)
    }

    private fun containsEntity(entityId: Int): Boolean {
        for (i in 0 until activeEntities.size) {
            if (activeEntities[i] == entityId) return true
        }
        return false
    }
}
