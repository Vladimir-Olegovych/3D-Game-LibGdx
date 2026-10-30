package com.gigcreator

class GamePacket(val events: Array<NetworkEvent> = emptyArray())

/**
 * Протокол без типов сущностей: что это за сущность, клиент определяет по modelId,
 * а поведение — по набору своих компонентов.
 *
 * entityId везде — id сущности на СЕРВЕРЕ (он же networkId на клиенте).
 */
sealed class NetworkEvent {

    /** TCP, сервер -> клиент, один раз при подключении. playerId — id сущности этого клиента на сервере. */
    class HelloFromServer(
        val playerId: Int = -1,
        val worldSeed: Int = 0,
        val timeOfDay: Float = 0.7f,
        val cycleDuration: Float = 600f,
    ) : NetworkEvent()

    /** TCP, сервер -> клиент: сущность вошла в зону видимости (создать её на клиенте). */
    class EntityJoined(
        val entityId: Int = 0,
        val name: String = "",
        val pos: NetVector3 = NetVector3.ZERO,
        val rot: NetQuaternion = NetQuaternion.ZERO,
        val modelId: Int = 0,
    ) : NetworkEvent()

    /** TCP, сервер -> клиент: сущность ушла из зоны видимости или удалена (удалить на клиенте). */
    class EntityLeft(
        val entityId: Int = 0,
    ) : NetworkEvent()

    /**
     * UDP, в обе стороны.
     * Клиент -> сервер: состояние своих сущностей (себя, своих мобов).
     * Сервер -> клиент: состояние чужих сущностей из видимых чанков.
     */
    class EntityStateUpdate(
        val entityId: Int = 0,
        val pos: NetVector3 = NetVector3.ZERO,
        val rot: NetQuaternion = NetQuaternion.ZERO,
        val modelId: Int = 0,
        val tick: Long = 0L,
    ) : NetworkEvent()
}