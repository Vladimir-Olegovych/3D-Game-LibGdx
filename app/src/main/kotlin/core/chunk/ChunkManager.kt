package core.chunk

import app.feature.game.event.GameEvent
import com.artemis.World
import com.gigapi.core.effects.DisposableEffect
import com.gigapi.core.effects.LaunchedEffect
import com.gigapi.eventbus.EventBus
import com.gigapi.general.Context
import com.gigapi.math.vector.IntVector3
import core.blocks.BlockType
import core.mesh.MeshData
import core.mesh.MeshHelper
import core.noice.PerlinNoise
import core.scope.DispatcherTypes
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class ChunkManager: LaunchedEffect, DisposableEffect {

    companion object {
        const val DRAW_RADIUS = 16
        const val CHUNK_SIZE = 8
        const val CHUNK_HEIGHT = 255
    }

    private val noice = PerlinNoise()
    private val chunkDataMap = HashMap<IntVector3, ChunkData>()
    private val meshDataMap = HashMap<IntVector3, MeshData>()

    private val defaultParallelism = 6
    private val parallelism = Semaphore(defaultParallelism)
    private var generationJob: Job? = null

    private lateinit var eventBus: EventBus
    private lateinit var meshHelper: MeshHelper
    private lateinit var defaultScope: CoroutineScope
    private lateinit var mainScope: CoroutineScope

    override fun launch(context: Context) {
        eventBus = context.getObject()
        meshHelper = context.getObject()
        defaultScope = CoroutineScope(Dispatchers.Default)
        mainScope = CoroutineScope(context.getObject<CoroutineDispatcher>(DispatcherTypes.MAIN))
    }


    fun generateWorld(world: World, playerPosition: IntVector3 = IntVector3()) {
        if(generationJob != null) return
        generationJob = worldGeneration(world, playerPosition)
    }

    private fun worldGeneration(world: World, playerPosition: IntVector3) = mainScope.launch {
        val positions = getPositions()

        val entityMap = positions.map { position ->
            position to world.create()
        }

        val chunkDataJobs = entityMap.map { (position, entityId) ->
            defaultScope.async {
                generateChunkData(position, entityId)
            }
        }
        chunkDataJobs.joinAll()

        val fullChunkDataMap = chunkDataMap.toMap()

        val meshDataJobs = entityMap.map { (position, entityId) ->
            defaultScope.async {
                parallelism.withPermit {
                    generateMeshData(position, entityId, fullChunkDataMap)
                }
            }
        }
        meshDataJobs.joinAll()
        generationJob = null
    }

    private suspend fun generateChunkData(position: IntVector3, entityId: Int) {
        val chunkData = withContext(Dispatchers.Default) {
            val data = ChunkData.create(position, CHUNK_SIZE, CHUNK_HEIGHT)
            setChunkBlocks(data)
            data
        }
        mainScope.launch {
            chunkDataMap[position] = chunkData
            eventBus.sendEvent(GameEvent.OnCreateChunkData(entityId, chunkData))
        }.join()
    }

    private suspend fun generateMeshData(
        position: IntVector3,
        entityId: Int,
        fullChunkDataMap: Map<IntVector3, ChunkData>
    ) {
        val ownChunkData = fullChunkDataMap[position]!!
        val rawMeshData = withContext(Dispatchers.Default) {
            meshHelper.createMesh(fullChunkDataMap, ownChunkData)
        }

        mainScope.launch {
            val meshData = rawMeshData.createMeshData()
            meshDataMap[position] = meshData
            eventBus.sendEvent(GameEvent.OnCreateChunkMeshData(entityId, meshData))
        }.join()
    }

    override fun dispose() {
        meshDataMap.clear()
        chunkDataMap.clear()
        generationJob?.cancel()
        defaultScope.cancel()
        generationJob = null
    }

    private fun getPositions(): List<IntVector3> {
        val mutableList = mutableListOf<IntVector3>()
        for (x in -DRAW_RADIUS .. DRAW_RADIUS) {
            for (y in 0..0) {
                for (z in -DRAW_RADIUS .. DRAW_RADIUS) {
                    val pos = IntVector3(x, y, z)
                    mutableList.add(pos)
                }
            }
        }
        return mutableList
    }

    private fun setChunkBlocks(chunkData: ChunkData) {
        val scale = 0.055
        val amplitude = (chunkData.chunkHeight * 0.35).toInt()
        val baseHeight = chunkData.chunkHeight / 2

        for (x in 0 until chunkData.chunkWidth) {
            for (z in 0 until chunkData.chunkWidth) {
                val worldX = chunkData.position.x * chunkData.chunkWidth + x
                val worldZ = chunkData.position.z * chunkData.chunkWidth + z

                val noiseValue = noice.warp(worldX * scale, worldZ * scale)
                val surfaceHeight = (baseHeight + noiseValue * amplitude).toInt()
                    .coerceIn(0, chunkData.chunkHeight - 1)

                for (y in 0 until chunkData.chunkHeight) {
                    val block = when {
                        y < surfaceHeight - 4 -> BlockType.STONE
                        y < surfaceHeight -> BlockType.DIRT
                        y == surfaceHeight -> BlockType.GRASS
                        else -> BlockType.AIR
                    }
                    chunkData.setBlockByLocal(block, x, y, z)
                }
            }
        }
    }
}