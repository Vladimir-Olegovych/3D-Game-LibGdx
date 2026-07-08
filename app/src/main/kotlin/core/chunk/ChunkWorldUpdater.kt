package core.chunk

import app.feature.game.event.ChunkEvent
import app.feature.game.event.EventBusTypes
import app.feature.game.event.GameEvent
import com.badlogic.gdx.math.Vector3
import com.gigapi.effects.DisposableEffect
import com.gigapi.effects.LaunchedEffect
import com.gigapi.coruntines.DeltaUpdater
import com.gigapi.eventbus.EventBus
import com.gigapi.eventbus.annotation.BusEvent
import com.gigapi.general.Context
import com.gigapi.math.vector.IntVector3
import com.gigapi.math.vector.roundToFloat
import com.gigapi.mesh.MeshData
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

class ChunkWorldUpdater : LaunchedEffect, DisposableEffect, DeltaUpdater(1 / 60F, Dispatchers.Default) {

    companion object {
        const val DRAW_RADIUS_X = 16
        const val DRAW_RADIUS_Y = 8
        const val CHUNK_SIZE = 16
        const val CHUNK_HEIGHT = 16
    }

    private val workerCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
    private val parallelismMesh = Semaphore(workerCount)
    private val parallelismChunk = Semaphore(workerCount)
    private val parallelismStart = Semaphore(1000)

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

    @Volatile
    private var isFirstGeneration = true
    @Volatile
    private var activeGenerationPosition: IntVector3? = null
    @Volatile
    private var queuedGenerationPosition: IntVector3? = null

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
        chunkEventBus.process()
    }

    override fun dispose() {
        chunkDataPositionToEntityId.clear()
        chunkMeshPositionToEntityId.clear()
        meshDataMap.clear()
        chunkDataMap.clear()
        activeGenerationPosition = null
        queuedGenerationPosition = null
        chunkEventBus.clear()
    }

    @BusEvent
    fun loadAdditionalChunksRequest(event: ChunkEvent.LoadAdditionalChunksRequest) {
        synchronized(this) {
            if (event.playerPosition == activeGenerationPosition || event.playerPosition == queuedGenerationPosition) return
            if (activeGenerationPosition != null) {
                queuedGenerationPosition = event.playerPosition
                return
            }
            startGeneration(event.playerPosition)
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
                    async(Dispatchers.Default) {
                        if (isFirstGeneration) parallelismStart.withPermit {
                            generateChunkData(position)
                        } else parallelismChunk.withPermit {
                            generateChunkData(position)
                        }
                    }
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
                            removedChunkMeshes.add(pendingChunkPosition)
                            affectedExistingChunks.add(pendingChunkPosition)
                        }
                    }
                }
            }

            val meshJobs = coroutineScope {
                affectedExistingChunks.map { chunkPos ->
                    async(Dispatchers.Default) {
                        parallelismMesh.withPermit {
                            val updateChunkData = chunkDataMap[chunkPos] ?: return@async
                            val updateChunkEntityId = chunkDataPositionToEntityId[chunkPos] ?: return@async

                            val rawMeshData = meshHelper.createMesh(chunkDataMap, updateChunkData)
                            val meshData = withContext(mainScope.coroutineContext) {
                                rawMeshData.createMeshData(MeshHelper.chunkMeshParams)
                            }
                            meshDataMap[chunkPos] = meshData
                            physicsEventBus.sendEvent(GameEvent.OnUpdateChunkData(updateChunkEntityId, updateChunkData))
                            mainEventBus.sendEvent(GameEvent.OnUpdateChunkMeshData(updateChunkEntityId, meshData))
                        }
                    }
                }
            }
            meshJobs.awaitAll()
            chunkEventBus.sendEvent(ChunkEvent.OnDrawResponse(event.generationData))
        }
    }
    @BusEvent
    fun chunkDrawResponse(event: ChunkEvent.OnDrawResponse) {
        lifecycleScope.launch {
            val meshJobs = coroutineScope {
                event.generationData.chunkPositionsToCreate.map { position ->
                    async(Dispatchers.Default) {
                        if (isFirstGeneration) parallelismStart.withPermit {
                            drawChunkData(position)
                        } else parallelismMesh.withPermit {
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
            val searchPosition = event.generationData.playerPosition.roundToFloat()
            val position = findSpawnPosition(searchPosition)?: searchPosition
            mainEventBus.sendEvent(ChunkEvent.GameWorldStarted(position))
            isFirstGeneration = false
        }
        removedChunkDates.clear()
        removedChunkMeshes.clear()

        val nextPosition = synchronized(this) {
            activeGenerationPosition = null
            queuedGenerationPosition?.also { queuedGenerationPosition = null }
        }
        if (nextPosition != null && nextPosition != event.generationData.playerPosition) {
            synchronized(this) {
                if (activeGenerationPosition == null) {
                    startGeneration(nextPosition)
                } else {
                    queuedGenerationPosition = nextPosition
                }
            }
        }
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

                val meshData = mainScope.async {
                    rawMeshData.createMeshData(MeshHelper.chunkMeshParams)
                }.await()
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
        chunkEventBus.sendEventNow(ChunkEvent.OnSetBlock(
            chunkData, BlockType.AIR, blockPosition
        ))
        mainEventBus.sendEvent(GameEvent.OnBlockRemoved(currentBlock, chunkPosition, blockPosition))
    }

    private fun findSpawnPosition(centerPosition: Vector3): Vector3? {
        val startChunkPos = WorldDataHelper.getChunkPositionFromWorldPosition(centerPosition)
        for (y in -4 + startChunkPos.y .. 4 + startChunkPos.y) {
            val chunkPosition = IntVector3(
                startChunkPos.x,
                y,
                startChunkPos.z
            )
            val chunkData = chunkDataMap[chunkPosition] ?: continue

            for (localX in 0 until chunkData.chunkWidth) {
                for (localZ in 0 until chunkData.chunkWidth) {
                    for (localY in 0 until chunkData.chunkHeight) {
                        val block = chunkData.getBlockByLocal(localX, localY, localZ)
                        if (block != BlockType.AIR) {
                            val aboveBlock = if (localY < chunkData.chunkHeight - 1) {
                                chunkData.getBlockByLocal(localX, localY + 1, localZ)
                            } else {
                                val aboveChunkPos = IntVector3(chunkPosition.x, chunkPosition.y + 1, chunkPosition.z)
                                val aboveChunk = chunkDataMap[aboveChunkPos]
                                aboveChunk?.getBlockByLocal(localX, 0, localZ)
                            }

                            val aboveAboveBlock = if (localY < chunkData.chunkHeight - 2) {
                                chunkData.getBlockByLocal(localX, localY + 2, localZ)
                            } else if (localY == chunkData.chunkHeight - 2) {
                                val aboveChunkPos = IntVector3(chunkPosition.x, chunkPosition.y + 1, chunkPosition.z)
                                val aboveChunk = chunkDataMap[aboveChunkPos]
                                aboveChunk?.getBlockByLocal(localX, 0, localZ)
                            } else {
                                val aboveChunkPos = IntVector3(chunkPosition.x, chunkPosition.y + 1, chunkPosition.z)
                                val aboveChunk = chunkDataMap[aboveChunkPos]
                                aboveChunk?.getBlockByLocal(localX, 1, localZ)
                            }

                            if (aboveBlock == BlockType.AIR && aboveAboveBlock == BlockType.AIR) {
                                val worldX = chunkPosition.x * chunkData.chunkWidth + localX
                                val worldY = chunkPosition.y * chunkData.chunkHeight + localY + 2
                                val worldZ = chunkPosition.z * chunkData.chunkWidth + localZ

                                return Vector3(worldX.toFloat(), worldY.toFloat(), worldZ.toFloat())
                            }
                        }
                    }
                }
            }
        }

        return null
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

        mainScope.async {
            val meshData = rawMeshData.createMeshData(MeshHelper.chunkMeshParams)
            meshDataMap[position] = meshData
            mainEventBus.sendEvent(GameEvent.OnCreateChunkMeshData(meshEntityId, meshData))
            physicsEventBus.sendEvent(GameEvent.OnCreateChunkRigidBody(meshEntityId, chunkData))
        }.await()
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

    private fun startGeneration(playerPosition: IntVector3) {
        activeGenerationPosition = playerPosition
        val generationData = getWorldGenerationData(playerPosition)
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

}