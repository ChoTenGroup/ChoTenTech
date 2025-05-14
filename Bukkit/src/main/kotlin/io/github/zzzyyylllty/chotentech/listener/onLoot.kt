package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.ChoTenTech.lootList
import io.github.zzzyyylllty.chotentech.data.Loot
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.block.Chest
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.event.SubscribeEvent
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.PageableChest
import taboolib.platform.util.buildItem
import java.time.LocalDateTime

@SubscribeEvent
fun onLoot(e: PlayerInteractEvent) {
    // TODO DEBOUNCE/THROLLTE
    if (e.hand != EquipmentSlot.HAND) return
    val loot = lootList[e.clickedBlock?.location]
    if (loot == null) return
    if (loot.refreshTimeEnd?.isAfter(LocalDateTime.now()) == false) {
        // Not Refresh, Use Caches Instead.
    } else {
        // Re-Generate Items
        regenItem(loot)
    }
    openLoot(e.player, loot)
}

fun regenItem(loot: Loot) {

}

fun openLoot(player: Player,loot: Loot) {
    player.openMenu<PageableChest<String>>("物资箱") {
        map(
            "#########",
            "#########",
            "#########",
        )
        slotsBy('#')
        elements {
            loot.items.values as List<String>
        }
        onGenerate { player, element, index, slot ->
            buildItem(XMaterial.STONE) {
                name = "§f${element}"
            }
        }
    }
}