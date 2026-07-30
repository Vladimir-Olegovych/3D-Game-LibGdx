package app.feature.game.ecs.components

import com.artemis.Component

class NetworkEntityComponent: Component() {
    var networkId: Int = -1
    var entityType: Byte = 0
    var isLocal: Boolean = false
}
