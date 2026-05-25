package core.artemis

import app.feature.game.ecs.components.MeshComponent
import com.artemis.Aspect
import com.gigapi.artemis.world.ArtemisWorld

fun ArtemisWorld.disposeALL() {
    val allEntitiesMeshComponent = this.aspectSubscriptionManager.get(Aspect.all(MeshComponent::class.java))
    for (i in 0 until allEntitiesMeshComponent.entities.size()){
        val entityId = allEntitiesMeshComponent.entities[i]
        this.getMapper(MeshComponent::class.java).get(entityId)?.dispose()
    }
    this.dispose()
}