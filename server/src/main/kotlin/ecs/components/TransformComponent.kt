package com.gigcreator.ecs.components

import com.artemis.Component
import com.gigcreator.NetQuaternion
import com.gigcreator.NetVector3

internal class TransformComponent: Component() {
    var pos: NetVector3? = null
    var rot: NetQuaternion? = null
}