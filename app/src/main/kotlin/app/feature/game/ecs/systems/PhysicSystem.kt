package app.feature.game.ecs.systems

import app.feature.game.ecs.components.MeshComponent
import app.feature.game.ecs.components.PhysicalComponent
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
import com.gigapi.eventbus.annotation.BusEvent
import core.assets.SkinID
import core.bullet.PhysicsUtils
import core.bullet.PhysicsWorld
import core.mesh.MeshUtils


class PhysicSystem: BaseSystem() {

    @Wire
    private lateinit var physicsWorld: PhysicsWorld
    @Wire
    private lateinit var assetManager: AssetManager

    private lateinit var physicalMapper: ComponentMapper<PhysicalComponent>
    private lateinit var meshMapper: ComponentMapper<MeshComponent>

    @BusEvent
    fun onChunkDataCreated(event: GameEvent.OnCreateChunkData) {
        val entityId = event.chunkEntityId
        val body = PhysicsUtils.createChunkBody(event.chunkData)
        physicalMapper.create(entityId).body = body
        physicsWorld.world.addRigidBody(body)
    }

    override fun initialize() {
        val meshTexture: TextureRegion = assetManager.get<TextureAtlas>(SkinID.BLOCK.atlas).findRegion("bl_wood")
        val t = extractRegionAsTexture(meshTexture)
        for (i in 0 .. 400) {
            val entityId = world.create()
            val body = PhysicsUtils.createTestBox()
            meshMapper.create(entityId).apply {
                this@apply.meshData = MeshUtils.createBoxMeshData()
                this@apply.meshTextureData = t
            }
            physicalMapper.create(entityId).body = body
            physicsWorld.world.addRigidBody(body)
        }
    }

    override fun processSystem() {
        physicsWorld.update(world.delta)
    }

    override fun dispose() {
        super.dispose()
        physicsWorld.dispose()
    }

    fun extractRegionAsTexture(region: TextureRegion): Texture {
        // 1. Получаем данные исходной текстуры
        val texture = region.getTexture()
        val textureData: TextureData = texture.getTextureData()
        if (!textureData.isPrepared()) {
            textureData.prepare()
        }
        val fullPixmap: Pixmap = textureData.consumePixmap()

        // 2. Создаем новый Pixmap размером с нашу область
        val regionPixmap = Pixmap(
            region.getRegionWidth(),
            region.getRegionHeight(),
            fullPixmap.getFormat()
        )

        // 3. Копируем пиксели из нужной области
        regionPixmap.drawPixmap(
            fullPixmap,  // исходный Pixmap
            0,  // x целевой координаты
            0,  // y целевой координаты
            region.getRegionX(),  // x исходной области (верхний левый угол)
            region.getRegionY(),  // y исходной области
            region.getRegionWidth(),
            region.getRegionHeight()
        )

        // 4. Создаем новую текстуру из скопированных пикселей
        val newTexture = Texture(regionPixmap)


        // 5. Очищаем Pixmap'ы для освобождения памяти
        fullPixmap.dispose()
        regionPixmap.dispose()

        return newTexture
    }
}