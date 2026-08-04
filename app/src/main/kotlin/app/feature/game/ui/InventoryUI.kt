package app.feature.game.ui

import app.feature.game.event.EventBusTypes
import app.feature.game.event.InventoryEvent
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.gigapi.effects.LaunchedEffect
import com.gigapi.eventbus.EventBus
import com.gigapi.eventbus.annotation.BusEvent
import com.gigapi.general.GContext
import core.assets.SkinID
import core.controls.PlayerInputProcessor
import core.items.InventoryManager
import core.ui.UIGetter

class InventoryUI: LaunchedEffect, UIGetter {

    private val layout = Table().apply {
        setFillParent(true)
        bottom()
        padBottom(20f)
    }

    private lateinit var inventoryCells: Array<Stack?>
    private lateinit var itemBoxOff: TextureRegion
    private lateinit var itemBoxOn: TextureRegion

    private var prevSelectedSlot = 0

    override fun launch(gContext: GContext) {
        val assetManager = gContext.getObject<AssetManager>()
        val skin = assetManager.get<Skin>(SkinID.BUTTON.skin)
        val btnAtlas = assetManager.get<TextureAtlas>(SkinID.BUTTON.atlas)
        itemBoxOff = btnAtlas.findRegion("ic_item_box_off")
        itemBoxOn = btnAtlas.findRegion("ic_item_box_on")
        val playerInputProcessor = gContext.getObject<PlayerInputProcessor>()
        val inventoryManager = gContext.getObject<InventoryManager>()
        val eventBus = gContext.getObject<EventBus>(EventBusTypes.MAIN_EVENT_BUS)
        eventBus.registerHandler(this)

        prevSelectedSlot = playerInputProcessor.getSelectedSlot()

        inventoryCells = Array(inventoryManager.inventorySize) { null }

        val inventoryTable = Table().apply {
            defaults().size(64f, 64f).pad(2f)
        }

        val startIndex = inventoryManager.inventorySize - TOOL_BAR_SIZE

        for (index in startIndex until inventoryManager.inventorySize) {
            val inventoryItem = inventoryManager.getInventoryItem(index)
            val itemTexture = inventoryItem?.texture
            val itemCount = inventoryItem?.count ?: 1

            val cellContainer = Stack().apply {
                setSize(64f, 64f)

                val background = Image(if (prevSelectedSlot == index) itemBoxOn else itemBoxOff)
                background.name = CELL_BACKGROUND_NAME

                val itemTable = Table()
                itemTable.center()
                itemTable.setFillParent(true)

                val item: Image = if (itemTexture != null) Image(itemTexture) else Image()
                item.name = CELL_ITEM_NAME
                itemTable.add(item).size(this.width / 1.5f, this.height / 1.5f)

                val labelTable = Table()
                labelTable.right().bottom()
                labelTable.setFillParent(true)

                val countLabel = Label(itemCount.toString(), skin, "small")
                countLabel.isVisible = itemCount > 1
                countLabel.name = CELL_ITEM_COUNT
                labelTable.add(countLabel)

                addActor(background)
                addActor(itemTable)
                addActor(labelTable)
            }
            inventoryCells[index] = cellContainer
            inventoryTable.add(cellContainer)
            cellContainer.setColor(1f, 1f, 1f, 1f)
        }

        layout.add(inventoryTable).center().bottom()
    }

    @BusEvent
    fun onSelectInventorySlot(event: InventoryEvent.OnSelectInventorySlot) {
        val prevInventoryCell = inventoryCells[prevSelectedSlot] ?: return
        val prevBackground = prevInventoryCell.findActor<Image>(CELL_BACKGROUND_NAME)
        prevBackground.drawable = TextureRegionDrawable(itemBoxOff)

        val inventoryCell = inventoryCells[event.slot] ?: return
        val background = inventoryCell.findActor<Image>(CELL_BACKGROUND_NAME)
        background.drawable = TextureRegionDrawable(itemBoxOn)
        prevSelectedSlot = event.slot
    }

    @BusEvent
    fun updateItem(event: InventoryEvent.OnUpdate) {
        val cellContainer = inventoryCells[event.slot] ?: return
        val image = cellContainer.findActor<Image>(CELL_ITEM_NAME)
        val label = cellContainer.findActor<Label>(CELL_ITEM_COUNT)

        val itemCount = event.inventoryItem?.count?: 1
        label.isVisible = itemCount > 1
        label.setText(itemCount)

        event.inventoryItem?.texture?.let { textureRegion ->
            image.drawable = TextureRegionDrawable(textureRegion)
            return
        }
        image.drawable = null
    }

    override fun getUI(): Actor {
        return layout
    }

    companion object {
        const val TOOL_BAR_SIZE = 8
        const val CELL_ITEM_COUNT = "CELL_ITEM_COUNT"
        const val CELL_ITEM_NAME = "CELL_ITEM_NAME"
        const val CELL_BACKGROUND_NAME = "CELL_BACKGROUND_NAME"
    }
}