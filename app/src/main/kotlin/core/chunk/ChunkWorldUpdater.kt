package core.chunk

import app.feature.game.event.ChunkEvent
import app.feature.game.event.EventBusTypes
import app.feature.game.event.GameChunkEvent
import app.feature.game.event.PhysicsEvent
import com.badlogic.gdx.math.Vector3
import com.gigapi.coruntines.DeltaUpdater
import com.gigapi.effects.DisposableEffect
import com.gigapi.effects.LaunchedEffect
import com.gigapi.eventbus.EventBus
import com.gigapi.eventbus.annotation.BusEvent
import com.gigapi.general.GContext
import com.gigapi.math.vector.IntVector3
import com.gigapi.math.vector.roundToFloat
import com.gigapi.mesh.MeshData
import core.blocks.types.BlockType
import core.bullet.raycast.RayCastTypes
import core.bullet.raycast.VoxelHit
import core.chunk.ChunkData.Companion.isSolid
import core.chunk.world.WorldChunksPreparation
import core.chunk.world.WorldDataHelper
import core.chunk.world.WorldGenerationData
import core.light.LightEngine
import core.math.createMatrixForChunk
import core.mesh.chunkMeshParams
import core.mesh.generators.ChunkMeshSplit
import core.mesh.generators.GreedyChunkMesher
import core.mesh.generators.MeshGenerator
import core.scope.DispatcherTypes
import core.terrain.TerrainGenerator
import core.terrain.biome.BiomePoints
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt

class ChunkWorldUpdater : LaunchedEffect, DisposableEffect, DeltaUpdater(1 / 60F, Dispatchers.Default) {

    companion object {
        const val DRAW_RADIUS_X = 16
        const val DRAW_RADIUS_Y = 10
        const val CHUNK_SIZE = 16
        const val CHUNK_HEIGHT = 16
        const val DATA_RADIUS_EXTRA = 1
        const val REMOVE_MARGIN = 1

        const val FIRST_BATCH_CHUNKS = 200
        const val BATCH_CHUNKS = 64 * 4
        const val BATCH_MESHES = 64 * 4
        const val BATCH_TIMEOUT_MS = 15_000L
    }

    private val isFirstGeneration = AtomicBoolean(true)

    private val workerCount = (Runtime.getRuntime().availableProcessors() + 1).coerceAtLeast(2)

    private val parallelismChunks = Semaphore(workerCount)
    private val parallelismMesh = Semaphore(workerCount)
    private val parallelismLight = Semaphore(workerCount)
    private val uploadGate = Semaphore(workerCount)
    private val editUploadGate = Semaphore(workerCount)

    private val editInFlight = ConcurrentHashMap.newKeySet<IntVector3>()
    private val editDirty = ConcurrentHashMap.newKeySet<IntVector3>()

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
        meshGenerator = gContext.getObject<GreedyChunkMesher>()
        terrainGenerator = gContext.getObject()
        mainEventBus = gContext.getObject(EventBusTypes.MAIN_EVENT_BUS)
        mainScope = CoroutineScope(
            gContext.getObject<CoroutineDispatcher>(DispatcherTypes.MAIN)
        )
        chunkEventBus?.registerHandler(this)
        mainEventBus.registerHandler(this)
        physicsEventBus.registerHandler(this)
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

            val lightJobs = createdChunks.values.map { chunkData ->
                async(Dispatchers.Default) {
                    parallelismLight.withPermit {
                        lightEngine.onChunkLoaded(chunkData)
                    }
                }
            }

            lightJobs.forEach {
                val toRemesh = it.await()
                for (affected in toRemesh) {
                    val position = affected.position
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

        chunk.setBlockByLocal(event.blockType, lp)
        feedback(old, true)

        val affected = lightEngine.onBlockChanged(chunk, lp.x, lp.y, lp.z)

        scheduleEditRemesh(event.chunkPosition)
        for (c in affected) {
            val p = c.position
            if (p == event.chunkPosition) continue
            if (createdMeshDataMap.containsKey(p)) lightRemeshPositions.add(p)
        }
        if (lightRemeshPositions.isNotEmpty()) scheduleRemesh()
    }

    @BusEvent
    fun onVoxelCastRequest(event: PhysicsEvent.OnRayCastRequest) {
        val id = event.requestId
        val isPlace = id == RayCastTypes.CHUNK_PLACE_RAY_CAST
        val isDig = id == RayCastTypes.CHUNK_DIG_RAY_CAST
        val isSelect = id == RayCastTypes.BLOCK_SELECT_RAY_CAST
        if (!isPlace && !isDig && !isSelect) return

        val hit = raycastVoxels(event.from, event.direction, event.maxDistance, isPlace)

        if (hit == null) {
            // Промах: сразу сообщаем, чтобы рамка пропала и копание сбросилось без таймаута.
            if (!isPlace) {
                mainEventBus.sendEvent(PhysicsEvent.OnRayCastBlockResult(
                    requestId = id,
                    requestOwner = event.requestOwner,
                    chunkEntityId = -1,
                    blockToSet = BlockType.AIR,
                    blockToRemove = BlockType.AIR,
                    chunkPosition = IntVector3(),
                    blockPosition = IntVector3()
                ))
            }
            return
        }

        mainEventBus.sendEvent(PhysicsEvent.OnRayCastBlockResult(
            requestId = id,
            requestOwner = event.requestOwner,
            chunkEntityId = hit.chunkEntityId,
            blockToSet = if (isPlace) BlockType.NOTHING else BlockType.AIR,
            blockToRemove = hit.block,
            chunkPosition = hit.chunkPosition,
            blockPosition = hit.blockPosition
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

    private fun scheduleEditRemesh(p: IntVector3) {
        if (!editInFlight.add(p)) { editDirty.add(p); return }   // уже мешится — пометим «грязным»
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                do {
                    editDirty.remove(p)
                    meshChunk(p, remesh = true, rebuildPhysics = true, urgent = true)
                } while (editDirty.contains(p))
            } finally {
                editInFlight.remove(p)
                if (editDirty.contains(p)) scheduleEditRemesh(p)
            }
        }
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

    private fun generateChunk(chunkData: ChunkData, points: BiomePoints) {
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

    private suspend fun meshChunk(position: IntVector3, remesh: Boolean, rebuildPhysics: Boolean, urgent: Boolean = false) {
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

        val split = GreedyChunkMesher.splitOf(rawMeshData)
        val gate = if (urgent) editUploadGate else uploadGate
        val meshData = gate.withPermit {
            withContext(mainScope.coroutineContext) {
                rawMeshData.createMeshData(chunkMeshParams).also { md ->
                    md.mesh?.let { ChunkMeshSplit.register(it, split) }
                }
            }
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

    private fun cellAt(x: Int, y: Int, z: Int): VoxelHit? {
        val chunkPosition = IntVector3(
            Math.floorDiv(x, CHUNK_SIZE),
            Math.floorDiv(y, CHUNK_HEIGHT),
            Math.floorDiv(z, CHUNK_SIZE)
        )
        if (pendingChunkPositions.contains(chunkPosition)) return null
        val chunk = createdChunkDataMap[chunkPosition] ?: return null
        val entityId = chunk.entityId ?: return null

        val lx = Math.floorMod(x, CHUNK_SIZE)
        val ly = Math.floorMod(y, CHUNK_HEIGHT)
        val lz = Math.floorMod(z, CHUNK_SIZE)

        return VoxelHit(
            chunkPosition = chunkPosition,
            chunkEntityId = entityId,
            blockPosition = IntVector3(lx, ly, lz),
            block = chunk.getBlockByLocal(lx, ly, lz)
        )
    }

    private fun raycastVoxels(
        origin: Vector3,
        direction: Vector3,
        maxDistance: Float,
        forPlace: Boolean
    ): VoxelHit? {
        val len = sqrt(
            direction.x * direction.x + direction.y * direction.y + direction.z * direction.z
        )
        if (len < 1e-6f) return null
        val dx = direction.x / len
        val dy = direction.y / len
        val dz = direction.z / len

        var x = floor(origin.x).toInt()
        var y = floor(origin.y).toInt()
        var z = floor(origin.z).toInt()

        val stepX = if (dx > 0f) 1 else -1
        val stepY = if (dy > 0f) 1 else -1
        val stepZ = if (dz > 0f) 1 else -1

        val inf = Float.MAX_VALUE
        val tDeltaX = if (dx != 0f) abs(1f / dx) else inf
        val tDeltaY = if (dy != 0f) abs(1f / dy) else inf
        val tDeltaZ = if (dz != 0f) abs(1f / dz) else inf

        var tMaxX = when {
            dx > 0f -> (x + 1 - origin.x) * tDeltaX
            dx < 0f -> (origin.x - x) * tDeltaX
            else -> inf
        }
        var tMaxY = when {
            dy > 0f -> (y + 1 - origin.y) * tDeltaY
            dy < 0f -> (origin.y - y) * tDeltaY
            else -> inf
        }
        var tMaxZ = when {
            dz > 0f -> (z + 1 - origin.z) * tDeltaZ
            dz < 0f -> (origin.z - z) * tDeltaZ
            else -> inf
        }

        var px = x
        var py = y
        var pz = z
        var hasPrev = false

        val maxSteps = (maxDistance * 3f).toInt() + 3
        for (i in 0 until maxSteps) {
            val cell = cellAt(x, y, z) ?: return null
            if (isSolid(cell.block)) {
                if (!forPlace) return cell
                return if (hasPrev) cellAt(px, py, pz) else null
            }
            px = x; py = y; pz = z
            hasPrev = true

            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                if (tMaxX > maxDistance) return null
                x += stepX; tMaxX += tDeltaX
            } else if (tMaxY < tMaxZ) {
                if (tMaxY > maxDistance) return null
                y += stepY; tMaxY += tDeltaY
            } else {
                if (tMaxZ > maxDistance) return null
                z += stepZ; tMaxZ += tDeltaZ
            }
        }
        return null
    }
}
