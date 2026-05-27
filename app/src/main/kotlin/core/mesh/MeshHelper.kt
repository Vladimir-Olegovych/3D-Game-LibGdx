package core.mesh

import com.gigapi.core.effects.LaunchedEffect
import com.gigapi.general.Context
import com.gigapi.math.vector.IntVector3
import core.blocks.BlockDataManager
import core.blocks.BlockType
import core.chunk.ChunkData

class MeshHelper: LaunchedEffect {

    companion object {
        val directions = listOf(
            Direction( 1, 0, 0, VertexAttribute.Normal(1F, 0f, 0f), DirectionType.RIGHT),
            Direction(-1, 0, 0, VertexAttribute.Normal(-1F, 0f, 0f), DirectionType.LEFT),
            Direction( 0, 1, 0, VertexAttribute.Normal(0f, 1F, 0f), DirectionType.UP),
            Direction( 0,-1, 0, VertexAttribute.Normal(0f,-1F, 0f), DirectionType.DOWN),
            Direction( 0, 0, 1, VertexAttribute.Normal(0f, 0f, 1F), DirectionType.FRONT),
            Direction( 0, 0,-1, VertexAttribute.Normal(0f, 0f,-1F), DirectionType.BACK)
        )
    }

    private lateinit var blockDataManager: BlockDataManager

    override fun launch(context: Context) {
        blockDataManager = context.getObject()
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
                            nx, ny, nz, w, h
                        )
                        if (neighborBlock == BlockType.AIR) {
                            MeshUtils.addFace(
                                blockDataManager = blockDataManager,
                                verticesList = verticesList,
                                indicesList = indicesList,
                                x, y, z,
                                normal = dir.normal,
                                blockType = block,
                                directionType = dir.directionType
                            )
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
}