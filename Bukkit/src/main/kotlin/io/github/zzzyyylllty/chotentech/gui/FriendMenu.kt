package io.github.zzzyyylllty.chotentech.gui

import io.github.zzzyyylllty.chotentech.ChoTenTech
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
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.PageableChest
import java.util.UUID

class FriendMenu {

    fun open(player: Player) {
        val friends = FriendManager.getFriends(player.uniqueId)

        player.openMenu<PageableChest<UUID>>(mmJsonUtil.serialize("<white><shift:-16><image:kangelicon:list><shift:-176><aqua><font:minecraft:fusionpixel10>好友列表".toComponent())) {
            rows(6)

            map(
                "CCC B  <>",
                "         ",
                " @@@@@@@ ",
                " @@@@@@@ ",
                " @@@@@@@ ",
                "A   R   X"
            )

            set('C', guiItem(XMaterial.COMPASS, "<aqua><bold>返回", listOf("<!i> <yellow>点击 <red>关闭"), model = "kangelitem:void")) {
                clicker.closeInventory()
            }

            set('B', buildBanner(player)) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
            }

            // ── Bottom row actions ──

            set('A', guiItem(XMaterial.PAPER, "<green><bold>添加好友", listOf(
                loreInfo("用法", "<white>/friend add <玩家名>"),
                loreInfo("说明", "<gray>向玩家发送好友申请")
            ), model = "kangelitem:void")) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                clicker.closeInventory()
                clicker.sendMessage("<green>请输入 <white>/friend add <玩家名> <green>添加好友".toComponent())
            }

            set('R', guiItem(XMaterial.BOOK, "<yellow><bold>申请列表", listOf(
                loreInfo("说明", "<gray>查看待处理的好友申请"),
                loreSpace(),
                "<!i> <yellow>左键 <green>同意",
                "<!i> <yellow>右键 <red>拒绝"
            ), model = "kangelitem:void")) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                FriendRequestMenu().open(clicker)
            }

            set('X', guiItem(XMaterial.BARRIER, "<red>关闭", listOf("<!i> <yellow>点击 <red>关闭"), model = "kangelitem:void")) {
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

                when {
                    event.clickEvent().click.name == "LEFT" || event.clickEvent().click.name == "SHIFT_LEFT" ->
                        teleportTo(clicker, friendId)
                    event.clickEvent().click.name == "RIGHT" || event.clickEvent().click.name == "SHIFT_RIGHT" ->
                        FriendDeleteConfirmMenu(friendId).open(clicker)
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

    // ── Item builders ──

    private fun buildBanner(player: Player): ItemStack {
        val total = FriendManager.getFriends(player.uniqueId).size
        val online = FriendManager.getOnlineFriends(player.uniqueId).size
        val max = FriendManager.getMaxFriends(player)

        val item = ItemStack(Material.NAME_TAG)
        val meta = item.itemMeta ?: return item
        meta.displayName("<gold><b>我的好友".toComponent())
        meta.lore(listOf(
            loreInfo("好友", "<white>$total<gray>/<white>$max"),
            loreInfo("在线", "<green>$online <gray>/ <white>$total"),
            loreSpace(),
            "<!i> <yellow>左键 <green>传送到好友身边",
            "<!i> <yellow>右键 <red>删除好友"
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
        meta.displayName("${if (online) "<green>" else "<gray>"}$name".toComponent())
        val cid = ProfileManager.getSharedData(friendId.toString()).cid.id
        val lore = mutableListOf<String>()
        if (cid != 0L) {
            lore += loreInfo("CID", "<white>$cid")
        }
        lore += loreSpace()
        lore += loreInfo("状态", if (online) "<green>● 在线" else "<gray>● 离线")
        if (online) {
            lore += loreSpace()
            lore += "<!i> <yellow>左键 <green>传送到好友身边"
        }
        lore += "<!i> <yellow>右键 <red>删除好友"
        meta.lore(lore.map { it.toComponent() })
        item.setItemMeta(meta)
        return item
    }

    private fun teleportTo(clicker: Player, friendId: UUID) {
        val friend = Bukkit.getPlayer(friendId)
        if (friend == null || !friend.isOnline) {
            clicker.sendMessage("<red>该好友不在线".toComponent())
            return
        }
        if (!clicker.hasPermission("chotentech.friend.teleport")) {
            clicker.sendMessage("<red>你没有传送权限".toComponent())
            return
        }
        if (!ChoTenTech.config.getBoolean("friend.teleport", true)) {
            clicker.sendMessage("<red>传送到好友功能已被管理员禁用".toComponent())
            return
        }
        clicker.teleportAsync(friend.location)
        clicker.sendMessage("<green>已传送到 <yellow>${friend.name} <green>身边".toComponent())
    }
}

class FriendDeleteConfirmMenu(private val friendId: UUID) {

    fun open(player: Player) {
        val name = Bukkit.getOfflinePlayer(friendId).name ?: "未知"

        player.openMenu<taboolib.module.ui.type.Chest>(mmJsonUtil.serialize("<white><shift:-16><image:kangelicon:list><shift:-176><red><font:minecraft:fusionpixel10>删除好友".toComponent())) {
            rows(3)

            map(
                "         ",
                "  X   Y  ",
                "         "
            )

            set('X', guiItem(XMaterial.RED_DYE, "<red><b>确认删除", listOf(
                loreInfo("将删除好友", "<white>$name"),
                loreInfo("警告", "<red>此操作不可撤销")
            ), model = "kangelitem:void")) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                if (FriendManager.remove(clicker, friendId)) {
                    clicker.sendMessage("<red>已删除好友 <yellow>$name".toComponent())
                } else {
                    clicker.sendMessage("<red>该玩家不是你的好友".toComponent())
                }
                clicker.closeInventory()
            }

            set('Y', guiItem(XMaterial.LIME_DYE, "<green><b>取消", listOf("<!i> <yellow>点击 <green>返回好友列表"), model = "kangelitem:void")) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                clicker.closeInventory()
                taboolib.common.platform.function.submit(delay = 1) {
                    FriendMenu().open(clicker)
                }
            }
        }
    }
}
