package com.gigcreator

class NetQuaternion(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val w: Float = 0f,
) {
    companion object {
        val ZERO = NetQuaternion()
        fun identity() = NetQuaternion(0f, 0f, 0f, 1f)
    }
}
