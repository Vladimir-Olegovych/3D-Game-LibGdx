package app.feature.game.ecs.systems

import app.feature.game.ecs.components.BoundRadiusComponent
import app.feature.game.ecs.components.BlenderModelComponent
import app.feature.game.ecs.components.ChunkComponent
import app.feature.game.ecs.components.MeshComponent
import app.feature.game.ecs.components.NetworkEntityComponent
import app.feature.game.ecs.components.StaticComponent
import app.feature.game.ecs.components.TransformComponent
import com.artemis.BaseEntitySystem
import com.artemis.ComponentMapper
import com.artemis.annotations.One
import com.artemis.annotations.Wire
import com.artemis.utils.IntBag
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Vector3
import com.gigapi.texture.DefaultsTextures
import core.chunk.ChunkWorldUpdater
import core.defaults.CameraTypes
import core.renderers.StarRenderer
import core.renderers.SkyPlanetRenderer
import core.shaders.ShaderTypes
import app.feature.game.ecs.states.TimeState

@One(MeshComponent::class, BlenderModelComponent::class)
class DrawSystem: BaseEntitySystem() {

    private lateinit var boundMapper: ComponentMapper<BoundRadiusComponent>
    private lateinit var blenderMapper: ComponentMapper<BlenderModelComponent>
    private lateinit var meshMapper: ComponentMapper<MeshComponent>
    private lateinit var transformMapper: ComponentMapper<TransformComponent>
    private lateinit var staticMapper: ComponentMapper<StaticComponent>
    private lateinit var chunkMapper: ComponentMapper<ChunkComponent>

    @Wire(name = CameraTypes.GL_3D)
    private lateinit var camera: PerspectiveCamera
    @Wire(name = ShaderTypes.CHUNK_SHADER)
    private lateinit var chunkShader: ShaderProgram

    @Wire(name = ShaderTypes.MODEL_SHADER)
    private lateinit var modelShader: ShaderProgram
    @Wire
    private lateinit var timeState: TimeState
    @Wire
    private lateinit var starRenderer: StarRenderer
    @Wire
    private lateinit var skyPlanetRenderer: SkyPlanetRenderer

    private val drawTasks = arrayOf(DrawTaskChunks(), DrawTaskModels())

    override fun begin() {
        val fog = timeState.fogColor()
        Gdx.gl.glClearColor(fog[0], fog[1], fog[2], 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        starRenderer.render()
        skyPlanetRenderer.render()
        Gdx.gl.glEnable(GL20.GL_CULL_FACE)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
    }

    override fun processSystem() {
        drawTasks.forEach { it.draw(subscription.entities) }
    }

    override fun end() {
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
    }

    private val tmpVec = Vector3()

    interface DrawTask {
        fun draw(entities: IntBag)
        fun singleCall(entityId: Int)
    }

    inner class DrawTaskModels: DrawTask {
        override fun draw(entities: IntBag) {
            modelShader.bind()
            modelShader.setUniformi("u_texture", 0)
            modelShader.setUniformMatrix("modelViewProjection", camera.combined)

            for (i in 0 until entities.size()) {
                singleCall(entities[i])
            }
        }

        override fun singleCall(entityId: Int) {
            if (chunkMapper[entityId] != null) return
            val transformComponent = transformMapper[entityId] ?: return
            val transform = transformComponent.transform ?: return
            val boundingRadius = boundMapper[entityId]?.boundingRadius

            if (boundingRadius != null) {
                val objectPosition = transform.getTranslation(tmpVec)
                val toObject = tmpVec.set(objectPosition).sub(camera.position)

                if (toObject.dot(camera.direction) + boundingRadius < 0f) return
            }

            modelShader.setUniformMatrix("transform", transform)

            blenderMapper[entityId]?.let(::drawModelMesh)
            meshMapper[entityId]?.let(::drawModelComp)
        }

        private fun drawModelMesh(blenderModelComponent: BlenderModelComponent) {
            val blenderRenderData = blenderModelComponent.blenderRenderData ?: return
            if (blenderRenderData.subMeshes.isEmpty()) return

            for ((index, subMesh) in blenderRenderData.subMeshes.withIndex()) {
                if (blenderModelComponent.ignoreMeshDrawing.contains(index)) continue
                val material = subMesh.material

                modelShader.setUniformf(
                    "objectColor",
                    material.diffuseColor[0],
                    material.diffuseColor[1],
                    material.diffuseColor[2]
                )
                val matTexture = material.texture
                if (matTexture != null) {
                    matTexture.bind(0)
                    modelShader.setUniformf("u_useTexture", 1f)
                } else {
                    DefaultsTextures.WHITE.bind(0)
                    modelShader.setUniformf("u_useTexture", 0f)
                }

                subMesh.mesh.render(modelShader, GL20.GL_TRIANGLES)
            }
        }

        private fun drawModelComp(meshComponent: MeshComponent) {
            val meshTextureData = meshComponent.meshTextureData ?: DefaultsTextures.WHITE
            val mesh = meshComponent.meshData?.mesh ?: return

            modelShader.setUniformf("objectColor", 1f, 1f, 1f)
            modelShader.setUniformf("u_useTexture", 1f)
            meshTextureData.bind(0)

            mesh.render(modelShader, GL20.GL_TRIANGLES)
        }
    }

    inner class DrawTaskChunks: DrawTask {
        override fun draw(entities: IntBag) {
            val fogVerticalRadius = ChunkWorldUpdater.CHUNK_HEIGHT * ChunkWorldUpdater.DRAW_RADIUS_Y - ChunkWorldUpdater.CHUNK_HEIGHT * 2F

            chunkShader.bind()
            chunkShader.setUniformi("u_texture", 0)
            chunkShader.setUniformMatrix("modelViewProjection", camera.combined)
            chunkShader.setUniformf("viewPosition", camera.position)
            chunkShader.setUniformf("horizontalRadius", camera.far)
            chunkShader.setUniformf("verticalRadius", fogVerticalRadius)
            val fog = timeState.fogColor()
            chunkShader.setUniformf("fogColor", fog[0], fog[1], fog[2])
            chunkShader.setUniformf("u_dayPhase", timeState.dayPhase)
            chunkShader.setUniformf("u_maxShadowThreshold", timeState.maxShadowThreshold)
            chunkShader.setUniformf("u_nightBlueFilter", timeState.nightBlueFilter)

            for (i in 0 until entities.size()) {
                val entityId = entities[i]
                singleCall(entityId)
            }
        }

        override fun singleCall(entityId: Int) {
            chunkMapper[entityId]?: return
            val transformComponent = transformMapper[entityId]?: return
            val transform = transformComponent.transform ?: return
            val boundingRadius = boundMapper[entityId]?.boundingRadius

            if(boundingRadius != null) {
                val objectPosition = transform.getTranslation(tmpVec)
                val toObject = tmpVec.set(objectPosition).sub(camera.position)

                if (toObject.dot(camera.direction) + boundingRadius < 0f) return
            }

            val meshComponent = meshMapper[entityId] ?: return
            val meshTextureData = meshComponent.meshTextureData ?: DefaultsTextures.WHITE
            val mesh = meshComponent.meshData?.mesh ?: return

            chunkShader.setUniformMatrix("transform", transform)
            chunkShader.setUniformf("objectColor", 1f, 1f, 1f)
            meshTextureData.bind(0)

            mesh.render(chunkShader, GL20.GL_TRIANGLES)
        }
    }
}