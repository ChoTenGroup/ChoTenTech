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
import io.github.zzzyyylllty.chotentech.listener.ProfileListener.releaseFromPending
import io.github.zzzyyylllty.chotentech.util.toComponent
import io.github.zzzyyylllty.chotentech.util.minimessage.mmJsonUtil
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
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
    val activeLevelStr: String?
)

class ProfileSelectionGUI {

    fun open(player: Player) {
        val uuid = player.uniqueId.toString()
        val shared = ProfileManager.getSharedData(uuid)
        val infos = getProfileInfos(uuid)
        val activeProfileId = shared.activeProfileId
        val maxProfiles = getMaxProfiles(player)

        // Load per-profile display data (reputation, stats)
        val displayMap = infos.associate { info ->
            val pData = loadProfileData(uuid, info.profileId)
            info.profileId to ProfileDisplayData(
                reputation = pData?.reputation?.toMap() ?: emptyMap(),
                exploredLevels = pData?.exploreredLevels?.size ?: 0,
                discoveredBases = pData?.discoveredBases?.size ?: 0,
                money = pData?.money ?: 0L,
                point = pData?.point ?: 0L,
                playTime = pData?.playTime ?: 0L,
                activeLevelStr = pData?.activeLevelStr
            )
        }

        val rowCount = maxOf(3, 2 + (infos.size + 8) / 9)

        player.openMenu<Chest>(
            mmJsonUtil.serialize(
                "<black><font:minecraft:fusionpixel10>选择玩家档案".toComponent()
            )
        ) {
            this.rows(rowCount)

            val bg = Array(rowCount) { "#".repeat(9) }
            map(*bg)

            set('#', XMaterial.BLACK_STAINED_GLASS_PANE) { name = " " }

            // Existing profiles
            infos.forEachIndexed { index, info ->
                val isActive = info.profileId == activeProfileId
                val display = displayMap[info.profileId] ?: ProfileDisplayData(emptyMap(), 0, 0, 0L, 0L, 0L, null)
                val item = buildProfileItem(player, info, display, isActive)
                set(index, item) {
                    clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)

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
                            deleteProfile(clicker, info.profileId)
                            clicker.sendMessage("<gold>已删除玩家档案: <yellow>$profileName".toComponent())
                            clicker.playSound(clicker.location, Sound.ENTITY_ITEM_BREAK, 1f, 1f)
                        }
                    } else {
                        clicker.sendMessage("<gray>当前已是此玩家档案".toComponent())
                    }
                }
            }

            // Create Profile button
            val createSlot = minOf(infos.size, 7)
            set(createSlot, buildCreateItem(infos.size, maxProfiles)) {
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

            // Info slot
            val infoSlot = 8
            set(infoSlot, buildInfoItem(player, infos.size, maxProfiles)) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                clicker.closeInventory()
                val activeInfo = infos.find { it.profileId == activeProfileId }
                if (activeInfo != null) {
                    clicker.sendMessage("<green>当前玩家档案: <yellow>${activeInfo.profileName}".toComponent())
                }
            }
        }
    }

    private fun buildProfileItem(player: Player, info: ProfileInfo, display: ProfileDisplayData, isActive: Boolean): ItemStack {
        val mat = if (isActive) XMaterial.PLAYER_HEAD else XMaterial.OAK_SIGN
        val item = mat.parseItem() ?: ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta ?: return item

        val statusColor = if (isActive) "<green>" else "<gray>"
        val statusText = if (isActive) "当前使用" else "点击切换"

        meta.displayName("${statusColor}<bold>${info.profileName}".toComponent())

        val lore = mutableListOf<String>()
        lore += "<!i><dark_gray><st>${" ".repeat(60)}</st>"
        lore += "<!i><dark_aqua>CID <white>${info.cid.id}  <dark_aqua>ID <white>${info.profileId}"
        lore += "<!i><dark_aqua>创建 <white>${
            dateTimeFormatter.format(
                Instant.ofEpochMilli(info.createTime).atZone(ZoneId.systemDefault()).toLocalDateTime()
            ).substringBefore(" ")
        }"

        // Faction reputation — one line per faction
        display.reputation.forEach { (faction, value) ->
            val color = when (faction.uppercase()) {
                "MEG" -> "<#ffdd00>"
                "BNTG" -> "<#33ffaa>"
                "UEC" -> "<#ff3d00>"
                else -> "<white>"
            }
            val sign = if (value >= 0) "+" else ""
            lore += "<!i>${color}${faction} <white>$sign$value"
        }

        // Play time
        val totalSec = display.playTime / 1000
        val hours = totalSec / 3600
        val mins = (totalSec % 3600) / 60
        lore += "<!i><gold>游玩 <white>${hours}小时 ${mins}分钟  <gold>已探索 <white>${display.exploredLevels}层, ${display.discoveredBases}基地"
        val levelName = display.activeLevelStr?.let { levelsList[it] }?.let { "${it.prefix}${it.name}" }
        if (levelName != null) {
            lore += "<!i><gold>层级位置 <white>$levelName"
        }
        lore += "<!i><#FFCC66>积分 <white>${display.money} <white>| <#22FF66>贸易点 <white>${display.point}"

        lore += "<!i><dark_gray><st>${" ".repeat(60)}</st>"
        lore += "<!i>${statusColor}${statusText}"
        if (isActive && getProfileInfos(player.uniqueId.toString()).size > 1) {
            lore += "  <!i><red>点击删除"
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
                "<!i><gray>当前: <yellow>$current <gray>/ <yellow>$max",
                "<!i><green>点击创建一个新的玩家档案",
                "<!i><gray>新玩家档案会独立存储进度"
            ).toComponent())
        } else {
            meta.displayName("<red><bold>已达上限".toComponent())
            meta.lore(listOf(
                "<!i><gray>当前: <yellow>$current <gray>/ <yellow>$max",
                "<!i><red>已达到最大玩家档案数量"
            ).toComponent())
        }

        item.setItemMeta(meta)
        return item
    }

    private fun buildInfoItem(player: Player, current: Int, max: Int): ItemStack {
        val item = XMaterial.COMPASS.parseItem() ?: ItemStack(Material.COMPASS)
        val meta = item.itemMeta ?: return item

        val shared = ProfileManager.getSharedData(player.uniqueId.toString())

        meta.displayName("<gold><bold>玩家档案信息".toComponent())
        meta.lore(listOf(
            "<!i><gray>当前: <yellow>$current <gray>/ <yellow>$max",
            "<!i><gray>活跃玩家档案ID: <yellow>${shared.activeProfileId}",
            "<!i>",
            "<!i><green>点击关闭"
        ).toComponent())

        item.setItemMeta(meta)
        return item
    }
}
