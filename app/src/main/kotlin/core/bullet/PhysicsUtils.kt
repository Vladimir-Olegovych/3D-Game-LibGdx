package core.bullet

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.physics.bullet.collision.*
import com.badlogic.gdx.physics.bullet.collision.CollisionConstants.DISABLE_SIMULATION
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody
import com.badlogic.gdx.physics.bullet.linearmath.btDefaultMotionState
import core.blocks.BlockType
import core.chunk.ChunkData
import core.math.createMatrixForChunk
import core.mesh.RawMeshData

object PhysicsUtils {

    fun createMeshBody(
        position: Vector3,
        rawMeshData: RawMeshData,
        mass: Float,
        friction: Float = 0.5f,
        restitution: Float = 0.5f
    ): PhysicalData {
        val isStatic = mass == 0f
        val physicalData = PhysicalData(isStatic)

        val shape: btCollisionShape = if (isStatic) {
            val triangleMesh = btTriangleMesh()
            val vertices = rawMeshData.vertices
            val indices = rawMeshData.indices

            for (i in indices.indices step 3) {
                val i1 = indices[i]
                val i2 = indices[i + 1]
                val i3 = indices[i + 2]

                val v1 = Vector3(vertices[i1 * 3], vertices[i1 * 3 + 1], vertices[i1 * 3 + 2])
                val v2 = Vector3(vertices[i2 * 3], vertices[i2 * 3 + 1], vertices[i2 * 3 + 2])
                val v3 = Vector3(vertices[i3 * 3], vertices[i3 * 3 + 1], vertices[i3 * 3 + 2])

                triangleMesh.addTriangle(v1, v2, v3, false)
            }

            physicalData.triangleMeshes.add(triangleMesh)
            btBvhTriangleMeshShape(triangleMesh, true, true)
        } else {
            val stride = 8
            val convexHull = btConvexHullShape()
            val vertices = rawMeshData.vertices

            for (i in vertices.indices step stride) {
                val x = rawMeshData.vertices[i]
                val y = rawMeshData.vertices[i+1]
                val z = rawMeshData.vertices[i+2]
                convexHull.addPoint(Vector3(x, y, z))
            }
            convexHull.recalcLocalAabb()
            convexHull.optimizeConvexHull()

            convexHull
        }

        physicalData.collisionShapes.add(shape)

        val motionState = btDefaultMotionState(Matrix4().setToTranslation(position))
        physicalData.motionStates.add(motionState)

        val localInertia = Vector3(0f, 0f, 0f)
        if (!isStatic) {
            shape.calculateLocalInertia(mass, localInertia)
        }

        val bodyInfo = btRigidBody.btRigidBodyConstructionInfo(mass, motionState, shape, localInertia)
        val body = btRigidBody(bodyInfo)
        bodyInfo.dispose()

        body.friction = friction
        body.restitution = restitution

        physicalData.rigidBodies.add(body)
        physicalData.setBody(body)

        return physicalData
    }

    fun createChunkBody(chunk: ChunkData): PhysicalData {
        val physicalData = PhysicalData(true)
        val compound = btCompoundShape()
        physicalData.compounds.add(compound)
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
                    physicalData.shapes.add(shape)
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
        physicalData.motionStates.add(motionState)
        val info = btRigidBody.btRigidBodyConstructionInfo(
            0f,
            motionState,
            compound,
            Vector3()
        )

        val body = btRigidBody(info)
        info.dispose()

        body.activationState = DISABLE_SIMULATION
        physicalData.rigidBodies.add(body)
        physicalData.setBody(body)
        return physicalData
    }
}