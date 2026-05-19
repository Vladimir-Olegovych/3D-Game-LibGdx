package core.artemis

import app.feature.game.ecs.components.MeshComponent
import app.feature.game.ecs.components.PhysicalComponent
import com.artemis.Aspect
import com.gigapi.artemis.world.ArtemisWorld

fun ArtemisWorld.disposeALL() {
    val allEntitiesMeshComponent = this.aspectSubscriptionManager.get(Aspect.all(MeshComponent::class.java))
    for (i in 0 until allEntitiesMeshComponent.entities.size()){
        val entityId = allEntitiesMeshComponent.entities[i]
        this.getMapper(MeshComponent::class.java).get(entityId)?.dispose()
    }
    val allEntitiesPhysicalComponent = this.aspectSubscriptionManager.get(Aspect.all(PhysicalComponent::class.java))
    for (i in 0 until allEntitiesPhysicalComponent.entities.size()){
        val entityId = allEntitiesPhysicalComponent.entities[i]
        this.getMapper(PhysicalComponent::class.java).get(entityId)?.dispose()
    }
    this.dispose()
}