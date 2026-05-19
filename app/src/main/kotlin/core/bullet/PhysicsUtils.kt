package core.bullet

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.physics.bullet.collision.CollisionConstants.DISABLE_SIMULATION
import com.badlogic.gdx.physics.bullet.collision.btBoxShape
import com.badlogic.gdx.physics.bullet.collision.btCompoundShape
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody
import com.badlogic.gdx.physics.bullet.linearmath.btDefaultMotionState
import core.blocks.BlockType
import core.chunk.ChunkData
import core.math.createMatrixForChunk

object PhysicsUtils {

    fun createTestBox(): btRigidBody {
        val shape = btBoxShape(Vector3(1f, 1f, 1f))
        val motionState = btDefaultMotionState(Matrix4().setToTranslation(Vector3(10f, 200f, 10f)))
        val localInertia = Vector3(0f, 0f, 0f)
        shape.calculateLocalInertia(1f, localInertia)

        val bodyInfo = btRigidBody.btRigidBodyConstructionInfo(1f, motionState, shape, localInertia)
        val body = btRigidBody(bodyInfo)
        bodyInfo.dispose()
        return body
    }

    fun createChunkBody(chunk: ChunkData): btRigidBody {

        val compound = btCompoundShape()

        val w = chunk.chunkWidth
        val h = chunk.chunkHeight

        val used = Array(w) { Array(h) { BooleanArray(w) } }

        for (y in 0 until h) {
            for (z in 0 until w) {
                for (x in 0 until w) {

                    if (used[x][y][z]) continue
                    if (chunk.getBlockByLocal(x, y, z) == BlockType.AIR) continue

                    var maxX = x
                    while (maxX < w &&
                        !used[maxX][y][z] &&
                        chunk.getBlockByLocal(maxX, y, z) != BlockType.AIR
                    ) {
                        maxX++
                    }

                    var maxZ = z
                    zLoop@ while (maxZ < w) {
                        for (xx in x until maxX) {
                            if (used[xx][y][maxZ] ||
                                chunk.getBlockByLocal(xx, y, maxZ) == BlockType.AIR
                            ) break@zLoop
                        }
                        maxZ++
                    }

                    var maxY = y
                    yLoop@ while (maxY < h) {
                        for (zz in z until maxZ) {
                            for (xx in x until maxX) {
                                if (used[xx][maxY][zz] ||
                                    chunk.getBlockByLocal(xx, maxY, zz) == BlockType.AIR
                                ) break@yLoop
                            }
                        }
                        maxY++
                    }

                    val sizeX = maxX - x
                    val sizeY = maxY - y
                    val sizeZ = maxZ - z

                    for (yy in y until maxY) {
                        for (zz in z until maxZ) {
                            for (xx in x until maxX) {
                                used[xx][yy][zz] = true
                            }
                        }
                    }

                    val shape = btBoxShape(
                        Vector3(sizeX / 2f, sizeY / 2f, sizeZ / 2f)
                    )

                    val transform = Matrix4().setToTranslation(
                        x + sizeX / 2f,
                        y + sizeY / 2f,
                        z + sizeZ / 2f
                    )

                    compound.addChildShape(transform, shape)
                }
            }
        }

        val motionState = btDefaultMotionState(createMatrixForChunk(chunk))

        val info = btRigidBody.btRigidBodyConstructionInfo(
            0f,
            motionState,
            compound,
            Vector3()
        )

        val body = btRigidBody(info)
        info.dispose()

        body.activationState = DISABLE_SIMULATION

        return body
    }
}