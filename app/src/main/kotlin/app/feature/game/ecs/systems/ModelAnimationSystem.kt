package app.feature.game.ecs.systems

import app.feature.game.ecs.components.AnimatorComponent
import app.feature.game.ecs.components.LookDirectionComponent
import com.artemis.Aspect
import com.artemis.ComponentMapper
import com.artemis.systems.IteratingSystem

class ModelAnimationSystem: IteratingSystem(
    Aspect.all(AnimatorComponent::class.java)
) {

    private lateinit var animatorMapper: ComponentMapper<AnimatorComponent>
    private lateinit var lookDirectionMapper: ComponentMapper<LookDirectionComponent>

    override fun process(entityId: Int) {
        val animator = animatorMapper[entityId]?.animator ?: return
        lookDirectionMapper[entityId]?.let { look ->
            animator.setLookDirection(look.yaw, look.pitch)
        }
        animator.update(world.delta)
    }
}
