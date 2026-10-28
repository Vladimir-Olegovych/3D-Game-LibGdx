package core.crafting

import app.feature.game.ecs.components.InventoryComponent
import com.gigapi.effects.LaunchedEffect
import com.gigapi.general.GContext
import core.items.ItemManager

class CraftManager: LaunchedEffect {

    private lateinit var itemManager: ItemManager

    override fun launch(gContext: GContext) {
        itemManager = gContext.getObject()
    }

    fun canCraft(
        component: InventoryComponent,
        recipe: CraftingRecipeData
    ): Boolean {
        return recipe.ingredients.isNotEmpty() &&
               recipe.ingredients.all {
                   component.countItem(it.item) >= it.count
               }
    }

    fun craftItem(
        component: InventoryComponent,
        recipe: CraftingRecipeData
    ): Boolean {
        if (!canCraft(component, recipe)) return false
        val resultItem = itemManager.getItem(recipe.result.item) ?: return false
        val resultCount = recipe.result.count.coerceAtLeast(1)

        for (ingredient in recipe.ingredients) {
            if (!component.removeItem(ingredient.item, ingredient.count)) return false
        }

        if (!component.addItem(resultItem, resultCount)) {
            for (ingredient in recipe.ingredients) {
                val ingredientItem = itemManager.getItem(ingredient.item) ?: continue
                component.addItem(ingredientItem, ingredient.count)
            }
            return false
        }
        return true
    }


}