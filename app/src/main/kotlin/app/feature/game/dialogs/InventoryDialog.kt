package app.feature.game.dialogs

import app.feature.game.event.EventBusTypes
import app.feature.game.event.InventoryEvent
import app.feature.game.ui.InventoryUI.Companion.CELL_BACKGROUND_NAME
import app.feature.game.ui.InventoryUI.Companion.CELL_ITEM_COUNT
import app.feature.game.ui.InventoryUI.Companion.CELL_ITEM_NAME
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.gigapi.dialogs.Dialog
import com.gigapi.effects.LaunchedEffect
import com.gigapi.eventbus.EventBus
import com.gigapi.eventbus.annotation.BusEvent
import com.gigapi.general.Context
import com.gigapi.texture.ColorDrawable
import core.assets.SkinID
import core.items.InventoryManager

class InventoryDialog: LaunchedEffect, Dialog() {

    private lateinit var stage: Stage
    private lateinit var fullscreenOverlay: Table
    private lateinit var dragAndDrop: DragAndDrop
    private lateinit var inventoryManager: InventoryManager
    private lateinit var inventoryCells: Array<Stack?>

    private var dragActor: Actor? = null

    override fun launch(context: Context) {
        stage = context.getObject()
        inventoryManager = context.getObject()
        dragAndDrop = DragAndDrop()
        val assetManager = context.getObject<AssetManager>()
        val skin = assetManager.get<Skin>(SkinID.BUTTON.skin)
        val itemBox = assetManager.get<TextureAtlas>(SkinID.BUTTON.atlas).findRegion("ic_item_box")
        val eventBus = context.getObject<EventBus>(EventBusTypes.MAIN_EVENT_BUS)
        eventBus.registerHandler(this)

        inventoryCells = Array(inventoryManager.inventorySize) { null }

        fullscreenOverlay = Table().apply {
            setFillParent(true)
            background(ColorDrawable(0f, 0f, 0f, 0.6f))
        }

        val inventoryTable = Table().apply {
            defaults().size(56f, 56f).pad(4f)
            background(ColorDrawable(0.15f, 0.15f, 0.15f, 0.9f))
            pad(16f)
        }

        for (row in 0 until inventoryManager.rows) {
            for (col in 0 until inventoryManager.cols) {
                val index = row * 8 + col
                val inventoryItem = inventoryManager.getInventoryItem(index)
                val itemTexture = inventoryItem?.texture
                val itemCount = inventoryItem?.count ?: 1

                val cellContainer = Stack().apply {
                    setSize(64f, 64f)

                    val background = Image(itemBox)
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
                setupDragAndDrop(cellContainer, index)
                inventoryCells[index] = cellContainer
                inventoryTable.add(cellContainer)
            }
            inventoryTable.row()
        }

        fullscreenOverlay.add(inventoryTable).center()
    }

    private fun setupDragAndDrop(cellContainer: Stack, slotIndex: Int) {
        val itemImage = cellContainer.findActor<Image>(CELL_ITEM_NAME) ?: return
        val countLabel = cellContainer.findActor<Label>(CELL_ITEM_COUNT) ?: return
        dragAndDrop.addSource(object : DragAndDrop.Source(cellContainer) {
            override fun dragStart(
                event: InputEvent?,
                x: Float,
                y: Float,
                pointer: Int
            ): DragAndDrop.Payload? {
                countLabel.isVisible = false
                itemImage.isVisible = false

                val payload = DragAndDrop.Payload()
                payload.`object` = slotIndex
                val inventoryItem = inventoryManager.getInventoryItem(slotIndex)
                val texture = inventoryItem?.texture?: return null
                val dragActor = Image(texture)
                dragActor.setSize(48f, 48f)
                payload.dragActor = dragActor
                this@InventoryDialog.dragActor = dragActor
                return payload
            }

            override fun dragStop(
                event: InputEvent?,
                x: Float,
                y: Float,
                pointer: Int,
                payload: DragAndDrop.Payload?,
                target: DragAndDrop.Target?
            ) {
                val inventoryItem = inventoryManager.getInventoryItem(slotIndex)
                val itemCount = inventoryItem?.count ?: 1

                countLabel.isVisible = itemCount > 1
                itemImage.isVisible = true
                this@InventoryDialog.dragActor = null
            }
        })
        setupAsTarget(cellContainer, slotIndex)
    }
    private fun setupAsTarget(cellContainer: Stack, slotIndex: Int) {
        dragAndDrop.addTarget(object : DragAndDrop.Target(cellContainer) {
            override fun drag(
                source: DragAndDrop.Source?,
                payload: DragAndDrop.Payload?,
                x: Float,
                y: Float,
                pointer: Int
            ): Boolean {
                val sourceSlot = dragAndDrop.dragPayload?.`object` as? Int ?: return false
                return sourceSlot != slotIndex
            }

            override fun drop(
                source: DragAndDrop.Source?,
                payload: DragAndDrop.Payload?,
                x: Float,
                y: Float,
                pointer: Int
            ) {
                val sourceSlot = dragAndDrop.dragPayload?.`object` as? Int ?: return
                inventoryManager.swapIndexes(sourceSlot, slotIndex)
            }
        })
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

    override fun onCreate() {
        stage.addActor(fullscreenOverlay)
        dragActor?.let { stage.addActor(it) }
    }

    override fun onDestroy() {
        dragActor?.remove()
        fullscreenOverlay.remove()
    }
}