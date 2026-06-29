package app.feature.game.ui

import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Stack
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.gigapi.core.effects.LaunchedEffect
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

    private val inventoryCells = ArrayList<Stack>()

    override fun launch(context: Context) {
        val assetManager = context.getObject<AssetManager>()
        val itemBox = assetManager.get<TextureAtlas>(SkinID.BUTTON.atlas).findRegion("ic_item_box")

        val inventoryTable = Table().apply {
            defaults().size(64f, 64f).pad(2f)
        }

        for (index in 0 until InventoryManager.TOOL_BAR_SIZE) {
            val cellContainer = Stack().apply {
                setSize(64f, 64f)

                val background = Image(itemBox)
                background.name = CELL_BACKGROUND_NAME

                val item = Image()
                item.setSize(48f, 48f)
                item.name = CELL_ITEM_NAME

                addActor(background)
                addActor(item)
            }
            inventoryCells.add(cellContainer)
            inventoryTable.add(cellContainer)
            cellContainer.setColor(1f, 1f, 1f, 1f)
        }

        layout.add(inventoryTable).center().bottom()
    }

    override fun getUI(): Actor {
        return layout
    }

    companion object {
        const val CELL_ITEM_NAME = "CELL_ITEM_NAME"
        const val CELL_BACKGROUND_NAME = "CELL_BACKGROUND_NAME"
    }
}