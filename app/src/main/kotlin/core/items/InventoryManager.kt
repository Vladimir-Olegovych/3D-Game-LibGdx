package core.items

import com.gigapi.core.effects.LaunchedEffect
import com.gigapi.general.Context

class InventoryManager: LaunchedEffect {

    companion object {
        const val INVENTORY_SIZE = 32

        const val TOOL_BAR_SIZE = 8
        const val COLS = 8
        const val ROWS = INVENTORY_SIZE / COLS
    }
    override fun launch(context: Context) {

    }
}