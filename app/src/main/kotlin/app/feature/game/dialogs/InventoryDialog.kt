package app.feature.game.dialogs

import app.feature.game.ui.InventoryUI.Companion.CELL_BACKGROUND_NAME
import app.feature.game.ui.InventoryUI.Companion.CELL_ITEM_NAME
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Stack
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.gigapi.core.effects.LaunchedEffect
import com.gigapi.dialogs.Dialog
import com.gigapi.general.Context
import com.gigapi.setOnClickListener
import com.gigapi.texture.ColorDrawable
import core.assets.SkinID
import core.controls.UiInputProcessor

class InventoryDialog: LaunchedEffect, Dialog() {

    private lateinit var stage: Stage
    private lateinit var fullscreenOverlay: Table

    override fun launch(context: Context) {
        stage = context.getObject()
        val assetManager = context.getObject<AssetManager>()
        val skin = assetManager.get<Skin>(SkinID.BUTTON.skin)
        val itemBox = assetManager.get<TextureAtlas>(SkinID.BUTTON.atlas).findRegion("ic_item_box")

        fullscreenOverlay = Table().apply {
            setFillParent(true)
            background(ColorDrawable(0f, 0f, 0f, 0.6f))
        }

        val inventoryTable = Table().apply {
            defaults().size(56f, 56f).pad(4f)
            background(ColorDrawable(0.15f, 0.15f, 0.15f, 0.9f))
            pad(16f)
        }

        for (row in 0 until 4) {
            for (col in 0 until 8) {
                val index = row * 8 + col
                val cellContainer = Stack().apply {
                    setSize(64f, 64f)

                    val background = Image(itemBox)
                    background.name = CELL_BACKGROUND_NAME

                    val item = Image()
                    item.setSize(48f, 48f)
                    item.name = CELL_ITEM_NAME

                    addActor(background)
                    addActor(item)
                }.setOnClickListener {
                    println("clicked on $index")
                }
                inventoryTable.add(cellContainer)
            }
            inventoryTable.row()
        }

        fullscreenOverlay.add(inventoryTable).center()
    }

    override fun onCreate() {
        stage.addActor(fullscreenOverlay)
    }

    override fun onDestroy() {
        fullscreenOverlay.remove()
    }
}