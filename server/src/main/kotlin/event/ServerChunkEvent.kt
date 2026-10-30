package com.gigcreator.event

sealed class ServerChunkEvent {
    /** Отправить [observer] данные этих сущностей (спавн на клиенте). */
    class Show(val observer: Int, val entities: IntArray) : ServerChunkEvent()

    /** Сообщить [observer], что эти сущности нужно удалить (ушли далеко или удалены). */
    class Hide(val observer: Int, val entities: IntArray) : ServerChunkEvent()
}
