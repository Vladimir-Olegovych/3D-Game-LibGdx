package com.gigcreator.core.ecs

import com.artemis.ComponentMapper
import com.gigcreator.ecs.components.OwnerComponent

internal fun ComponentMapper<OwnerComponent>.canSee(observer: Int, entity: Int): Boolean {
    if (observer == entity) return false
    val owner = this[entity]?.owner ?: return true
    return owner != observer
}