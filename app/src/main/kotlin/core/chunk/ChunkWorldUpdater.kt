package core.chunk

import app.feature.game.event.ChunkEvent
import app.feature.game.event.EventBusTypes
import app.feature.game.event.GameChunkEvent
import app.feature.game.event.PhysicsEvent
import com.gigapi.coruntines.DeltaUpdater
import com.gigapi.effects.DisposableEffect
import com.gigapi.effects.LaunchedEffect
import com.gigapi.eventbus.EventBus
import com.gigapi.eventbus.annotation.BusEvent
import com.gigapi.general.GContext
import com.gigapi.math.vector.IntVector3
import com.gigapi.math.vector.roundToFloat
import com.gigapi.mesh.MeshData
import core.blocks.BlockType
import core.bullet.raycast.RayCastTypes
import core.chunk.world.ChunkHelper
import core.chunk.world.WorldChunksPreparation
import core.chunk.world.WorldDataHelper
import core.chunk.world.WorldGenerationData
import core.light.LightEngine
import core.light.LightProps
import core.math.createMatrixForChunk
import core.mesh.MeshGenerator
import core.mesh.MeshHelper
import core.mesh.chunkMeshParams
import core.scope.DispatcherTypes
import core.terrain.TerrainGenerator
import core.terrain.biome.models.BiomeType
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class ChunkWorldUpdater : LaunchedEffect, DisposableEffect, DeltaUpdater(1 / 60F, Dispatchers.Default) {

    companion object {
        const val DRAW_RADIUS_X = 16
        const val DRAW_RADIUS_Y = 10
        const val CHUNK_SIZE = 16
        const val CHUNK_HEIGHT = 16
        const val DATA_RADIUS_EXTRA = 1
        const val REMOVE_MARGIN = 1

        const val FIRST_BATCH_CHUNKS = 200
        const val BATCH_CHUNKS = 96
        const val BATCH_MESHES = 96
        const val BATCH_TIMEOUT_MS = 15_000L
    }

    private val isFirstGeneration = AtomicBoolean(true)

    private val workerCount = (Runtime.getRuntime().availableProcessors() / 2 + 1).coerceAtLeast(2)

    private val parallelismChunks = Semaphore(workerCount)
    private val parallelismMesh = Semaphore(workerCount)
    private val uploadGate = Semaphore(workerCount)
    private val chunkPoolUpdater = ChunkPoolUpdater()

    private val pendingMeshPositions = ConcurrentHashMap.newKeySet<IntVector3>()
    private val pendingChunkPositions = ConcurrentHashMap.newKeySet<IntVector3>()

    private val lightRemeshPositions = ConcurrentHashMap.newKeySet<IntVector3>()
    private val createdChunkDataMap = ConcurrentHashMap<IntVector3, ChunkData>()
    private val createdMeshDataMap = ConcurrentHashMap<IntVector3, MeshData>()

    private val editRemeshPositions = ConcurrentHashMap.newKeySet<IntVector3>()

    private val remeshRunning = AtomicBoolean(false)
    private val batchInFlight = AtomicBoolean(false)
    private val repeatRequested = AtomicBoolean(false)
    private val batchProgress = AtomicInteger(0)
    @Volatile private var batchStartedAt = 0L
    @Volatile private var lastPlayerPosition: IntVector3? = null

    private var chunkEventBus: EventBus? = null
    private lateinit var lightEngine: LightEngine
    private lateinit var mainEventBus: EventBus
    private lateinit var physicsEventBus: EventBus

    private lateinit var meshGenerator: MeshGenerator
    private lateinit var terrainGenerator: TerrainGenerator
    private lateinit var mainScope: CoroutineScope

    override fun launch(gContext: GContext) {
        chunkEventBus = gContext.getObject(EventBusTypes.CHUNK_EVENT_BUS)
        physicsEventBus = gContext.getObject(EventBusTypes.PHYSICS_EVENT_BUS)
        meshGenerator = gContext.getObject<MeshHelper>()
        terrainGenerator = gContext.getObject()
        mainEventBus = gContext.getObject(EventBusTypes.MAIN_EVENT_BUS)
        mainScope = CoroutineScope(
            gContext.getObject<CoroutineDispatcher>(DispatcherTypes.MAIN)
        )

        chunkEventBus?.registerHandler(this)
        mainEventBus.registerHandler(this)

        LightProps.set(BlockType.LEAVES, opacity = 1)
        //LightProps.set(BlockType.TORCH, opacity = 0, emission = 14)
        LightProps.set(BlockType.OAK_WOODEN_PLANKS, opacity = 0, emission = 14)

        lightEngine = LightEngine(
            w = CHUNK_SIZE,
            h = CHUNK_HEIGHT,
            chunkAt = { x, y, z ->
                createdChunkDataMap[IntVector3(x, y, z)]
            },
            isSkyOpenAbove = { x, y, z ->
                terrainGenerator.isSkyOpenAbove(x, y, z, CHUNK_HEIGHT)
            }
        )
    }

    override fun dispose() {
        createdChunkDataMap.clear()
        createdMeshDataMap.clear()
        pendingMeshPositions.clear()
        pendingChunkPositions.clear()
        chunkPoolUpdater.clear()
        chunkEventBus?.clear()
    }

    override fun create() = Unit

    override fun update(deltaTime: Float) {
        chunkEventBus?.process()
    }

    // ------------------------------------------------------------------ pipeline

    @BusEvent
    fun loadChunksRequest(event: ChunkEvent.LoadAdditionalChunksRequest) {
        lastPlayerPosition = event.playerPosition

        val now = System.currentTimeMillis()
        if (!batchInFlight.compareAndSet(false, true)) {
            if (now - batchStartedAt < BATCH_TIMEOUT_MS) {
                // Батч ещё идёт: запомним, что игрок сместился, и повторим по его окончании.
                repeatRequested.set(true)
                return
            }
        }
        batchStartedAt = now

        val generationData = getWorldGenerationData(event.playerPosition)
        chunkEventBus?.sendEvent(ChunkEvent.ChunksPreparation(generationData))
    }

    @BusEvent
    fun chunksPreparation(event: ChunkEvent.ChunksPreparation) {
        val generationData = event.worldGenerationData
        val player = generationData.playerPosition

        removeOutsideChunks(player)

        val chunkLimit = if (isFirstGeneration.get()) FIRST_BATCH_CHUNKS else BATCH_CHUNKS
        val chunkCandidates = ArrayList<IntVector3>()
        for (position in generationData.allChunkDataPositionsAround) {
            if (createdChunkDataMap.containsKey(position)) continue
            if (pendingChunkPositions.contains(position)) continue
            chunkCandidates.add(position)
        }
        WorldDataHelper.sortChunksByDistance(chunks = chunkCandidates, playerPosition = player)

        val chunkPositionsToCreate = ArrayList<IntVector3>()
        for (position in chunkCandidates) {
            if (chunkPositionsToCreate.size >= chunkLimit) break
            if (pendingChunkPositions.add(position)) chunkPositionsToCreate.add(position)
        }

        val inBatch = HashSet(chunkPositionsToCreate)
        fun available(p: IntVector3) = createdChunkDataMap.containsKey(p) || p in inBatch

        val meshCandidates = ArrayList<IntVector3>()
        for (position in generationData.allMeshDataPositionsAround) {
            if (createdMeshDataMap.containsKey(position)) continue
            if (pendingMeshPositions.contains(position)) continue
            if (!available(position)) continue
            if (!available(IntVector3(position.x + 1, position.y, position.z))) continue
            if (!available(IntVector3(position.x - 1, position.y, position.z))) continue
            if (!available(IntVector3(position.x, position.y, position.z + 1))) continue
            if (!available(IntVector3(position.x, position.y, position.z - 1))) continue
            meshCandidates.add(position)
        }
        WorldDataHelper.sortChunksByDistance(chunks = meshCandidates, playerPosition = player)

        val meshPositionsToCreate = ArrayList<IntVector3>()
        for (position in meshCandidates) {
            if (meshPositionsToCreate.size >= BATCH_MESHES) break
            if (pendingMeshPositions.add(position)) meshPositionsToCreate.add(position)
        }

        if (chunkPositionsToCreate.isEmpty() && meshPositionsToCreate.isEmpty()) {
            finishBatch(allowFollowUp = false)
            return
        }

        val preparation = WorldChunksPreparation(
            playerPosition = player,
            toCreateMeshDates = meshPositionsToCreate,
            toCreateChunkDates = chunkPositionsToCreate
        )

        mainEventBus.sendEvent(GameChunkEvent.OnRequireChunkEntityIds(preparation))
    }

    @BusEvent
    fun chunksGenerating(event: ChunkEvent.ChunksGenerating) = lifecycleScope.launch(Dispatchers.Default) {
        val preparation = event.worldChunksPreparation
        val createdChunks = HashMap<IntVector3, ChunkData>()

        try {
            val points = terrainGenerator.generateBiomePoints(preparation.playerPosition)

            for (position in preparation.toCreateChunkDates) {
                val entityId = event.entityIds[position] ?: continue
                val chunkData = chunkPoolUpdater.createChunk(position)
                chunkData.entityId = entityId
                createdChunks[position] = chunkData
            }

            createdChunks.values.map { chunkData ->
                async(Dispatchers.Default) {
                    parallelismChunks.withPermit { generateChunk(chunkData, points) }
                }
            }.awaitAll()

            createdChunkDataMap.putAll(createdChunks)

            for (chunk in createdChunks.values) {
                val toRemesh = lightEngine.onChunkLoaded(chunk)

                for (affected in toRemesh) {
                    val position = affected.position

                    // Новые меши построятся обычным путём.
                    // Здесь запоминаем только уже существующие.
                    if (createdMeshDataMap.containsKey(position)) {
                        lightRemeshPositions.add(position)
                    }
                }
            }
            if (lightRemeshPositions.isNotEmpty()) scheduleRemesh()
            batchProgress.addAndGet(createdChunks.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            pendingChunkPositions.removeAll(preparation.toCreateChunkDates.toSet())
        }

        chunkEventBus?.sendEvent(ChunkEvent.ChunksMeshing(preparation))
    }

    @BusEvent
    fun chunksMeshing(event: ChunkEvent.ChunksMeshing) = lifecycleScope.launch(Dispatchers.Default) {
        val preparation = event.worldChunksPreparation
        try {
            preparation.toCreateMeshDates.map { position ->
                async(Dispatchers.Default) {
                    parallelismMesh.withPermit { meshChunk(position, remesh = false, rebuildPhysics = false) }
                }
            }.awaitAll()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            pendingMeshPositions.removeAll(preparation.toCreateMeshDates.toSet())
        }
        chunkEventBus?.sendEvent(ChunkEvent.FinalizeCreation(preparation))
    }

    @BusEvent
    fun finalizeCreation(event: ChunkEvent.FinalizeCreation) {
        if (isFirstGeneration.compareAndSet(true, false)) {
            val searchPosition = event.worldChunksPreparation.playerPosition.roundToFloat()
            val position = WorldDataHelper.findSpawnPosition(createdChunkDataMap, searchPosition) ?: searchPosition
            mainEventBus.sendEvent(GameChunkEvent.GameWorldStarted(position))
        }
        finishBatch(allowFollowUp = true)
    }
    @BusEvent
    fun setBlock(event: ChunkEvent.OnSetBlock) {
        fun feedback(removed: BlockType, success: Boolean) {
            mainEventBus.sendEvent(
                ChunkEvent.OnSetBlockFeedBack(
                    owner = event.owner,
                    isPlaceContext = event.isPlaceContext,
                    chunkPosition = event.chunkPosition,
                    blockPosition = event.blockPosition,
                    removedBlockType = removed,
                    setBlockType = event.blockType,
                    isSuccess = success
                )
            )
        }

        val lp = event.blockPosition
        val chunk = createdChunkDataMap[event.chunkPosition]

        if (chunk?.entityId == null || chunk.light == null ||
            lp.x !in 0 until CHUNK_SIZE || lp.y !in 0 until CHUNK_HEIGHT || lp.z !in 0 until CHUNK_SIZE
        ) {
            feedback(BlockType.AIR, false); return
        }

        val old = chunk.getBlockByLocal(lp)
        val allowed = if (event.isPlaceContext) old == BlockType.AIR   // ставим только в воздух
        else old != BlockType.AIR                        // ломаем только твёрдое (+ проверка на неразрушаемые)
        if (!allowed || old == event.blockType) {
            feedback(old, false); return
        }

        // 1) блок (airCount в ChunkData обновится сам)
        chunk.setBlockByLocal(event.blockType, lp)

        // 2) свет (ПОСЛЕ смены блока) — вернёт правленый чанк и затронутых соседей
        val affected = lightEngine.onBlockChanged(chunk, lp.x, lp.y, lp.z)

        // 3) ремеш: правленый чанк — срочно и с коллизией, остальные — только если у них уже есть меш
        editRemeshPositions.add(event.chunkPosition)
        for (c in affected) {
            val p = c.position
            if (p == event.chunkPosition) continue
            if (createdMeshDataMap.containsKey(p)) lightRemeshPositions.add(p)
        }
        scheduleRemesh()

        feedback(old, true)
    }
    @BusEvent
    fun onRayCastResult(event: PhysicsEvent.OnRayCastResult) {
        if (event.requestId != RayCastTypes.CHUNK_DIG_RAY_CAST || !event.hasHit) return

        val offset = event.direction.nor().scl(0.5f)
        val hitPoint = event.hitPoint.add(offset)

        val chunkPosition = WorldDataHelper.getChunkPositionFromWorldPosition(hitPoint)
        if (pendingChunkPositions.contains(chunkPosition)) return
        val chunkData = createdChunkDataMap[chunkPosition] ?: return
        val chunkEntityId = chunkData.entityId ?: return
        val blockPosition = ChunkHelper.getBlockPositionFromWorldPosition(hitPoint)
        val currentBlock = chunkData.getBlockByLocal(blockPosition)

        if (currentBlock == BlockType.AIR) return
        mainEventBus.sendEvent(PhysicsEvent.OnRayCastBlockResult(
            requestId = RayCastTypes.CHUNK_DIG_RAY_CAST,
            chunkEntityId = chunkEntityId,
            blockToSet = BlockType.AIR,
            blockToRemove = currentBlock,
            chunkPosition = chunkPosition,
            blockPosition = blockPosition
        ))
    }

    @BusEvent
    fun onPlaceCastResult(event: PhysicsEvent.OnRayCastResult) {
        if (event.requestId != RayCastTypes.CHUNK_PLACE_RAY_CAST || !event.hasHit) return

        val normal = event.direction.nor()
        val offset = normal.scl(0.2f)
        val hitPoint = event.hitPoint.sub(offset)

        val chunkPosition = WorldDataHelper.getChunkPositionFromWorldPosition(hitPoint)
        if (pendingChunkPositions.contains(chunkPosition)) return
        val chunkData = createdChunkDataMap[chunkPosition] ?: return
        val chunkEntityId = chunkData.entityId ?: return
        val blockPosition = ChunkHelper.getBlockPositionFromWorldPosition(hitPoint)
        val currentBlock = chunkData.getBlockByLocal(blockPosition)

        if (currentBlock != BlockType.AIR) return

        mainEventBus.sendEvent(PhysicsEvent.OnRayCastPlaceBlockResult(
            chunkEntityId = chunkEntityId,
            blockToRemove = currentBlock,
            chunkPosition = chunkPosition,
            blockPosition = blockPosition
        ))
    }

    private fun finishBatch(allowFollowUp: Boolean) {
        val progressed = batchProgress.getAndSet(0) > 0
        batchInFlight.set(false)
        val moved = repeatRequested.getAndSet(false)
        if ((allowFollowUp && progressed) || moved) {
            lastPlayerPosition?.let {
                chunkEventBus?.sendEvent(ChunkEvent.LoadAdditionalChunksRequest(it))
            }
        }
    }

    private fun removeOutsideChunks(playerPosition: IntVector3) {
        val playerChunk = WorldDataHelper.chunkPositionFromBlockCoords(playerPosition)

        val meshRemoveRadiusX = DRAW_RADIUS_X + REMOVE_MARGIN
        val meshRemoveRadiusY = DRAW_RADIUS_Y + REMOVE_MARGIN

        val dataRemoveRadiusX = DRAW_RADIUS_X + DATA_RADIUS_EXTRA + REMOVE_MARGIN
        val dataRemoveRadiusY = DRAW_RADIUS_Y + DATA_RADIUS_EXTRA + REMOVE_MARGIN

        fun outsideMesh(position: IntVector3) = WorldDataHelper.isOutsideRadius(
            position = position, center = playerChunk,
            radiusXZ = meshRemoveRadiusX, radiusY = meshRemoveRadiusY
        )

        val meshesToRemove = createdMeshDataMap.keys.filter { outsideMesh(it) }

        val chunksToRemove = createdChunkDataMap.keys.filter { position ->
            WorldDataHelper.isOutsideRadius(
                position = position, center = playerChunk,
                radiusXZ = dataRemoveRadiusX, radiusY = dataRemoveRadiusY
            )
        }

        for (position in meshesToRemove) {
            createdMeshDataMap.remove(position) ?: continue
            val entityId = createdChunkDataMap[position]?.entityId

            if (entityId != null) {
                mainEventBus.sendEvent(GameChunkEvent.OnRemoveChunkMeshData(entityId))
                physicsEventBus.sendEvent(PhysicsEvent.OnRemoveRigidBody(entityId))
            }
            pendingMeshPositions.remove(position)
        }

        for (position in chunksToRemove) {
            val chunkData = createdChunkDataMap.remove(position) ?: continue
            val entityId = chunkData.entityId

            if (entityId != null) {
                mainEventBus.sendEvent(GameChunkEvent.OnRemoveChunkData(entityId))
            }

            chunkPoolUpdater.removeChunk(chunkData)
            pendingChunkPositions.remove(position)
        }

        val r = dataRemoveRadiusX + 1
        terrainGenerator.retainColumns(
            playerChunk.x - r, playerChunk.x + r,
            playerChunk.z - r, playerChunk.z + r
        )
    }

    private fun scheduleRemesh() {
        if (!remeshRunning.compareAndSet(false, true)) return

        lifecycleScope.launch(Dispatchers.Default) {
            try {
                while (true) {
                    val edited = editRemeshPositions.toList()
                    editRemeshPositions.removeAll(edited.toSet())

                    val lit = lightRemeshPositions.filter { it !in edited }
                    lightRemeshPositions.removeAll(lit.toSet())

                    if (edited.isEmpty() && lit.isEmpty()) break

                    edited.map { p ->
                        async { parallelismMesh.withPermit { meshChunk(p, remesh = true, rebuildPhysics = true) } }
                    }.awaitAll()
                    lit.map { p ->
                        async { parallelismMesh.withPermit { meshChunk(p, remesh = true, rebuildPhysics = false) } }
                    }.awaitAll()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                remeshRunning.set(false)
                if (editRemeshPositions.isNotEmpty() || lightRemeshPositions.isNotEmpty()) scheduleRemesh()
            }
        }
    }

    private fun generateChunk(chunkData: ChunkData, points: Map<IntVector3, BiomeType>) {
        val entityId = chunkData.entityId ?: return

        terrainGenerator.generateChunkData(chunkData, points)
        chunkData.recount()

        mainEventBus.sendEvent(
            GameChunkEvent.OnCreateChunkTransform(
                chunkEntityId = entityId,
                transform = createMatrixForChunk(chunkData)
            )
        )
    }

    private fun isBuried(position: IntVector3, chunk: ChunkData): Boolean {
        if (!chunk.isFull) return false
        val neighbors = arrayOf(
            IntVector3(position.x + 1, position.y, position.z),
            IntVector3(position.x - 1, position.y, position.z),
            IntVector3(position.x, position.y + 1, position.z),
            IntVector3(position.x, position.y - 1, position.z),
            IntVector3(position.x, position.y, position.z + 1),
            IntVector3(position.x, position.y, position.z - 1),
        )
        for (n in neighbors) {
            val c = createdChunkDataMap[n] ?: return false
            if (!c.isFull) return false
        }
        return true
    }

    private suspend fun meshChunk(position: IntVector3, remesh: Boolean, rebuildPhysics: Boolean) {
        val chunkData = createdChunkDataMap[position] ?: return
        val entityId = chunkData.entityId ?: return

        if (!WorldDataHelper.hasRequiredNeighbors(createdChunkDataMap, position)) return

        val hadMesh = createdMeshDataMap[position]?.mesh != null

        val rawMeshData =
            if (chunkData.isEmpty || isBuried(position, chunkData)) null
            else meshGenerator.createMesh(createdChunkDataMap, chunkData).takeUnless { it.isEmpty() }

        if (rawMeshData == null) {
            createdMeshDataMap[position] = MeshData(null)
            if (!remesh) batchProgress.incrementAndGet()
            if (hadMesh) {
                mainEventBus.sendEvent(GameChunkEvent.OnRemoveChunkMeshData(entityId))
                physicsEventBus.sendEvent(PhysicsEvent.OnRemoveRigidBody(entityId))
            }
            return
        }

        val meshData = uploadGate.withPermit {
            withContext(mainScope.coroutineContext) { rawMeshData.createMeshData(chunkMeshParams) }
        }

        val current = createdChunkDataMap[position] ?: return
        if (current.entityId != entityId) return

        createdMeshDataMap[position] = meshData
        if (!remesh) batchProgress.incrementAndGet()

        if (hadMesh) {
            mainEventBus.sendEvent(GameChunkEvent.OnUpdateChunkMeshData(entityId, meshData))
            if (rebuildPhysics) {                            // геометрия поменялась — пересобираем коллизию
                physicsEventBus.sendEvent(PhysicsEvent.OnRemoveRigidBody(entityId))
                physicsEventBus.sendEvent(PhysicsEvent.OnCreateChunkRigidBody(entityId, current))
            }
        } else {
            mainEventBus.sendEvent(GameChunkEvent.OnCreateChunkMeshData(entityId, meshData))
            physicsEventBus.sendEvent(PhysicsEvent.OnCreateChunkRigidBody(entityId, current))
        }
    }

    private fun getWorldGenerationData(playerPosition: IntVector3): WorldGenerationData {
        val meshPositions = WorldDataHelper.getChunkPositionsAroundPlayer(playerPosition)
        val dataPositions = WorldDataHelper.getDataPositionsAroundPlayer(playerPosition)

        return WorldGenerationData(
            playerPosition = playerPosition,
            allChunkDataPositionsAround = dataPositions,
            allMeshDataPositionsAround = meshPositions
        )
    }
}
