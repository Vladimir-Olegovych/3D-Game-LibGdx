package core.mesh

import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.gigapi.screens.mesh.RawMeshData
import core.blocks.BlockDataManager
import core.blocks.BlockType

data class Direction(
    val dx: Int, val dy: Int, val dz: Int,
    val normal: VertexAttribute.Normal,
    val directionType: DirectionType
)

class VertexAttribute {
    data class Normal(val x: Float, val y: Float, val z: Float)
}

enum class DirectionType {
    UP, DOWN, LEFT, RIGHT, FRONT, BACK
}

object MeshUtils {
    fun getBoundRadius(mesh: Mesh?): Float {
        mesh?: return 0F
        val boundingBox = BoundingBox()
        mesh.calculateBoundingBox(boundingBox)
        return boundingBox.getDimensions(Vector3()).len()
    }

    fun createBoxBySize(width: Float, height: Float, depth: Float = width): RawMeshData {
        val halfW = width / 2f
        val halfH = height / 2f
        val halfD = depth / 2f

        val vertices = floatArrayOf(
            -halfW, -halfH,  halfD,  0f, 0f, 1f,  0f, 0f,  // 0
            halfW, -halfH,  halfD,  0f, 0f, 1f,  1f, 0f,  // 1
            halfW,  halfH,  halfD,  0f, 0f, 1f,  1f, 1f,  // 2
            -halfW,  halfH,  halfD,  0f, 0f, 1f,  0f, 1f,  // 3

            -halfW, -halfH, -halfD,  0f, 0f, -1f,  0f, 0f, // 4
            -halfW,  halfH, -halfD,  0f, 0f, -1f,  0f, 1f, // 5
            halfW,  halfH, -halfD,  0f, 0f, -1f,  1f, 1f, // 6
            halfW, -halfH, -halfD,  0f, 0f, -1f,  1f, 0f, // 7

            -halfW, -halfH, -halfD,  -1f, 0f, 0f,  0f, 0f, // 8
            -halfW, -halfH,  halfD,  -1f, 0f, 0f,  1f, 0f, // 9
            -halfW,  halfH,  halfD,  -1f, 0f, 0f,  1f, 1f, // 10
            -halfW,  halfH, -halfD,  -1f, 0f, 0f,  0f, 1f, // 11

            halfW, -halfH,  halfD,   1f, 0f, 0f,  0f, 0f, // 12
            halfW, -halfH, -halfD,   1f, 0f, 0f,  1f, 0f, // 13
            halfW,  halfH, -halfD,   1f, 0f, 0f,  1f, 1f, // 14
            halfW,  halfH,  halfD,   1f, 0f, 0f,  0f, 1f, // 15

            -halfW,  halfH,  halfD,   0f, 1f, 0f,  0f, 0f, // 16
            halfW,  halfH,  halfD,   0f, 1f, 0f,  1f, 0f, // 17
            halfW,  halfH, -halfD,   0f, 1f, 0f,  1f, 1f, // 18
            -halfW,  halfH, -halfD,   0f, 1f, 0f,  0f, 1f, // 19

            -halfW, -halfH, -halfD,   0f, -1f, 0f,  0f, 0f, // 20
            -halfW, -halfH,  halfD,   0f, -1f, 0f,  0f, 1f, // 21
            halfW, -halfH,  halfD,   0f, -1f, 0f,  1f, 1f, // 22
            halfW, -halfH, -halfD,   0f, -1f, 0f,  1f, 0f  // 23
        )

        val indices = shortArrayOf(
            0, 1, 2,     0, 2, 3,      // передняя
            4, 5, 6,     4, 6, 7,      // задняя
            8, 9, 10,    8, 10, 11,    // левая
            12, 13, 14,  12, 14, 15,   // правая
            16, 17, 18,  16, 18, 19,   // верхняя
            20, 22, 21,  20, 23, 22    // нижняя (исправлено)
        )

        return RawMeshData(vertices, indices)
    }

    fun createBoxMeshData(
        blockDataManager: BlockDataManager,
        blockType: BlockType,
        size: Float = 1F
    ): RawMeshData {
        val verticesList = ArrayList<Float>()
        val indicesList = ArrayList<Short>()

        val directions = listOf(
            Direction( 1, 0, 0, VertexAttribute.Normal(1f, 0f, 0f), DirectionType.RIGHT),
            Direction(-1, 0, 0, VertexAttribute.Normal(-1f, 0f, 0f), DirectionType.LEFT),
            Direction( 0, 1, 0, VertexAttribute.Normal(0f, 1f, 0f), DirectionType.UP),
            Direction( 0,-1, 0, VertexAttribute.Normal(0f,-1f, 0f), DirectionType.DOWN),
            Direction( 0, 0, 1, VertexAttribute.Normal(0f, 0f, 1f), DirectionType.FRONT),
            Direction( 0, 0,-1, VertexAttribute.Normal(0f, 0f,-1f), DirectionType.BACK)
        )

        for (dir in directions) {
            addFaceSingle(
                blockDataManager = blockDataManager,
                verticesList = verticesList,
                indicesList = indicesList,
                normal = dir.normal,
                blockType = blockType,
                directionType = dir.directionType,
                size = size
            )
        }

        return RawMeshData(
            vertices = verticesList.toFloatArray(),
            indices = indicesList.toShortArray()
        )
    }

    fun addFaceSingle(
        blockDataManager: BlockDataManager,
        verticesList: ArrayList<Float>,
        indicesList: ArrayList<Short>,
        normal: VertexAttribute.Normal,
        blockType: BlockType,
        directionType: DirectionType,
        size: Float
    ) {
        val nx = normal.x
        val ny = normal.y
        val nz = normal.z
        val s2 = size / 2f
        val quadVertices: Array<FloatArray> = when {
            nz == 1f -> arrayOf(   // +Z
                floatArrayOf(-s2,  s2, s2),
                floatArrayOf(-s2, -s2, s2),
                floatArrayOf( s2, -s2, s2),
                floatArrayOf( s2,  s2, s2)
            )
            nz == -1f -> arrayOf(  // -Z
                floatArrayOf( s2,  s2, -s2),
                floatArrayOf( s2, -s2, -s2),
                floatArrayOf(-s2, -s2, -s2),
                floatArrayOf(-s2,  s2, -s2)
            )
            nx == -1f -> arrayOf(  // -X
                floatArrayOf(-s2,  s2, -s2),
                floatArrayOf(-s2, -s2, -s2),
                floatArrayOf(-s2, -s2,  s2),
                floatArrayOf(-s2,  s2,  s2)
            )
            nx == 1f -> arrayOf(   // +X
                floatArrayOf( s2,  s2,  s2),
                floatArrayOf( s2, -s2,  s2),
                floatArrayOf( s2, -s2, -s2),
                floatArrayOf( s2,  s2, -s2)
            )
            ny == -1f -> arrayOf(  // -Y
                floatArrayOf(-s2, -s2, -s2),
                floatArrayOf( s2, -s2, -s2),
                floatArrayOf( s2, -s2,  s2),
                floatArrayOf(-s2, -s2,  s2)
            )
            ny == 1f -> arrayOf(   // +Y
                floatArrayOf(-s2,  s2,  s2),
                floatArrayOf( s2,  s2,  s2),
                floatArrayOf( s2,  s2, -s2),
                floatArrayOf(-s2,  s2, -s2)
            )
            else -> error("Invalid normal")
        }

        val uvs = blockDataManager.faceUVs(directionType, blockType)
        val baseIndex = (verticesList.size / 8).toShort()

        for (i in quadVertices.indices) {
            val v = quadVertices[i]
            val uv = uvs[i]
            verticesList.add(v[0])     // x
            verticesList.add(v[1])     // y
            verticesList.add(v[2])     // z
            verticesList.add(nx)       // normal
            verticesList.add(ny)
            verticesList.add(nz)
            verticesList.add(uv.x)     // u
            verticesList.add(uv.y)     // v
        }

        indicesList.add(baseIndex)
        indicesList.add((baseIndex + 1).toShort())
        indicesList.add((baseIndex + 2).toShort())
        indicesList.add(baseIndex)
        indicesList.add((baseIndex + 2).toShort())
        indicesList.add((baseIndex + 3).toShort())
    }

    fun addFace(
        blockDataManager: BlockDataManager,
        verticesList: ArrayList<Float>,
        indicesList: ArrayList<Short>,
        bx: Int, by: Int, bz: Int,
        normal: VertexAttribute.Normal,
        blockType: BlockType,
        directionType: DirectionType
    ) {
        val x = bx.toFloat()
        val y = by.toFloat()
        val z = bz.toFloat()
        val nx = normal.x
        val ny = normal.y
        val nz = normal.z
        val quadVertices: Array<FloatArray> = when {
            nz == -1F -> arrayOf(
                floatArrayOf(1F, 1F, 0f),
                floatArrayOf(1F, 0f, 0f),
                floatArrayOf(0f, 0f, 0f),
                floatArrayOf(0f, 1F, 0f),
            )
            nz == 1F -> arrayOf(
                floatArrayOf(0f, 1F, 1F),
                floatArrayOf(0f, 0f, 1F),
                floatArrayOf(1F, 0f, 1F),
                floatArrayOf(1F, 1F, 1F),
            )
            nx == -1F -> arrayOf(
                floatArrayOf(0f, 1F, 0f),
                floatArrayOf(0f, 0f, 0f),
                floatArrayOf(0f, 0f, 1F),
                floatArrayOf(0f, 1F, 1F),
            )
            nx == 1F -> arrayOf(
                floatArrayOf(1F, 1F, 1F),
                floatArrayOf(1F, 0f, 1F),
                floatArrayOf(1F, 0f, 0f),
                floatArrayOf(1F, 1F, 0f),
            )
            ny == -1F -> arrayOf(
                floatArrayOf(0f, 0f, 0f),
                floatArrayOf(1F, 0f, 0f),
                floatArrayOf(1F, 0f, 1F),
                floatArrayOf(0f, 0f, 1F)
            )
            ny == 1F -> arrayOf(
                floatArrayOf(0f, 1F, 1F),
                floatArrayOf(1F, 1F, 1F),
                floatArrayOf(1F, 1F, 0f),
                floatArrayOf(0f, 1F, 0f)
            )
            else -> error("Invalid normal")
        }

        val uvs = blockDataManager.faceUVs(directionType, blockType)

        val baseIndex = (verticesList.size / 8).toShort()

        for (i in quadVertices.indices) {
            val v = quadVertices[i]
            val uv = uvs[i]
            verticesList.add(x + v[0])
            verticesList.add(y + v[1])
            verticesList.add(z + v[2])
            verticesList.add(nx)
            verticesList.add(ny)
            verticesList.add(nz)
            verticesList.add(uv.x)
            verticesList.add(uv.y)
        }

        indicesList.add(baseIndex)
        indicesList.add((baseIndex + 1).toShort())
        indicesList.add((baseIndex + 2).toShort())
        indicesList.add(baseIndex)
        indicesList.add((baseIndex + 2).toShort())
        indicesList.add((baseIndex + 3).toShort())
    }
}