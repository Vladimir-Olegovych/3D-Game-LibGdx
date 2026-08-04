package app.feature.game.dialogs

import app.feature.game.event.EventBusTypes
import app.feature.game.event.InventoryEvent
import app.feature.game.ui.InventoryUI.Companion.CELL_BACKGROUND_NAME
import app.feature.game.ui.InventoryUI.Companion.CELL_ITEM_COUNT
import app.feature.game.ui.InventoryUI.Companion.CELL_ITEM_NAME
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.gigapi.dialogs.Dialog
import com.gigapi.effects.LaunchedEffect
import com.gigapi.eventbus.EventBus
import com.gigapi.eventbus.annotation.BusEvent
import com.gigapi.general.GContext
import com.gigapi.storage.json.AppConfig
import com.gigapi.texture.ColorDrawable
import core.assets.SkinID
import core.configs.ConfigTypes
import core.crafting.CraftingRecipeData
import core.crafting.CraftingRecipeSO
import core.items.InventoryManager
import core.items.Item
import core.items.ItemManager

class InventoryDialog: LaunchedEffect, Dialog() {

    private lateinit var stage: Stage
    private lateinit var fullscreenOverlay: Table
    private lateinit var dragAndDrop: DragAndDrop
    private lateinit var inventoryManager: InventoryManager
    private lateinit var itemManager: ItemManager
    private lateinit var inventoryCells: Array<Stack?>
    private lateinit var assetManager: AssetManager
    private lateinit var skin: Skin
    private lateinit var itemBox: TextureAtlas.AtlasRegion
    private lateinit var allRecipes: List<CraftingRecipeData>
    private lateinit var craftingRecipesTable: Table

    private var dragActor: Actor? = null

    override fun launch(gContext: GContext) {
        stage = gContext.getObject()
        itemManager = gContext.getObject()
        inventoryManager = gContext.getObject()
        dragAndDrop = DragAndDrop()
        assetManager = gContext.getObject()
        skin = assetManager.get(SkinID.BUTTON.skin)
        itemBox = assetManager.get<TextureAtlas>(SkinID.BUTTON.atlas).findRegion("ic_item_box_off")
        val eventBus = gContext.getObject<EventBus>(EventBusTypes.MAIN_EVENT_BUS)
        eventBus.registerHandler(this)

        allRecipes = gContext.getObject<AppConfig<CraftingRecipeSO>>(ConfigTypes.CRAFTING_RECIPES_SO)
            .getConfig()
            .recipes

        inventoryCells = Array(inventoryManager.inventorySize) { null }

        fullscreenOverlay = Table().apply {
            setFillParent(true)
            background(ColorDrawable(0f, 0f, 0f, 0.6f))
        }

        val inventoryTable = Table().apply {
            defaults().size(56f, 56f).pad(4f)
        }

        for (row in 0 until inventoryManager.rows) {
            for (col in 0 until inventoryManager.cols) {
                val index = row * 8 + col
                val inventoryItem = inventoryManager.getInventoryItem(index)
                val itemTexture = inventoryItem?.texture
                val itemCount = inventoryItem?.count ?: 1

                val cellContainer = createItemCell(
                    size = INVENTORY_CELL_SIZE,
                    itemTexture = itemTexture,
                    itemCount = itemCount,
                    showCountOnlyIfStacked = true
                )
                setupDragAndDrop(cellContainer, index)
                inventoryCells[index] = cellContainer
                inventoryTable.add(cellContainer)
            }
            inventoryTable.row()
        }

        val craftingList = createCraftingList()
        inventoryTable.pack()

        val content = Table().apply {
            background(ColorDrawable(0.15f, 0.15f, 0.15f, 0.9f))
            pad(16f)
        }
        content.add(craftingList)
            .width(CRAFTING_PANEL_MIN_WIDTH)
            .height(inventoryTable.height)
            .top()
            .padRight(16f)
        content.add(inventoryTable).top()

        fullscreenOverlay.add(content).center()
        refreshCraftableRecipes()
    }

    private fun createCraftingList(): ScrollPane {
        craftingRecipesTable = Table().apply {
            defaults().left().padBottom(10f)
        }

        return ScrollPane(craftingRecipesTable).apply {
            setScrollingDisabled(true, false)
            setFadeScrollBars(false)
            setScrollbarsOnTop(true)
            setForceScroll(false, true)
            setOverscroll(false, false)
            setSmoothScrolling(true)
        }
    }

    private fun refreshCraftableRecipes() {
        if (!::craftingRecipesTable.isInitialized) return
        craftingRecipesTable.clearChildren()
        for (recipe in allRecipes) {
            if (!inventoryManager.canCraft(recipe)) continue
            craftingRecipesTable.add(createRecipeRow(recipe)).left().row()
        }
    }

    private fun createRecipeRow(recipe: CraftingRecipeData): Table {
        val row = Table()

        val item = itemManager.getItem(recipe.result.item)?: return row
        val resultTexture = resolveItemTexture(item)
        val resultCell = createItemCell(
            size = RESULT_CELL_SIZE,
            itemTexture = resultTexture,
            itemCount = recipe.result.count,
            showCountOnlyIfStacked = true
        )
        row.add(resultCell).padRight(10f)

        val ingredientsTable = Table()
        for (ingredient in recipe.ingredients) {
            val ingredientColumn = Table()
            val item = itemManager.getItem(ingredient.item)?: continue
            val ingredientTexture = resolveItemTexture(item)
            val ingredientCell = createItemCell(
                size = INGREDIENT_CELL_SIZE,
                itemTexture = ingredientTexture,
                itemCount = 1,
                showCountOnlyIfStacked = true
            )
            ingredientColumn.add(ingredientCell).size(INGREDIENT_CELL_SIZE).row()
            ingredientColumn.add(Label(ingredient.count.toString(), skin, "small")).padTop(2f)
            ingredientsTable.add(ingredientColumn).padRight(6f)
        }
        row.add(ingredientsTable).top()

        row.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                inventoryManager.craftItem(recipe)
            }
        })

        return row
    }

    private fun createItemCell(
        size: Float,
        itemTexture: TextureRegion?,
        itemCount: Int,
        showCountOnlyIfStacked: Boolean
    ): Stack {
        return Stack().apply {
            setSize(size, size)

            val background = Image(itemBox)
            background.name = CELL_BACKGROUND_NAME

            val itemTable = Table()
            itemTable.center()
            itemTable.setFillParent(true)

            val item: Image = if (itemTexture != null) Image(itemTexture) else Image()
            item.name = CELL_ITEM_NAME
            itemTable.add(item).size(size / 1.5f, size / 1.5f)

            val labelTable = Table()
            labelTable.right().bottom()
            labelTable.setFillParent(true)

            val countLabel = Label(itemCount.toString(), skin, "small")
            countLabel.isVisible = if (showCountOnlyIfStacked) itemCount > 1 else true
            countLabel.name = CELL_ITEM_COUNT
            labelTable.add(countLabel)

            addActor(background)
            addActor(itemTable)
            addActor(labelTable)
        }
    }

    private fun resolveItemTexture(item: Item): TextureRegion? {
        return assetManager.get<TextureAtlas>(item.skinID.atlas).findRegion(item.regionName)
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
        val cellContainer = inventoryCells[event.slot]
        if (cellContainer != null) {
            val image = cellContainer.findActor<Image>(CELL_ITEM_NAME)
            val label = cellContainer.findActor<Label>(CELL_ITEM_COUNT)

            val itemCount = event.inventoryItem?.count ?: 1
            label.isVisible = itemCount > 1
            label.setText(itemCount)

            event.inventoryItem?.texture?.let { textureRegion ->
                image.drawable = TextureRegionDrawable(textureRegion)
            } ?: run {
                image.drawable = null
            }
        }
        refreshCraftableRecipes()
    }

    override fun onCreate() {
        refreshCraftableRecipes()
        stage.addActor(fullscreenOverlay)
        dragActor?.let { stage.addActor(it) }
    }

    override fun onDestroy() {
        dragActor?.remove()
        fullscreenOverlay.remove()
    }

    companion object {
        private const val INVENTORY_CELL_SIZE = 64f
        private const val RESULT_CELL_SIZE = 56f
        private const val INGREDIENT_CELL_SIZE = 36f
        private const val CRAFTING_PANEL_MIN_WIDTH = 200f
    }
}
