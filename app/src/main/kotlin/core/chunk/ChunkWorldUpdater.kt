package core.chunk

import app.feature.game.event.ChunkEvent
import app.feature.game.event.EventBusTypes
import app.feature.game.event.GameEvent
import com.gigapi.core.effects.DisposableEffect
import com.gigapi.core.effects.LaunchedEffect
import com.gigapi.coruntines.DeltaUpdater
import com.gigapi.eventbus.EventBus
import com.gigapi.eventbus.annotation.BusEvent
import com.gigapi.general.Context
import com.gigapi.math.vector.IntVector3
import com.gigapi.screens.mesh.MeshData
import core.blocks.BlockType
import core.bullet.raycast.RayCastTypes
import core.chunk.world.ChunkHelper
import core.chunk.world.WorldDataHelper
import core.chunk.world.WorldGenerationData
import core.math.createMatrixForChunk
import core.mesh.MeshHelper
import core.scope.DispatcherTypes
import core.terrain.TerrainGenerator
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class ChunkWorldUpdater : LaunchedEffect, DisposableEffect, DeltaUpdater(1 / 60F, Dispatchers.Default) {

    companion object {
        const val DRAW_RADIUS_X = 16
        const val DRAW_RADIUS_Y = 12
        const val CHUNK_SIZE = 16
        const val CHUNK_HEIGHT = 16
    }

    private val maxTasks = 2
    private val parallelismMesh = Semaphore(64)

    private val chunkDataPositionToEntityId = ConcurrentHashMap<IntVector3, Int>()
    private val chunkMeshPositionToEntityId = ConcurrentHashMap<IntVector3, Int>()
    private val chunkDataMap = ConcurrentHashMap<IntVector3, ChunkData>()
    private val meshDataMap = ConcurrentHashMap<IntVector3, MeshData>()
    private val removedChunkDates = ConcurrentHashMap.newKeySet<IntVector3>()
    private val removedChunkMeshes = ConcurrentHashMap.newKeySet<IntVector3>()

    private lateinit var mainEventBus: EventBus
    private lateinit var chunkEventBus: EventBus
    private lateinit var physicsEventBus: EventBus
    private lateinit var meshHelper: MeshHelper
    private lateinit var terrainGenerator: TerrainGenerator
    private lateinit var mainScope: CoroutineScope

    private var isFirstGeneration = true

    private val generateRequests = ConcurrentLinkedQueue<IntVector3>()

    override fun launch(context: Context) {
        mainEventBus = context.getObject(EventBusTypes.MAIN_EVENT_BUS)
        chunkEventBus = context.getObject(EventBusTypes.CHUNK_EVENT_BUS)
        physicsEventBus = context.getObject(EventBusTypes.PHYSICS_EVENT_BUS)
        meshHelper = context.getObject()
        terrainGenerator = context.getObject()
        mainScope = CoroutineScope(context.getObject<CoroutineDispatcher>(DispatcherTypes.MAIN))
        chunkEventBus.registerHandler(this)
        mainEventBus.registerHandler(this)
    }

    override fun create() {

    }

    override fun update(deltaTime: Float) {
        val swap = chunkEventBus.getEventsSwap()
        chunkEventBus.processSwap(swap, ChunkEvent.LoadAdditionalChunksRequest::class.java)
        chunkEventBus.processSwap(swap, ChunkEvent.ChunkEntitiesResponse::class.java)
        chunkEventBus.processSwap(swap, ChunkEvent.OnGenerateResponse::class.java)
        chunkEventBus.processSwap(swap, ChunkEvent.OnAcceptPendingResponse::class.java)
        chunkEventBus.processSwap(swap, ChunkEvent.OnDrawResponse::class.java)
        chunkEventBus.processSwap(swap, ChunkEvent.OnFinalizeResponse::class.java)
        chunkEventBus.processSwap(swap, ChunkEvent.OnSetBlock::class.java)
    }

    override fun dispose() {
        chunkDataPositionToEntityId.clear()
        chunkMeshPositionToEntityId.clear()
        meshDataMap.clear()
        chunkDataMap.clear()
        chunkEventBus.clear()
    }

    @BusEvent
    fun loadAdditionalChunksRequest(event: ChunkEvent.LoadAdditionalChunksRequest) {
        if (generateRequests.isNotEmpty() || generateRequests.contains(event.playerPosition)) return
        generateRequests.add(event.playerPosition)

        val generationData = getWorldGenerationData(event.playerPosition)
        mainEventBus.sendEvent(ChunkEvent.ChunkEntitiesRequest(generationData))
        for (position in generationData.chunkDataPositionsToCreate) {
            val chunkData = ChunkData.create(position, CHUNK_SIZE, CHUNK_HEIGHT)
            chunkData.status = ChunkStatus.GENERATION
            chunkDataMap[position] = chunkData
        }
        for (position in generationData.chunkPositionsToCreate) {
            val meshData = MeshData(null)
            meshDataMap[position] = meshData
        }
    }

    @BusEvent
    fun chunkEntitiesResponse(event: ChunkEvent.ChunkEntitiesResponse) {
        val generationData = event.generationData

        generationData.chunkPositionsToRemove.forEach { pos ->
            removedChunkMeshes.add(pos)
            chunkMeshPositionToEntityId[pos]?.let { entityId ->
                mainEventBus.sendEvent(GameEvent.OnRemoveChunkMeshData(entityId))
                physicsEventBus.sendEvent(GameEvent.OnRemoveRigidBody(entityId))
                meshDataMap.remove(pos)
                chunkMeshPositionToEntityId.remove(pos)
            }
        }
        generationData.chunkDataToRemove.forEach { pos ->
            removedChunkDates.add(pos)
            chunkDataPositionToEntityId[pos]?.let { entityId ->
                mainEventBus.sendEvent(GameEvent.OnRemoveChunkData(entityId))
                chunkDataMap.remove(pos)
                chunkDataPositionToEntityId.remove(pos)
            }
        }

        val entities = event.entities
        for (pos in generationData.chunkDataPositionsToCreate) {
            val entityId = entities[pos]?: continue
            chunkDataPositionToEntityId[pos] = entityId
        }
        for (pos in generationData.chunkPositionsToCreate) {
            var entityId = chunkDataPositionToEntityId[pos]
            if (entityId == null) {
                entityId = entities[pos]?: continue
                chunkDataPositionToEntityId[pos] = entityId
            }
            chunkMeshPositionToEntityId[pos] = entityId
        }

        chunkEventBus.sendEvent(ChunkEvent.OnGenerateResponse(event.generationData))
    }


    @BusEvent
    fun chunkGenerationResponse(event: ChunkEvent.OnGenerateResponse) {
        lifecycleScope.launch(Dispatchers.Default) {
            val dataJobs = coroutineScope {
                event.generationData.chunkDataPositionsToCreate.map { position ->
                    async(Dispatchers.Default) { generateChunkData(position) }
                }
            }
            dataJobs.awaitAll()
            chunkEventBus.sendEvent(ChunkEvent.OnAcceptPendingResponse(event.generationData))
        }
    }

    @BusEvent
    fun chunkPendingGenerationResponse(event: ChunkEvent.OnAcceptPendingResponse) {
        lifecycleScope.launch {
            val affectedExistingChunks = mutableSetOf<IntVector3>()

            for ((_, chunkData) in chunkDataMap) {
                val pendingMap = chunkData.pendingBlocks
                val iterator = pendingMap.iterator()
                while (iterator.hasNext()) {
                    val pending = iterator.next()
                    val pendingChunkPosition = WorldDataHelper.getChunkPositionFromBlockCoords(pending.key)
                    val pendingChunkData = chunkDataMap[pendingChunkPosition] ?: continue
                    val localBlockPosition = ChunkHelper.getLocalPosition(pending.key, pendingChunkPosition)
                    if (pendingChunkData.setBlockByLocal(pending.value, localBlockPosition)) {
                        iterator.remove()
                        if (pendingChunkData.status != ChunkStatus.GENERATION &&
                            pendingChunkPosition !in event.generationData.chunkPositionsToCreate &&
                            meshDataMap[pendingChunkPosition] != null
                        ) {
                            affectedExistingChunks.add(pendingChunkPosition)
                        }
                    }
                }
            }

            for (chunkPos in affectedExistingChunks) {
                val updateChunkData = chunkDataMap[chunkPos] ?: continue
                val updateChunkEntityId = chunkDataPositionToEntityId[chunkPos] ?: continue

                val rawMeshData = meshHelper.createMesh(chunkDataMap, updateChunkData)
                val meshData = withContext(mainScope.coroutineContext) {
                    rawMeshData.createMeshData()
                }
                meshDataMap[chunkPos] = meshData
                physicsEventBus.sendEvent(GameEvent.OnUpdateChunkData(updateChunkEntityId, updateChunkData))
                mainEventBus.sendEvent(GameEvent.OnUpdateChunkMeshData(updateChunkEntityId, meshData))
            }

            chunkEventBus.sendEvent(ChunkEvent.OnDrawResponse(event.generationData))
        }
    }
    @BusEvent
    fun chunkDrawResponse(event: ChunkEvent.OnDrawResponse) {
        lifecycleScope.launch {
            val meshJobs = coroutineScope {
                event.generationData.chunkPositionsToCreate.map { position ->
                    async(Dispatchers.Default) {
                        parallelismMesh.withPermit {
                            drawChunkData(position)
                        }
                    }
                }
            }
            meshJobs.awaitAll()
            chunkEventBus.sendEvent(ChunkEvent.OnFinalizeResponse(event.generationData))
        }
    }

    @BusEvent
    fun chunkFinalizeResponse(event: ChunkEvent.OnFinalizeResponse) {
        if (isFirstGeneration) {
            mainEventBus.sendEvent(ChunkEvent.GameWorldStarted)
            isFirstGeneration = false
        }
        generateRequests.remove(event.generationData.playerPosition)
        removedChunkDates.clear()
        removedChunkMeshes.clear()
    }

    @BusEvent
    fun setBlock(event: ChunkEvent.OnSetBlock) {
        val chunkData = event.chunkData
        if (chunkData.status == ChunkStatus.GENERATION) return
        val blockPosition = event.position
        chunkData.setBlockByLocal(event.blockType, blockPosition)

        lifecycleScope.launch {
            val neighboursToUpdate = WorldDataHelper.getEdgeNeighbourChunks(chunkData, blockPosition, chunkDataMap)
            val chunksToUpdate = (listOf(chunkData.position) + neighboursToUpdate.map { it.position }).toSet()

            for (chunkPos in chunksToUpdate) {
                val updateChunkData = chunkDataMap[chunkPos] ?: continue
                if (updateChunkData.status == ChunkStatus.GENERATION) continue
                val updateChunkEntityId = chunkDataPositionToEntityId[chunkPos] ?: continue

                val rawMeshData = meshHelper.createMesh(chunkDataMap, updateChunkData)

                val meshData = withContext(mainScope.coroutineContext) {
                    rawMeshData.createMeshData()
                }
                meshDataMap[chunkPos] = meshData
                physicsEventBus.sendEvent(GameEvent.OnUpdateChunkData(updateChunkEntityId, updateChunkData))
                mainEventBus.sendEvent(GameEvent.OnUpdateChunkMeshData(updateChunkEntityId, meshData))
            }
        }
    }

    @BusEvent
    fun onRayCastResult(event: GameEvent.OnRayCastResult) {
        if (event.requestId != RayCastTypes.CHUNK_RAY_CAST || !event.hasHit) return

        val offset = event.direction.nor().scl(0.5f)
        val hitPoint = event.hitPoint.add(offset)

        val chunkPosition = WorldDataHelper.getChunkPositionFromWorldPosition(hitPoint)
        val chunkData = chunkDataMap[chunkPosition] ?: return
        val blockPosition = ChunkHelper.getBlockPositionFromWorldPosition(hitPoint)
        val currentBlock = chunkData.getBlockByLocal(blockPosition)

        if (currentBlock == BlockType.AIR) return
        chunkEventBus.sendEvent(ChunkEvent.OnSetBlock(
            chunkData, BlockType.AIR, blockPosition
        ))
    }

    private suspend fun generateChunkData(position: IntVector3) {
        if (removedChunkDates.contains(position)) return
        val chunkData = chunkDataMap[position]?: return
        val chunkEntityId = chunkDataPositionToEntityId[position]?: return
        terrainGenerator.generateChunkData(chunkData)
        chunkData.status = ChunkStatus.CREATED
        mainEventBus.sendEvent(
            GameEvent.OnCreateChunkTransform(
                chunkEntityId = chunkEntityId,
                transform = createMatrixForChunk(chunkData)
            )
        )
    }

    private suspend fun drawChunkData(position: IntVector3) {
        if (removedChunkDates.contains(position) || removedChunkMeshes.contains(position)) return
        val chunkData = chunkDataMap[position]?: return
        val meshEntityId = chunkMeshPositionToEntityId[position]?: return
        val rawMeshData = meshHelper.createMesh(chunkDataMap, chunkData)
        if (rawMeshData.isEmpty()) return

        withContext(mainScope.coroutineContext) {
            val meshData = rawMeshData.createMeshData()
            meshDataMap[position] = meshData
            mainEventBus.sendEvent(GameEvent.OnCreateChunkMeshData(meshEntityId, meshData))
            physicsEventBus.sendEvent(GameEvent.OnCreateChunkRigidBody(meshEntityId, chunkData))
        }
    }

    private fun getWorldGenerationData(playerPosition: IntVector3): WorldGenerationData {
        val allChunkPositions = WorldDataHelper.getChunkPositionsAroundPlayer(playerPosition)
        val allDataPositions = WorldDataHelper.getDataPositionsAroundPlayer(playerPosition)

        val chunkPositionsToCreate = WorldDataHelper.selectPositionsToCreate(meshDataMap, allChunkPositions, playerPosition)

        val dataPositionsToCreate = WorldDataHelper.selectDataPositionsToCreate(chunkDataMap, allDataPositions, playerPosition)


        val chunkPositionsToRemove = WorldDataHelper.getUnneededChunks(meshDataMap, allChunkPositions)
        val dataToRemove = WorldDataHelper.getUnneededData(chunkDataMap, allDataPositions)

        return WorldGenerationData(
            playerPosition = playerPosition,
            chunkPositionsToCreate = chunkPositionsToCreate,
            chunkDataPositionsToCreate = dataPositionsToCreate,
            chunkPositionsToRemove = chunkPositionsToRemove,
            chunkDataToRemove = dataToRemove
        )
    }

}