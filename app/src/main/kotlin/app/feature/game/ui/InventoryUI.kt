package app.feature.game.ui

import app.feature.game.ecs.components.InventoryComponent
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.gigapi.eventbus.annotation.BusEvent
import core.assets.SkinID
import core.controls.PInputProcessor
import core.items.InventoryItem
import core.ui.UIGetter

class InventoryUI(
    private val assetManager: AssetManager,
    private val inventoryComponent: InventoryComponent
): InventoryComponent.InventoryListener, UIGetter {

    private val layout = Table().apply {
        setFillParent(true)
        bottom()
        padBottom(20f)
    }

    private val inventoryCells: Array<Stack?>
    private val itemBoxOff: TextureRegion
    private val itemBoxOn: TextureRegion

    private var prevSelectedSlot = 0

    init {
        val skin = assetManager.get<Skin>(SkinID.BUTTON.skin)
        val btnAtlas = assetManager.get<TextureAtlas>(SkinID.BUTTON.atlas)
        itemBoxOff = btnAtlas.findRegion("ic_item_box_off")
        itemBoxOn = btnAtlas.findRegion("ic_item_box_on")

        prevSelectedSlot = inventoryComponent.selectedSlot

        inventoryCells = Array(inventoryComponent.inventorySize) { null }

        val inventoryTable = Table().apply {
            defaults().size(64f, 64f).pad(2f)
        }

        val startIndex = inventoryComponent.inventorySize - TOOL_BAR_SIZE

        for (index in startIndex until inventoryComponent.inventorySize) {
            val inventoryItem = inventoryComponent.getInventoryItem(index)
            val item = inventoryItem?.item
            val itemTexture = item?.let {
                assetManager.get<TextureAtlas>(item.skinID.atlas).findRegion(item.regionName)
            }
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

        inventoryComponent.addListener(this)
    }

    override fun onSelectSlot(slot: Int) {
        val prevInventoryCell = inventoryCells[prevSelectedSlot] ?: return
        val prevBackground = prevInventoryCell.findActor<Image>(CELL_BACKGROUND_NAME)
        prevBackground.drawable = TextureRegionDrawable(itemBoxOff)

        val inventoryCell = inventoryCells[slot] ?: return
        val background = inventoryCell.findActor<Image>(CELL_BACKGROUND_NAME)
        background.drawable = TextureRegionDrawable(itemBoxOn)
        prevSelectedSlot = slot
    }

    override fun onUpdate(inventoryItem: InventoryItem?, slot: Int) {
        val cellContainer = inventoryCells[slot] ?: return
        val image = cellContainer.findActor<Image>(CELL_ITEM_NAME)
        val label = cellContainer.findActor<Label>(CELL_ITEM_COUNT)

        val itemCount = inventoryItem?.count?: 1
        label.isVisible = itemCount > 1
        label.setText(itemCount)

        val item = inventoryItem?.item
        val textureRegion = item?.let {
            assetManager.get<TextureAtlas>(item.skinID.atlas).findRegion(item.regionName)
        }

        textureRegion?.let {
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