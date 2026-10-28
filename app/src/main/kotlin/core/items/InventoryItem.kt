package core.items


data class InventoryItem(
    val item: Item,
    var count: Int
) {
    fun refreshCount() {

    }
}