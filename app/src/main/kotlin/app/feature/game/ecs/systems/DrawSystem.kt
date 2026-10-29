package app.feature.game.ecs.systems

import app.feature.game.ecs.components.*
import app.feature.game.ecs.states.TimeState
import com.artemis.BaseEntitySystem
import com.artemis.ComponentMapper
import com.artemis.annotations.One
import com.artemis.annotations.Wire
import com.artemis.utils.IntBag
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.gigapi.texture.DefaultsTextures
import core.animator.ModelAnimator
import core.chunk.ChunkWorldUpdater
import core.defaults.CameraTypes
import core.mesh.generators.ChunkMeshSplit
import core.mesh.generators.GreedyChunkMesher
import core.renderers.SkyPlanetRenderer
import core.renderers.StarRenderer
import core.shaders.ShaderTypes

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

        private val fogVerticalRadius =
            ChunkWorldUpdater.CHUNK_HEIGHT * ChunkWorldUpdater.DRAW_RADIUS_Y -
                    ChunkWorldUpdater.CHUNK_HEIGHT * 2f

        /** Вторая программа: тот же код, но с #define CUTOUT (discard для листвы и т.п.). */
        private val cutoutShader: ShaderProgram by lazy {
            val program = ShaderProgram(
                chunkShader.vertexShaderSource,
                "#define CUTOUT\n" + chunkShader.fragmentShaderSource
            )
            check(program.isCompiled) { "Cutout chunk shader failed:\n${program.log}" }
            program
        }

        private var opaqueTransformLoc = -1
        private var cutoutTransformLoc = -1

        // видимые чанки текущего кадра (тест видимости делается один раз на оба прохода)
        private var visCount = 0
        private var visMesh = arrayOfNulls<Mesh>(2048)
        private var visTransform = arrayOfNulls<Matrix4>(2048)
        private var visSplit = IntArray(2048)

        private fun pushVisible(mesh: Mesh, transform: Matrix4) {
            if (visCount == visMesh.size) {
                val n = visCount * 2
                visMesh = visMesh.copyOf(n)
                visTransform = visTransform.copyOf(n)
                visSplit = visSplit.copyOf(n)
            }
            visMesh[visCount] = mesh
            visTransform[visCount] = transform
            visSplit[visCount] = ChunkMeshSplit.get(mesh)
            visCount++
        }

        private fun bindCommon(shader: ShaderProgram) {
            shader.bind()
            shader.setUniformi("u_texture", 0)
            shader.setUniformMatrix("modelViewProjection", camera.combined)
            shader.setUniformf("viewPosition", camera.position)
            shader.setUniformf("horizontalRadius", camera.far)
            shader.setUniformf("verticalRadius", fogVerticalRadius)
            shader.setUniformf("u_tileSize", GreedyChunkMesher.tileSizeU, GreedyChunkMesher.tileSizeV)

            val fog = timeState.fogColor()
            shader.setUniformf("fogColor", fog[0], fog[1], fog[2])
            shader.setUniformf("u_dayPhase", timeState.dayPhase)
            shader.setUniformf("u_maxShadowThreshold", timeState.maxShadowThreshold)
            shader.setUniformf("u_nightBlueFilter", timeState.nightBlueFilter)
        }

        override fun draw(entities: IntBag) {
            // 1. видимость (один раз)
            visCount = 0
            var firstMeshComponent: MeshComponent? = null
            for (i in 0 until entities.size()) {
                val id = entities[i]
                if (chunkMapper[id] == null) continue
                val transform = transformMapper[id]?.transform ?: continue
                if (!isChunkVisible(transform)) continue
                val meshComponent = meshMapper[id] ?: continue
                val mesh = meshComponent.meshData?.mesh ?: continue
                if (firstMeshComponent == null) firstMeshComponent = meshComponent
                pushVisible(mesh, transform)
            }
            if (visCount == 0) return

            val texture = firstMeshComponent?.meshTextureData ?: DefaultsTextures.WHITE
            texture.bind(0) // атлас один на все чанки

            // 2. непрозрачный проход: без discard, early-Z работает полностью
            bindCommon(chunkShader)
            if (opaqueTransformLoc < 0) opaqueTransformLoc = chunkShader.fetchUniformLocation("transform", true)
            for (k in 0 until visCount) {
                val split = visSplit[k]
                if (split <= 0) continue
                chunkShader.setUniformMatrix(opaqueTransformLoc, visTransform[k]!!)
                visMesh[k]!!.render(chunkShader, GL20.GL_TRIANGLES, 0, split)
            }

            // 3. cutout-проход (листва и т.п.) — после непрозрачных
            var cutoutBound = false
            for (k in 0 until visCount) {
                val mesh = visMesh[k]!!
                val split = visSplit[k]
                val count = mesh.numIndices - split
                if (count <= 0) continue
                if (!cutoutBound) {
                    bindCommon(cutoutShader)
                    if (cutoutTransformLoc < 0) {
                        cutoutTransformLoc = cutoutShader.fetchUniformLocation("transform", true)
                    }
                    cutoutBound = true
                }
                cutoutShader.setUniformMatrix(cutoutTransformLoc, visTransform[k]!!)
                mesh.render(cutoutShader, GL20.GL_TRIANGLES, split, count)
            }
        }

        override fun singleCall(entityId: Int) = Unit // чанки рисуются целиком в draw()
    }
}