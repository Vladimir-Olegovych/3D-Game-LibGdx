package core.crafting

import com.fasterxml.jackson.annotation.JsonProperty

class CraftingRecipeSO(
    @get:JsonProperty("recipes") @param:JsonProperty("recipes")
    val recipes: List<CraftingRecipeData> = emptyList()
)

class CraftingRecipeData(
    @get:JsonProperty("result") @param:JsonProperty("result")
    val result: RecipeItemData = RecipeItemData(),
    @get:JsonProperty("ingredients") @param:JsonProperty("ingredients")
    val ingredients: List<RecipeItemData> = emptyList()
)

class RecipeItemData(
    /** Item id — for now BlockType name (e.g. "STONE", "WOOD"). */
    @get:JsonProperty("item") @param:JsonProperty("item")
    val item: String = "",
    @get:JsonProperty("count") @param:JsonProperty("count")
    val count: Int = 1
)
