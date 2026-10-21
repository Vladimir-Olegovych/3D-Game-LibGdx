package app.feature.game.ecs.systems

import app.feature.game.ecs.components.AnimatorComponent
import app.feature.game.ecs.components.BoundRadiusComponent
import app.feature.game.ecs.components.BlenderModelComponent
import app.feature.game.ecs.components.ChunkComponent
import app.feature.game.ecs.components.ColorComponent
import app.feature.game.ecs.components.MeshComponent
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
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.gigapi.texture.DefaultsTextures
import core.animator.ModelAnimator
import core.chunk.ChunkWorldUpdater
import core.defaults.CameraTypes
import core.renderers.StarRenderer
import core.renderers.SkyPlanetRenderer
import core.shaders.ShaderTypes
import app.feature.game.ecs.states.TimeState
import kotlin.math.sqrt

@One(MeshComponent::class, BlenderModelComponent::class)
class DrawSystem : BaseEntitySystem() {

    private lateinit var boundMapper: ComponentMapper<BoundRadiusComponent>
    private lateinit var blenderMapper: ComponentMapper<BlenderModelComponent>
    private lateinit var animatorMapper: ComponentMapper<AnimatorComponent>
    private lateinit var meshMapper: ComponentMapper<MeshComponent>
    private lateinit var transformMapper: ComponentMapper<TransformComponent>
    private lateinit var staticMapper: ComponentMapper<StaticComponent>
    private lateinit var colorMapper: ComponentMapper<ColorComponent>
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

    private val drawTasks = arrayOf(
        DrawTaskChunks(),
        DrawTaskModels()
    )

    private val tmpCenter = Vector3()
    private val tmpDimensions = Vector3()
    private val tmpToObject = Vector3()

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
        drawTasks.forEach { task ->
            task.draw(subscription.entities)
        }
    }

    override fun end() {
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
    }

    private fun isBehindCamera(
        center: Vector3,
        boundingRadius: Float
    ): Boolean {
        tmpToObject.set(center).sub(camera.position)

        val forwardDistance = tmpToObject.dot(camera.direction)

        return forwardDistance + boundingRadius < 0f
    }

    private fun isBeyondFarPlane(
        center: Vector3,
        boundingRadius: Float
    ): Boolean {
        val distanceSquared = tmpToObject.len2()

        val maxDistance = camera.far + boundingRadius

        return distanceSquared > maxDistance * maxDistance
    }

    private fun isSphereVisible(
        transform: Matrix4,
        boundingRadius: Float?
    ): Boolean {
        if (boundingRadius == null) return true

        transform.getTranslation(tmpCenter)

        if (isBehindCamera(tmpCenter, boundingRadius)) return false
        if (isBeyondFarPlane(tmpCenter, boundingRadius)) return false

        return camera.frustum.sphereInFrustum(
            tmpCenter,
            boundingRadius
        )
    }

    private fun isChunkVisible(transform: Matrix4): Boolean {
        transform.getTranslation(tmpCenter)

        val chunkSize = ChunkWorldUpdater.CHUNK_SIZE.toFloat()
        val chunkHeight = ChunkWorldUpdater.CHUNK_HEIGHT.toFloat()

        /*
         * If chunk transform contains the chunk's minimum corner:
         * center = corner + size / 2
         */
        tmpCenter.add(
            chunkSize * 0.5f,
            chunkHeight * 0.5f,
            chunkSize * 0.5f
        )

        /*
         * If your createMatrixForChunk() already places the transform
         * at the chunk center, remove the tmpCenter.add(...) above and
         * use this instead:
         *
         * tmpCenter.set(transform.getTranslation(tmpCenter))
         */

        tmpDimensions.set(
            chunkSize,
            chunkHeight,
            chunkSize
        )

        return camera.frustum.boundsInFrustum(
            tmpCenter,
            tmpDimensions
        )
    }

    interface DrawTask {
        fun draw(entities: IntBag)
        fun singleCall(entityId: Int)
    }

    inner class DrawTaskModels : DrawTask {

        override fun draw(entities: IntBag) {
            modelShader.bind()
            modelShader.setUniformi("u_texture", 0)
            modelShader.setUniformMatrix("modelViewProjection", camera.combined)
            modelShader.setUniformf("u_dayPhase", timeState.dayPhase)
            modelShader.setUniformf(
                "u_maxShadowThreshold",
                timeState.maxShadowThreshold
            )
            modelShader.setUniformf(
                "u_nightBlueFilter",
                timeState.nightBlueFilter
            )

            for (i in 0 until entities.size()) {
                singleCall(entities[i])
            }
        }

        override fun singleCall(entityId: Int) {
            if (chunkMapper[entityId] != null) return

            val transformComponent = transformMapper[entityId] ?: return
            val transform = transformComponent.transform ?: return
            val color = colorMapper[entityId]?.color

            val boundingRadius = boundMapper[entityId]?.boundingRadius

            if (!isSphereVisible(transform, boundingRadius)) return

            modelShader.setUniformMatrix("transform", transform)

            if (color != null) {
                modelShader.setUniformf("objectColor", color.r, color.g, color.b)
            } else {
                modelShader.setUniformf("objectColor", 1f, 1f, 1f)
            }
            modelShader.setUniformf("u_useTexture", 1f)

            blenderMapper[entityId]?.let(::drawModelMesh)
            animatorMapper[entityId]?.animator?.let(::drawAnimator)
            meshMapper[entityId]?.let(::drawModelComp)
        }

        private fun drawModelMesh(
            blenderModelComponent: BlenderModelComponent
        ) {
            val renderData = blenderModelComponent.blenderRenderData ?: return

            if (renderData.subMeshes.isEmpty()) return

            for ((index, subMesh) in renderData.subMeshes.withIndex()) {
                if (blenderModelComponent.ignoreDrawingAll) continue
                if (blenderModelComponent.ignoreMeshDrawing.contains(index)) continue

                val material = subMesh.material

                modelShader.setUniformf(
                    "objectColor",
                    material.diffuseColor[0],
                    material.diffuseColor[1],
                    material.diffuseColor[2]
                )

                val texture = material.texture

                if (texture != null) {
                    texture.bind(0)
                    modelShader.setUniformf("u_useTexture", 1f)
                } else {
                    DefaultsTextures.WHITE.bind(0)
                    modelShader.setUniformf("u_useTexture", 0f)
                }

                subMesh.mesh.render(
                    modelShader,
                    GL20.GL_TRIANGLES
                )
            }
        }

        private fun drawAnimator(animator: ModelAnimator) {
            val mesh = animator.getRightHandItemMesh() ?: return
            val texture = animator.getRightHandItemTexture()
                ?: DefaultsTextures.WHITE

            texture.bind(0)

            mesh.render(
                modelShader,
                GL20.GL_TRIANGLES
            )
        }

        private fun drawModelComp(meshComponent: MeshComponent) {
            val mesh = meshComponent.meshData?.mesh ?: return
            val texture = meshComponent.meshTextureData ?: DefaultsTextures.WHITE

            texture.bind(0)

            mesh.render(
                modelShader,
                GL20.GL_TRIANGLES
            )
        }
    }

    inner class DrawTaskChunks : DrawTask {
        val fogVerticalRadius = ChunkWorldUpdater.CHUNK_HEIGHT * ChunkWorldUpdater.DRAW_RADIUS_Y -
                    ChunkWorldUpdater.CHUNK_HEIGHT * 2f


        override fun draw(entities: IntBag) {


            chunkShader.bind()
            chunkShader.setUniformi("u_texture", 0)
            chunkShader.setUniformMatrix("modelViewProjection", camera.combined)
            chunkShader.setUniformf("viewPosition", camera.position)
            chunkShader.setUniformf("horizontalRadius", camera.far)
            chunkShader.setUniformf("verticalRadius", fogVerticalRadius)

            val fog = timeState.fogColor()

            chunkShader.setUniformf(
                "fogColor",
                fog[0],
                fog[1],
                fog[2]
            )
            chunkShader.setUniformf("u_dayPhase", timeState.dayPhase)
            chunkShader.setUniformf(
                "u_maxShadowThreshold",
                timeState.maxShadowThreshold
            )
            chunkShader.setUniformf(
                "u_nightBlueFilter",
                timeState.nightBlueFilter
            )

            for (i in 0 until entities.size()) {
                singleCall(entities[i])
            }
        }

        override fun singleCall(entityId: Int) {
            if (chunkMapper[entityId] == null) return

            val transformComponent = transformMapper[entityId] ?: return
            val transform = transformComponent.transform ?: return

            if (!isChunkVisible(transform)) return

            val meshComponent = meshMapper[entityId] ?: return
            val mesh = meshComponent.meshData?.mesh ?: return
            val texture = meshComponent.meshTextureData
                ?: DefaultsTextures.WHITE

            chunkShader.setUniformMatrix("transform", transform)
            chunkShader.setUniformf("objectColor", 1f, 1f, 1f)

            texture.bind(0)

            mesh.render(
                chunkShader,
                GL20.GL_TRIANGLES
            )
        }
    }
}