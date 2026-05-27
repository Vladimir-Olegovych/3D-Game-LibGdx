package core.bullet

import com.badlogic.gdx.physics.bullet.collision.btBoxShape
import com.badlogic.gdx.physics.bullet.collision.btCollisionShape
import com.badlogic.gdx.physics.bullet.collision.btCompoundShape
import com.badlogic.gdx.physics.bullet.collision.btTriangleMesh
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody
import com.badlogic.gdx.physics.bullet.linearmath.btDefaultMotionState
import com.badlogic.gdx.utils.Disposable

class PhysicalData(val isStatic: Boolean) : Disposable {
    val compounds = mutableListOf<btCompoundShape>()
    val shapes = mutableListOf<btBoxShape>()
    val motionStates = mutableListOf<btDefaultMotionState>()
    val rigidBodies = mutableListOf<btRigidBody>()
    val collisionShapes = mutableListOf<btCollisionShape>()
    val triangleMeshes = mutableListOf<btTriangleMesh>()

    private var body: btRigidBody? = null

    fun getBody() = body!!

    fun setBody(body: btRigidBody) {
        this.body = body
    }

    override fun dispose() {
        rigidBodies.forEach { if (!it.isDisposed) it.dispose() }
        motionStates.forEach { if (!it.isDisposed) it.dispose() }
        collisionShapes.forEach { if (!it.isDisposed) it.dispose() }
        shapes.forEach { if (!it.isDisposed) it.dispose() }
        compounds.forEach { if (!it.isDisposed) it.dispose() }
        triangleMeshes.forEach { if (!it.isDisposed) it.dispose() }

        rigidBodies.clear()
        motionStates.clear()
        collisionShapes.clear()
        shapes.clear()
        compounds.clear()
        triangleMeshes.clear()

        body = null
    }
}