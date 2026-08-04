package core.items

import app.feature.game.event.EventBusTypes
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.gigapi.effects.DisposableEffect
import com.gigapi.effects.LaunchedEffect
import com.gigapi.eventbus.EventBus
import com.gigapi.general.GContext
import com.gigapi.mesh.RawMeshData
import com.gigapi.storage.json.AppConfig
import core.assets.SkinID
import core.blocks.BlockDataManager
import core.blocks.BlockType
import core.configs.ConfigTypes
import core.mesh.MeshUtils


class ItemManager: DisposableEffect, LaunchedEffect {

    private val itemMap = HashMap<String, Item>()
    private val itemMeshMap = HashMap<String, RawMeshData>()

    private lateinit var blockDataManager: BlockDataManager
    private lateinit var assetManager: AssetManager
    private lateinit var eventBus: EventBus

    override fun dispose() {
        itemMeshMap.clear()
    }

    override fun launch(gContext: GContext) {
        blockDataManager = gContext.getObject()
        assetManager = gContext.getObject()
        eventBus = gContext.getObject(EventBusTypes.MAIN_EVENT_BUS)
        eventBus.registerHandler(this)

        val config = gContext.getObject<AppConfig<ItemDataSO>>(ConfigTypes.ITEM_DATA_SO)
        val itemsData = config.getConfig()

        for (blockType in BlockType.entries) {
            val blockData = blockDataManager.getBlockTextureDataMap()[blockType] ?: continue
            itemMap[blockType.name] = Item(
                id = blockType.name,
                name = blockType.name.lowercase(),
                description = "Block",
                skinID = SkinID.BLOCK,
                regionName = blockData.regionNameSide,
                maxStack = 64,
                stackable = true
            )
        }

        for (item in itemsData.items) {
            itemMap[item.id] = item
        }

        for (blockType in BlockType.entries) {
            itemMeshMap[blockType.name] = MeshUtils.createBlockMeshData(blockDataManager, blockType, 0.4f)?: continue
        }

        val itemAtlas = assetManager.get<TextureAtlas>(SkinID.ITEM.atlas)
        for (itemType in ItemType.entries) {
            val item = itemMap[itemType.name] ?: continue
            itemMeshMap[itemType.name] = MeshUtils.createItemMeshData(itemAtlas, item) ?: continue
        }
    }

    fun getItemModel(id: String): RawMeshData? {
        return itemMeshMap[id]
    }

    fun getItem(id: String): Item? {
        return itemMap[id]
    }

}