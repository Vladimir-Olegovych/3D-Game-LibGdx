package core.mesh

import com.gigapi.mesh.RawMeshData
import core.blocks.BlockDataManager
import core.blocks.BlockType
import core.chunk.ChunkData

/**
 * Greedy meshing for voxel chunks (https://0fps.net/2012/06/30/meshing-in-a-minecraft-game/).
 *
 * Coplanar faces with the same block type and shadow value are merged into larger quads.
 * AO and shadow are sampled at each corner of the merged quad.
 */
class GreedyMesher(
    private val blockDataManager: BlockDataManager,
) {

    private data class MaskCell(
        val blockType: BlockType,
        val shadow: Float,
        val blockX: Int,
        val blockY: Int,
        val blockZ: Int,
    )

    enum class FaceDirection(
        val axis: Int,
        val positive: Boolean,
        val normal: VertexAttribute3.Normal,
        val directionType: DirectionType,
    ) {
        POS_X(0, true, VertexAttribute3.Normal(1f, 0f, 0f), DirectionType.RIGHT),
        NEG_X(0, false, VertexAttribute3.Normal(-1f, 0f, 0f), DirectionType.LEFT),
        POS_Y(1, true, VertexAttribute3.Normal(0f, 1f, 0f), DirectionType.UP),
        NEG_Y(1, false, VertexAttribute3.Normal(0f, -1f, 0f), DirectionType.DOWN),
        POS_Z(2, true, VertexAttribute3.Normal(0f, 0f, 1f), DirectionType.FRONT),
        NEG_Z(2, false, VertexAttribute3.Normal(0f, 0f, -1f), DirectionType.BACK),
    }

    fun build(
        chunkMap: Map<com.gigapi.math.vector.IntVector3, ChunkData>,
        chunkData: ChunkData,
    ): RawMeshData {
        val access = ChunkBlockAccess(chunkData, chunkMap, chunkData.chunkWidth, chunkData.chunkHeight)
        val w = access.width
        val h = access.height

        val verticesList = ArrayList<Float>(8192)
        val indicesList = ArrayList<Short>(12288)

        for (face in FaceDirection.entries) {
            meshAxis(access, face, w, h, verticesList, indicesList)
        }

        return RawMeshData(
            vertices = verticesList.toFloatArray(),
            indices = indicesList.toShortArray(),
        )
    }

    private fun meshAxis(
        access: ChunkBlockAccess,
        face: FaceDirection,
        w: Int,
        h: Int,
        verticesList: ArrayList<Float>,
        indicesList: ArrayList<Short>,
    ) {
        val axis = face.axis
        val dims = intArrayOf(w, h, w)
        val uAxis = (axis + 1) % 3
        val vAxis = (axis + 2) % 3
        val du = dims[uAxis]
        val dv = dims[vAxis]
        val mask = arrayOfNulls<MaskCell>(du * dv)

        val q = intArrayOf(0, 0, 0)
        val coord = intArrayOf(0, 0, 0)

        for (slice in -1 until dims[axis]) {
            var n = 0
            for (v in 0 until dv) {
                for (u in 0 until du) {
                    coord[axis] = slice
                    coord[uAxis] = u
                    coord[vAxis] = v

                    q[0] = 0
                    q[1] = 0
                    q[2] = 0
                    q[axis] = 1

                    val blockNeg = access.getBlock(coord[0], coord[1], coord[2])
                    val blockPos = access.getBlock(
                        coord[0] + q[0], coord[1] + q[1], coord[2] + q[2],
                    )
                    val solidNeg = access.isSolid(coord[0], coord[1], coord[2])
                    val solidPos = access.isSolid(
                        coord[0] + q[0], coord[1] + q[1], coord[2] + q[2],
                    )

                    mask[n++] = if (face.positive) {
                        if (solidNeg && !solidPos) {
                            MaskCell(
                                blockType = blockNeg,
                                shadow = access.getShadow(
                                    coord[0] + q[0], coord[1] + q[1], coord[2] + q[2],
                                ),
                                blockX = coord[0],
                                blockY = coord[1],
                                blockZ = coord[2],
                            )
                        } else {
                            null
                        }
                    } else {
                        if (solidPos && !solidNeg) {
                            MaskCell(
                                blockType = blockPos,
                                shadow = access.getShadow(coord[0], coord[1], coord[2]),
                                blockX = coord[0] + q[0],
                                blockY = coord[1] + q[1],
                                blockZ = coord[2] + q[2],
                            )
                        } else {
                            null
                        }
                    }
                }
            }

            greedyMerge(access, face, mask, du, dv, uAxis, vAxis, verticesList, indicesList)
        }
    }

    private fun greedyMerge(
        access: ChunkBlockAccess,
        face: FaceDirection,
        mask: Array<MaskCell?>,
        du: Int,
        dv: Int,
        uAxis: Int,
        vAxis: Int,
        verticesList: ArrayList<Float>,
        indicesList: ArrayList<Short>,
    ) {
        var v = 0
        while (v < dv) {
            var u = 0
            while (u < du) {
                val cell = mask[u + v * du]
                if (cell == null) {
                    u++
                    continue
                }

                var quadU = 1
                while (u + quadU < du && mergesWith(cell, mask[u + quadU + v * du])) {
                    quadU++
                }

                var quadV = 1
                outer@ while (v + quadV < dv) {
                    for (k in 0 until quadU) {
                        if (!mergesWith(cell, mask[u + k + (v + quadV) * du])) break@outer
                    }
                    quadV++
                }

                for (j in 0 until quadV) {
                    for (i in 0 until quadU) {
                        mask[u + i + (v + j) * du] = null
                    }
                }

                val origin = intArrayOf(cell.blockX, cell.blockY, cell.blockZ)
                val ao = computeMergedAo(access, face, origin, quadU, quadV, uAxis, vAxis)
                val shadows = computeMergedShadows(access, face, origin, quadU, quadV, uAxis, vAxis)

                MeshUtils.addGreedyQuad(
                    blockDataManager = blockDataManager,
                    verticesList = verticesList,
                    indicesList = indicesList,
                    face = face,
                    originX = origin[0],
                    originY = origin[1],
                    originZ = origin[2],
                    quadU = quadU,
                    quadV = quadV,
                    uAxis = uAxis,
                    vAxis = vAxis,
                    blockType = cell.blockType,
                    ao = ao,
                    shadows = shadows,
                )

                u += quadU
            }
            v++
        }
    }

    private fun mergesWith(a: MaskCell, b: MaskCell?): Boolean {
        return b != null && a.blockType == b.blockType && a.shadow == b.shadow
    }

    private fun blockAt(
        origin: IntArray,
        uAxis: Int,
        vAxis: Int,
        uOffset: Int,
        vOffset: Int,
        quadU: Int,
        quadV: Int,
    ): IntArray {
        val block = origin.clone()
        block[uAxis] += if (uOffset == 0) 0 else quadU - 1
        block[vAxis] += if (vOffset == 0) 0 else quadV - 1
        return block
    }

    private fun computeMergedAo(
        access: ChunkBlockAccess,
        face: FaceDirection,
        origin: IntArray,
        quadU: Int,
        quadV: Int,
        uAxis: Int,
        vAxis: Int,
    ): FloatArray {
        val nx = face.normal.x
        val ny = face.normal.y
        val nz = face.normal.z
        val blockExists: (Int, Int, Int) -> Boolean = access::blockExists

        fun ao(uOffset: Int, vOffset: Int, vertexIndex: Int): Float {
            val block = blockAt(origin, uAxis, vAxis, uOffset, vOffset, quadU, quadV)
            return ShadowCompilerAO.computeFaceAO(
                block[0], block[1], block[2],
                nx, ny, nz,
                blockExists,
            )[vertexIndex]
        }

        return when (face) {
            FaceDirection.POS_X -> floatArrayOf(
                ao(1, 1, 0),
                ao(0, 1, 1),
                ao(0, 0, 2),
                ao(1, 0, 3),
            )
            FaceDirection.NEG_X -> floatArrayOf(
                ao(0, 1, 0),
                ao(0, 0, 1),
                ao(1, 0, 2),
                ao(1, 1, 3),
            )
            FaceDirection.POS_Y -> floatArrayOf(
                ao(0, 1, 0),
                ao(1, 1, 1),
                ao(1, 0, 2),
                ao(0, 0, 3),
            )
            FaceDirection.NEG_Y -> floatArrayOf(
                ao(0, 0, 0),
                ao(1, 0, 1),
                ao(1, 1, 2),
                ao(0, 1, 3),
            )
            FaceDirection.POS_Z -> floatArrayOf(
                ao(0, 1, 0),
                ao(0, 0, 1),
                ao(1, 0, 2),
                ao(1, 1, 3),
            )
            FaceDirection.NEG_Z -> floatArrayOf(
                ao(1, 1, 0),
                ao(1, 0, 1),
                ao(0, 0, 2),
                ao(0, 1, 3),
            )
        }
    }

    private fun computeMergedShadows(
        access: ChunkBlockAccess,
        face: FaceDirection,
        origin: IntArray,
        quadU: Int,
        quadV: Int,
        uAxis: Int,
        vAxis: Int,
    ): FloatArray {
        fun shadow(uOffset: Int, vOffset: Int): Float {
            val air = blockAt(origin, uAxis, vAxis, uOffset, vOffset, quadU, quadV)
            when (face) {
                FaceDirection.POS_X -> air[0] += 1
                FaceDirection.NEG_X -> air[0] -= 1
                FaceDirection.POS_Y -> air[1] += 1
                FaceDirection.NEG_Y -> air[1] -= 1
                FaceDirection.POS_Z -> air[2] += 1
                FaceDirection.NEG_Z -> air[2] -= 1
            }
            return access.getShadow(air[0], air[1], air[2])
        }

        return when (face) {
            FaceDirection.POS_X -> floatArrayOf(
                shadow(1, 1),
                shadow(0, 1),
                shadow(0, 0),
                shadow(1, 0),
            )
            FaceDirection.NEG_X -> floatArrayOf(
                shadow(0, 1),
                shadow(0, 0),
                shadow(1, 0),
                shadow(1, 1),
            )
            FaceDirection.POS_Y -> floatArrayOf(
                shadow(0, 1),
                shadow(1, 1),
                shadow(1, 0),
                shadow(0, 0),
            )
            FaceDirection.NEG_Y -> floatArrayOf(
                shadow(0, 0),
                shadow(1, 0),
                shadow(1, 1),
                shadow(0, 1),
            )
            FaceDirection.POS_Z -> floatArrayOf(
                shadow(0, 1),
                shadow(0, 0),
                shadow(1, 0),
                shadow(1, 1),
            )
            FaceDirection.NEG_Z -> floatArrayOf(
                shadow(1, 1),
                shadow(1, 0),
                shadow(0, 0),
                shadow(0, 1),
            )
        }
    }
}
