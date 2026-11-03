package core.chunk.kryo

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.util.DefaultInstantiatorStrategy
import com.gigapi.math.vector.IntVector3
import org.objenesis.strategy.StdInstantiatorStrategy
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.Inflater
import kotlin.collections.iterator

/*
 * ПУБЛИЧНЫЙ API (всё — функции верхнего уровня):
 *
 *   readMetaData(file)                      -> MetaData        только мета, чанки НЕ читаются и не сканируются
 *   readWorldData(file)                     -> WorldData       мета + хранилище чанков существующего мира
 *   createWorldData(file, name, seed, ...)  -> WorldData       создаёт новый файл мира
 *
 * Все функции могут бросить IOException (FileNotFoundException, WorldFormatException и т.д.).
 *
 * ФОРМАТ ФАЙЛА:
 *
 *   [magic:int][version:int]                                   8 байт
 *   [слот меты A: 8192 байт]                                   [seq:long][len:int][crc:int][Kryo(WorldMeta)][паддинг]
 *   [слот меты B: 8192 байт]                                   то же самое
 *   [запись чанка]...                                          [x:int][y:int][z:int][len:int][crc:int][Kryo(ChunkRecord)]
 *
 * Мета лежит в самом начале в фиксированном месте, поэтому readMetaData читает ~16 КБ и всё.
 * Слотов два: мета пишется в неактуальный слот с seq+1, так что краш посреди записи не убьёт мету —
 * при чтении берётся валидный слот с наибольшим seq.
 * Чанки — append-only журнал: при сохранении дописываются только изменённые чанки, побеждает последняя запись.
 * Оборванная запись в хвосте (краш) отбрасывается при открытии.
 */

// ───────────────────────────── Публичные функции ─────────────────────────────

/**
 * Прочитать только метаданные мира. Чанки не загружаются и даже не сканируются.
 * @throws FileNotFoundException нет файла
 * @throws WorldFormatException файл не является миром / повреждён / версия новее поддерживаемой
 * @throws IOException ошибка чтения
 */
fun readMetaData(file: File): MetaData = readMetaHead(file).meta

/**
 * Открыть существующий мир: мета + хранилище чанков. Индекс чанков строится быстрым проходом по заголовкам,
 * сами блоки в память не грузятся — они читаются лениво через [ChunkStorage.loadChunk].
 */
fun readWorldData(file: File): WorldData {
    val head = readMetaHead(file)
    val scan = scanChunks(file)
    return WorldData(head.meta, ChunkStorage(file, head, scan))
}

/**
 * Создать новый мир: пишет файл с метой (имя, seed, размеры чанка, дата и время создания)
 * и возвращает [WorldData] с пустым хранилищем чанков.
 * @throws IOException если файл уже существует или запись не удалась
 */
fun createWorldData(
    file: File,
    name: String,
    seed: Long = 0L,
    chunkWidth: Int = 16,
    chunkHeight: Int = 16
): WorldData {
    if (file.exists()) throw IOException("World file already exists: $file")
    file.absoluteFile.parentFile?.mkdirs()

    val now = System.currentTimeMillis()
    val meta = WorldMeta(
        name = name,
        seed = seed,
        chunkWidth = chunkWidth,
        chunkHeight = chunkHeight,
        createdAtMillis = now,
        lastSavedAtMillis = now
    )

    val head = ByteBuffer.allocate(CHUNKS_START)
    head.putInt(MAGIC)
    head.putInt(FORMAT_VERSION)
    head.put(encodeSlot(newKryo(), meta, seq = 1L))   // слот A; слот B остаётся нулями
    // НЕ flip(): он бы обрезал буфер по позиции (после слота A), а писать нужно весь заголовок вместе со слотом B
    head.clear()                                       // position = 0, limit = capacity (CHUNKS_START байт)

    FileChannel.open(file.toPath(), CREATE_NEW, WRITE).use { ch ->
        var pos = 0L
        while (head.hasRemaining()) pos += ch.write(head, pos)
        ch.force(true)
    }
    return readWorldData(file)
}

// ───────────────────────────── Модели ─────────────────────────────

typealias MetaData = WorldMeta

class WorldFormatException(message: String) : java.io.IOException(message)

/**
 * Метаданные мира. Все поля с дефолтами — у Kotlin появляется пустой конструктор, он нужен Kryo.
 * Время хранится в epoch-millis, а Instant/LocalDateTime считаются на лету.
 * Сериализованный размер должен влезать в ~8 КБ (слот меты) — для customData этого более чем достаточно.
 */
class WorldMeta(
    var name: String = "",
    var seed: Long = 0L,
    var chunkWidth: Int = 16,
    var chunkHeight: Int = 16,
    var createdAtMillis: Long = 0L,
    var lastSavedAtMillis: Long = 0L,
    /** Любые твои данные: позиция спавна, время суток, версия игры и т.п. */
    var customData: HashMap<String, String> = HashMap()
) {
    // Нет backing field -> Kryo их не сериализует
    val createdAt: LocalDateTime
        get() = LocalDateTime.ofInstant(Instant.ofEpochMilli(createdAtMillis), ZoneId.systemDefault())
    val lastSavedAt: LocalDateTime
        get() = LocalDateTime.ofInstant(Instant.ofEpochMilli(lastSavedAtMillis), ZoneId.systemDefault())

    override fun toString() = "WorldMeta(name='$name', seed=$seed, created=$createdAt, lastSaved=$lastSavedAt)"
}

/** Сохранённое состояние одного чанка. Позиция лежит в заголовке записи, а не здесь. */
class ChunkRecord(
    var width: Int = 0,
    var height: Int = 0,
    var rawSize: Int = 0,
    /** Deflate-сжатый массив блоков. */
    var data: ByteArray = ByteArray(0)
)

data class ChunkKey(val x: Int, val y: Int, val z: Int) {
    companion object {
        fun of(p: IntVector3) = ChunkKey(p.x, p.y, p.z)
    }
}

// ───────────────────────────── WorldData ─────────────────────────────

/** Мир целиком: метаданные + хранилище чанков. Закрывай через [close] / `use {}`. */
class WorldData internal constructor(
    val meta: MetaData,
    val chunks: ChunkStorage
) : Closeable {

    /** Записать мету (обновив время последнего сохранения). */
    fun saveMeta() {
        meta.lastSavedAtMillis = System.currentTimeMillis()
        chunks.writeMeta(meta)
    }

    /**
     * Сохранить все изменённые чанки и мету. Возвращает число записанных чанков.
     * [chunkProvider] отдаёт загруженный чанк по позиции (см. [ChunkStorage.saveDirty]).
     */
    fun save(chunkProvider: (IntVector3) -> core.chunk.ChunkData?): Int {
        val saved = chunks.saveDirty(chunkProvider)
        if (saved > 0) saveMeta()
        return saved
    }

    /** Не сохраняет автоматически — перед закрытием вызови [save]. */
    override fun close() = chunks.close()
}

// ───────────────────────────── ChunkStorage ─────────────────────────────

class ChunkStorage internal constructor(
    private val file: File,
    head: MetaHead,
    scan: ScanResult
) : Closeable {

    private val path = file.toPath()
    private val chunkWidth = head.meta.chunkWidth
    private val chunkHeight = head.meta.chunkHeight

    private val lock = Any()
    private val kryo = newKryo()                       // Kryo не потокобезопасен — используем только под lock
    private val dirty = ConcurrentHashMap.newKeySet<ChunkKey>()
    private var channel: FileChannel = FileChannel.open(path, READ, WRITE)
    private val index = HashMap<ChunkKey, Loc>(scan.index)
    private var totalChunkRecords = scan.totalChunkRecords
    private var activeSlot = head.slot
    private var metaSeq = head.seq

    init {
        // Отбрасываем недописанный хвост после возможного краша
        if (channel.size() > scan.validEnd) channel.truncate(scan.validEnd)
    }

    /** Сколько чанков реально лежит в файле. */
    val savedChunkCount: Int get() = synchronized(lock) { index.size }

    val dirtyCount: Int get() = dirty.size

    fun hasChunk(position: IntVector3): Boolean =
        synchronized(lock) { index.containsKey(ChunkKey.of(position)) }

    /** Вызывай при любом изменении блока в чанке. */
    fun markDirty(position: IntVector3) {
        dirty.add(ChunkKey.of(position))
    }

    fun markDirty(chunk: core.chunk.ChunkData) = markDirty(chunk.position)

    /**
     * Записать все изменённые чанки. Возвращает, сколько записано.
     * Если чанка уже нет в памяти ([chunkProvider] вернул null) — он пропускается,
     * поэтому при выгрузке грязного чанка вызывай [saveChunk].
     */
    fun saveDirty(chunkProvider: (IntVector3) -> core.chunk.ChunkData?): Int {
        val keys = ArrayList<ChunkKey>()
        val it = dirty.iterator()
        while (it.hasNext()) {
            keys += it.next()
            it.remove()
        }
        if (keys.isEmpty()) return 0

        val chunks = keys.mapNotNull { chunkProvider(IntVector3(it.x, it.y, it.z)) }
        try {
            append(chunks)
        } catch (e: Exception) {
            dirty.addAll(keys)      // не теряем пометки, если запись упала
            throw e
        }
        return chunks.size
    }

    /** Сохранить один чанк (например, при выгрузке). */
    fun saveChunk(chunk: core.chunk.ChunkData) = saveChunks(listOf(chunk))

    /** Сохранить набор чанков одной записью на диск. */
    fun saveChunks(chunks: Collection<core.chunk.ChunkData>) {
        if (chunks.isEmpty()) return
        val keys = chunks.map { ChunkKey.of(it.position) }
        dirty.removeAll(keys.toSet())      // сначала снимаем пометки: правка во время записи снова пометит чанк
        try {
            append(chunks)
        } catch (e: Exception) {
            dirty.addAll(keys)             // запись не удалась — чанки остаются «грязными»
            throw e
        }
    }

    fun isDirty(position: IntVector3): Boolean = dirty.contains(ChunkKey.of(position))

    /**
     * Восстановить содержимое чанка из файла ПРЯМО В [target] (например, чанк из пула).
     * Позиция берётся из target.position. Возвращает true, если чанк был в файле и успешно прочитан;
     * false — чанка нет (или запись повреждена) и его нужно сгенерировать.
     */
    fun loadChunkInto(target: core.chunk.ChunkData): Boolean {
        return synchronized(lock) {
            val loc = index[ChunkKey.of(target.position)] ?: return false
            try {
                check(channel.isOpen) { "World file is closed" }
                val payload = readPayload(loc)
                if (crc32(payload) != loc.crc) {
                    System.err.println("ChunkStorage: CRC mismatch for chunk ${target.position}, regenerating")
                    return false
                }
                val rec = deserialize(kryo, payload, ChunkRecord::class.java)
                if (rec.width != chunkWidth || rec.height != chunkHeight || rec.rawSize != target.blocks.size) {
                    System.err.println("ChunkStorage: chunk ${target.position} has unexpected size, regenerating")
                    return false
                }
                val blocks = inflate(rec.data, rec.rawSize)
                System.arraycopy(blocks, 0, target.blocks, 0, blocks.size)
                target.recount()
                true
            } catch (e: Exception) {
                System.err.println("ChunkStorage: failed to load chunk ${target.position}: $e")
                false
            }
        }
    }

    /** Загрузить чанк. null — его нет в файле (генерируй заново) или запись повреждена. */
    fun loadChunk(position: IntVector3): core.chunk.ChunkData? {
        return synchronized(lock) {
            val loc = index[ChunkKey.of(position)] ?: return null
            try {
                val payload = readPayload(loc)
                if (crc32(payload) != loc.crc) {
                    System.err.println("ChunkStorage: CRC mismatch for chunk $position, ignoring")
                    return null
                }
                val rec = deserialize(kryo, payload, ChunkRecord::class.java)
                if (rec.width != chunkWidth || rec.height != chunkHeight) {
                    System.err.println("ChunkStorage: chunk $position has unexpected size, ignoring")
                    return null
                }
                _root_ide_package_.core.chunk.ChunkData(
                    null,
                    position,
                    rec.width,
                    rec.height,
                    inflate(rec.data, rec.rawSize)
                )
            } catch (e: Exception) {
                System.err.println("ChunkStorage: failed to load chunk $position: $e")
                null
            }
        }
    }

    /**
     * Переписать файл, оставив только актуальные версии чанков (через временный файл + атомарная замена).
     * Вызывается автоматически, когда мусора становится много.
     */
    fun compact() {
        synchronized(lock) {
            val tmp = File(file.path + ".tmp")
            val newIndex = HashMap<ChunkKey, Loc>()

            FileOutputStream(tmp).use { fos ->
                val out = DataOutputStream(BufferedOutputStream(fos, 1 shl 16))

                // Заголовок и оба слота меты копируем как есть
                val head = ByteBuffer.allocate(CHUNKS_START)
                readFully(head, 0L)
                out.write(head.array())
                var pos = CHUNKS_START.toLong()

                for ((key, loc) in index) {
                    val payload = readPayload(loc)
                    if (crc32(payload) != loc.crc) continue          // битые записи выбрасываем
                    writeRecord(out, key, payload, loc.crc)
                    newIndex[key] = Loc(pos + RECORD_HEADER_SIZE, payload.size, loc.crc)
                    pos += RECORD_HEADER_SIZE + payload.size
                }
                out.flush()
                fos.fd.sync()
            }

            channel.close()   // на Windows нельзя заменить открытый файл
            try {
                Files.move(tmp.toPath(), path, REPLACE_EXISTING, ATOMIC_MOVE)
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(tmp.toPath(), path, REPLACE_EXISTING)
            }
            channel = FileChannel.open(path, READ, WRITE)

            index.clear()
            index.putAll(newIndex)
            totalChunkRecords = index.size
        }
    }

    override fun close() {
        synchronized(lock) {
            if (channel.isOpen) channel.close()
        }
    }

    // ───────── внутреннее ─────────

    /** Записать мету в неактуальный слот (A/B), потом переключить актуальный. */
    internal fun writeMeta(meta: MetaData) {
        synchronized(lock) {
            check(channel.isOpen) { "World file is closed" }
            val nextSlot = 1 - activeSlot
            val seq = metaSeq + 1
            val bytes = encodeSlot(kryo, meta, seq)
            writeFully(slotOffset(nextSlot), bytes)
            channel.force(false)
            activeSlot = nextSlot
            metaSeq = seq
        }
    }

    private fun append(chunks: Collection<core.chunk.ChunkData>) {
        if (chunks.isEmpty()) return
        synchronized(lock) {
            check(channel.isOpen) { "World file is closed" }
            for (c in chunks) {
                require(c.chunkWidth == chunkWidth && c.chunkHeight == chunkHeight) {
                    "Chunk size ${c.chunkWidth}x${c.chunkHeight} differs from world's ${chunkWidth}x${chunkHeight}"
                }
            }

            val base = channel.size()
            val buf = ByteArrayOutputStream()
            val out = DataOutputStream(buf)
            val added = ArrayList<Pair<ChunkKey, Loc>>(chunks.size)

            for (chunk in chunks) {
                val key = ChunkKey.of(chunk.position)
                val record = ChunkRecord(
                    width = chunk.chunkWidth,
                    height = chunk.chunkHeight,
                    rawSize = chunk.blocks.size,
                    data = deflate(chunk.blocks)
                )
                val payload = serialize(kryo, record)
                val crc = crc32(payload)
                added += key to Loc(base + buf.size() + RECORD_HEADER_SIZE, payload.size, crc)
                writeRecord(out, key, payload, crc)
            }
            out.flush()

            try {
                writeFully(base, buf.toByteArray())
                channel.force(false)
            } catch (e: IOException) {
                runCatching { channel.truncate(base) }
                throw e
            }

            // Индекс обновляем только после успешной записи
            for ((key, loc) in added) index[key] = loc
            totalChunkRecords += added.size

            val garbage = totalChunkRecords - index.size
            if (garbage >= COMPACT_MIN_GARBAGE && garbage > index.size) compact()
        }
    }

    private fun writeFully(position: Long, bytes: ByteArray) {
        val bb = ByteBuffer.wrap(bytes)
        var pos = position
        while (bb.hasRemaining()) pos += channel.write(bb, pos)
    }

    private fun readFully(bb: ByteBuffer, position: Long) {
        var pos = position
        while (bb.hasRemaining()) {
            val n = channel.read(bb, pos)
            if (n < 0) throw EOFException("Unexpected end of world file")
            pos += n
        }
    }

    private fun readPayload(loc: Loc): ByteArray {
        val bb = ByteBuffer.allocate(loc.length)
        readFully(bb, loc.payloadOffset)
        return bb.array()
    }
}

// ───────────────────────────── Низкоуровневый формат ─────────────────────────────

private const val MAGIC = 0x47574C44            // "GWLD"
private const val FORMAT_VERSION = 1
private const val FILE_HEADER_SIZE = 8
private const val META_SLOT_SIZE = 8192
private const val SLOT_HEADER_SIZE = 8 + 4 + 4  // seq + len + crc
private const val SLOT_PAYLOAD_MAX = META_SLOT_SIZE - SLOT_HEADER_SIZE
private const val CHUNKS_START = FILE_HEADER_SIZE + 2 * META_SLOT_SIZE
private const val RECORD_HEADER_SIZE = 4 * 3 + 4 + 4   // xyz + len + crc
private const val COMPACT_MIN_GARBAGE = 512

private fun slotOffset(slot: Int): Long = (FILE_HEADER_SIZE + slot * META_SLOT_SIZE).toLong()

/** Где в файле лежит payload записи чанка. */
internal class Loc(val payloadOffset: Long, val length: Int, val crc: Int)

internal class MetaHead(val meta: WorldMeta, val slot: Int, val seq: Long)

internal class ScanResult(
    val index: HashMap<ChunkKey, Loc>,
    val totalChunkRecords: Int,
    val validEnd: Long
)

/** Читает ТОЛЬКО начало файла (заголовок + 2 слота меты, ~16 КБ). */
internal fun readMetaHead(file: File): MetaHead {
    if (!file.isFile) throw FileNotFoundException("World file not found: $file")

    val head = ByteBuffer.allocate(CHUNKS_START)
    FileChannel.open(file.toPath(), READ).use { ch ->
        var pos = 0L
        while (head.hasRemaining()) {
            val n = ch.read(head, pos)
            if (n < 0) throw WorldFormatException("World file is too short: $file")
            pos += n
        }
    }

    if (head.getInt(0) != MAGIC) throw WorldFormatException("Not a world file: $file")
    val version = head.getInt(4)
    if (version != FORMAT_VERSION) {
        throw WorldFormatException("Unsupported world file version $version (supported: $FORMAT_VERSION): $file")
    }

    val kryo = newKryo()
    val array = head.array()
    var best: MetaHead? = null
    for (slot in 0..1) {
        val base = slotOffset(slot).toInt()
        val seq = head.getLong(base)
        val len = head.getInt(base + 8)
        val crc = head.getInt(base + 12)
        if (len <= 0 || len > SLOT_PAYLOAD_MAX) continue
        val payload = array.copyOfRange(base + SLOT_HEADER_SIZE, base + SLOT_HEADER_SIZE + len)
        if (crc32(payload) != crc) continue
        val meta = try {
            deserialize(kryo, payload, WorldMeta::class.java)
        } catch (e: Exception) {
            continue
        }
        if (best == null || seq > best.seq) best = MetaHead(meta, slot, seq)
    }
    return best ?: throw WorldFormatException("World file has no valid metadata: $file")
}

/** Проход по журналу чанков: читает только заголовки записей, payload пропускает. */
internal fun scanChunks(file: File): ScanResult {
    val fileSize = file.length()
    val index = HashMap<ChunkKey, Loc>()
    var total = 0
    var pos = CHUNKS_START.toLong()

    DataInputStream(BufferedInputStream(FileNotFoundSafe.open(file), 1 shl 16)).use { input ->
        skipFully(input, CHUNKS_START.toLong())
        while (true) {
            val x: Int
            val y: Int
            val z: Int
            val len: Int
            val crc: Int
            try {
                x = input.readInt(); y = input.readInt(); z = input.readInt()
                len = input.readInt(); crc = input.readInt()
            } catch (e: EOFException) {
                break
            }
            val payloadStart = pos + RECORD_HEADER_SIZE
            if (len < 0 || payloadStart + len > fileSize) break    // оборванная запись
            skipFully(input, len.toLong())
            index[ChunkKey(x, y, z)] = Loc(payloadStart, len, crc)
            total++
            pos = payloadStart + len
        }
    }
    return ScanResult(index, total, pos)
}

private object FileNotFoundSafe {
    fun open(file: File) = Files.newInputStream(file.toPath())
}

private fun skipFully(input: DataInputStream, count: Long) {
    var left = count
    while (left > 0) {
        val skipped = input.skip(left)
        if (skipped <= 0) throw EOFException()
        left -= skipped
    }
}

private fun writeRecord(out: DataOutputStream, key: ChunkKey, payload: ByteArray, crc: Int) {
    out.writeInt(key.x)
    out.writeInt(key.y)
    out.writeInt(key.z)
    out.writeInt(payload.size)
    out.writeInt(crc)
    out.write(payload)
}

/** Слот меты ровно META_SLOT_SIZE байт: [seq][len][crc][payload][нули]. */
private fun encodeSlot(kryo: Kryo, meta: WorldMeta, seq: Long): ByteArray {
    val payload = serialize(kryo, meta)
    check(payload.size <= SLOT_PAYLOAD_MAX) {
        "Metadata is too large: ${payload.size} bytes (max $SLOT_PAYLOAD_MAX). Reduce customData."
    }
    return ByteBuffer.allocate(META_SLOT_SIZE)
        .putLong(seq)
        .putInt(payload.size)
        .putInt(crc32(payload))
        .put(payload)
        .array()
}

private fun crc32(bytes: ByteArray): Int = CRC32().also { it.update(bytes) }.value.toInt()

// ───────────────────────────── Kryo ─────────────────────────────

private fun newKryo(): Kryo = Kryo().apply {
    setRegistrationRequired(true)
    // На случай классов без пустого конструктора
    setInstantiatorStrategy(DefaultInstantiatorStrategy(StdInstantiatorStrategy()))
    // ВАЖНО: порядок регистрации определяет id классов в файле — не меняй его в уже существующих мирах!
    register(WorldMeta::class.java)
    register(ChunkRecord::class.java)
    register(ByteArray::class.java)
    register(HashMap::class.java)
}

private fun serialize(kryo: Kryo, obj: Any): ByteArray {
    val output = Output(512, -1)
    kryo.writeObject(output, obj)
    return output.toBytes()
}

private fun <T> deserialize(kryo: Kryo, bytes: ByteArray, type: Class<T>): T =
    kryo.readObject(Input(bytes), type)

// ───────────────────────────── Сжатие блоков ─────────────────────────────

private fun deflate(data: ByteArray): ByteArray {
    val deflater = Deflater(Deflater.BEST_SPEED)
    try {
        deflater.setInput(data)
        deflater.finish()
        val out = ByteArrayOutputStream(maxOf(64, data.size / 4))
        val buf = ByteArray(4096)
        while (!deflater.finished()) {
            val n = deflater.deflate(buf)
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    } finally {
        deflater.end()
    }
}

private fun inflate(data: ByteArray, rawSize: Int): ByteArray {
    val inflater = Inflater()
    try {
        inflater.setInput(data)
        val result = ByteArray(rawSize)
        var off = 0
        while (off < rawSize) {
            val n = inflater.inflate(result, off, rawSize - off)
            if (n == 0 && (inflater.finished() || inflater.needsInput() || inflater.needsDictionary())) break
            off += n
        }
        if (off != rawSize) throw IOException("Corrupted chunk data: expected $rawSize bytes, got $off")
        return result
    } finally {
        inflater.end()
    }
}