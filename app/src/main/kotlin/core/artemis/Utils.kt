package core.artemis

import app.feature.game.ecs.components.BlenderModelComponent
import app.feature.game.ecs.components.MeshComponent
import com.artemis.Aspect
import com.gigapi.artemis.world.ArtemisWorld

fun ArtemisWorld.disposeALL() {
    val allEntitiesMeshComponent = this.aspectSubscriptionManager.get(Aspect.all(MeshComponent::class.java))
    val allEntitiesBlenderComponent = this.aspectSubscriptionManager.get(Aspect.all(BlenderModelComponent::class.java))
    for (i in 0 until allEntitiesMeshComponent.entities.size()){
        val entityId = allEntitiesMeshComponent.entities[i]
        this.getMapper(MeshComponent::class.java).get(entityId)?.dispose()
    }
    for (i in 0 until allEntitiesBlenderComponent.entities.size()){
        val entityId = allEntitiesBlenderComponent.entities[i]
        this.getMapper(BlenderModelComponent::class.java).get(entityId)?.dispose()
    }
    this.dispose()
}
