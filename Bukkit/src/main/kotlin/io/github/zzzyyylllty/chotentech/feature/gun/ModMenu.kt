package io.github.zzzyyylllty.chotentech.feature.gun

import io.github.zzzyyylllty.chotentech.util.minimessage.mmPlainUtil
import io.github.zzzyyylllty.chotentech.util.minimessage.mmUtil
import io.github.zzzyyylllty.sertraline.Sertraline
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.util.Locale

/** P4 武器改装：点击槽位装入背包首个匹配配件，或卸下当前配件。 */
object ModMenu {
    const val SIZE = 27
    const val SLOT_PREVIEW = 0
    const val SLOT_ATTRIBUTES = 23
    const val SLOT_BACK = 26
    val SLOT_POSITIONS = linkedMapOf(
        "MUZZLE" to 10,
        "SIGHT" to 11,
        "GRIP" to 12,
        "MAGWELL" to 14,
        "STOCK" to 15,
    )

    class Holder : CTGunMenuHolder {
        private var backing: Inventory? = null
        fun attach(inventory: Inventory) { backing = inventory }
        override fun getInventory(): Inventory = backing
            ?: throw IllegalStateException("ModMenuHolder 尚未绑定背包")
    }

    fun open(player: Player) {
        val gun = GunRegistry.gunOf(player.inventory.itemInMainHand) ?: run {
            menuActionBar(player, "<#E08A98>主手不是枪械，无法改装")
            return
        }
        val holder = Holder()
        val inventory = Bukkit.createInventory(holder, SIZE, mmUtil.deserialize("<gold>武器改装 - ${gun.id}</gold>"))
        holder.attach(inventory)
        render(player, inventory)
        player.openInventory(inventory)
        GunMenuListener.onMenuOpened(player, inventory)
    }

    fun render(player: Player, inventory: Inventory) {
        val filler = menuItem(Material.GRAY_STAINED_GLASS_PANE, "<#2A3038> </#2A3038>")
        for (i in 0 until SIZE) inventory.setItem(i, filler)
        val held = player.inventory.itemInMainHand
        val gun = GunRegistry.gunOf(held) ?: run { player.closeInventory(); return }
        val installed = AddonManager.readAddons(held)
        inventory.setItem(SLOT_PREVIEW, preview(player, held, gun, installed))

        for ((slot, position) in SLOT_POSITIONS) {
            val id = installed[slot]
            val addon = GunRegistry.addon(id)
            inventory.setItem(position, when {
                gun.addonSlots.none { it.equals(slot, true) } -> menuItem(
                    Material.BARRIER, "<#6E7A8C>$slot</#6E7A8C>", listOf("<#E08A98>此枪不支持"),
                )
                addon != null -> addonIcon(player, addon)
                else -> menuItem(
                    icon(slot), "<#F2F5FA>$slot</#F2F5FA>",
                    listOf("<#A9B4C3>点击从背包装入第一个匹配配件"),
                )
            })
        }
        inventory.setItem(SLOT_ATTRIBUTES, attributesItem(gun, held))
        inventory.setItem(SLOT_BACK, menuItem(Material.ARROW, "<#EFDF8F>返回枪械菜单</#EFDF8F>"))
    }

    fun handleClick(player: Player, slot: Int) {
        if (slot == SLOT_BACK) {
            closeThen(player) { GunMenu.open(player) }
            return
        }
        val addonSlot = SLOT_POSITIONS.entries.firstOrNull { it.value == slot }?.key ?: return
        val held = player.inventory.itemInMainHand
        val gun = GunRegistry.gunOf(held) ?: run { player.closeInventory(); return }
        if (gun.addonSlots.none { it.equals(addonSlot, true) }) {
            menuActionBar(player, "<#E08A98>此枪不支持该配件槽")
            return
        }
        val oldId = AddonManager.readAddons(held)[addonSlot]
        val success = if (oldId != null) {
            val old = GunRegistry.addon(oldId)
            if (old == null) {
                menuActionBar(player, "<#E08A98>旧配件已失效，已中止卸下")
                return
            }
            // 先用旧 ID 构建返还物；任何构建失败都发生在 NBT 卸下之前。
            val returnItem = try {
                Sertraline.api().buildItem(oldId, player)
            } catch (_: Exception) {
                null
            }
            if (returnItem == null) {
                menuActionBar(player, "<#E08A98>返还配件构建失败，已中止卸下")
                return
            }
            val removed = AddonManager.uninstall(player, held, addonSlot)
            if (removed && !giveBack(player, returnItem)) {
                // 已在 uninstall 成功后才交付；交付异常则恢复完整旧配件 map。
                val restored = AddonManager.readAddons(held).apply { this[addonSlot] = oldId }
                if (!AddonManager.writeAddons(held, restored)) {
                    menuActionBar(player, "<#E08A98>配件返还及 NBT 回滚失败，请联系管理员")
                    render(player, player.openInventory.topInventory)
                    return
                }
                try { player.inventory.setItemInMainHand(held) } catch (_: Exception) { }
                false
            } else removed
        } else {
            val candidate = findCandidate(player, gun, addonSlot)
            if (candidate == null) {
                menuActionBar(player, "<#E08A98>背包里没有匹配该槽位、枪型和口径的配件")
                false
            } else AddonManager.install(player, held, candidate.second, candidate.first)
        }
        if (!success) {
            menuActionBar(player, "<#E08A98>改装失败，配件未消耗")
            render(player, player.openInventory.topInventory)
            return
        }
        // 改装会改变模型/瞄具状态；ADS 中直接退出，热成像也随之立即清理。
        if (GunAim.isAds(player)) {
            GunAim.setAds(player, false)
            menuActionBar(player, "<#DFBD7C>配件已变更，已退出瞄准以重算瞄具效果")
        } else {
            GunModel.applyModel(player, GunModel.State.GENERAL)
            menuActionBar(player, "<#8FD4A6>配件已变更")
        }
        GunHud.update(player)
        render(player, player.openInventory.topInventory)
    }

    private fun findCandidate(player: Player, gun: GunData, slot: String): Pair<Int, ItemStack>? {
        for (index in 0 until player.inventory.size) {
            val item = try { player.inventory.getItem(index) } catch (_: Exception) { null } ?: continue
            if (item.type.isAir) continue
            val addon = GunRegistry.addonOf(item) ?: continue
            if (AddonManager.compatible(gun, addon, slot)) return index to item
        }
        return null
    }

    private fun giveBack(player: Player, item: ItemStack): Boolean = try {
        val left = player.inventory.addItem(item)
        left.values.forEach { player.world.dropItemNaturally(player.location, it) }
        true
    } catch (_: Exception) {
        false
    }

    private fun preview(player: Player, held: ItemStack, gun: GunData, addons: Map<String, String>): ItemStack {
        val result = held.clone().apply { amount = 1 }
        val meta = result.itemMeta ?: return result
        val old = meta.lore() ?: emptyList()
        val lines = mutableListOf("<#6E7A8C>─────────────────────", "<#A9B4C3>口径 <#EFDF8F>${gun.caliber}")
        if (addons.isEmpty()) lines += "<#A9B4C3>已装配件 <#6E7A8C>无" else {
            for ((slot, id) in addons) lines += "<#A9B4C3>$slot <#EFDF8F>${addonName(player, id)}"
        }
        lines += "<#6E7A8C>─────────────────────"
        meta.lore(old + lines.map(mmUtil::deserialize))
        result.itemMeta = meta
        return result
    }

    private fun addonIcon(player: Player, addon: AddonData): ItemStack {
        val item = try { Sertraline.api().buildItem(addon.id, player) } catch (_: Exception) { null }
            ?: ItemStack(Material.PAPER)
        item.amount = 1
        val meta = item.itemMeta ?: return item
        val lore = (meta.lore() ?: emptyList()).toMutableList()
        lore += mmUtil.deserialize("<#6E7A8C>槽位 ${addon.slot}")
        for ((key, value) in addon.attributes) lore += mmUtil.deserialize(coloredDelta(key, value))
        if (addon.zoom != 1.0) lore += mmUtil.deserialize("<#A9B4C3>瞄具倍率 <#EFDF8F>${num(addon.zoom)}x")
        if (addon.suppressor > 0.0) lore += mmUtil.deserialize("<#A9B4C3>音量倍率 <#EFDF8F>${num(addon.suppressor)}")
        if (addon.thermal) lore += mmUtil.deserialize("<#8FD4A6>热成像")
        lore += mmUtil.deserialize("<#DFBD7C>点击卸下")
        meta.lore(lore)
        item.itemMeta = meta
        return item
    }

    private fun attributesItem(gun: GunData, held: ItemStack): ItemStack {
        val base = gun.attributes
        val effective = AddonManager.effectiveAttributes(gun, held)
        val total = mapOf(
            "machineEfficiency" to effective.machineEfficiency - base.machineEfficiency,
            "accuracy" to effective.accuracy - base.accuracy,
            "bulletSpeed" to effective.bulletSpeed - base.bulletSpeed,
            "fireRate" to effective.fireRate - base.fireRate,
            "barrelLength" to effective.barrelLength - base.barrelLength,
            "range" to effective.range - base.range,
            "recoilV" to effective.recoilV - base.recoilV,
            "recoilH" to effective.recoilH - base.recoilH,
            "durability" to effective.durability - base.durability,
        )
        val lines = mutableListOf("<#A9B4C3>口径 <#EFDF8F>${gun.caliber}", "<#6E7A8C>当前配件实际差值（已钳制）")
        for (key in ATTRIBUTE_KEYS) lines += coloredDelta(key, total[key] ?: 0.0)
        return menuItem(Material.PAPER, "<gold>属性加成</gold>", lines)
    }

    private fun coloredDelta(key: String, value: Double): String = when {
        value > 0.0 -> "<#A9B4C3>$key <green>+${num(value)}</green>"
        value < 0.0 -> "<#A9B4C3>$key <red>${num(value)}</red>"
        else -> "<#6E7A8C>$key ±0"
    }

    private fun addonName(player: Player, id: String): String = try {
        val component = Sertraline.api().buildItem(id, player)?.itemMeta?.displayName()
        component?.let(mmPlainUtil::serialize)?.trim()?.ifEmpty { id } ?: id
    } catch (_: Exception) { id }

    private fun icon(slot: String): Material = when (slot) {
        "MUZZLE" -> Material.BLAST_FURNACE
        "SIGHT" -> Material.SPYGLASS
        "GRIP" -> Material.STICK
        "MAGWELL" -> Material.HOPPER
        else -> Material.BRICK
    }

    private fun num(value: Double): String {
        val rounded = Math.round(value * 10.0) / 10.0
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString()
        else String.format(Locale.US, "%.1f", rounded)
    }

    private val ATTRIBUTE_KEYS = listOf(
        "machineEfficiency", "accuracy", "bulletSpeed", "fireRate", "barrelLength",
        "range", "recoilV", "recoilH", "durability",
    )
}
