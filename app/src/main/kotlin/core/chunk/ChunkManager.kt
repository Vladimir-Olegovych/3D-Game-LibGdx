package core.chunk

import app.feature.game.event.GameEvent
import com.artemis.World
import com.gigapi.core.effects.DisposableEffect
import com.gigapi.core.effects.LaunchedEffect
import com.gigapi.eventbus.EventBus
import com.gigapi.eventbus.annotation.BusEvent
import com.gigapi.general.Context
import com.gigapi.math.vector.IntVector3
import core.blocks.BlockType
import core.chunk.world.WorldDataHelper
import core.chunk.world.WorldGenerationData
import core.mesh.MeshData
import core.mesh.MeshHelper
import core.noice.PerlinNoise
import core.scope.DispatcherTypes
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.random.Random


class ChunkManager: LaunchedEffect, DisposableEffect {

    companion object {
        const val DRAW_RADIUS = 32
        const val CHUNK_SIZE = 8
        const val CHUNK_HEIGHT = 255
    }

    private val noice = PerlinNoise(Random.nextInt())

    private val chunkDataPositionToEntityId = HashMap<IntVector3, Int>()
    private val chunkMeshPositionToEntityId = HashMap<IntVector3, Int>()

    private val chunkDataMap = HashMap<IntVector3, ChunkData>()
    private val meshDataMap = HashMap<IntVector3, MeshData>()

    private val parallelismMesh = Semaphore(12)
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
        eventBus.registerHandler(this)
    }

    override fun dispose() {
        chunkDataPositionToEntityId.clear()
        chunkMeshPositionToEntityId.clear()

        meshDataMap.clear()
        chunkDataMap.clear()

        generationJob?.cancel()
        defaultScope.cancel()
        generationJob = null
    }

    @BusEvent
    fun loadAdditionalChunks(event: GameEvent.LoadAdditionalChunksRequest) {
        tryGenerateWorld(event.world, event.playerPosition)
    }

    private fun tryGenerateWorld(world: World, playerPosition: IntVector3 = IntVector3()) {
        if(generationJob != null) return
        generationJob = worldGeneration(world, playerPosition)
    }

    private fun worldGeneration(world: World, playerPosition: IntVector3) = defaultScope.launch {
        //New world generation data by playerPosition
        val worldGenerationData = getWorldGenerationData(playerPosition)?: run {
            generationJob = null
            return@launch
        }
        //Update new data for map
        mainScope.launch {
            worldGenerationData.chunkPositionsToRemove.forEach {
                if(chunkMeshPositionToEntityId[it] != null) {
                    eventBus.sendEvent(GameEvent.OnRemoveChunkMeshData(chunkMeshPositionToEntityId[it]!!))
                    eventBus.sendEvent(GameEvent.OnRemoveChunkRigidBody(chunkMeshPositionToEntityId[it]!!))
                    meshDataMap.remove(it)
                    chunkMeshPositionToEntityId.remove(it)
                }
            }
            worldGenerationData.chunkDataToRemove.forEach {
                if(chunkDataPositionToEntityId[it] != null) {
                    eventBus.sendEvent(GameEvent.OnRemoveChunkData(chunkDataPositionToEntityId[it]!!))
                    chunkDataMap.remove(it)
                    chunkDataPositionToEntityId.remove(it)
                }
            }
        }.join()
        //Creating new entity ids for chunks
        val resultGenerationData = mainScope.async {
            val dataMap = HashMap<IntVector3, Int>()
            for(position in  worldGenerationData.chunkDataPositionsToCreate) {
                val entityId = world.create()
                dataMap[position] = entityId
                chunkDataPositionToEntityId[position] = entityId
            }
            val meshMap = HashMap<IntVector3, Int>()
            for(position in  worldGenerationData.chunkPositionsToCreate) {
                val entityId = chunkDataPositionToEntityId[position]!!
                meshMap[position] = entityId
                chunkMeshPositionToEntityId[position] = entityId
            }
            return@async Pair(dataMap, meshMap)
        }.await()
        //ChunkData jobs
        val chunkDataJobs = resultGenerationData.first.map { (position, entityId) ->
            defaultScope.async {
                //parallelismChunk.withPermit {
                    generateChunkData(position, entityId)
                //}
            }
        }
        chunkDataJobs.joinAll()
        //MeshData jobs
        val fullChunkDataMap = chunkDataMap.toMap()
        val meshDataJobs = resultGenerationData.second.map { (position, entityId) ->
            defaultScope.async {
                parallelismMesh.withPermit {
                    generateMeshData(position, entityId, fullChunkDataMap)
                }
            }
        }
        meshDataJobs.joinAll()
        //End Generation Callback
        mainScope.launch { generationJob = null }.join()
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
            eventBus.sendEvent(GameEvent.OnCreateChunkRigidBody(entityId, chunkDataMap[position]!!))
        }.join()
    }

    private fun getWorldGenerationData(playerPosition: IntVector3): WorldGenerationData? {
        val allChunkPositionsNeeded = WorldDataHelper.getChunkPositionsAroundPlayer(playerPosition)

        val allChunkDataPositionsNeeded = WorldDataHelper.getDataPositionsAroundPlayer(playerPosition)

        val chunkPositionsToCreate = WorldDataHelper.selectPositionsToCreate(meshDataMap, allChunkPositionsNeeded, playerPosition)
        val chunkDataPositionsToCreate = WorldDataHelper.selectDataPositionsToCreate(chunkDataMap, allChunkDataPositionsNeeded, playerPosition)

        if(chunkPositionsToCreate.isEmpty() || chunkDataPositionsToCreate.isEmpty()) {
            return null
        }

        val chunkPositionsToRemove = WorldDataHelper.getUnneededChunks(meshDataMap, allChunkPositionsNeeded)
        val chunkDataToRemove = WorldDataHelper.getUnneededData(chunkDataMap, allChunkDataPositionsNeeded)

        val data = WorldGenerationData(
            chunkPositionsToCreate = chunkPositionsToCreate,
            chunkDataPositionsToCreate = chunkDataPositionsToCreate,
            chunkPositionsToRemove = chunkPositionsToRemove,
            chunkDataToRemove = chunkDataToRemove
        )
        return data
    }

    private fun setChunkBlocks(chunkData: ChunkData) {
        val scale = 0.0015
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
