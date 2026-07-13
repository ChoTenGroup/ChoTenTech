package io.github.zzzyyylllty.chotentech.gui

import io.github.zzzyyylllty.chotentech.data.CosmeticConfig
import io.github.zzzyyylllty.chotentech.data.CosmeticSlot
import io.github.zzzyyylllty.chotentech.data.cosmeticConfigs
import io.github.zzzyyylllty.chotentech.data.getCosmeticsBySlot
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.util.minimessage.mmJsonUtil
import io.github.zzzyyylllty.chotentech.util.toComponent
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.PageableChest
import io.github.zzzyyylllty.chotentech.util.guiItem
import java.util.concurrent.ConcurrentHashMap

private val showAllMap = ConcurrentHashMap<String, Boolean>()
private val patternFilterMap = ConcurrentHashMap<String, String?>()

@Awake(LifeCycle.DISABLE)
fun clearCosmeticMenuMaps() {
    showAllMap.clear()
    patternFilterMap.clear()
}

private val typeIcons = mapOf(
    CosmeticSlot.AURA to XMaterial.END_CRYSTAL,
    CosmeticSlot.FOOTSTEP to XMaterial.LEATHER_BOOTS,
    CosmeticSlot.KILL_PARTICLE to XMaterial.CROSSBOW,
    CosmeticSlot.KILL_MESSAGE to XMaterial.OAK_SIGN
)

private val typeDisplayNames = mapOf(
    CosmeticSlot.AURA to "<gold>粒子环绕",
    CosmeticSlot.FOOTSTEP to "<aqua>脚步粒子",
    CosmeticSlot.KILL_PARTICLE to "<red>击杀粒子",
    CosmeticSlot.KILL_MESSAGE to "<light_purple>击杀信息"
)

class CosmeticMenu {

    fun open(player: Player) {
        val data = player.getData() ?: return
        val uuid = player.uniqueId.toString()
        val showAll = showAllMap.getOrDefault(uuid, false)
        val slots = CosmeticSlot.entries.toList()

        player.openMenu<PageableChest<CosmeticSlot>>(
            mmJsonUtil.serialize("<white><shift:-16><image:kangelicon:list><shift:-176><aqua><font:minecraft:fusionpixel10>装饰品".toComponent())
        ) {
            rows(6)

            map(
                "CCC    HF",
                "         ",
                " @@@@@@@ ",
                " @@@@@@@ ",
                "         ",
                "         "
            )

            slotsBy('@')

            elements { slots }

            onGenerate { _, slot, _, _ ->
                buildTypeButton(slot, data.cosmetics[slot.name]?.let { cosmeticConfigs[it]?.name } ?: "无")
            }

            onClick { event, slot ->
                val clicker = event.clicker
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                clicker.closeInventory()
                openType(clicker, slot)
            }

            set('C', buildReturnButton(0)) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "invero menu open menu ${clicker.name}")
                clicker.closeInventory()
            }

            // H: toggle show all / permission-only
            set('H', buildToggleItem(showAll)) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                showAllMap[clicker.uniqueId.toString()] = !showAll
                clicker.closeInventory()
                open(clicker)
            }

            // F: stats
            set('F', buildStatsItem(data)) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                clicker.closeInventory()
                open(clicker)
            }
        }
    }

    private fun openType(player: Player, slot: CosmeticSlot) {
        val data = player.getData() ?: return
        val uuid = player.uniqueId.toString()
        val showAll = showAllMap.getOrDefault(uuid, false)
        val patternDisplay = patternFilterMap[uuid]
        val hasParticles = slot != CosmeticSlot.KILL_MESSAGE

        val available = getCosmeticsBySlot(slot).filter {
            if (!showAll) it.permission == null || player.hasPermission(it.permission) else true
        }

        val titleKey = typeDisplayNames[slot] ?: slot.name
        val mapRows = if (hasParticles) {
            listOf(
                "CCC   P<>",
                "         ",
                " @@@@@@@ ",
                " @@@@@@@ ",
                " @@@@@@@ ",
                "         "
            )
        } else {
            listOf(
                "CCC     <>",
                "         ",
                " @@@@@@@ ",
                " @@@@@@@ ",
                " @@@@@@@ ",
                "         "
            )
        }

        player.openMenu<PageableChest<CosmeticConfig>>(
            mmJsonUtil.serialize("<white><shift:-16><image:kangelicon:list><shift:-176><aqua><font:minecraft:fusionpixel10>$titleKey".toComponent())
        ) {
            rows(6)

            map(*mapRows.toTypedArray())

            slotsBy('@')

            elements {
                val unequip = CosmeticConfig("", slot, "卸下装饰", null, "BARRIER", null, null, 0, 0.0, 0.0, 0.0, null, null)
                listOf(unequip) + available
            }

            onGenerate { p, cosmetic, _, _ ->
                val equippedId = data.cosmetics[cosmetic.slot.name]
                val locked = cosmetic.permission != null && !p.hasPermission(cosmetic.permission)
                val isActive = if (cosmetic.id.isEmpty()) equippedId != null else cosmetic.id == equippedId
                buildCosmeticItem(cosmetic, isActive, locked)
            }


            onClick { event, cosmetic ->
                val clicker = event.clicker
                if (cosmetic.permission != null && !clicker.hasPermission(cosmetic.permission)) {
                    clicker.sendMessage("<red>你没有权限使用此装饰品".toComponent())
                    clicker.playSound(clicker.location, Sound.ENTITY_VILLAGER_NO, 1f, 1f)
                    return@onClick
                }
                if (cosmetic.id.isEmpty()) {
                    clicker.getData()?.cosmetics?.remove(cosmetic.slot.name)
                    clicker.sendMessage("<gray>已卸下装饰".toComponent())
                    clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                } else {
                    clicker.getData()?.cosmetics?.put(cosmetic.slot.name, cosmetic.id)
                    clicker.sendMessage("<green>已装备: <yellow>${cosmetic.name}".toComponent())
                    clicker.playSound(clicker.location, Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1f, 1f)
                }
                resetElementsCache()
                clicker.closeInventory()
                openType(clicker, slot)
            }

            onPageChange { p ->
                p.playSound(p.location, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f)
            }

            set('C', guiItem(XMaterial.COMPASS, "<gold>返回", listOf("<gray>点击返回装饰品主菜单"), model = "kangelitem:void")) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                clicker.closeInventory()
                open(clicker)
            }

            if (hasParticles) {
                set('P', buildPatternItem(patternDisplay)) {
                    clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                    val current = patternFilterMap[clicker.uniqueId.toString()]
                    patternFilterMap[clicker.uniqueId.toString()] = when (current) {
                        null -> "scatter"
                        "scatter" -> "orbit"
                        "orbit" -> "halo"
                        "halo" -> "spiral"
                        "spiral" -> "wave"
                        "wave" -> null
                        else -> null
                    }
                    val newDisplay = patternFilterMap[clicker.uniqueId.toString()]
                    clicker.openInventory.topInventory.setItem(5, buildPatternItem(newDisplay))
                }
            }

            setPreviousPage(7) { _, page ->
                guiItem(XMaterial.ARROW, "<green>上一页", model = if (page) "kangelitem:previous" else "kangelitem:previous_no")
            }

            setNextPage(8) { _, page ->
                guiItem(XMaterial.ARROW, "<green>下一页", model = if (page) "kangelitem:next" else "kangelitem:next_no")
            }
        }
    }

    private fun buildReturnButton(index: Int): ItemStack {
        return when (index) {
            0 -> {
                val item = XMaterial.COMPASS.parseItem() ?: ItemStack(Material.COMPASS)
                val meta = item.itemMeta ?: return item
                meta.displayName("<aqua><bold>菜单".toComponent())
                meta.lore(listOf("<gray>点击打开主菜单".toComponent()))
                meta.itemModel = NamespacedKey.fromString("kangelitem:void")
                item.setItemMeta(meta)
                item
            }
            1 -> {
                val item = XMaterial.BARRIER.parseItem() ?: ItemStack(Material.BARRIER)
                val meta = item.itemMeta ?: return item
                meta.displayName("<red><bold>关闭".toComponent())
                meta.lore(listOf("<gray>点击关闭菜单".toComponent()))
                item.setItemMeta(meta)
                item
            }
            else -> {
                val item = XMaterial.OAK_DOOR.parseItem() ?: ItemStack(Material.OAK_DOOR)
                val meta = item.itemMeta ?: return item
                meta.displayName("<gray><bold>返回".toComponent())
                meta.lore(listOf("<gray>点击返回".toComponent()))
                item.setItemMeta(meta)
                item
            }
        }
    }

    private fun buildTypeButton(slot: CosmeticSlot, equippedName: String): ItemStack {
        val icon = typeIcons[slot] ?: XMaterial.BARRIER
        val displayName = typeDisplayNames[slot] ?: slot.name
        val item = icon.parseItem() ?: ItemStack(Material.BARRIER)
        val meta = item.itemMeta ?: return item

        meta.displayName("<bold>$displayName".toComponent())
        meta.lore(listOf(
            "<!i><gray>当前: <yellow>$equippedName".toComponent(),
            "<!i>".toComponent(),
            "<!i><green>点击选择装饰".toComponent()
        ))

        item.setItemMeta(meta)
        return item
    }

    private fun buildToggleItem(showAll: Boolean): ItemStack {
        val icon = if (showAll) XMaterial.WRITABLE_BOOK else XMaterial.BOOK
        val item = icon.parseItem() ?: ItemStack(Material.BOOK)
        val meta = item.itemMeta ?: return item

        if (showAll) {
            meta.displayName("<gold>显示模式: <green>全部装饰".toComponent())
            meta.lore(listOf(
                "<!i><gray>当前显示所有装饰品".toComponent(),
                "<!i><gray>点击切换为仅显示已解锁".toComponent()
            ))
        } else {
            meta.displayName("<gold>显示模式: <gray>仅已解锁".toComponent())
            meta.lore(listOf(
                "<!i><gray>当前仅显示有权限的装饰品".toComponent(),
                "<!i><gray>点击切换为显示全部".toComponent()
            ))
        }

        item.setItemMeta(meta)
        return item
    }

    private fun buildStatsItem(data: io.github.zzzyyylllty.chotentech.data.PlayerData): ItemStack {
        val item = XMaterial.COMPASS.parseItem() ?: ItemStack(Material.COMPASS)
        val meta = item.itemMeta ?: return item

        val total = cosmeticConfigs.size
        val equipped = data.cosmetics.size

        meta.displayName("<gold>装饰品进度".toComponent())
        meta.lore(listOf(
            "<!i><gray>已装备: <green>$equipped <gray>/ <yellow>${CosmeticSlot.entries.size}".toComponent(),
            "<!i><gray>总装饰品: <yellow>$total".toComponent(),
            "<!i>".toComponent(),
            "<!i><gray>点击刷新".toComponent()
        ))

        item.setItemMeta(meta)
        return item
    }

    private fun buildPatternItem(currentPattern: String?): ItemStack {
        val item = XMaterial.FIREWORK_STAR.parseItem() ?: ItemStack(Material.FIREWORK_STAR)
        val meta = item.itemMeta ?: return item

        val patternDisplay = when (currentPattern) {
            "scatter" -> "<green>散发"
            "orbit" -> "<aqua>环绕"
            "halo" -> "<light_purple>光环"
            "spiral" -> "<gold>螺旋"
            "wave" -> "<yellow>波浪"
            else -> "<gray>默认"
        }

        meta.displayName("<gold>粒子展示: $patternDisplay".toComponent())
        meta.lore(listOf(
            "<!i><gray>当前: $patternDisplay".toComponent(),
            "<!i>".toComponent(),
            "<!i><gray>点击切换展示方式".toComponent(),
            "<!i><gray>散发 → 环绕 → 光环 → 螺旋 → 波浪 → 默认".toComponent()
        ))

        item.setItemMeta(meta)
        return item
    }

    private fun buildCosmeticItem(cosmetic: CosmeticConfig, isActive: Boolean, locked: Boolean = false): ItemStack {
        val mat = if (cosmetic.id.isEmpty()) {
            ItemStack(Material.BARRIER)
        } else {
            try {
                XMaterial.valueOf(cosmetic.material.uppercase()).parseItem()
            } catch (_: Exception) {
                null
            } ?: ItemStack(Material.BARRIER)
        }

        val meta = mat.itemMeta ?: return mat

        if (!cosmetic.model.isNullOrBlank()) {
            NamespacedKey.fromString(cosmetic.model)?.let { meta.itemModel = it }
        }

        if (isActive) {
            meta.setEnchantmentGlintOverride(true)
        }

        val (statusColor, statusText) = if (cosmetic.id.isEmpty()) {
            if (isActive) "<red>" to "点击卸下"
            else "<gray>" to "未装备"
        } else when {
            locked -> "<red>" to "暂无权限"
            isActive -> "<green>" to "已装备"
            else -> "<gray>" to "点击装备"
        }

        meta.displayName("${statusColor}<bold>${cosmetic.name}".toComponent())

        val lore = mutableListOf<String>()
        lore += "<!i><dark_gray><st>${" ".repeat(60)}</st>"

        if (cosmetic.id.isEmpty()) {
            if (isActive) lore += "<!i><gray>点击卸下当前装备的装饰品"
            else lore += "<!i><gray>当前未装备任何装饰品"
        } else {
            val slotName = slotDisplayName(cosmetic.slot)
            lore += "<!i><dark_aqua>类型: <white>$slotName"

            if (!cosmetic.particle.isNullOrBlank()) {
                lore += "<!i><dark_aqua>粒子: <white>${cosmetic.particle}"
            }
            if (!cosmetic.particlePattern.isNullOrBlank()) {
                val patternName = when (cosmetic.particlePattern) {
                    "scatter" -> "散发"
                    "orbit" -> "环绕"
                    "halo" -> "光环"
                    "spiral" -> "螺旋"
                    "wave" -> "波浪"
                    else -> cosmetic.particlePattern
                }
                lore += "<!i><dark_aqua>样式: <white>$patternName"
            }
            if (!cosmetic.messageFormat.isNullOrBlank()) {
                lore += "<!i><dark_aqua>格式: <white>${cosmetic.messageFormat}"
            }
            if (cosmetic.particleCount > 0) {
                lore += "<!i><dark_aqua>数量: <white>${cosmetic.particleCount}"
            }
            if (cosmetic.particleRadius > 0 && cosmetic.particlePattern != null) {
                lore += "<!i><dark_aqua>半径: <white>${cosmetic.particleRadius}"
            }
        }

        lore += "<!i><dark_gray><st>${" ".repeat(60)}</st>"
        lore += "<!i>${statusColor}${statusText}"

        meta.lore(lore.map { it.toComponent() })
        mat.setItemMeta(meta)
        return mat
    }

    private fun slotDisplayName(slot: CosmeticSlot): String = when (slot) {
        CosmeticSlot.AURA -> "粒子环绕"
        CosmeticSlot.FOOTSTEP -> "脚步粒子"
        CosmeticSlot.KILL_PARTICLE -> "击杀粒子"
        CosmeticSlot.KILL_MESSAGE -> "击杀信息"
    }
}
