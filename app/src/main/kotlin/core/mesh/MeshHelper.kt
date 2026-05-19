package core.mesh

import com.badlogic.gdx.math.Vector2
import com.gigapi.core.effects.LaunchedEffect
import com.gigapi.general.Context
import com.gigapi.math.vector.IntVector3
import core.blocks.BlockDataManager
import core.blocks.BlockType
import core.chunk.ChunkData

class MeshHelper: LaunchedEffect {

    private lateinit var blockDataManager: BlockDataManager

    override fun launch(context: Context) {
        blockDataManager = context.getObject()
    }

    companion object {
        private val directions = listOf(
            Direction( 1, 0, 0, VertexAttribute.Normal(1f, 0f, 0f), DirectionType.RIGHT),
            Direction(-1, 0, 0, VertexAttribute.Normal(-1f, 0f, 0f), DirectionType.LEFT),
            Direction( 0, 1, 0, VertexAttribute.Normal(0f, 1f, 0f), DirectionType.UP),
            Direction( 0,-1, 0, VertexAttribute.Normal(0f,-1f, 0f), DirectionType.DOWN),
            Direction( 0, 0, 1, VertexAttribute.Normal(0f, 0f, 1f), DirectionType.FRONT),
            Direction( 0, 0,-1, VertexAttribute.Normal(0f, 0f,-1f), DirectionType.BACK)
        )
    }

    fun createMesh(chunkMap: Map<IntVector3, ChunkData>, chunkData: ChunkData): RawMeshData {
        val w = chunkData.chunkWidth
        val h = chunkData.chunkHeight

        val verticesList = ArrayList<Float>()
        val indicesList = ArrayList<Short>()

        for (x in 0 until w) {
            for (y in 0 until h) {
                for (z in 0 until w) {
                    val block = chunkData.getBlockByLocal(x, y, z)
                    if (block == BlockType.AIR) continue

                    for (dir in directions) {
                        val nx = x + dir.dx
                        val ny = y + dir.dy
                        val nz = z + dir.dz
                        val neighborBlock = getNeighborBlock(
                            chunkData, chunkMap,
                            x, y, z, nx, ny, nz, w, h
                        )
                        if (neighborBlock == BlockType.AIR) {
                            addFace(verticesList, indicesList, x, y, z, dir.normal, block, dir.directionType)
                        }
                    }
                }
            }
        }

        val vertices = verticesList.toFloatArray()
        val indices = indicesList.toShortArray()

        return RawMeshData(vertices, indices)
    }

    private fun getNeighborBlock(
        currentChunk: ChunkData,
        chunkMap: Map<IntVector3, ChunkData>,
        origX: Int, origY: Int, origZ: Int,
        nx: Int, ny: Int, nz: Int,
        w: Int, h: Int
    ): BlockType {
        if (nx in 0 until w && ny in 0 until h && nz in 0 until w) {
            return currentChunk.getBlockByLocal(nx, ny, nz)
        }

        var chunkOffX = 0
        var chunkOffY = 0
        var chunkOffZ = 0
        var localX = nx
        var localY = ny
        var localZ = nz

        when {
            nx < 0 -> {
                chunkOffX = -1
                localX = nx + w
            }
            nx >= w -> {
                chunkOffX = 1
                localX = nx - w
            }
        }
        when {
            ny < 0 -> {
                chunkOffY = -1
                localY = ny + h
            }
            ny >= h -> {
                chunkOffY = 1
                localY = ny - h
            }
        }
        when {
            nz < 0 -> {
                chunkOffZ = -1
                localZ = nz + w
            }
            nz >= w -> {
                chunkOffZ = 1
                localZ = nz - w
            }
        }

        val neighborChunkPos = IntVector3(
            currentChunk.position.x + chunkOffX,
            currentChunk.position.y + chunkOffY,
            currentChunk.position.z + chunkOffZ
        )
        val neighborChunk = chunkMap[neighborChunkPos]
        return if (neighborChunk != null && localY in 0 until h) {
            neighborChunk.getBlockByLocal(localX, localY, localZ)
        } else {
            BlockType.NOTHING
        }
    }

    private fun addFace(
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
            nz == -1f -> arrayOf(
                floatArrayOf(1f, 1f, 0f),
                floatArrayOf(1f, 0f, 0f),
                floatArrayOf(0f, 0f, 0f),
                floatArrayOf(0f, 1f, 0f),
            )
            nz == 1f -> arrayOf(
                floatArrayOf(0f, 1f, 1f),
                floatArrayOf(0f, 0f, 1f),
                floatArrayOf(1f, 0f, 1f),
                floatArrayOf(1f, 1f, 1f),
            )
            nx == -1f -> arrayOf(
                floatArrayOf(0f, 1f, 0f),
                floatArrayOf(0f, 0f, 0f),
                floatArrayOf(0f, 0f, 1f),
                floatArrayOf(0f, 1f, 1f),
            )
            nx == 1f -> arrayOf(
                floatArrayOf(1f, 1f, 1f),
                floatArrayOf(1f, 0f, 1f),
                floatArrayOf(1f, 0f, 0f),
                floatArrayOf(1f, 1f, 0f),
            )
            ny == -1f -> arrayOf(
                floatArrayOf(0f, 0f, 0f),
                floatArrayOf(1f, 0f, 0f),
                floatArrayOf(1f, 0f, 1f),
                floatArrayOf(0f, 0f, 1f)
            )
            ny == 1f -> arrayOf(
                floatArrayOf(0f, 1f, 1f),
                floatArrayOf(1f, 1f, 1f),
                floatArrayOf(1f, 1f, 0f),
                floatArrayOf(0f, 1f, 0f)
            )
            else -> error("Invalid normal")
        }

        val uvs = faceUVs(directionType, blockType)

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

    private fun faceUVs(directionType: DirectionType, blockType: BlockType): Array<Vector2> {
        val texturePosition = texturePosition(directionType, blockType)
        val tileSizeX = blockDataManager.getTileSizeX()
        val tileSizeY = blockDataManager.getTileSizeY()
        val textureOffset = blockDataManager.getTextureOffset()

        return arrayOf(
            Vector2(
                tileSizeX * texturePosition.x + textureOffset,
                tileSizeY * texturePosition.y + textureOffset
            ),
            Vector2(
                tileSizeX * texturePosition.x + textureOffset,
                tileSizeY * texturePosition.y + tileSizeY - textureOffset
            ),
            Vector2(
                tileSizeX * texturePosition.x + tileSizeX - textureOffset,
                tileSizeY * texturePosition.y + tileSizeY - textureOffset
            ),
            Vector2(
                tileSizeX * texturePosition.x + tileSizeX - textureOffset,
                tileSizeY * texturePosition.y + textureOffset
            )
        )
    }

    private fun texturePosition(directionType: DirectionType, blockType: BlockType): Vector2 {
        val textureDataMap = blockDataManager.getBlockTextureDataMap()
        val textureData = textureDataMap[blockType] ?: error("No texture data for block type: $blockType")

        return when (directionType) {
            DirectionType.UP -> Vector2(textureData.up.x.toFloat(), textureData.up.y.toFloat())
            DirectionType.DOWN -> Vector2(textureData.down.x.toFloat(), textureData.down.y.toFloat())
            else -> Vector2(textureData.side.x.toFloat(), textureData.side.y.toFloat())
        }
    }

    private data class Direction(
        val dx: Int, val dy: Int, val dz: Int,
        val normal: VertexAttribute.Normal,
        val directionType: DirectionType
    )

    private class VertexAttribute {
        data class Normal(val x: Float, val y: Float, val z: Float)
    }

    enum class DirectionType {
        UP, DOWN, LEFT, RIGHT, FRONT, BACK
    }
}