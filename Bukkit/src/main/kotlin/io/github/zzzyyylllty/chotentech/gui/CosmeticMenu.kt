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
import io.github.zzzyyylllty.chotentech.util.loreInfo
import io.github.zzzyyylllty.chotentech.util.loreSpace
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
    CosmeticSlot.AURA to "<#DFBD7C>粒子环绕",
    CosmeticSlot.FOOTSTEP to "<#84D1CB>脚步粒子",
    CosmeticSlot.KILL_PARTICLE to "<#E08A98>击杀粒子",
    CosmeticSlot.KILL_MESSAGE to "<#CDAEEF>击杀信息"
)

class CosmeticMenu {

    fun open(player: Player) {
        val data = player.getData() ?: return
        val uuid = player.uniqueId.toString()
        val showAll = showAllMap.getOrDefault(uuid, false)
        val slots = CosmeticSlot.entries.toList()

        player.openMenu<PageableChest<CosmeticSlot>>(
            mmJsonUtil.serialize("<#F2F5FA><shift:-16><image:kangelicon:list><shift:-176><#84D1CB><font:minecraft:fusionpixel10>装饰品".toComponent())
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
            mmJsonUtil.serialize("<#F2F5FA><shift:-16><image:kangelicon:list><shift:-176><#84D1CB><font:minecraft:fusionpixel10>$titleKey".toComponent())
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
                    clicker.sendMessage("<#E08A98>你没有权限使用此装饰品".toComponent())
                    clicker.playSound(clicker.location, Sound.ENTITY_VILLAGER_NO, 1f, 1f)
                    return@onClick
                }
                if (cosmetic.id.isEmpty()) {
                    clicker.getData()?.cosmetics?.remove(cosmetic.slot.name)
                    clicker.sendMessage("<#A9B4C3>已卸下装饰".toComponent())
                    clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                } else {
                    clicker.getData()?.cosmetics?.put(cosmetic.slot.name, cosmetic.id)
                    clicker.sendMessage("<#8FD4A6>已装备: <#EFDF8F>${cosmetic.name}".toComponent())
                    clicker.playSound(clicker.location, Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1f, 1f)
                }
                resetElementsCache()
                clicker.closeInventory()
                openType(clicker, slot)
            }

            onPageChange { p ->
                p.playSound(p.location, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f)
            }

            set('C', guiItem(XMaterial.COMPASS, "<#DFBD7C>返回", listOf("<#A9B4C3>点击返回装饰品主菜单"), model = "kangelitem:void")) {
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
                guiItem(XMaterial.ARROW, "<#8FD4A6>上一页", model = if (page) "kangelitem:previous" else "kangelitem:previous_no")
            }

            setNextPage(8) { _, page ->
                guiItem(XMaterial.ARROW, "<#8FD4A6>下一页", model = if (page) "kangelitem:next" else "kangelitem:next_no")
            }
        }
    }

    private fun buildReturnButton(index: Int): ItemStack {
        return when (index) {
            0 -> {
                val item = XMaterial.COMPASS.parseItem() ?: ItemStack(Material.COMPASS)
                val meta = item.itemMeta ?: return item
                meta.displayName("<#84D1CB><bold>菜单".toComponent())
                meta.lore(listOf("<!i> <#EFDF8F>点击 <#8FD4A6>打开主菜单".toComponent()))
                meta.itemModel = NamespacedKey.fromString("kangelitem:void")
                item.setItemMeta(meta)
                item
            }
            1 -> {
                val item = XMaterial.BARRIER.parseItem() ?: ItemStack(Material.BARRIER)
                val meta = item.itemMeta ?: return item
                meta.displayName("<#E08A98><bold>关闭".toComponent())
                meta.lore(listOf("<!i> <#EFDF8F>点击 <#E08A98>关闭菜单".toComponent()))
                item.setItemMeta(meta)
                item
            }
            else -> {
                val item = XMaterial.OAK_DOOR.parseItem() ?: ItemStack(Material.OAK_DOOR)
                val meta = item.itemMeta ?: return item
                meta.displayName("<#A9B4C3><bold>返回".toComponent())
                meta.lore(listOf("<!i> <#EFDF8F>点击 <#8FD4A6>返回".toComponent()))
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
            loreInfo("当前", "<#EFDF8F>$equippedName").toComponent(),
            loreSpace().toComponent(),
            "<!i> <#EFDF8F>点击 <#8FD4A6>选择装饰".toComponent()
        ))

        item.setItemMeta(meta)
        return item
    }

    private fun buildToggleItem(showAll: Boolean): ItemStack {
        val icon = if (showAll) XMaterial.WRITABLE_BOOK else XMaterial.BOOK
        val item = icon.parseItem() ?: ItemStack(Material.BOOK)
        val meta = item.itemMeta ?: return item

        if (showAll) {
            meta.displayName("<#DFBD7C>显示模式: <#8FD4A6>全部装饰".toComponent())
            meta.lore(listOf(
                "<!i><#A9B4C3>当前显示所有装饰品".toComponent(),
                "<!i> <#EFDF8F>点击 <#8FD4A6>切换为仅显示已解锁".toComponent()
            ))
        } else {
            meta.displayName("<#DFBD7C>显示模式: <#A9B4C3>仅已解锁".toComponent())
            meta.lore(listOf(
                "<!i><#A9B4C3>当前仅显示有权限的装饰品".toComponent(),
                "<!i> <#EFDF8F>点击 <#8FD4A6>切换为显示全部".toComponent()
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

        meta.displayName("<#DFBD7C>装饰品进度".toComponent())
        meta.lore(listOf(
            loreInfo("已装备", "<#8FD4A6>$equipped <#A9B4C3>/ <#EFDF8F>${CosmeticSlot.entries.size}").toComponent(),
            loreInfo("总装饰品", "<#EFDF8F>$total").toComponent(),
            loreSpace().toComponent(),
            "<!i> <#EFDF8F>点击 <#8FD4A6>刷新".toComponent()
        ))

        item.setItemMeta(meta)
        return item
    }

    private fun buildPatternItem(currentPattern: String?): ItemStack {
        val item = XMaterial.FIREWORK_STAR.parseItem() ?: ItemStack(Material.FIREWORK_STAR)
        val meta = item.itemMeta ?: return item

        val patternDisplay = when (currentPattern) {
            "scatter" -> "<#8FD4A6>散发"
            "orbit" -> "<#84D1CB>环绕"
            "halo" -> "<#CDAEEF>光环"
            "spiral" -> "<#DFBD7C>螺旋"
            "wave" -> "<#EFDF8F>波浪"
            else -> "<#A9B4C3>默认"
        }

        meta.displayName("<#DFBD7C>粒子展示: $patternDisplay".toComponent())
        meta.lore(listOf(
            loreInfo("当前", "$patternDisplay").toComponent(),
            loreSpace().toComponent(),
            "<!i> <#EFDF8F>点击 <#8FD4A6>切换展示方式".toComponent(),
            "<!i><#A9B4C3>散发 → 环绕 → 光环 → 螺旋 → 波浪 → 默认".toComponent()
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
            if (isActive) "<#E08A98>" to "点击卸下"
            else "<#A9B4C3>" to "未装备"
        } else when {
            locked -> "<#E08A98>" to "暂无权限"
            isActive -> "<#8FD4A6>" to "已装备"
            else -> "<#A9B4C3>" to "点击装备"
        }

        meta.displayName("${statusColor}<bold>${cosmetic.name}".toComponent())

        val lore = mutableListOf<String>()

        if (cosmetic.id.isEmpty()) {
            if (isActive) lore += "<!i> <#EFDF8F>点击 <#E08A98>卸下当前装备的装饰品"
            else lore += "<!i><#A9B4C3>当前未装备任何装饰品"
        } else {
            val slotName = slotDisplayName(cosmetic.slot)
            lore += loreInfo("类型", "<#F2F5FA>$slotName")

            if (!cosmetic.particle.isNullOrBlank()) {
                lore += loreInfo("粒子", "<#F2F5FA>${cosmetic.particle}")
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
                lore += loreInfo("样式", "<#F2F5FA>$patternName")
            }
            if (!cosmetic.messageFormat.isNullOrBlank()) {
                lore += loreInfo("格式", "<#F2F5FA>${cosmetic.messageFormat}")
            }
            if (cosmetic.particleCount > 0) {
                lore += loreInfo("数量", "<#F2F5FA>${cosmetic.particleCount}")
            }
            if (cosmetic.particleRadius > 0 && cosmetic.particlePattern != null) {
                lore += loreInfo("半径", "<#F2F5FA>${cosmetic.particleRadius}")
            }
        }

        lore += loreSpace()
        lore += when (statusText) {
            "点击装备" -> "<!i> <#EFDF8F>点击 <#8FD4A6>装备"
            "点击卸下" -> "<!i> <#EFDF8F>点击 <#E08A98>卸下"
            else -> "<!i>${statusColor}${statusText}"
        }

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
