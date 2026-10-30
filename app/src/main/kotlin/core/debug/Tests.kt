package core.debug

import app.feature.game.ecs.components.BoundRadiusComponent
import app.feature.game.ecs.components.MeshComponent
import app.feature.game.ecs.components.NetworkEntityComponent
import app.feature.game.ecs.components.TransformComponent
import app.feature.game.event.EventBusTypes
import app.feature.game.event.PhysicsEvent
import com.artemis.World
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.gigapi.artemis.world.ArtemisWorld
import com.gigapi.eventbus.EventBus
import com.gigapi.mesh.blender.BlenderParser
import core.assets.ModelID
import core.assets.SkinID
import core.blocks.BlockDataManager
import core.blocks.types.BlockType
import core.mesh.MeshUtils
import core.mesh.MeshUtils.createBlockMeshData
import core.mesh.MeshUtils.createHitboxModel
import core.mesh.rawMeshParams

fun World.startTest100Box(): List<Int> {
    val physicsEventBus = this.getRegistered<EventBus>(EventBusTypes.PHYSICS_EVENT_BUS)
    val blockDataManager = this.getRegistered(BlockDataManager::class.java)
    val assetManager = this.getRegistered(AssetManager::class.java)
    val transformMapper = this.getMapper(TransformComponent::class.java)
    val meshMapper = this.getMapper(MeshComponent::class.java)
    val boundMapper = this.getMapper(BoundRadiusComponent::class.java)
    val networkMapper = this.getMapper(NetworkEntityComponent::class.java)

    val meshTexture = assetManager.get<TextureAtlas>(SkinID.BLOCK.atlas).textures.first()

    val size = 2F
    val m = size * 2.1F
    val playerPhysicalModel = createHitboxModel(size, size)

    val rawBoxMesh = createBlockMeshData(blockDataManager, BlockType.STONE, size)

    val mutableList = mutableListOf<Int>()
    for (x in 0 .. 3) {
        for (y in -3 .. 0) {
            for (z in 0 .. 3) {
                val entityId = this.create()
                mutableList.add(entityId)
                val meshData = rawBoxMesh?.createMeshData(BlenderParser.modelMeshParams)?: continue
                val radius = MeshUtils.getBoundRadius(meshData.mesh)
                boundMapper.create(entityId).boundingRadius = radius
                transformMapper.create(entityId)
                meshMapper.create(entityId).apply {
                    this@apply.meshData = meshData
                    this@apply.meshTextureData = meshTexture
                }
                networkMapper.create(entityId).apply {
                    isLocal = true
                    modelId = ModelID.NULL.ordinal
                }
                physicsEventBus.sendEvent(PhysicsEvent.OnCreateMeshRigidBody(
                    entityId = entityId,
                    position = Vector3(x * m + 10, y * m + 500, z * m + 10),
                    rawMeshData = playerPhysicalModel
                ))
            }
        }
    }
    return mutableList
}