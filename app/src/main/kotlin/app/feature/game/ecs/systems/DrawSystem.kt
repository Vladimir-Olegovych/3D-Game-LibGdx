package app.feature.game.ecs.systems

import app.feature.game.ecs.components.BlenderModelComponent
import app.feature.game.ecs.components.BoundRadiusComponent
import app.feature.game.ecs.components.MeshComponent
import app.feature.game.ecs.components.TransformComponent
import com.artemis.ComponentMapper
import com.artemis.annotations.One
import com.artemis.annotations.Wire
import com.artemis.systems.IteratingSystem
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.gigapi.screens.texture.DefaultsTextures
import core.chunk.ChunkManager
import core.defaults.CameraTypes
import core.renderers.ShadowRenderer
import core.renderers.SunRenderer
import core.shaders.ShaderTypes
import core.terrain.TerrainGenerator

@One(MeshComponent::class, BlenderModelComponent::class)
class DrawSystem: IteratingSystem() {

    private lateinit var boundMapper: ComponentMapper<BoundRadiusComponent>
    private lateinit var blenderMapper: ComponentMapper<BlenderModelComponent>
    private lateinit var meshMapper: ComponentMapper<MeshComponent>
    private lateinit var transformMapper: ComponentMapper<TransformComponent>

    @Wire(name = CameraTypes.GL_3D)
    private lateinit var camera: PerspectiveCamera
    @Wire(name = ShaderTypes.SIMPLE_SHADER)
    private lateinit var simpleShader: ShaderProgram
    @Wire
    private lateinit var sunRenderer: SunRenderer
    @Wire
    private lateinit var shadowRenderer: ShadowRenderer

    override fun begin() {
        updateShadows()
        Gdx.gl.glClearColor(135 / 255f, 206 / 255f, 235 / 255f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        Gdx.gl.glEnable(GL20.GL_CULL_FACE)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)

        val fogVerticalRadius = ChunkManager.CHUNK_HEIGHT * ChunkManager.DRAW_RADIUS_Y - ChunkManager.CHUNK_HEIGHT * 2F

        simpleShader.bind()
        //Shadows-Light
        shadowRenderer.shadowTexture.bind(1)
        simpleShader.setUniformi("u_shadowMap", 1)
        simpleShader.setUniformMatrix("u_lightViewProjection", shadowRenderer.lightViewProjectionMatrix)
        simpleShader.setUniformf("u_lightDirection", ShadowRenderer.normalizedLightDirection)
        simpleShader.setUniformf("u_shadowIntensity", 0.7f)
        simpleShader.setUniformf("u_shadowMapSize", ShadowRenderer.SHADOW_MAP_SIZE.toFloat())
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
        val lightPosition = shadowRenderer.getLightCameraPosition()
        sunRenderer.render(lightPosition)
    }

    private fun updateShadows() {
        shadowRenderer.begin()
        val shadowShader = shadowRenderer.shadowShader

        val entities = subscription.getEntities()
        for (i in 0 until entities.size()) {
            val entityId = entities.get(i)
            val transform = transformMapper[entityId]?.transform ?: continue
            shadowShader.setUniformMatrix("u_worldTrans", transform)

            val blenderRenderData = blenderMapper[entityId]?.blenderRenderData
            if (blenderRenderData != null) {
                for (subMesh in blenderRenderData.subMeshes) {
                    subMesh.mesh.render(shadowShader, GL20.GL_TRIANGLES)
                }
            }

            val meshComponent = meshMapper[entityId]
            if (meshComponent?.meshData?.mesh != null) {
                meshComponent.meshData?.mesh?.render(shadowShader, GL20.GL_TRIANGLES)
            }
        }
        shadowRenderer.end()
    }

    private val tmpVec = Vector3()

    override fun process(entityId: Int) {
        val transform = transformMapper[entityId]?.transform ?: return
        val boundingRadius = boundMapper[entityId]?.boundingRadius

        if(boundingRadius != null) {
            val objectPosition = transform.getTranslation(tmpVec)
            val toObject = tmpVec.set(objectPosition).sub(camera.position)

            if (toObject.dot(camera.direction) + boundingRadius < 0f) return
        }

        processModelMesh(entityId)
        processMesh(entityId)
    }


    private fun processModelMesh(entityId: Int) {
        val blenderRenderData = blenderMapper[entityId]?.blenderRenderData ?: return
        val transform = transformMapper[entityId]?.transform ?: return
        if (blenderRenderData.subMeshes.isEmpty()) return

        simpleShader.setUniformMatrix("transform", transform)

        for (subMesh in blenderRenderData.subMeshes) {
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
        val meshTextureData = meshComponent.meshTextureData ?: return
        val mesh = meshComponent.meshData?.mesh ?: return
        val transform = transformMapper[entityId]?.transform ?: return

        simpleShader.setUniformf("objectColor", 1f, 1f, 1f)
        simpleShader.setUniformMatrix("transform", transform)
        meshTextureData.bind(0)
        simpleShader.setUniformf("u_useTexture", 1f)

        mesh.render(simpleShader, GL20.GL_TRIANGLES)
    }
}