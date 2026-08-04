package app.feature.game.ecs.systems

import app.feature.game.ecs.components.AnimatorComponent
import app.feature.game.ecs.components.HoldingItemComponent
import app.feature.game.ecs.components.LookDirectionComponent
import com.artemis.Aspect
import com.artemis.ComponentMapper
import com.artemis.annotations.Wire
import com.artemis.systems.IteratingSystem
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.gigapi.mesh.blender.BlenderParser
import core.animator.ModelAnimator
import core.items.ItemManager
import core.items.ItemType

class ModelAnimationSystem: IteratingSystem(
    Aspect.all(AnimatorComponent::class.java)
) {

    @Wire
    private lateinit var itemManager: ItemManager
    @Wire
    private lateinit var assetManager: AssetManager

    private lateinit var animatorMapper: ComponentMapper<AnimatorComponent>
    private lateinit var lookDirectionMapper: ComponentMapper<LookDirectionComponent>
    private lateinit var holdingItemMapper: ComponentMapper<HoldingItemComponent>

    override fun process(entityId: Int) {
        val animator = animatorMapper[entityId]?.animator ?: return

        holdingItemMapper[entityId]?.let { holding ->
            if (holding.dirty) {
                syncRightHandItem(animator, holding)
                holding.dirty = false
            }
        }

        lookDirectionMapper[entityId]?.let { look ->
            animator.setLookDirection(look.yaw, look.pitch)
        }
        animator.update(world.delta)
    }

    private fun syncRightHandItem(animator: ModelAnimator, holding: HoldingItemComponent) {
        val item = holding.item
        if (item == null) {
            animator.setRightHandItem(null)
            return
        }

        val mesh = itemManager.getItemModel(item.id)
            ?.createMeshData(BlenderParser.modelMeshParams)
            ?.mesh
        if (mesh == null) {
            animator.setRightHandItem(null)
            return
        }

        val texture = assetManager.get<TextureAtlas>(item.skinID.atlas).textures.first()
        val isTool = ItemType.entries.any { it.name == item.id }
        animator.setRightHandItem(
            mesh = mesh,
            texture = texture,
            isTool = isTool
        )
    }
}
