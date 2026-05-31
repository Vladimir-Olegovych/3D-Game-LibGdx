package core.artemis

import app.feature.game.ecs.components.BoundRadiusComponent
import app.feature.game.ecs.components.MeshComponent
import app.feature.game.ecs.components.TransformComponent
import app.feature.game.event.EventBusTypes
import app.feature.game.event.GameEvent
import com.artemis.World
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.math.Vector3
import com.gigapi.eventbus.EventBus
import core.assets.SkinID
import core.blocks.BlockDataManager
import core.blocks.BlockType
import core.mesh.MeshUtils

fun World.startTest100Box() {
    val physicsEventBus = this.getRegistered<EventBus>(EventBusTypes.PHYSICS_EVENT_BUS)
    val blockDataManager = this.getRegistered(BlockDataManager::class.java)
    val assetManager = this.getRegistered(AssetManager::class.java)
    val transformMapper = this.getMapper(TransformComponent::class.java)
    val meshMapper = this.getMapper(MeshComponent::class.java)
    val boundMapper = this.getMapper(BoundRadiusComponent::class.java)

    val meshTexture = assetManager.get<TextureAtlas>(SkinID.BLOCK.atlas).textures.first()

    val size = 2F
    val m = size * 2.1F


    val rawBoxMesh = MeshUtils.createBoxMeshData(blockDataManager, BlockType.STONE, size)

    for (x in 0 .. 5) {
        for (y in -5 .. 0) {
            for (z in 0 .. 5) {
                val entityId = this.create()

                val meshData = rawBoxMesh.createMeshData()
                val radius = MeshUtils.getBoundRadius(meshData.mesh)
                boundMapper.create(entityId).boundingRadius = radius
                transformMapper.create(entityId)
                meshMapper.create(entityId).apply {
                    this@apply.meshData = meshData
                    this@apply.meshTextureData = meshTexture
                }
                physicsEventBus.sendEvent(GameEvent.OnCreateMeshRigidBody(
                    entityId = entityId,
                    position = Vector3(x * m + 10, y * m + 700, z * m + 10),
                    rawMeshData = rawBoxMesh
                ))
            }
        }
    }
}