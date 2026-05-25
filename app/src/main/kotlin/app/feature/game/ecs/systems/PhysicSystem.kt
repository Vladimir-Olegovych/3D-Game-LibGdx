package app.feature.game.ecs.systems

import app.feature.game.ecs.components.MeshComponent
import app.feature.game.ecs.components.TransformComponent
import app.feature.game.event.EventBusTypes
import app.feature.game.event.GameEvent
import com.artemis.BaseSystem
import com.artemis.ComponentMapper
import com.artemis.annotations.Wire
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.TextureData
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Vector3
import com.gigapi.eventbus.EventBus
import com.gigapi.eventbus.annotation.BusEvent
import core.assets.SkinID
import core.bullet.PhysicsWorldUpdater
import core.mesh.MeshUtils


class PhysicSystem: BaseSystem() {

    @Wire
    private lateinit var physicsWorldUpdater: PhysicsWorldUpdater
    @Wire(name = EventBusTypes.PHYSICS_EVENT_BUS)
    private lateinit var physicsEventBus: EventBus

    private lateinit var transformMapper: ComponentMapper<TransformComponent>

    //Delete
    @Wire private lateinit var assetManager: AssetManager
    private lateinit var meshMapper: ComponentMapper<MeshComponent>
    private lateinit var texture: Texture

    override fun initialize() {
        physicsWorldUpdater.start()

        val meshTexture: TextureRegion = assetManager.get<TextureAtlas>(SkinID.BLOCK.atlas).findRegion("bl_wood")
        texture = extractRegionAsTexture(meshTexture)

        val rawBoxMesh = MeshUtils.createBoxMeshData()

        for (i in 0 .. 100) {
            val entityId = world.create()
            transformMapper.create(entityId)
            meshMapper.create(entityId).apply {
                this@apply.meshData = rawBoxMesh.createMeshData()
                this@apply.meshTextureData = texture
            }
            physicsEventBus.sendEvent(GameEvent.OnCreateMeshRigidBody(
                entityId = entityId,
                position = Vector3(10f, 400f, 10f),
                rawMeshData = rawBoxMesh
            ))
        }
    }

    @BusEvent
    fun onRigidBodyTransformUpdate(event: GameEvent.OnRigidBodyTransformUpdate) {
        val component = transformMapper[event.entityId]?: return
        component.transform = event.transform
    }

    override fun processSystem() {}

    override fun dispose() {
        texture.dispose()
        physicsWorldUpdater.stop()
    }

    //Delete
    fun extractRegionAsTexture(region: TextureRegion): Texture {
        val texture = region.getTexture()
        val textureData: TextureData = texture.getTextureData()
        if (!textureData.isPrepared()) {
            textureData.prepare()
        }
        val fullPixmap: Pixmap = textureData.consumePixmap()
        val regionPixmap = Pixmap(
            region.getRegionWidth(),
            region.getRegionHeight(),
            fullPixmap.getFormat()
        )
        regionPixmap.drawPixmap(
            fullPixmap,  // исходный Pixmap
            0,  // x целевой координаты
            0,  // y целевой координаты
            region.getRegionX(),  // x исходной области (верхний левый угол)
            region.getRegionY(),  // y исходной области
            region.getRegionWidth(),
            region.getRegionHeight()
        )
        val newTexture = Texture(regionPixmap)
        fullPixmap.dispose()
        regionPixmap.dispose()

        return newTexture
    }
}