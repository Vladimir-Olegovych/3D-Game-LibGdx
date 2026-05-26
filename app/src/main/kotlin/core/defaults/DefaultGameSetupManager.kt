package core.defaults

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g3d.utils.FirstPersonCameraController
import com.gigapi.core.effects.LaunchedEffect
import com.gigapi.general.Context
import core.blocks.BlockDataManager
import core.chunk.ChunkManager
import core.mesh.MeshHelper
import core.scope.DispatcherTypes
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.io.File
import java.util.concurrent.Executor

object DefaultGameSetupManager: Executor, LaunchedEffect {
    override fun launch(context: Context) {
        val camera = PerspectiveCamera(67f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        camera.position.set(10f, 200f, 10f)
        camera.lookAt(-10f, 200f, -10f)
        camera.near = 1f
        camera.far = (ChunkManager.CHUNK_SIZE * ChunkManager.DRAW_RADIUS_X) - ChunkManager.CHUNK_SIZE * 1.5F
        camera.update()
        context.setObject(CameraTypes.GL_3D, camera)
        //---
        val controller = FirstPersonCameraController(camera)
        controller.setVelocity(40f)
        context.setObject(controller)
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