package core.items

import com.fasterxml.jackson.annotation.JsonProperty

class ItemDataSO(
    @get:JsonProperty("items") @param:JsonProperty("items")
    val items: List<Item> = emptyList()
)