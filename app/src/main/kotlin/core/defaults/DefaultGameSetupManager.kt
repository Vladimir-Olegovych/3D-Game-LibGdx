package core.defaults

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Vector3
import com.gigapi.core.effects.LaunchedEffect
import com.gigapi.general.Context
import core.blocks.BlockDataManager
import core.chunk.ChunkWorldUpdater
import core.mesh.MeshHelper
import core.scope.DispatcherTypes
import core.terrain.TerrainGenerator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executor

object DefaultGameSetupManager: Executor, LaunchedEffect {
    override fun launch(context: Context) {
        val perspectiveCamera = PerspectiveCamera(67f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        perspectiveCamera.position.set(Vector3(0f, TerrainGenerator.WORLD_SURFACE.toFloat(), 0f))
        perspectiveCamera.lookAt(-10f, TerrainGenerator.WORLD_SURFACE.toFloat(), -10f)
        perspectiveCamera.near = 0.2f
        perspectiveCamera.far = (ChunkWorldUpdater.CHUNK_SIZE * ChunkWorldUpdater.DRAW_RADIUS_X) - ChunkWorldUpdater.CHUNK_SIZE * 1.5F
        perspectiveCamera.update()
        context.setObject(CameraTypes.GL_3D, perspectiveCamera)
        //---
        val orthographicCamera = OrthographicCamera()
        context.setObject(CameraTypes.GL_2D, orthographicCamera)
        //---
        context.setObject(InputMultiplexer())
        //---
        context.setObject(BlockDataManager())
        //---
        context.setObject(MeshHelper())
        //---
        context.setObject<CoroutineDispatcher>(DispatcherTypes.MAIN, asCoroutineDispatcher())
    }

    override fun execute(runnable: Runnable) {
        Gdx.app.postRunnable { runnable.run() }
    }
}