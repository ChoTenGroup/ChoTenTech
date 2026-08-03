package io.github.zzzyyylllty.chotentech.gui

import io.github.zzzyyylllty.chotentech.team.Team
import io.github.zzzyyylllty.chotentech.team.TeamManager
import io.github.zzzyyylllty.chotentech.util.guiItem
import io.github.zzzyyylllty.chotentech.util.loreInfo
import io.github.zzzyyylllty.chotentech.util.loreSpace
import io.github.zzzyyylllty.chotentech.util.minimessage.mmJsonUtil
import io.github.zzzyyylllty.chotentech.util.toComponent
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import taboolib.common.platform.function.submit
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import taboolib.platform.compat.replacePlaceholder

class TeamMenu {

    fun open(player: Player) {
        val team = TeamManager.getTeam(player.uniqueId) ?: run {
            player.sendMessage("<red>你不在队伍中".toComponent())
            return
        }

        val isLeader = team.isLeader(player.uniqueId)
        val members = team.allMembers.toList()
        val maxMemberSlots = 18  // rows 1-2, 9 cols each

        val titleStr = "<white><shift:-16><image:kangelicon:list><shift:-176><aqua><font:minecraft:fusionpixel10>队伍管理"

        player.openMenu<Chest>(mmJsonUtil.serialize(titleStr.toComponent())) {
            rows(6)

            map(
                "         ",
                "         ",
                "         ",
                "         ",
                "         ",
                "         "
            )

            // ── Team banner (slot 4) ──
            val bannerItem = buildBanner(player, team)
            set(4, bannerItem)

            // ── Close button (slot 8) ──
            set(8, guiItem(XMaterial.BARRIER, "<red>关闭", listOf("<gray>点击关闭"), model = "kangelitem:void"))

            // ── Member heads (starting slot 9) ──
            for ((i, memberId) in members.withIndex()) {
                if (i >= maxMemberSlots) break
                val slot = 9 + i
                val isThisPlayer = memberId == player.uniqueId
                val role = if (memberId == team.leader) "队长" else "队员"
                val memberPlayer = Bukkit.getPlayer(memberId)
                val online = memberPlayer != null && memberPlayer.isOnline
                val head = buildMemberHead(memberPlayer, memberId, role, online)

                set(slot, head) {
                    clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)

                    if (isThisPlayer) return@set

                    if (isLeader) {
                        // Check click type
                        val click = clickEvent().click
                        when {
                            click.name == "LEFT" || click.name == "SHIFT_LEFT" -> {
                                // Kick
                                val target = Bukkit.getPlayer(memberId)
                                if (target == null) {
                                    clicker.sendMessage("<red>该玩家已离线".toComponent())
                                    return@set
                                }
                                if (TeamManager.kick(clicker, target)) {
                                    clicker.sendMessage("<green>已踢出 <yellow>${target.name}</yellow>".toComponent())
                                    target.sendMessage("<red>你被队长踢出了队伍".toComponent())
                                    clicker.closeInventory()
                                    submit(delay = 1) { open(clicker) }
                                }
                            }
                            click.name == "RIGHT" || click.name == "SHIFT_RIGHT" -> {
                                // Transfer
                                val target = Bukkit.getPlayer(memberId)
                                if (target == null) {
                                    clicker.sendMessage("<red>该玩家已离线，无法移交队长".toComponent())
                                    return@set
                                }
                                if (TeamManager.transfer(clicker, target)) {
                                    clicker.sendMessage("<green>已将队长移交给 <yellow>${target.name}</yellow>".toComponent())
                                    target.sendMessage("<gold>你已成为队长!".toComponent())
                                    clicker.closeInventory()
                                    submit(delay = 1) { open(clicker) }
                                }
                            }
                        }
                    }
                }
            }

            // ── Bottom row actions ──

            // Slot 45: Leave / Disband
            if (isLeader) {
                val disbandItem = buildActionItem(
                    Material.RED_BANNER,
                    "<red><b>解散队伍",
                    listOf(
                        "<!i> <yellow>点击 <red>解散队伍",
                        loreInfo("警告", "<red>此操作不可撤销"),
                        loreSpace(),
                        loreInfo("影响", "<red>所有队员将被移出")
                    )
                )
                set(45, disbandItem) {
                    clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                    if (TeamManager.disband(clicker)) {
                        for (memberId in team.allMembers) {
                            val mp = Bukkit.getPlayer(memberId)
                            if (mp != null && mp.isOnline) {
                                mp.sendMessage("<red>队伍已被队长解散".toComponent())
                            }
                        }
                        clicker.sendMessage("<green>队伍已解散".toComponent())
                        clicker.closeInventory()
                    }
                }
            } else {
                val leaveItem = buildActionItem(
                    Material.IRON_DOOR,
                    "<yellow><b>退出队伍",
                    listOf(
                        "<!i> <yellow>点击 <green>退出队伍",
                        loreSpace(),
                        loreInfo("提醒", "<gray>退出后需重新邀请才能加入")
                    )
                )
                set(45, leaveItem) {
                    clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                    if (TeamManager.leave(clicker)) {
                        clicker.sendMessage("<green>你已退出队伍".toComponent())
                        val mp = Bukkit.getPlayer(team.leader)
                        if (mp != null && mp.isOnline) {
                            mp.sendMessage("<yellow>${clicker.name} <gray>已退出队伍".toComponent())
                        }
                        clicker.closeInventory()
                    }
                }
            }

            // Slot 49: Transfer (leader only)
            if (isLeader) {
                val transferItem = buildActionItem(
                    Material.GOLD_INGOT,
                    "<gold><b>移交队长",
                    listOf(
                        loreInfo("操作", "<gray>点击队员头像 <gold>右键 <gray>移交队长"),
                        loreSpace(),
                        loreInfo("需要", "<gray>目标在线")
                    )
                )
                set(49, transferItem) {
                    clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                    clicker.sendMessage("<gold>请在成员列表中 <yellow>右键 <gold>点击要移交的队员".toComponent())
                }
            }

            // Slot 53: Close
            set(53, guiItem(XMaterial.BARRIER, "<red>关闭", listOf("<gray>点击关闭"), model = "kangelitem:void")) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                clicker.closeInventory()
            }
        }
    }

    // ── Item builders ──

    private fun buildBanner(player: Player, team: Team): ItemStack {
        val onlineCount = team.allMembers.count { id ->
            val p = Bukkit.getPlayer(id)
            p != null && p.isOnline
        }
        val totalCount = team.allMembers.size
        val maxSize = TeamManager.getMaxTeamSize(player)

        val item = ItemStack(Material.NAME_TAG)
        val meta = item.itemMeta ?: return item
        meta.displayName("<gold><b>我的队伍".replacePlaceholder(player).toComponent())
        val lore = mutableListOf(
            loreInfo("队长", "<white>${Bukkit.getOfflinePlayer(team.leader).name ?: "未知"}"),
            loreInfo("成员", "<white>$totalCount<gray>/<white>$maxSize"),
            loreInfo("在线", "<green>$onlineCount <gray>/ <white>$totalCount"),
            loreSpace(),
            loreInfo("队伍伤害减免", "<green>50%")
        )
        meta.lore(lore.map { it.replacePlaceholder(player).toComponent() })
        item.setItemMeta(meta)
        return item
    }

    private fun buildMemberHead(player: Player?, memberId: java.util.UUID, role: String, online: Boolean): ItemStack {
        val item: ItemStack
        val name: String

        if (player != null && player.isOnline) {
            item = ItemStack(Material.PLAYER_HEAD)
            val skullMeta = item.itemMeta as? SkullMeta
            skullMeta?.owningPlayer = player
            if (skullMeta != null) item.setItemMeta(skullMeta)
            name = player.name
        } else {
            item = ItemStack(Material.PLAYER_HEAD)
            name = Bukkit.getOfflinePlayer(memberId).name ?: "未知"
        }

        val meta = item.itemMeta ?: return item
        val statusColor = if (online) "<green>" else "<gray>"
        meta.displayName("${statusColor}${name}".toComponent())
        val lore = mutableListOf(
            loreInfo("角色", if (role == "队长") "<gold>队长" else "<white>队员"),
            loreSpace(),
            loreInfo("状态", if (player != null && player.isOnline) "<green>● 在线" else "<gray>● 离线")
        )

        meta.lore(lore.map { it.toComponent() })
        item.setItemMeta(meta)
        return item
    }

    private fun buildActionItem(material: Material, name: String, lore: List<String>): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        meta.displayName(name.toComponent())
        meta.lore(lore.map { it.toComponent() })
        NamespacedKey.fromString("kangelitem:void")?.let { meta.itemModel = it }
        item.setItemMeta(meta)
        return item
    }
}
