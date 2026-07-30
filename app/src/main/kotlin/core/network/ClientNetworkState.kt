package core.network

import com.esotericsoftware.kryonet.Connection

class ClientNetworkState {
    var connection: Connection? = null
    var localPlayerId: Int = -1
}
