package app.feature.game.ecs.systems

import app.feature.game.ecs.components.MeshComponent
import app.feature.game.ecs.components.TransformComponent
import com.artemis.ComponentMapper
import com.artemis.annotations.All
import com.artemis.annotations.Wire
import com.artemis.systems.IteratingSystem
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Vector3
import core.defaults.CameraTypes
import core.shaders.ShaderTypes

@All(MeshComponent::class)
class DrawSystem: IteratingSystem() {

    private val lightDirection = Vector3(30f, 30f, 10f).nor()

    private lateinit var meshMapper: ComponentMapper<MeshComponent>
    private lateinit var transformMapper: ComponentMapper<TransformComponent>

    @Wire(name = CameraTypes.GL_3D)
    private lateinit var camera: PerspectiveCamera
    @Wire(name = ShaderTypes.SIMPLE_SHADER)
    private lateinit var simpleShader: ShaderProgram

    override fun begin() {
        Gdx.gl.glEnable(GL20.GL_CULL_FACE)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glClearColor(135 / 255f, 206 / 255f, 235 / 255f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        simpleShader.bind()
        simpleShader.setUniformi("u_texture", 0)

        simpleShader.setUniformMatrix("modelViewProjection", camera.combined)
        simpleShader.setUniformf("lightDirection", lightDirection)
        simpleShader.setUniformf("lightColor", 1f, 1f, 1f)
        simpleShader.setUniformf("ambientLight", 0.04f, 0.04f, 0.06f)
        simpleShader.setUniformf("viewPosition", camera.position)
        simpleShader.setUniformf("objectColor", 1f, 1f, 1f)
        simpleShader.setUniformf("cameraFar", camera.far)
        simpleShader.setUniformf("fogColor", 135 / 255f, 206 / 255f, 240 / 255f)
    }

    private val tmpVec = Vector3()
    private val cameraForwardXZ = Vector3()
    private val toObjectXZ = Vector3()

    override fun process(entityId: Int) {
        val meshComponent = meshMapper[entityId] ?: return
        val meshTextureData = meshComponent.meshTextureData ?: return
        val mesh = meshComponent.meshData?.mesh ?: return

        val transform = transformMapper[entityId]?.transform?: return

        val objectPosition = transform.getTranslation(tmpVec)
        val objectRadius = meshComponent.boundingRadius
        val toObject = tmpVec.set(objectPosition).sub(camera.position)

        cameraForwardXZ.set(camera.direction).y = 0f
        if (!cameraForwardXZ.isZero) cameraForwardXZ.nor()
        toObjectXZ.set(toObject).y = 0f

        if (toObject.dot(camera.direction) + objectRadius < 0f) {
            if(!toObjectXZ.isZero) {
                val dotProduct = cameraForwardXZ.dot(toObjectXZ)
                val isBack = dotProduct + objectRadius < 0f
                if (isBack) return
            } else {
                return
            }
        }


        simpleShader.setUniformMatrix("transform", transform)
        meshTextureData.bind(0)
        mesh.render(simpleShader, GL20.GL_TRIANGLES)
    }
}