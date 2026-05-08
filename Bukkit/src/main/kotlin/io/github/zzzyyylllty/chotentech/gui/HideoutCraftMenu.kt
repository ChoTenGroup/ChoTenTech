package io.github.zzzyyylllty.chotentech.gui

import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.function.hideout.HideoutConfig
import io.github.zzzyyylllty.chotentech.function.hideout.HideoutManager
import io.github.zzzyyylllty.chotentech.function.hideout.HideoutRecipe
import io.github.zzzyyylllty.chotentech.player.sendComponent
import io.github.zzzyyylllty.chotentech.util.minimessage.mmJsonUtil
import io.github.zzzyyylllty.chotentech.util.toComponent
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.PageableChest

class HideoutCraftMenu(private val baseKey: String) {

    fun open(player: Player) {
        val data = player.getData() ?: return
        val hideout = data.hideoutData[baseKey] ?: return
        val craftLevel = hideout.getModuleLevel("crafting_station")
        if (craftLevel <= 0) {
            player.sendComponent("<red>合成站未建造！")
            return
        }

        val recipes = HideoutConfig.getRecipesForModule("crafting_station")
            .filter { craftLevel >= it.minLevel }

        player.openMenu<PageableChest<HideoutRecipe>>(
            mmJsonUtil.serialize("<white><shift:-16><image:kangelicon:list><shift:-176><aqua><font:minecraft:fusionpixel10>合成站 - 配方".toComponent())
        ) {
            rows(6)
            map(
                "CCC  Q  <>",
                "         ",
                " ####### ",
                " ####### ",
                " ####### ",
                "         "
            )
            slotsBy('#')

            elements { recipes }

            onGenerate { _, recipe, _, _ ->
                buildRecipeItem(recipe, hideout)
            }

            onClick { event, recipe ->
                event.isCancelled = true
                if (hideout.fuelAmount <= 0) {
                    event.clicker.sendComponent("<red>发电机未运行！")
                    return@onClick
                }
                if (hideout.craftingQueues.any { it.recipeId == recipe.id }) {
                    event.clicker.sendComponent("<red>该配方已在合成中！")
                    return@onClick
                }
                event.clicker.closeInventory()
                if (HideoutManager.startCrafting(event.clicker, recipe.id)) {
                    HideoutMainMenu(baseKey).open(event.clicker)
                } else {
                    HideoutCraftMenu(baseKey).open(event.clicker)
                }
            }

            set('C', ItemStack(Material.COMPASS).apply {
                editMeta { m ->
                    m.displayName("<b><aqua>返回".toComponent())
                    m.lore(listOf("<gray>返回基地总览".toComponent()))
                    m.itemModel = NamespacedKey.fromString("kangelitem:void")
                }
            }) {
                clicker.closeInventory()
                HideoutMainMenu(baseKey).open(clicker)
            }

            set('Q', buildQueueItem(hideout)) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
            }

            onPageChange { p ->
                p.playSound(p.location, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f)
            }

            setPreviousPage(7) { _, page ->
                ItemStack(Material.ARROW).apply {
                    editMeta { m ->
                        m.displayName("<aqua><b>上一页".toComponent())
                        m.itemModel = NamespacedKey.fromString(if (page) "kangelitem:previous" else "kangelitem:previous_no")
                    }
                }
            }

            setNextPage(8) { _, page ->
                ItemStack(Material.ARROW).apply {
                    editMeta { m ->
                        m.displayName("<aqua><b>下一页".toComponent())
                        m.itemModel = NamespacedKey.fromString(if (page) "kangelitem:next" else "kangelitem:next_no")
                    }
                }
            }
        }
    }

    private fun buildRecipeItem(
        recipe: HideoutRecipe,
        hideout: io.github.zzzyyylllty.chotentech.data.HideoutPlayerData
    ): ItemStack {
        val item = recipe.outputs.firstOrNull()?.toItemStack() ?: ItemStack(Material.BARRIER)
        item.editMeta { m ->
            m.displayName("<gold>${recipe.id}".toComponent())
            val queued = hideout.craftingQueues.any { it.recipeId == recipe.id }
            val running = hideout.fuelAmount > 0

            val lore = mutableListOf<Component>()
            lore += "<!i><gray>合成时间: <yellow>${recipe.craftTime} 秒".toComponent()
            if (recipe.usageCost > 0) lore += "<!i><gray>额外能耗: <yellow>${recipe.usageCost}/s".toComponent()
            lore += Component.empty()
            lore += "<!i><gray>材料:".toComponent()
            for (input in recipe.inputs) {
                lore += "<!i> <gray>- <white>${input.sertralineId ?: input.material} <gray>x${input.amount}".toComponent()
            }
            lore += "<!i><gray>产出:".toComponent()
            for (out in recipe.outputs) {
                lore += "<!i> <gray>- <white>${out.sertralineId ?: out.material} <gray>x${out.amount}".toComponent()
            }
            lore += Component.empty()
            when {
                queued -> lore += "<!i><green>✓ 已在合成队列中".toComponent()
                !running -> lore += "<!i><red>✗ 发电机未运行".toComponent()
                else -> lore += "<!i><gray>点击开始合成".toComponent()
            }
            m.lore(lore)
        }
        return item
    }

    private fun buildQueueItem(
        hideout: io.github.zzzyyylllty.chotentech.data.HideoutPlayerData
    ): ItemStack = ItemStack(Material.CLOCK).apply {
        editMeta { m ->
            m.displayName("<gold>合成队列".toComponent())
            val lore = mutableListOf<Component>()
            if (hideout.craftingQueues.isEmpty()) {
                lore += "<!i><gray>队列为空".toComponent()
            } else {
                for (q in hideout.craftingQueues) {
                    val name = HideoutConfig.getRecipe(q.recipeId)?.id ?: q.recipeId
                    lore += "<!i><gray>- <yellow>$name <gray>剩余 ${q.remainingTime} 秒".toComponent()
                }
            }
            m.lore(lore)
        }
    }
}
