package app.feature.game.ecs.systems

import app.feature.game.dialogs.InventoryDialog
import app.feature.game.dialogs.PauseDialog
import app.feature.game.dialogs.PauseDialogCallbacks
import app.feature.game.ecs.components.InventoryComponent
import app.feature.game.ui.AimUI
import app.feature.game.ui.DataUI
import app.feature.game.ui.InventoryUI
import com.artemis.BaseSystem
import com.artemis.ComponentMapper
import com.artemis.annotations.Wire
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.scenes.scene2d.Stage
import com.gigapi.dialogs.DialogManager
import com.gigapi.storage.json.AppConfig
import com.gigapi.viewport.UnfairViewport
import core.client.GameServerAddress
import core.configs.ConfigTypes
import core.controls.PInputProcessor
import core.controls.UInputProcessor
import core.crafting.CraftManager
import core.crafting.CraftingRecipeSO
import core.defaults.PlayerConstants
import core.items.ItemManager
import core.navigation.Navigation
import core.viewport.ViewportTypes

class UISystem(
    private val onQuit: () -> Unit,
    private val openLanWorld: (GameServerAddress) -> Boolean
): UInputProcessor.InputListener, BaseSystem() {
    //Custom
    @Wire(name = ViewportTypes.UNFAIR)
    private lateinit var viewport: UnfairViewport
    @Wire(name = ConfigTypes.CRAFTING_RECIPES_SO)
    private lateinit var craftConfig: AppConfig<CraftingRecipeSO>
    @Wire
    private lateinit var stage: Stage
    @Wire
    private lateinit var itemManager: ItemManager
    @Wire
    private lateinit var craftManager: CraftManager
    @Wire
    private lateinit var assetManager: AssetManager
    @Wire
    private lateinit var dialogManager: DialogManager
    @Wire
    private lateinit var navigation: Navigation.Game
    @Wire
    private lateinit var inputMultiplexer: InputMultiplexer
    @Wire
    private lateinit var pInputProcessor: PInputProcessor
    @Wire
    private lateinit var uInputProcessor: UInputProcessor
    //UI
    private lateinit var inventoryUI: InventoryUI
    @Wire
    private lateinit var aimUI: AimUI
    @Wire
    private lateinit var dataUI: DataUI
    //Mappers
    private lateinit var inventoryMapper: ComponentMapper<InventoryComponent>
    //Dialogs
    private lateinit var inventoryDialog: InventoryDialog
    private lateinit var pauseDialog: PauseDialog

    override fun initialize() {
        val playerEntityId = PlayerConstants.getLocalPlayerEntityId()
        val playerInventoryComponent = inventoryMapper[playerEntityId]

        val pauseDialogCallbacks = PauseDialogCallbacks(
            onDismiss = {
                inputMultiplexer.addProcessor(pInputProcessor)
                Gdx.input.isCursorCatched = true
            },
            onResume = {},
            onSettings = {},
            openLanWorld = openLanWorld,
            onQuit = {
                onQuit.invoke()
            },
        )
        pauseDialog = PauseDialog(
            stage = stage,
            navigation = navigation,
            assetManager = assetManager,
            callbacks = pauseDialogCallbacks
        ).apply { attach(dialogManager) }

        pauseDialog.attach(dialogManager)

        inventoryDialog = InventoryDialog(
            stage = stage,
            craftManager = craftManager,
            itemManager = itemManager,
            assetManager = assetManager,
            craftConfig = craftConfig,
            inventoryComponent = playerInventoryComponent
        )
        inventoryDialog.attach(dialogManager)

        inventoryUI = InventoryUI(
            assetManager = assetManager,
            inventoryComponent = playerInventoryComponent
        )

        stage.addActor(aimUI.getUI())
        stage.addActor(dataUI.getUI())
        stage.addActor(inventoryUI.getUI())

        uInputProcessor.addListener(this)
    }

    override fun onInventory() {
        if (pauseDialog.isShowed()) return

        if (inventoryDialog.isShowed()) {
            Gdx.input.isCursorCatched = true
            inventoryDialog.dismiss()
            inventoryUI.getUI().isVisible = true
            inputMultiplexer.addProcessor(pInputProcessor)
        } else {
            Gdx.input.isCursorCatched = false
            inventoryDialog.show()
            inventoryUI.getUI().isVisible = false
            pInputProcessor.clear()
            inputMultiplexer.removeProcessor(pInputProcessor)
        }
    }

    override fun onPause() {
        if (inventoryDialog.isShowed()) {
            onInventory()
            return
        }

        if (pauseDialog.isShowed()) {
            Gdx.input.isCursorCatched = true
            pauseDialog.dismiss()
            inputMultiplexer.addProcessor(pInputProcessor)
        } else {
            Gdx.input.isCursorCatched = false
            pauseDialog.show()
            pInputProcessor.clear()
            inputMultiplexer.removeProcessor(pInputProcessor)
        }
    }

    override fun processSystem() {
        dataUI.update(world.delta)

        stage.act(world.delta)
        viewport.apply()
        stage.draw()
    }

    override fun dispose() {
        uInputProcessor.removeListener(this)

        pauseDialog.unattach()
        inventoryDialog.unattach()
        stage.clear()
    }
}