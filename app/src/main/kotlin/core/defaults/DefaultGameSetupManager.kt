package core.defaults

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.scenes.scene2d.Stage
import com.gigapi.effects.LaunchedEffect
import com.gigapi.eventbus.EventBus
import com.gigapi.general.GContext
import com.gigapi.kryo.GameClient
import com.gigapi.viewport.UnfairViewport
import core.blocks.BlockDataManager
import core.chunk.ChunkWorldUpdater
import core.mesh.MeshHelper
import core.chunk.ShadowUpdater
import core.client.ClientAcceptor
import core.scope.DispatcherTypes
import core.terrain.TerrainGenerator
import core.viewport.ViewportTypes
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executor

object DefaultGameSetupManager: Executor, LaunchedEffect {
    override fun launch(gContext: GContext) {
        val perspectiveCamera = PerspectiveCamera(67f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        perspectiveCamera.position.set(Vector3(0f, TerrainGenerator.WORLD_SURFACE.toFloat(), 0f))
        perspectiveCamera.lookAt(-10f, TerrainGenerator.WORLD_SURFACE.toFloat(), -10f)
        perspectiveCamera.near = 0.2f
        perspectiveCamera.far = (ChunkWorldUpdater.CHUNK_SIZE * ChunkWorldUpdater.DRAW_RADIUS_X) - ChunkWorldUpdater.CHUNK_SIZE * 1.5F
        perspectiveCamera.update()
        gContext.setObject(CameraTypes.GL_3D, perspectiveCamera)
        //---
        val orthographicCamera = OrthographicCamera()
        gContext.setObject(CameraTypes.GL_2D, orthographicCamera)
        //---
        val unViewport = UnfairViewport(700F, orthographicCamera)
        gContext.setObject(ViewportTypes.UNFAIR, unViewport)
        //---
        val spriteBatch = SpriteBatch()
        gContext.setObject(spriteBatch)
        //---
        val stage = Stage(unViewport)
        gContext.setObject(stage)
        //---
        val inputMultiplexer = InputMultiplexer()
        gContext.setObject(inputMultiplexer)
        //---
        gContext.setObject(BlockDataManager())
        //---
        gContext.setObject(MeshHelper())
        //---
        gContext.setObject(ShadowUpdater())
        //---
        gContext.setObject<CoroutineDispatcher>(DispatcherTypes.MAIN, asCoroutineDispatcher())
        //---
        gContext.setObject(GameClient(1024))
    }

    override fun execute(runnable: Runnable) {
        Gdx.app.postRunnable { runnable.run() }
    }
}