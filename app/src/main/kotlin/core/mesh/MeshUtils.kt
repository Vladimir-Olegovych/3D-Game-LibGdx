package core.mesh

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.gigapi.mesh.MeshParams
import com.gigapi.mesh.RawMeshData
import com.gigapi.mesh.blender.BlenderParser
import core.blocks.BlockDataManager
import core.blocks.BlockType
import core.items.Item
import core.mesh.MeshUtils.createHitboxModel

val rawMeshParams = MeshParams(
    attributes = arrayOf(
        VertexAttribute(VertexAttributes.Usage.Position, 3, "a_Position"),
        VertexAttribute(VertexAttributes.Usage.Normal, 3, "a_Normal"),
    ),
    stride = 6
)

val chunkMeshParams = MeshParams(
    attributes = arrayOf(
        VertexAttribute(VertexAttributes.Usage.Position, 3, "a_Position"),
        VertexAttribute(VertexAttributes.Usage.Normal, 3, "a_Normal"),
        VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "a_TexCoord"),
        VertexAttribute(VertexAttributes.Usage.Generic, 1, "a_AO"),
        VertexAttribute(VertexAttributes.Usage.Generic, 1, "a_Shadow")
    ),
    stride = 10
)

val defaultPlayerHitBox = createHitboxModel(1F, 1.8F)

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

    fun createFaceUvs(region: TextureRegion): Array<Vector2> {
        val textureWidth = region.texture.width.toFloat()
        val textureHeight = region.texture.height.toFloat()

        val epsilonU = 1f / textureWidth
        val epsilonV = 1f / textureHeight

        val left = minOf(region.u, region.u2) + epsilonU
        val right = maxOf(region.u, region.u2) - epsilonU
        val top = minOf(region.v, region.v2) + epsilonV
        val bottom = maxOf(region.v, region.v2) - epsilonV

        return arrayOf(
            Vector2(left, top),
            Vector2(left, bottom),
            Vector2(right, bottom),
            Vector2(right, top)
        )
    }

    fun createBlockMeshData(
        blockDataManager: BlockDataManager,
        blockType: BlockType,
        size: Float = 1F
    ): RawMeshData? {
        if (blockType == BlockType.NOTHING || blockType == BlockType.AIR) return null

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

    fun createItemMeshData(
        atlas: TextureAtlas,
        item: Item,
        size: Float = 0.4f,
        depth: Float = size / 16f,
        resolution: Int = 16,
        alphaThreshold: Int = 128
    ): RawMeshData? {
        val region = atlas.findRegion(item.regionName)
        val atlasRegion = region ?: return null
        val pagePixmap = atlas.textures.first().let {
            it.textureData.prepare()
            it.textureData.consumePixmap()
        }

        try {
            val gridW = resolution.coerceAtLeast(1)
            val gridH = resolution.coerceAtLeast(1)
            val opaque = Array(gridH) { BooleanArray(gridW) }

            for (gy in 0 until gridH) {
                for (gx in 0 until gridW) {
                    val px = atlasRegion.regionX + ((gx + 0.5f) * atlasRegion.regionWidth / gridW).toInt()
                        .coerceIn(0, pagePixmap.width - 1)
                    val py = atlasRegion.regionY + ((gy + 0.5f) * atlasRegion.regionHeight / gridH).toInt()
                        .coerceIn(0, pagePixmap.height - 1)
                    opaque[gy][gx] = (pagePixmap.getPixel(px, py) and 0xff) >= alphaThreshold
                }
            }

            val verticesList = ArrayList<Float>()
            val indicesList = ArrayList<Short>()
            val stride = BlenderParser.modelMeshParams.stride

            val half = size / 2f
            val halfD = depth / 2f
            val cellW = size / gridW
            val cellH = size / gridH

            fun isOpaque(x: Int, y: Int): Boolean =
                x in 0 until gridW && y in 0 until gridH && opaque[y][x]

            fun addQuad(
                positions: Array<FloatArray>,
                nx: Float, ny: Float, nz: Float,
                uvs: Array<FloatArray>
            ) {
                val baseIndex = (verticesList.size / stride).toShort()
                for (i in positions.indices) {
                    val p = positions[i]
                    val uv = uvs[i]
                    verticesList.add(p[0])
                    verticesList.add(p[1])
                    verticesList.add(p[2])
                    verticesList.add(nx)
                    verticesList.add(ny)
                    verticesList.add(nz)
                    verticesList.add(uv[0])
                    verticesList.add(uv[1])
                }
                indicesList.add(baseIndex)
                indicesList.add((baseIndex + 1).toShort())
                indicesList.add((baseIndex + 2).toShort())
                indicesList.add(baseIndex)
                indicesList.add((baseIndex + 2).toShort())
                indicesList.add((baseIndex + 3).toShort())
            }

            for (gy in 0 until gridH) {
                for (gx in 0 until gridW) {
                    if (!opaque[gy][gx]) continue

                    val x0 = -half + gx * cellW
                    val x1 = x0 + cellW
                    // pixmap y=0 is top of texture → +Y
                    val y1 = half - gy * cellH
                    val y0 = y1 - cellH
                    val z0 = -halfD
                    val z1 = halfD

                    val u0 = region.u + gx * (region.u2 - region.u) / gridW
                    val u1 = region.u + (gx + 1) * (region.u2 - region.u) / gridW
                    val v0 = region.v + gy * (region.v2 - region.v) / gridH
                    val v1 = region.v + (gy + 1) * (region.v2 - region.v) / gridH
                    val uC = (u0 + u1) * 0.5f
                    val vC = (v0 + v1) * 0.5f
                    val solidUv = arrayOf(
                        floatArrayOf(uC, vC),
                        floatArrayOf(uC, vC),
                        floatArrayOf(uC, vC),
                        floatArrayOf(uC, vC)
                    )

                    // Front (+Z)
                    addQuad(
                        positions = arrayOf(
                            floatArrayOf(x0, y1, z1),
                            floatArrayOf(x0, y0, z1),
                            floatArrayOf(x1, y0, z1),
                            floatArrayOf(x1, y1, z1)
                        ),
                        nx = 0f, ny = 0f, nz = 1f,
                        uvs = arrayOf(
                            floatArrayOf(u0, v0),
                            floatArrayOf(u0, v1),
                            floatArrayOf(u1, v1),
                            floatArrayOf(u1, v0)
                        )
                    )

                    // Back (-Z)
                    addQuad(
                        positions = arrayOf(
                            floatArrayOf(x1, y1, z0),
                            floatArrayOf(x1, y0, z0),
                            floatArrayOf(x0, y0, z0),
                            floatArrayOf(x0, y1, z0)
                        ),
                        nx = 0f, ny = 0f, nz = -1f,
                        uvs = arrayOf(
                            floatArrayOf(u1, v0),
                            floatArrayOf(u1, v1),
                            floatArrayOf(u0, v1),
                            floatArrayOf(u0, v0)
                        )
                    )

                    // Left (-X) — transparent neighbor or edge
                    if (!isOpaque(gx - 1, gy)) {
                        addQuad(
                            positions = arrayOf(
                                floatArrayOf(x0, y1, z0),
                                floatArrayOf(x0, y0, z0),
                                floatArrayOf(x0, y0, z1),
                                floatArrayOf(x0, y1, z1)
                            ),
                            nx = -1f, ny = 0f, nz = 0f,
                            uvs = solidUv
                        )
                    }

                    // Right (+X)
                    if (!isOpaque(gx + 1, gy)) {
                        addQuad(
                            positions = arrayOf(
                                floatArrayOf(x1, y1, z1),
                                floatArrayOf(x1, y0, z1),
                                floatArrayOf(x1, y0, z0),
                                floatArrayOf(x1, y1, z0)
                            ),
                            nx = 1f, ny = 0f, nz = 0f,
                            uvs = solidUv
                        )
                    }

                    // Up (+Y) — neighbor above in texture (smaller gy)
                    if (!isOpaque(gx, gy - 1)) {
                        addQuad(
                            positions = arrayOf(
                                floatArrayOf(x0, y1, z1),
                                floatArrayOf(x1, y1, z1),
                                floatArrayOf(x1, y1, z0),
                                floatArrayOf(x0, y1, z0)
                            ),
                            nx = 0f, ny = 1f, nz = 0f,
                            uvs = solidUv
                        )
                    }

                    // Down (-Y)
                    if (!isOpaque(gx, gy + 1)) {
                        addQuad(
                            positions = arrayOf(
                                floatArrayOf(x0, y0, z0),
                                floatArrayOf(x1, y0, z0),
                                floatArrayOf(x1, y0, z1),
                                floatArrayOf(x0, y0, z1)
                            ),
                            nx = 0f, ny = -1f, nz = 0f,
                            uvs = solidUv
                        )
                    }
                }
            }

            if (verticesList.isEmpty()) return null

            return RawMeshData(
                vertices = verticesList.toFloatArray(),
                indices = indicesList.toShortArray()
            )
        } finally {
            pagePixmap.dispose()
        }
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

        val baseIndex = (verticesList.size / chunkMeshParams.stride).toShort()

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
}