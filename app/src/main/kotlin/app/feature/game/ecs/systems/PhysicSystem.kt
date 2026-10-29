package app.feature.game.ecs.systems

import app.feature.game.ecs.components.PhysicsInterpolationComponent
import app.feature.game.ecs.components.PhysicsInterpolationComponent.Companion.DEFAULT_INTERVAL
import app.feature.game.ecs.components.PhysicsInterpolationComponent.Companion.INTERVAL_SMOOTHING
import app.feature.game.ecs.components.PhysicsInterpolationComponent.Companion.MAX_INTERVAL
import app.feature.game.ecs.components.PhysicsInterpolationComponent.Companion.MIN_INTERVAL
import app.feature.game.ecs.components.PhysicsInterpolationComponent.Companion.RENDER_DELAY_STEPS
import app.feature.game.ecs.components.PhysicsInterpolationComponent.Companion.SNAP_DISTANCE
import app.feature.game.ecs.components.PhysicsInterpolationComponent.Companion.TIME_SMOOTHING
import app.feature.game.ecs.components.TransformComponent
import app.feature.game.event.PhysicsEvent
import com.artemis.BaseSystem
import com.artemis.ComponentMapper
import com.artemis.annotations.Wire
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.IntArray
import com.gigapi.eventbus.annotation.BusEvent
import core.bullet.world.PhysicsWorldUpdater
import kotlin.math.abs
import kotlin.math.max

class PhysicSystem : BaseSystem() {

    @Wire
    private lateinit var physicsWorldUpdater: PhysicsWorldUpdater

    private lateinit var transformMapper: ComponentMapper<TransformComponent>
    private lateinit var physicsInterpMapper: ComponentMapper<PhysicsInterpolationComponent>

    private val lock = Any()
    private val tmpPos = Vector3()
    private val tmpRot = Quaternion()
    private val tmpScale = Vector3(1f, 1f, 1f)
    private val activeEntities = IntArray()

    private var renderClock = 0L
    private val renderDelayNanos = (DEFAULT_INTERVAL * RENDER_DELAY_STEPS * 1e9f).toLong()
    private val maxGapNanos = (MAX_INTERVAL * 1e9f).toLong()
    private val minStepNanos = (MIN_INTERVAL * 1e9f).toLong()

    @BusEvent
    fun onRigidBodyTransformUpdate(event: PhysicsEvent.OnRigidBodyTransformUpdate) {
        synchronized(lock) { handleSnapshot(event) }
    }

    private fun handleSnapshot(event: PhysicsEvent.OnRigidBodyTransformUpdate) {
        val id = event.entityId
        if (transformMapper[id] == null) return

        event.transform.getTranslation(tmpPos)
        event.transform.getRotation(tmpRot, true)

        val interp = physicsInterpMapper[id] ?: physicsInterpMapper.create(id)
        val raw = event.timeNanos

        // Первый снапшот
        if (interp.count == 0) {
            interp.push(tmpPos, tmpRot, raw)
            interp.renderPos.set(tmpPos)
            interp.renderRot.set(tmpRot)
            interp.interval = DEFAULT_INTERVAL
            interp.lastRawNanos = raw
            markActive(id)
            return
        }

        // Оценка реального интервала физики
        val sample = (raw - interp.lastRawNanos) / 1_000_000_000f
        interp.lastRawNanos = raw
        if (sample in MIN_INTERVAL..MAX_INTERVAL) {
            interp.interval += (sample - interp.interval) * INTERVAL_SMOOTHING
        }

        val last = interp.count - 1
        val lastTs = interp.times[last]
        val intervalNanos = (interp.interval * 1e9f).toLong()

        // Телепорт
        if (interp.positions[last].dst2(tmpPos) > SNAP_DISTANCE * SNAP_DISTANCE) {
            interp.clear()
            interp.push(tmpPos, tmpRot, raw)
            interp.renderPos.set(tmpPos)
            interp.renderRot.set(tmpRot)
            markActive(id)
            return
        }

        var ts: Long
        if (raw - lastTs > maxGapNanos) {
            // Тело спало: стартуем плавно из текущей отрисованной позы
            interp.clear()
            interp.push(interp.renderPos, interp.renderRot, raw - intervalNanos)
            ts = raw
        } else {
            // Фильтр джиттера: метки идут по ровной сетке, подстраиваясь к реальному времени
            val expected = lastTs + intervalNanos
            val err = raw - expected
            ts = if (abs(err) > intervalNanos) raw
            else expected + (err * TIME_SMOOTHING).toLong()
            ts = max(ts, lastTs + minStepNanos) // время строго растёт
        }

        interp.push(tmpPos, tmpRot, ts)
        markActive(id)
    }

    override fun initialize() {
        physicsWorldUpdater.start()
    }

    override fun processSystem() {
        // Часы рендера: идут по delta, медленно подтягиваются к реальному времени
        val target = System.nanoTime() - renderDelayNanos
        val err = target - renderClock
        renderClock = if (renderClock == 0L || abs(err) > CLOCK_RESYNC_NANOS) {
            target
        } else {
            renderClock + (world.delta * 1e9f).toLong() + (err * CLOCK_FOLLOW).toLong()
        }

        synchronized(lock) {
            var i = activeEntities.size - 1
            while (i >= 0) {
                if (interpolateEntity(activeEntities[i], renderClock)) activeEntities.removeIndex(i)
                i--
            }
        }
    }

    override fun dispose() {
        physicsWorldUpdater.stop()
    }

    /** @return true, если тело дошло до последнего снапшота и его можно убрать из активных. */
    private fun interpolateEntity(entityId: Int, renderNanos: Long): Boolean {
        val interp = physicsInterpMapper[entityId] ?: return true
        val transformComponent = transformMapper[entityId] ?: return true
        if (interp.count == 0) return true
        val transform = transformComponent.transform
            ?: Matrix4().also { transformComponent.transform = it }

        // Выкидываем сегменты, которые рендер уже прошёл
        while (interp.count >= 3 && interp.times[1] <= renderNanos) interp.dropFirst(1)

        if (interp.count == 1) {
            interp.renderPos.set(interp.positions[0])
            interp.renderRot.set(interp.rotations[0])
            transform.set(interp.renderPos, interp.renderRot, tmpScale)
            return true
        }

        val t0 = interp.times[0]
        val t1 = interp.times[1]
        val t = ((renderNanos - t0).toDouble() / (t1 - t0).toDouble()).toFloat().coerceIn(0f, 1f)

        interp.renderPos.set(interp.positions[0]).lerp(interp.positions[1], t)
        interp.renderRot.set(interp.rotations[0]).slerp(interp.rotations[1], t)
        transform.set(interp.renderPos, interp.renderRot, tmpScale)

        return interp.count == 2 && t >= 1f
    }

    private fun markActive(entityId: Int) {
        for (i in 0 until activeEntities.size) if (activeEntities[i] == entityId) return
        activeEntities.add(entityId)
    }

    private companion object {
        const val CLOCK_FOLLOW = 0.02f
        const val CLOCK_RESYNC_NANOS = 100_000_000L // 100 мс
    }
}