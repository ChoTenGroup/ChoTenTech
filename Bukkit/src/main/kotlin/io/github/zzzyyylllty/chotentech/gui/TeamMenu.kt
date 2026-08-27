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
            player.sendMessage("<#E08A98>你不在队伍中".toComponent())
            return
        }

        val isLeader = team.isLeader(player.uniqueId)
        val members = team.allMembers.toList()
        val maxMemberSlots = 18  // rows 1-2, 9 cols each

        val titleStr = "<#F2F5FA><shift:-16><image:kangelicon:list><shift:-176><#84D1CB><font:minecraft:fusionpixel10>队伍管理"

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
            set(8, guiItem(XMaterial.BARRIER, "<#E08A98>关闭", listOf("<#A9B4C3>点击关闭"), model = "kangelitem:void"))

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
                                    clicker.sendMessage("<#E08A98>该玩家已离线".toComponent())
                                    return@set
                                }
                                if (TeamManager.kick(clicker, target)) {
                                    clicker.sendMessage("<#8FD4A6>已踢出 <#EFDF8F>${target.name}</#EFDF8F>".toComponent())
                                    target.sendMessage("<#E08A98>你被队长踢出了队伍".toComponent())
                                    clicker.closeInventory()
                                    submit(delay = 1) { open(clicker) }
                                }
                            }
                            click.name == "RIGHT" || click.name == "SHIFT_RIGHT" -> {
                                // Transfer
                                val target = Bukkit.getPlayer(memberId)
                                if (target == null) {
                                    clicker.sendMessage("<#E08A98>该玩家已离线，无法移交队长".toComponent())
                                    return@set
                                }
                                if (TeamManager.transfer(clicker, target)) {
                                    clicker.sendMessage("<#8FD4A6>已将队长移交给 <#EFDF8F>${target.name}</#EFDF8F>".toComponent())
                                    target.sendMessage("<#DFBD7C>你已成为队长!".toComponent())
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
                    "<#E08A98><b>解散队伍",
                    listOf(
                        "<!i> <#EFDF8F>点击 <#E08A98>解散队伍",
                        loreInfo("警告", "<#E08A98>此操作不可撤销"),
                        loreSpace(),
                        loreInfo("影响", "<#E08A98>所有队员将被移出")
                    )
                )
                set(45, disbandItem) {
                    clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                    if (TeamManager.disband(clicker)) {
                        for (memberId in team.allMembers) {
                            val mp = Bukkit.getPlayer(memberId)
                            if (mp != null && mp.isOnline) {
                                mp.sendMessage("<#E08A98>队伍已被队长解散".toComponent())
                            }
                        }
                        clicker.sendMessage("<#8FD4A6>队伍已解散".toComponent())
                        clicker.closeInventory()
                    }
                }
            } else {
                val leaveItem = buildActionItem(
                    Material.IRON_DOOR,
                    "<#EFDF8F><b>退出队伍",
                    listOf(
                        "<!i> <#EFDF8F>点击 <#8FD4A6>退出队伍",
                        loreSpace(),
                        loreInfo("提醒", "<#A9B4C3>退出后需重新邀请才能加入")
                    )
                )
                set(45, leaveItem) {
                    clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                    if (TeamManager.leave(clicker)) {
                        clicker.sendMessage("<#8FD4A6>你已退出队伍".toComponent())
                        val mp = Bukkit.getPlayer(team.leader)
                        if (mp != null && mp.isOnline) {
                            mp.sendMessage("<#EFDF8F>${clicker.name} <#A9B4C3>已退出队伍".toComponent())
                        }
                        clicker.closeInventory()
                    }
                }
            }

            // Slot 49: Transfer (leader only)
            if (isLeader) {
                val transferItem = buildActionItem(
                    Material.GOLD_INGOT,
                    "<#DFBD7C><b>移交队长",
                    listOf(
                        loreInfo("操作", "<#A9B4C3>点击队员头像 <#DFBD7C>右键 <#A9B4C3>移交队长"),
                        loreSpace(),
                        loreInfo("需要", "<#A9B4C3>目标在线")
                    )
                )
                set(49, transferItem) {
                    clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                    clicker.sendMessage("<#DFBD7C>请在成员列表中 <#EFDF8F>右键 <#DFBD7C>点击要移交的队员".toComponent())
                }
            }

            // Slot 53: Close
            set(53, guiItem(XMaterial.BARRIER, "<#E08A98>关闭", listOf("<#A9B4C3>点击关闭"), model = "kangelitem:void")) {
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
        meta.displayName("<#DFBD7C><b>我的队伍".replacePlaceholder(player).toComponent())
        val lore = mutableListOf(
            loreInfo("队长", "<#F2F5FA>${Bukkit.getOfflinePlayer(team.leader).name ?: "未知"}"),
            loreInfo("成员", "<#F2F5FA>$totalCount<#A9B4C3>/<#F2F5FA>$maxSize"),
            loreInfo("在线", "<#8FD4A6>$onlineCount <#A9B4C3>/ <#F2F5FA>$totalCount"),
            loreSpace(),
            loreInfo("队伍伤害减免", "<#8FD4A6>50%")
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
        val statusColor = if (online) "<#8FD4A6>" else "<#A9B4C3>"
        meta.displayName("${statusColor}${name}".toComponent())
        val lore = mutableListOf(
            loreInfo("角色", if (role == "队长") "<#DFBD7C>队长" else "<#F2F5FA>队员"),
            loreSpace(),
            loreInfo("状态", if (player != null && player.isOnline) "<#8FD4A6>● 在线" else "<#A9B4C3>● 离线")
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
