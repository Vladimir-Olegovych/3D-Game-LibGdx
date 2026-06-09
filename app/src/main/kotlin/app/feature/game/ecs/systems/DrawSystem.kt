package app.feature.game.ecs.systems

import app.feature.game.ecs.components.*
import com.artemis.ComponentMapper
import com.artemis.annotations.One
import com.artemis.annotations.Wire
import com.artemis.systems.IteratingSystem
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Vector3
import com.gigapi.screens.texture.DefaultsTextures
import core.chunk.ChunkManager
import core.defaults.CameraTypes
import core.renderers.SunRenderer
import core.shaders.ShaderTypes

@One(MeshComponent::class, BlenderModelComponent::class)
class DrawSystem: IteratingSystem() {

    private lateinit var boundMapper: ComponentMapper<BoundRadiusComponent>
    private lateinit var blenderMapper: ComponentMapper<BlenderModelComponent>
    private lateinit var meshMapper: ComponentMapper<MeshComponent>
    private lateinit var transformMapper: ComponentMapper<TransformComponent>
    private lateinit var staticMapper: ComponentMapper<StaticComponent>
    private lateinit var aoMapper: ComponentMapper<AOComponent>

    @Wire(name = CameraTypes.GL_3D)
    private lateinit var camera: PerspectiveCamera
    @Wire(name = ShaderTypes.SIMPLE_SHADER)
    private lateinit var simpleShader: ShaderProgram
    @Wire
    private lateinit var sunRenderer: SunRenderer

    override fun begin() {
        Gdx.gl.glClearColor(135 / 255f, 206 / 255f, 235 / 255f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        Gdx.gl.glEnable(GL20.GL_CULL_FACE)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)

        val fogVerticalRadius = ChunkManager.CHUNK_HEIGHT * ChunkManager.DRAW_RADIUS_Y - ChunkManager.CHUNK_HEIGHT * 2F

        simpleShader.bind()
        //Mesh
        simpleShader.setUniformi("u_texture", 0)
        simpleShader.setUniformMatrix("modelViewProjection", camera.combined)
        //Fog
        simpleShader.setUniformf("viewPosition", camera.position)
        simpleShader.setUniformf("horizontalRadius", camera.far)
        simpleShader.setUniformf("verticalRadius", fogVerticalRadius)
        simpleShader.setUniformf("fogColor", 135 / 255f, 206 / 255f, 240 / 255f)

    }

    override fun end() {
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
    }

    private val tmpVec = Vector3()

    override fun process(entityId: Int) {
        val transformComponent = transformMapper[entityId]?: return
        val transform = transformComponent.transform ?: return
        val aoCount = aoMapper[entityId]?.count
        val boundingRadius = boundMapper[entityId]?.boundingRadius

        if(boundingRadius != null) {
            val objectPosition = transform.getTranslation(tmpVec)
            val toObject = tmpVec.set(objectPosition).sub(camera.position)

            if (toObject.dot(camera.direction) + boundingRadius < 0f) return
        }

        /*
        if (staticMapper[entityId] != null) {
            val interpolated = transformComponent.getInterpolated()?: return
            simpleShader.setUniformMatrix("transform", interpolated)
        } else {
            simpleShader.setUniformMatrix("transform", transform)
        }
         */
        simpleShader.setUniformMatrix("transform", transform)


        if (aoCount != null) {
            simpleShader.setUniformf("u_modelAO", aoCount)
        } else {
            simpleShader.setUniformf("u_modelAO", 0f)
        }

        processModelMesh(entityId)
        processMesh(entityId)
    }


    private fun processModelMesh(entityId: Int) {
        val blenderModel = blenderMapper[entityId]?: return
        val blenderRenderData = blenderModel.blenderRenderData ?: return
        if (blenderRenderData.subMeshes.isEmpty()) return

        for ((index, subMesh) in blenderRenderData.subMeshes.withIndex()) {
            if(blenderModel.ignoreMeshDrawing.contains(index)) continue
            val material = subMesh.material

            simpleShader.setUniformf("objectColor", material.diffuseColor[0], material.diffuseColor[1], material.diffuseColor[2])
            val matTexture = material.texture
            if (matTexture != null) {
                matTexture.bind(0)
                simpleShader.setUniformf("u_useTexture", 1f)
            } else {
                DefaultsTextures.WHITE.bind(0)
                simpleShader.setUniformf("u_useTexture", 0f)
            }

            subMesh.mesh.render(simpleShader, GL20.GL_TRIANGLES)
        }
    }

    private fun processMesh(entityId: Int) {
        val meshComponent = meshMapper[entityId] ?: return
        val meshTextureData = meshComponent.meshTextureData ?: DefaultsTextures.WHITE
        val mesh = meshComponent.meshData?.mesh ?: return

        simpleShader.setUniformf("objectColor", 1f, 1f, 1f)
        meshTextureData.bind(0)
        simpleShader.setUniformf("u_useTexture", 1f)

        mesh.render(simpleShader, GL20.GL_TRIANGLES)
    }
}