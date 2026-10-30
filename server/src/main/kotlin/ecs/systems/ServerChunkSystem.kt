package com.gigcreator.ecs.systems

import com.artemis.ComponentMapper
import com.artemis.annotations.All
import com.artemis.annotations.Wire
import com.artemis.systems.IteratingSystem
import com.artemis.utils.IntBag
import com.gigapi.eventbus.EventBus
import com.gigcreator.core.chunk.ServerChunk
import com.gigcreator.core.chunk.ServerChunkManager
import com.gigcreator.core.ecs.canSee
import com.gigcreator.ecs.components.ObserverComponent
import com.gigcreator.ecs.components.OwnerComponent
import com.gigcreator.ecs.components.TransformComponent
import com.gigcreator.event.ServerChunkEvent

@All(TransformComponent::class)
internal class ServerChunkSystem : ServerChunkManager.Listener, IteratingSystem() {

    @Wire
    private lateinit var eventBus: EventBus
    @Wire
    private lateinit var chunkManager: ServerChunkManager

    private lateinit var positionMapper: ComponentMapper<TransformComponent>
    private lateinit var observerMapper: ComponentMapper<ObserverComponent>
    private lateinit var ownerMapper: ComponentMapper<OwnerComponent>

    override fun initialize() {
        chunkManager.addListener(this)
    }

    override fun dispose() {
        chunkManager.removeListener(this)
    }

    override fun process(entityId: Int) {
        val pos = positionMapper[entityId]?.pos ?: return
        val cx = chunkManager.toChunkX(pos.x)
        val cy = chunkManager.toChunkY(pos.y)
        val cz = chunkManager.toChunkZ(pos.z)

        chunkManager.moveTo(entityId, cx, cy, cz)

        val observer = observerMapper[entityId]
        if (observer != null) {
            chunkManager.updateObserver(entityId, cx, cy, cz, observer.radiusXZ, observer.radiusY)
        } else if (chunkManager.isObserver(entityId)) {
            chunkManager.removeObserver(entityId)
        }
    }

    override fun removed(entityId: Int) {
        chunkManager.removeObserver(entityId)
        chunkManager.remove(entityId)
    }

    override fun onObserve(observer: Int, chunk: ServerChunk, entities: IntBag, first: Boolean) {
        val visible = visibleTo(observer, entities) ?: return
        eventBus.sendEventNow(ServerChunkEvent.Show(observer, visible))
    }

    override fun onUnobserve(observer: Int, chunk: ServerChunk, entities: IntBag, last: Boolean) {
        val visible = visibleTo(observer, entities) ?: return
        eventBus.sendEventNow(ServerChunkEvent.Hide(observer, visible))
    }

    override fun onEntityShow(entity: Int, observers: IntBag, chunk: ServerChunk) {
        for (i in 0 until observers.size()) {
            val observer = observers[i]
            if (ownerMapper.canSee(observer, entity)) {
                eventBus.sendEventNow(ServerChunkEvent.Show(observer, intArrayOf(entity)))
            }
        }
    }

    override fun onEntityHide(entity: Int, observers: IntBag, chunk: ServerChunk) {
        for (i in 0 until observers.size()) {
            val observer = observers[i]
            if (ownerMapper.canSee(observer, entity)) {
                eventBus.sendEventNow(ServerChunkEvent.Hide(observer, intArrayOf(entity)))
            }
        }
    }


    private fun visibleTo(observer: Int, entities: IntBag): IntArray? {
        val size = entities.size()
        if (size == 0) return null
        val out = IntArray(size)
        var n = 0
        for (i in 0 until size) {
            val e = entities[i]
            if (ownerMapper.canSee(observer, e)) out[n++] = e
        }
        return if (n == 0) null else out.copyOf(n)
    }
}