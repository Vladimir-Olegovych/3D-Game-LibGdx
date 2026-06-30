package app.feature.game.ui

import app.feature.game.event.EventBusTypes
import app.feature.game.event.InventoryEvent
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Stack
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.gigapi.core.effects.LaunchedEffect
import com.gigapi.eventbus.EventBus
import com.gigapi.eventbus.annotation.BusEvent
import com.gigapi.general.Context
import core.assets.SkinID
import core.items.InventoryManager
import core.ui.UIGetter

class InventoryUI: LaunchedEffect, UIGetter {

    private val layout = Table().apply {
        setFillParent(true)
        bottom()
        padBottom(20f)
    }

    private lateinit var inventoryCells: Array<Stack?>

    override fun launch(context: Context) {
        val assetManager = context.getObject<AssetManager>()
        val itemBox = assetManager.get<TextureAtlas>(SkinID.BUTTON.atlas).findRegion("ic_item_box")
        val inventoryManager = context.getObject<InventoryManager>()
        val eventBus = context.getObject<EventBus>(EventBusTypes.MAIN_EVENT_BUS)
        eventBus.registerHandler(this)

        inventoryCells = Array(inventoryManager.inventorySize) { null }

        val inventoryTable = Table().apply {
            defaults().size(64f, 64f).pad(2f)
        }

        val startIndex = inventoryManager.inventorySize - TOOL_BAR_SIZE

        for (index in startIndex until inventoryManager.inventorySize) {
            val inventoryItem = inventoryManager.getInventoryItem(index)
            val itemTexture = inventoryItem?.texture
            val cellContainer = Stack().apply {
                setSize(64f, 64f)

                val background = Image(itemBox)
                background.name = CELL_BACKGROUND_NAME

                val item: Image = if (itemTexture != null) Image(itemTexture) else Image()
                item.setSize(48f, 48f)
                item.name = CELL_ITEM_NAME

                addActor(background)
                addActor(item)
            }
            inventoryCells[index] = cellContainer
            inventoryTable.add(cellContainer)
            cellContainer.setColor(1f, 1f, 1f, 1f)
        }

        layout.add(inventoryTable).center().bottom()
    }

    @BusEvent
    fun updateItem(event: InventoryEvent.OnUpdate) {
        val cellContainer = inventoryCells[event.slot] ?: return
        val image = cellContainer.findActor<Image>(CELL_ITEM_NAME)
        image.drawable = TextureRegionDrawable(event.inventoryItem.texture)
    }

    override fun getUI(): Actor {
        return layout
    }

    companion object {
        const val TOOL_BAR_SIZE = 8
        const val CELL_ITEM_NAME = "CELL_ITEM_NAME"
        const val CELL_BACKGROUND_NAME = "CELL_BACKGROUND_NAME"
    }
}