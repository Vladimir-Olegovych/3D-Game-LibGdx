package core.bullet

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.physics.bullet.collision.*
import com.badlogic.gdx.physics.bullet.collision.CollisionConstants.DISABLE_SIMULATION
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody
import com.badlogic.gdx.physics.bullet.linearmath.btDefaultMotionState
import com.badlogic.gdx.physics.bullet.linearmath.btTransform
import com.badlogic.gdx.physics.bullet.linearmath.btVector3
import core.blocks.BlockType
import core.chunk.ChunkData
import core.math.createMatrixForChunk
import core.mesh.RawMeshData
import java.nio.ByteBuffer
import java.nio.ByteOrder

object PhysicsUtils {

    fun createMeshBody(
        position: Vector3,
        rawMeshData: RawMeshData,
        mass: Float = 0f,
        restitution: Float = 0.5f,
        friction: Float = 0.5f,
        additionalDampingFactor: Float = 0.005f,
        additionalLinearDampingThresholdSqr: Float = 0.01f,
        additionalAngularDampingThresholdSqr: Float = 0.01f,
        additionalAngularDampingFactor: Float = 0.005f
    ): PhysicalData {
        val vertices = rawMeshData.vertices
        val indices = rawMeshData.indices

        val vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
        vertexBuffer.asFloatBuffer().apply { put(vertices); position(0) }

        val indexBuffer = ByteBuffer.allocateDirect(indices.size * 2)
            .order(ByteOrder.nativeOrder())
        indexBuffer.asShortBuffer().apply { put(indices); position(0) }

        val indexedMesh = btIndexedMesh().apply {
            vertexBase = vertexBuffer
            vertexStride = 3 * 4
            numVertices = vertices.size / 3
            triangleIndexBase = indexBuffer
            triangleIndexStride = 2
            numTriangles = indices.size / 3
            indexType = 1
        }
        val indexVertexArray = btTriangleIndexVertexArray()
        indexVertexArray.addIndexedMesh(indexedMesh)

        val shape = btBvhTriangleMeshShape(indexVertexArray, true, true)

        val startTransform = btTransform().apply {
            setIdentity()
            origin.set(position)
        }
        val motionState = btDefaultMotionState(startTransform.inverse())

        val localInertia = Vector3(0f, 0f, 0f)
        if (mass > 0f) {
            shape.calculateLocalInertia(mass, localInertia)
        }

        val bodyInfo = btRigidBody.btRigidBodyConstructionInfo(mass, motionState, shape, localInertia)
        bodyInfo.restitution = restitution
        bodyInfo.friction = friction
        bodyInfo.additionalDampingFactor = additionalDampingFactor
        bodyInfo.additionalLinearDampingThresholdSqr = additionalLinearDampingThresholdSqr
        bodyInfo.additionalAngularDampingThresholdSqr = additionalAngularDampingThresholdSqr
        bodyInfo.additionalAngularDampingFactor = additionalAngularDampingFactor

        val body = btRigidBody(bodyInfo)
        bodyInfo.dispose()

        val physicalData = PhysicalData(mass == 0F)
        physicalData.collisionShapes.add(shape)
        physicalData.motionStates.add(motionState)
        physicalData.rigidBodies.add(body)
        physicalData.setBody(body)

        return physicalData
    }
    fun createTestBox(position: Vector3): PhysicalData {
        val physicalData = PhysicalData(false)

        val shape = btBoxShape(Vector3(1f, 1f, 1f))
        physicalData.shapes.add(shape)
        val motionState = btDefaultMotionState(Matrix4().setToTranslation(position))
        physicalData.motionStates.add(motionState)
        val localInertia = Vector3(0f, 0f, 0f)
        shape.calculateLocalInertia(1f, localInertia)

        val bodyInfo = btRigidBody.btRigidBodyConstructionInfo(1f, motionState, shape, localInertia)
        val body = btRigidBody(bodyInfo)
        bodyInfo.dispose()
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