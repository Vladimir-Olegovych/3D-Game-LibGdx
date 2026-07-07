package core.mesh

import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.gigapi.mesh.RawMeshData
import com.gigapi.mesh.blender.BlenderParser
import core.blocks.BlockDataManager
import core.blocks.BlockType

data class Direction(
    val dx: Int, val dy: Int, val dz: Int,
    val normal: VertexAttribute3.Normal,
    val directionType: DirectionType
)

class VertexAttribute3 {
    data class Normal(val x: Float, val y: Float, val z: Float)
}

enum class DirectionType {
    UP, DOWN, LEFT, RIGHT, FRONT, BACK
}

object MeshUtils {
    fun getBoundRadius(mesh: Mesh?): Float {
        mesh?: return 0F
        val boundingBox = BoundingBox()
        runCatching { mesh.calculateBoundingBox(boundingBox) }.onFailure {
            return@getBoundRadius 0f
        }
        return boundingBox.getDimensions(Vector3()).len()
    }

    fun createHitboxModel(width: Float, height: Float, depth: Float = width): RawMeshData {
        val halfW = width / 2f
        val halfH = height / 2f
        val halfD = depth / 2f

        val vertices = floatArrayOf(
            -halfW, -halfH,  halfD,  0f, 0f, 1f,
            halfW, -halfH,  halfD,  0f, 0f, 1f,
            halfW,  halfH,  halfD,  0f, 0f, 1f,
            -halfW,  halfH,  halfD,  0f, 0f, 1f,

            -halfW, -halfH, -halfD,  0f, 0f, -1f,
            -halfW,  halfH, -halfD,  0f, 0f, -1f,
            halfW,  halfH, -halfD,  0f, 0f, -1f,
            halfW, -halfH, -halfD,  0f, 0f, -1f,

            -halfW, -halfH, -halfD,  -1f, 0f, 0f,
            -halfW, -halfH,  halfD,  -1f, 0f, 0f,
            -halfW,  halfH,  halfD,  -1f, 0f, 0f,
            -halfW,  halfH, -halfD,  -1f, 0f, 0f,

            halfW, -halfH,  halfD,   1f, 0f, 0f,
            halfW, -halfH, -halfD,   1f, 0f, 0f,
            halfW,  halfH, -halfD,   1f, 0f, 0f,
            halfW,  halfH,  halfD,   1f, 0f, 0f,

            -halfW,  halfH,  halfD,   0f, 1f, 0f,
            halfW,  halfH,  halfD,   0f, 1f, 0f,
            halfW,  halfH, -halfD,   0f, 1f, 0f,
            -halfW,  halfH, -halfD,   0f, 1f, 0f,

            -halfW, -halfH, -halfD,   0f, -1f, 0f,
            -halfW, -halfH,  halfD,   0f, -1f, 0f,
            halfW, -halfH,  halfD,   0f, -1f, 0f,
            halfW, -halfH, -halfD,   0f, -1f, 0f
        )

        val indices = shortArrayOf(
            0, 1, 2,     0, 2, 3,
            4, 5, 6,     4, 6, 7,
            8, 9, 10,    8, 10, 11,
            12, 13, 14,  12, 14, 15,
            16, 17, 18,  16, 18, 19,
            20, 22, 21,  20, 23, 22
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
            Direction( 1, 0, 0, VertexAttribute3.Normal(1f, 0f, 0f), DirectionType.RIGHT),
            Direction(-1, 0, 0, VertexAttribute3.Normal(-1f, 0f, 0f), DirectionType.LEFT),
            Direction( 0, 1, 0, VertexAttribute3.Normal(0f, 1f, 0f), DirectionType.UP),
            Direction( 0,-1, 0, VertexAttribute3.Normal(0f,-1f, 0f), DirectionType.DOWN),
            Direction( 0, 0, 1, VertexAttribute3.Normal(0f, 0f, 1f), DirectionType.FRONT),
            Direction( 0, 0,-1, VertexAttribute3.Normal(0f, 0f,-1f), DirectionType.BACK)
        )

        for (dir in directions) {
            addModelFace(
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

    fun addModelFace(
        blockDataManager: BlockDataManager,
        verticesList: ArrayList<Float>,
        indicesList: ArrayList<Short>,
        normal: VertexAttribute3.Normal,
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
        val baseIndex = (verticesList.size / BlenderParser.modelMeshParams.stride).toShort()

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

    fun addChunkFace(
        blockDataManager: BlockDataManager,
        verticesList: ArrayList<Float>,
        indicesList: ArrayList<Short>,
        bx: Int, by: Int, bz: Int,
        normal: VertexAttribute3.Normal,
        blockType: BlockType,
        directionType: DirectionType,
        shadow: Float,
        blockExists: (Int, Int, Int) -> Boolean
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

        val ao = ShadowCompilerAO.computeFaceAO(bx, by, bz, nx, ny, nz, blockExists)

        val flip = ao[0] + ao[2] < ao[1] + ao[3]

        val uvs = blockDataManager.faceUVs(directionType, blockType)

        val baseIndex = (verticesList.size / MeshHelper.chunkMeshParams.stride).toShort()

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
            verticesList.add(ao[i])
            verticesList.add(shadow)
        }

        if (flip) {
            indicesList.add((baseIndex + 1).toShort())
            indicesList.add((baseIndex + 2).toShort())
            indicesList.add((baseIndex + 3).toShort())
            indicesList.add((baseIndex + 1).toShort())
            indicesList.add((baseIndex + 3).toShort())
            indicesList.add(baseIndex)
        } else {
            indicesList.add(baseIndex)
            indicesList.add((baseIndex + 1).toShort())
            indicesList.add((baseIndex + 2).toShort())
            indicesList.add(baseIndex)
            indicesList.add((baseIndex + 2).toShort())
            indicesList.add((baseIndex + 3).toShort())
        }
    }

    /**
     * Adds a merged greedy-mesh quad. [quadU] and [quadV] are sizes in block units
     * along [uAxis] and [vAxis] tangent axes.
     */
    fun addGreedyQuad(
        blockDataManager: BlockDataManager,
        verticesList: ArrayList<Float>,
        indicesList: ArrayList<Short>,
        face: GreedyMesher.FaceDirection,
        originX: Int,
        originY: Int,
        originZ: Int,
        quadU: Int,
        quadV: Int,
        uAxis: Int,
        vAxis: Int,
        blockType: BlockType,
        ao: FloatArray,
        shadows: FloatArray,
    ) {
        val nx = face.normal.x
        val ny = face.normal.y
        val nz = face.normal.z

        val positions = greedyCornerPositions(
            face = face,
            originX = originX,
            originY = originY,
            originZ = originZ,
            quadU = quadU,
            quadV = quadV,
        )

        val uvs = mergedGreedyFaceUvs(
            blockDataManager = blockDataManager,
            face = face,
            blockType = blockType,
            quadU = quadU,
            quadV = quadV,
        )

        val flip = ao[0] + ao[2] < ao[1] + ao[3]
        val baseIndex = (verticesList.size / MeshHelper.chunkMeshParams.stride).toShort()

        for (i in 0 until 4) {
            val p = positions[i]
            val uv = uvs[i]
            verticesList.add(p[0])
            verticesList.add(p[1])
            verticesList.add(p[2])
            verticesList.add(nx)
            verticesList.add(ny)
            verticesList.add(nz)
            verticesList.add(uv.x)
            verticesList.add(uv.y)
            verticesList.add(ao[i])
            verticesList.add(shadows[i])
        }

        if (flip) {
            indicesList.add((baseIndex + 1).toShort())
            indicesList.add((baseIndex + 2).toShort())
            indicesList.add((baseIndex + 3).toShort())
            indicesList.add((baseIndex + 1).toShort())
            indicesList.add((baseIndex + 3).toShort())
            indicesList.add(baseIndex)
        } else {
            indicesList.add(baseIndex)
            indicesList.add((baseIndex + 1).toShort())
            indicesList.add((baseIndex + 2).toShort())
            indicesList.add(baseIndex)
            indicesList.add((baseIndex + 2).toShort())
            indicesList.add((baseIndex + 3).toShort())
        }
    }

    private fun greedyCornerPositions(
        face: GreedyMesher.FaceDirection,
        originX: Int,
        originY: Int,
        originZ: Int,
        quadU: Int,
        quadV: Int,
    ): Array<FloatArray> {
        val ox = originX.toFloat()
        val oy = originY.toFloat()
        val oz = originZ.toFloat()
        val su = when (face) {
            GreedyMesher.FaceDirection.POS_Y,
            GreedyMesher.FaceDirection.NEG_Y -> quadV.toFloat()
            else -> quadU.toFloat()
        }
        val sv = when (face) {
            GreedyMesher.FaceDirection.POS_Y,
            GreedyMesher.FaceDirection.NEG_Y -> quadU.toFloat()
            else -> quadV.toFloat()
        }

        return when (face) {
            GreedyMesher.FaceDirection.POS_X -> arrayOf(
                floatArrayOf(ox + 1f, oy + su, oz + sv),
                floatArrayOf(ox + 1f, oy, oz + sv),
                floatArrayOf(ox + 1f, oy, oz),
                floatArrayOf(ox + 1f, oy + su, oz),
            )
            GreedyMesher.FaceDirection.NEG_X -> arrayOf(
                floatArrayOf(ox, oy + su, oz),
                floatArrayOf(ox, oy, oz),
                floatArrayOf(ox, oy, oz + sv),
                floatArrayOf(ox, oy + su, oz + sv),
            )
            GreedyMesher.FaceDirection.POS_Y -> arrayOf(
                floatArrayOf(ox, oy + 1f, oz + sv),
                floatArrayOf(ox + su, oy + 1f, oz + sv),
                floatArrayOf(ox + su, oy + 1f, oz),
                floatArrayOf(ox, oy + 1f, oz),
            )
            GreedyMesher.FaceDirection.NEG_Y -> arrayOf(
                floatArrayOf(ox, oy, oz),
                floatArrayOf(ox + su, oy, oz),
                floatArrayOf(ox + su, oy, oz + sv),
                floatArrayOf(ox, oy, oz + sv),
            )
            GreedyMesher.FaceDirection.POS_Z -> arrayOf(
                floatArrayOf(ox, oy + sv, oz + 1f),
                floatArrayOf(ox, oy, oz + 1f),
                floatArrayOf(ox + su, oy, oz + 1f),
                floatArrayOf(ox + su, oy + sv, oz + 1f),
            )
            GreedyMesher.FaceDirection.NEG_Z -> arrayOf(
                floatArrayOf(ox + su, oy + sv, oz),
                floatArrayOf(ox + su, oy, oz),
                floatArrayOf(ox, oy, oz),
                floatArrayOf(ox, oy + sv, oz),
            )
        }
    }

    private fun mergedGreedyFaceUvs(
        blockDataManager: BlockDataManager,
        face: GreedyMesher.FaceDirection,
        blockType: BlockType,
        quadU: Int,
        quadV: Int,
    ): Array<Vector2> {
        // Atlas stores one tile per block type. Scaling UV by merge size walks across unrelated
        // atlas cells and, with clamp, stretches the whole atlas onto the face.
        // Keep the single-block UVs; the merged quad repeats one tile (same as naive mesh per block).
        return blockDataManager.faceUVs(face.directionType, blockType)
    }
}