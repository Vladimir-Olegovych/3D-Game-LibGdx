package core.network

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.gigcreator.NetQuaternion
import com.gigcreator.NetVector3
import kotlin.math.cos
import kotlin.math.sin

fun Vector3.toNetVector3() = NetVector3(x, y, z)

fun NetVector3.toVector3() = Vector3(x, y, z)

fun yawToNetQuaternion(yaw: Float): NetQuaternion {
    val halfYaw = Math.toRadians(yaw.toDouble()) / 2.0
    return NetQuaternion(0f, sin(halfYaw).toFloat(), 0f, cos(halfYaw).toFloat())
}

fun Matrix4.setFromNetTransform(pos: NetVector3, rot: NetQuaternion): Matrix4 {
    idt().setTranslation(pos.x, pos.y, pos.z)
    if (rot.w != 0f || rot.x != 0f || rot.y != 0f || rot.z != 0f) {
        rotate(Quaternion(rot.x, rot.y, rot.z, rot.w))
    }
    return this
}
