package app.feature.game.ecs.components

import com.artemis.Component
import core.assets.ModelID

class NetworkEntityComponent: Component() {
    var networkId: Int = -1
    var entityType: Byte = 0
    var isLocal: Boolean = false
    var modelId: Int = ModelID.NULL.ordinal
}
