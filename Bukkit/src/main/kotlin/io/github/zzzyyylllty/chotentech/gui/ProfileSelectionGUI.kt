package io.github.zzzyyylllty.chotentech.gui

import io.github.zzzyyylllty.chotentech.ChoTenTech.dateTimeFormatter
import io.github.zzzyyylllty.chotentech.ChoTenTech.levelsList
import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.data.RespawnPoint
import io.github.zzzyyylllty.chotentech.data.profile.ProfileInfo
import io.github.zzzyyylllty.chotentech.data.profile.ProfileManager
import io.github.zzzyyylllty.chotentech.data.profile.ProfileManager.getMaxProfiles
import io.github.zzzyyylllty.chotentech.data.profile.ProfileManager.switchProfile
import io.github.zzzyyylllty.chotentech.data.profile.ProfileManager.createProfile
import io.github.zzzyyylllty.chotentech.data.profile.ProfileManager.deleteProfile
import io.github.zzzyyylllty.chotentech.data.profile.ProfileManager.getProfileInfos
import io.github.zzzyyylllty.chotentech.data.profile.ProfileManager.loadProfileData
import io.github.zzzyyylllty.chotentech.listener.GameTimeManager
import io.github.zzzyyylllty.chotentech.listener.ProfileListener
import io.github.zzzyyylllty.chotentech.listener.ProfileListener.dismissGui
import io.github.zzzyyylllty.chotentech.listener.ProfileListener.releaseFromPending
import io.github.zzzyyylllty.chotentech.util.guiItem
import io.github.zzzyyylllty.chotentech.util.toComponent
import io.github.zzzyyylllty.chotentech.util.devLog
import io.github.zzzyyylllty.chotentech.util.loreDash
import io.github.zzzyyylllty.chotentech.util.loreInfo
import io.github.zzzyyylllty.chotentech.util.loreSection
import io.github.zzzyyylllty.chotentech.util.loreSpace
import io.github.zzzyyylllty.chotentech.util.minimessage.mmJsonUtil
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.submitAsync
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.PageableChest
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

/**
 * Players who are currently being prompted to enter a profile name in chat.
 */
object ProfileCreationPending {
    val pending = ConcurrentHashMap<String, (String) -> Unit>()

    fun register(player: Player, callback: (String) -> Unit) {
        pending[player.uniqueId.toString()] = callback
        player.closeInventory()
        player.sendMessage("<gold>┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━".toComponent())
        player.sendMessage("<gold>┃ <yellow>请在聊天栏输入新玩家档案的名称：".toComponent())
        player.sendMessage("<gold>┃ <gray>输入完成后按 Enter 发送即可".toComponent())
        player.sendMessage("<gold>┃ <red>输入 cancel 取消创建".toComponent())
        player.sendMessage("<gold>┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━".toComponent())
    }

    fun handleChat(player: Player, message: String): Boolean {
        val uuid = player.uniqueId.toString()
        val callback = pending[uuid] ?: return false
        pending.remove(uuid)

        if (message.equals("cancel", ignoreCase = true)) {
            player.sendMessage("<red>已取消创建玩家档案".toComponent())
            ProfileSelectionGUI().open(player)
            return true
        }

        if (message.length > 32) {
            player.sendMessage("<red>名称过长，最多32个字符".toComponent())
            ProfileSelectionGUI().open(player)
            return true
        }

        if (message.isBlank()) {
            player.sendMessage("<red>名称不能为空".toComponent())
            ProfileSelectionGUI().open(player)
            return true
        }

        callback(message)
        return true
    }
}

object ProfileDeletionPending {
    val pending = ConcurrentHashMap<String, () -> Unit>()

    fun register(player: Player, callback: () -> Unit) {
        pending[player.uniqueId.toString()] = callback
        player.closeInventory()
        player.sendMessage("<red>┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━".toComponent())
        player.sendMessage("<red>┃ <yellow>确认删除此玩家档案？此操作不可撤销！".toComponent())
        player.sendMessage("<red>┃ <green>输入 confirm 确认删除".toComponent())
        player.sendMessage("<red>┃ <gray>输入 cancel 取消".toComponent())
        player.sendMessage("<red>┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━".toComponent())
    }

    fun handleChat(player: Player, message: String): Boolean {
        val uuid = player.uniqueId.toString()
        val callback = pending[uuid] ?: return false
        pending.remove(uuid)

        if (message.equals("cancel", ignoreCase = true)) {
            player.sendMessage("<green>已取消删除".toComponent())
            ProfileSelectionGUI().open(player)
            return true
        }

        if (message.equals("confirm", ignoreCase = true)) {
            callback()
            return true
        }

        player.sendMessage("<red>未知命令，输入 confirm 确认删除，cancel 取消".toComponent())
        ProfileSelectionGUI().open(player)
        return true
    }
}

private data class ProfileDisplayData(
    val reputation: Map<String, Long>,
    val exploredLevels: Int,
    val discoveredBases: Int,
    val money: Long,
    val point: Long,
    val playTime: Long,
    val activeLevelStr: String?,
    val gameTimeHours: Long
)

class ProfileSelectionGUI {

    companion object {
        /** 打开中的玩家(含异步预加载期间):防止移动事件/ESC 重开竞态导致的重复打开 */
        private val opening = ConcurrentHashMap.newKeySet<String>()

        /** 档案展示数据缓存(uuid -> 加载时间戳 + 每个档案的展示数据):命中时界面可即时弹出,无需等待 DB */
        private val displayCache = ConcurrentHashMap<String, Pair<Long, Map<String, ProfileDisplayData>>>()

        /** 展示数据为选择界面用,允许 30s 内的轻微滞后(货币/游玩时间),换取打开零等待 */
        private const val DISPLAY_CACHE_TTL_MS = 30_000L

        private fun loadDisplayMap(uuid: String, infos: List<ProfileInfo>): Map<String, ProfileDisplayData> {
            return infos.associate { info ->
                val pData = loadProfileData(uuid, info.profileId)
                info.profileId to ProfileDisplayData(
                    reputation = pData?.reputation?.toMap() ?: emptyMap(),
                    exploredLevels = pData?.exploreredLevels?.size ?: 0,
                    discoveredBases = pData?.discoveredBases?.size ?: 0,
                    money = pData?.money ?: 0L,
                    point = pData?.point ?: 0L,
                    playTime = pData?.playTime ?: 0L,
                    activeLevelStr = pData?.activeLevelStr,
                    gameTimeHours = pData?.gameTimeHours ?: 0L
                )
            }
        }
    }

    fun open(player: Player) {
        if (!player.isOnline) return
        val uuid = player.uniqueId.toString()
        if (!opening.add(uuid)) return

        // 主线程只读缓存数据(共享数据/档案列表/上限),零 DB 查询
        val shared = ProfileManager.getSharedData(uuid)
        val infos = getProfileInfos(uuid)
        val activeProfileId = shared.activeProfileId
        val maxProfiles = getMaxProfiles(player)
        val sharedCid = shared.cid.id

        // 缓存命中:同一 tick 内同步弹出,不给排队中的陈旧玩家包(移动/交互)留出打断窗口
        val cached = displayCache[uuid]
        if (cached != null && System.currentTimeMillis() - cached.first < DISPLAY_CACHE_TTL_MS) {
            opening.remove(uuid)
            devLog("[ProfileGUI] ${player.name} 打开(缓存) ${infos.size} 档案")
            openMenuWith(player, infos, cached.second, activeProfileId, maxProfiles, sharedCid)
            return
        }

        // 打开时是否处于强制选择状态:加载完成后若已被释放(如聊天创建了档案),不再弹出
        val wasPending = ProfileListener.pendingSelection[uuid] === player

        // 缓存未命中:每个档案的展示数据(声望/进度/货币)在异步线程预加载:
        // 在线玩家多/自动保存高峰时 DB 变慢,若在主线程同步查询会冻结主线程
        devLog("[ProfileGUI] ${player.name} 打开(异步加载) ${infos.size} 档案")
        submitAsync {
            try {
                val displayMap = loadDisplayMap(uuid, infos)
                displayCache[uuid] = System.currentTimeMillis() to displayMap
                submit {
                    try {
                        if (!player.isOnline) return@submit
                        // 加载期间玩家可能已掉线/选定档案,不再强行弹出
                        if (wasPending && ProfileListener.pendingSelection[uuid] !== player) return@submit
                        openMenuWith(player, infos, displayMap, activeProfileId, maxProfiles, sharedCid)
                    } finally {
                        opening.remove(uuid)
                    }
                }
            } catch (e: Exception) {
                opening.remove(uuid)
                devLog("[ProfileGUI] 档案数据预加载失败: ${e.message}")
            }
        }
    }

    private fun openMenuWith(
        player: Player,
        infos: List<ProfileInfo>,
        displayMap: Map<String, ProfileDisplayData>,
        activeProfileId: String,
        maxProfiles: Int,
        sharedCid: Long
    ) {
        player.openMenu<PageableChest<ProfileInfo>>(
            mmJsonUtil.serialize("<white><shift:-16><image:kangelicon:list><shift:-176><aqua><font:minecraft:fusionpixel10>选择玩家档案".toComponent())
        ) {
            rows(6)

            map(
                "CCC   D<>",
                "         ",
                " @@@@@@@ ",
                " @@@@@@@ ",
                " @@@@@@@ ",
                "         "
            )

            set('C', guiItem(XMaterial.BARRIER, "<red><bold>关闭", listOf("<!i> <yellow>点击 <red>关闭界面"), model = "kangelitem:void")) {
                clicker.closeInventory()
                // 未选择档案时保持 pending,移动时会再次弹出选择窗口
                dismissGui(clicker)
            }

            set('D', buildCreateItem(infos.size, maxProfiles)) {
                if (infos.size >= maxProfiles) {
                    clicker.sendMessage("<red>已达到最大玩家档案数量 ($maxProfiles)".toComponent())
                    clicker.playSound(clicker.location, Sound.ENTITY_VILLAGER_NO, 1f, 1f)
                    return@set
                }

                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                ProfileCreationPending.register(clicker) { name ->
                    val uuid = clicker.uniqueId.toString()
                    val existing = ProfileManager.getProfileInfos(uuid)
                    if (existing.any { it.profileName == name }) {
                        clicker.sendMessage("<red>玩家档案名 <yellow>$name <red>已存在".toComponent())
                        open(clicker)
                        return@register
                    }
                    val result = createProfile(clicker, name)
                    if (result != null) {
                        releaseFromPending(clicker)
                        val world = Bukkit.getWorld("Area0") ?: clicker.world
                        val spawnLoc = Location(world, -6.0, 14.0, 5.0)
                        clicker.teleport(spawnLoc)
                        clicker.setRespawnLocation(spawnLoc, true)
                        val uuid = clicker.uniqueId.toString()
                        val data = playerDataMap[uuid]
                        if (data != null) {
                            data.profileLocation = RespawnPoint.fromLocation(spawnLoc)
                            ProfileManager.saveProfileData(uuid, data.profileId, ProfileManager.stripSharedFields(data))
                        }
                        clicker.sendMessage("<green>玩家档案 <yellow>$name <green>创建成功！".toComponent())
                        clicker.sendMessage("<green>已切换到新玩家档案".toComponent())
                        clicker.playSound(clicker.location, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1f, 1f)
                    } else {
                        clicker.sendMessage("<red>创建失败，请检查是否达到上限".toComponent())
                        open(clicker)
                    }
                }
            }

            slotsBy('@')

            elements { infos }

            onGenerate { _, info, _, _ ->
                val isActive = info.profileId == activeProfileId
                val display = displayMap[info.profileId] ?: ProfileDisplayData(emptyMap(), 0, 0, 0L, 0L, 0L, null, 0L)
                buildProfileItem(info, display, isActive, sharedCid, infos.size)
            }

            onClick { event, info ->
                event.isCancelled = true
                val clicker = event.clicker
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)

                val isActive = info.profileId == activeProfileId
                if (!isActive) {
                    // Click to switch
                    clicker.closeInventory()
                    switchProfile(clicker, info.profileId)
                    releaseFromPending(clicker)
                    clicker.sendMessage("<green>已切换到玩家档案: <yellow>${info.profileName}".toComponent())
                    clicker.playSound(clicker.location, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1f, 1f)
                } else if (infos.size > 1) {
                    // Click active profile when multiple exist — prompt deletion
                    ProfileDeletionPending.register(clicker) {
                        val profileName = info.profileName
                        if (deleteProfile(clicker, info.profileId)) {
                            clicker.sendMessage("<gold>已删除玩家档案: <yellow>$profileName".toComponent())
                            clicker.playSound(clicker.location, Sound.ENTITY_ITEM_BREAK, 1f, 1f)
                        } else {
                            clicker.sendMessage("<red>删除玩家档案失败".toComponent())
                        }
                        // 删除激活档案后玩家仍处于 pending 且无界面,重新打开选择界面避免软锁
                        open(clicker)
                    }
                } else {
                    clicker.sendMessage("<gray>当前已是此玩家档案".toComponent())
                }
            }

            onPageChange { p ->
                p.playSound(p.location, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f)
            }

            setPreviousPage(7) { _, page ->
                guiItem(XMaterial.ARROW, "<green>上一页", model = if (page) "kangelitem:previous" else "kangelitem:previous_no")
            }

            setNextPage(8) { _, page ->
                guiItem(XMaterial.ARROW, "<green>下一页", model = if (page) "kangelitem:next" else "kangelitem:next_no")
            }
        }
    }

    private fun buildProfileItem(
        info: ProfileInfo,
        display: ProfileDisplayData,
        isActive: Boolean,
        sharedCid: Long,
        profileCount: Int
    ): ItemStack {
        val mat = if (isActive) XMaterial.PLAYER_HEAD else XMaterial.OAK_SIGN
        val item = mat.parseItem() ?: ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta ?: return item

        val statusColor = if (isActive) "<green>" else "<gray>"

        meta.displayName("${statusColor}<bold>${info.profileName}".toComponent())

        val lore = mutableListOf<String>()
        lore += loreInfo("CID", "<white>$sharedCid")
        lore += loreInfo("ID", "<white>${info.profileId}")
        lore += loreInfo("创建", "<white>${
            dateTimeFormatter.format(
                Instant.ofEpochMilli(info.createTime).atZone(ZoneId.systemDefault()).toLocalDateTime()
            ).substringBefore(" ")
        }")

        // Faction reputation — one line per faction
        if (display.reputation.isNotEmpty()) {
            lore += loreSection("阵营声望")
            display.reputation.forEach { (faction, value) ->
                val color = when (faction.uppercase()) {
                    "MEG" -> "<#ffdd00>"
                    "BNTG" -> "<#33ffaa>"
                    "UEC" -> "<#ff3d00>"
                    else -> "<white>"
                }
                val sign = if (value >= 0) "+" else ""
                lore += loreDash("$color$faction <white>$sign$value")
            }
        }

        // Play time
        val totalSec = display.playTime / 1000
        val hours = totalSec / 3600
        val mins = (totalSec % 3600) / 60
        val gameTimeStr = GameTimeManager.formatOneLine(display.gameTimeHours)
        lore += loreSpace()
        lore += loreInfo("游戏内时间", "<white>$gameTimeStr")
        lore += loreSpace()
        lore += loreInfo("游玩", "<white>${hours}小时 ${mins}分钟")
        lore += loreInfo("已探索", "<white>${display.exploredLevels}层, ${display.discoveredBases}基地")
        val levelName = display.activeLevelStr?.let { levelsList[it] }?.let { "${it.prefix}${it.name}" }
        if (levelName != null) {
            lore += loreInfo("层级位置", "<white>$levelName")
        }
        lore += loreInfo("积分", "<#FFCC66>${display.money}")
        lore += loreInfo("贸易点", "<#22FF66>${display.point}")

        lore += loreSpace()
        if (isActive) {
            lore += loreInfo("状态", "<green>当前使用")
            if (profileCount > 1) {
                lore += "<!i> <yellow>点击 <red>删除"
            }
        } else {
            lore += "<!i> <yellow>点击 <green>切换"
        }

        meta.lore(lore.toComponent())
        item.setItemMeta(meta)
        return item
    }

    private fun buildCreateItem(current: Int, max: Int): ItemStack {
        val canCreate = current < max
        val mat = if (canCreate) XMaterial.LIME_DYE else XMaterial.GRAY_DYE
        val item = mat.parseItem() ?: ItemStack(Material.GRAY_DYE)
        val meta = item.itemMeta ?: return item

        if (canCreate) {
            meta.displayName("<green><bold>创建新玩家档案".toComponent())
            meta.lore(listOf(
                loreInfo("当前", "<yellow>$current <gray>/ <yellow>$max"),
                "<!i> <yellow>点击 <green>创建新的玩家档案",
                loreInfo("说明", "<gray>新玩家档案会独立存储进度")
            ).toComponent())
        } else {
            meta.displayName("<red><bold>已达上限".toComponent())
            meta.lore(listOf(
                loreInfo("当前", "<yellow>$current <gray>/ <yellow>$max"),
                loreInfo("状态", "<red>已达到最大玩家档案数量")
            ).toComponent())
        }

        item.setItemMeta(meta)
        return item
    }

}
