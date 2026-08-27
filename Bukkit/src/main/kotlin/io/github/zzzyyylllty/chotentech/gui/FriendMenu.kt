package io.github.zzzyyylllty.chotentech.gui

import io.github.zzzyyylllty.chotentech.data.profile.ProfileManager
import io.github.zzzyyylllty.chotentech.friend.FriendManager
import io.github.zzzyyylllty.chotentech.util.guiItem
import io.github.zzzyyylllty.chotentech.util.loreInfo
import io.github.zzzyyylllty.chotentech.util.loreSpace
import io.github.zzzyyylllty.chotentech.util.minimessage.mmJsonUtil
import io.github.zzzyyylllty.chotentech.util.toComponent
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.PageableChest
import java.util.UUID

class FriendMenu {

    fun open(player: Player) {
        val friends = FriendManager.getFriends(player.uniqueId)

        player.openMenu<PageableChest<UUID>>(mmJsonUtil.serialize("<#F2F5FA><shift:-16><image:kangelicon:list><shift:-176><#84D1CB><font:minecraft:fusionpixel10>好友列表".toComponent())) {
            rows(6)

            map(
                "CCC B  <>",
                "         ",
                " @@@@@@@ ",
                " @@@@@@@ ",
                " @@@@@@@ ",
                "A   R   X"
            )

            set('C', guiItem(XMaterial.COMPASS, "<#84D1CB><bold>返回", listOf("<!i> <#EFDF8F>点击 <#E08A98>关闭"), model = "kangelitem:void")) {
                clicker.closeInventory()
            }

            set('B', buildBanner(player)) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
            }

            // ── Bottom row actions ──

            set('A', guiItem(XMaterial.PAPER, "<#8FD4A6><bold>添加好友", listOf(
                loreInfo("用法", "<#F2F5FA>/friend add <玩家名>"),
                loreInfo("说明", "<#A9B4C3>向玩家发送好友申请")
            ), model = "kangelitem:void")) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                clicker.closeInventory()
                clicker.sendMessage("<#8FD4A6>请输入 <#F2F5FA>/friend add <玩家名> <#8FD4A6>添加好友".toComponent())
            }

            set('R', guiItem(XMaterial.BOOK, "<#EFDF8F><bold>申请列表", listOf(
                loreInfo("说明", "<#A9B4C3>查看待处理的好友申请"),
                loreSpace(),
                "<!i> <#EFDF8F>左键 <#8FD4A6>同意",
                "<!i> <#EFDF8F>右键 <#E08A98>拒绝"
            ), model = "kangelitem:void")) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                FriendRequestMenu().open(clicker)
            }

            set('X', guiItem(XMaterial.BARRIER, "<#E08A98>关闭", listOf("<!i> <#EFDF8F>点击 <#E08A98>关闭"), model = "kangelitem:void")) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                clicker.closeInventory()
            }

            slotsBy('@')

            elements { friends }

            onGenerate { _, friendId, _, _ ->
                buildFriendItem(friendId)
            }

            onClick { event, friendId ->
                event.isCancelled = true
                val clicker = event.clicker
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)

                if (event.clickEvent().click.name == "RIGHT" || event.clickEvent().click.name == "SHIFT_RIGHT") {
                    FriendDeleteConfirmMenu(friendId).open(clicker)
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

    // ── Item builders ──

    private fun buildBanner(player: Player): ItemStack {
        val total = FriendManager.getFriends(player.uniqueId).size
        val online = FriendManager.getOnlineFriends(player.uniqueId).size
        val max = FriendManager.getMaxFriends(player)

        val item = ItemStack(Material.NAME_TAG)
        val meta = item.itemMeta ?: return item
        meta.displayName("<#DFBD7C><b>我的好友".toComponent())
        meta.lore(listOf(
            loreInfo("好友", "<#F2F5FA>$total<#A9B4C3>/<#F2F5FA>$max"),
            loreInfo("在线", "<#8FD4A6>$online <#A9B4C3>/ <#F2F5FA>$total"),
            loreSpace(),
            "<!i> <#EFDF8F>右键 <#E08A98>删除好友"
        ).map { it.toComponent() })
        item.setItemMeta(meta)
        return item
    }

    private fun buildFriendItem(friendId: UUID): ItemStack {
        val friend = Bukkit.getPlayer(friendId)
        val online = friend != null && friend.isOnline
        val name = if (online) friend!!.name else (Bukkit.getOfflinePlayer(friendId).name ?: "未知")

        val item = ItemStack(Material.PLAYER_HEAD)
        if (online) {
            val skullMeta = item.itemMeta as? SkullMeta
            skullMeta?.owningPlayer = friend
            if (skullMeta != null) item.setItemMeta(skullMeta)
        }

        val meta = item.itemMeta ?: return item
        meta.displayName("${if (online) "<#8FD4A6>" else "<#A9B4C3>"}$name".toComponent())
        val cid = ProfileManager.getSharedData(friendId.toString()).cid.id
        val lore = mutableListOf<String>()
        if (cid != 0L) {
            lore += loreInfo("CID", "<#F2F5FA>$cid")
        }
        lore += loreSpace()
        lore += loreInfo("状态", if (online) "<#8FD4A6>● 在线" else "<#A9B4C3>● 离线")
        lore += "<!i> <#EFDF8F>右键 <#E08A98>删除好友"
        meta.lore(lore.map { it.toComponent() })
        item.setItemMeta(meta)
        return item
    }
}

class FriendDeleteConfirmMenu(private val friendId: UUID) {

    fun open(player: Player) {
        val name = Bukkit.getOfflinePlayer(friendId).name ?: "未知"

        player.openMenu<taboolib.module.ui.type.Chest>(mmJsonUtil.serialize("<#F2F5FA><shift:-16><image:kangelicon:list><shift:-176><#E08A98><font:minecraft:fusionpixel10>删除好友".toComponent())) {
            rows(3)

            map(
                "         ",
                "  X   Y  ",
                "         "
            )

            set('X', guiItem(XMaterial.RED_DYE, "<#E08A98><b>确认删除", listOf(
                loreInfo("将删除好友", "<#F2F5FA>$name"),
                loreInfo("警告", "<#E08A98>此操作不可撤销")
            ), model = "kangelitem:void")) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                if (FriendManager.remove(clicker, friendId)) {
                    clicker.sendMessage("<#E08A98>已删除好友 <#EFDF8F>$name".toComponent())
                } else {
                    clicker.sendMessage("<#E08A98>该玩家不是你的好友".toComponent())
                }
                clicker.closeInventory()
            }

            set('Y', guiItem(XMaterial.LIME_DYE, "<#8FD4A6><b>取消", listOf("<!i> <#EFDF8F>点击 <#8FD4A6>返回好友列表"), model = "kangelitem:void")) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                clicker.closeInventory()
                taboolib.common.platform.function.submit(delay = 1) {
                    FriendMenu().open(clicker)
                }
            }
        }
    }
}
