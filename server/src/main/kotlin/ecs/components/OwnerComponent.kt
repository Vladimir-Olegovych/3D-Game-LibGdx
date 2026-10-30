package com.gigcreator.ecs.components

import com.artemis.Component

internal class OwnerComponent: Component() {
    var owner: Int = -1
}