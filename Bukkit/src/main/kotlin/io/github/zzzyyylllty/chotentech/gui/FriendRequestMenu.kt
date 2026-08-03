package io.github.zzzyyylllty.chotentech.gui

import io.github.zzzyyylllty.chotentech.friend.FriendManager
import io.github.zzzyyylllty.chotentech.friend.FriendRequest
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
import taboolib.common.platform.function.submit
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import taboolib.module.ui.type.PageableChest

class FriendRequestMenu {

    fun open(player: Player) {
        val requests = FriendManager.getRequests(player.uniqueId)

        if (requests.isEmpty()) {
            player.openMenu<Chest>(mmJsonUtil.serialize("<white><shift:-16><image:kangelicon:list><shift:-176><yellow><font:minecraft:fusionpixel10>好友申请".toComponent())) {
                rows(3)

                map(
                    "         ",
                    "  X     Y",
                    "         "
                )

                set('X', guiItem(XMaterial.GRAY_DYE, "<gray>没有待处理的好友申请", listOf(
                    loreInfo("用法", "<white>/friend add <玩家名>"),
                    loreInfo("说明", "<gray>向其他玩家发送申请")
                ), model = "kangelitem:void"))

                set('Y', guiItem(XMaterial.COMPASS, "<aqua><bold>返回", listOf("<!i> <yellow>点击 <green>返回好友列表"), model = "kangelitem:void")) {
                    clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                    FriendMenu().open(clicker)
                }
            }
            return
        }

        player.openMenu<PageableChest<FriendRequest>>(mmJsonUtil.serialize("<white><shift:-16><image:kangelicon:list><shift:-176><yellow><font:minecraft:fusionpixel10>好友申请".toComponent())) {
            rows(6)

            map(
                "CCC    <>",
                "         ",
                " @@@@@@@ ",
                " @@@@@@@ ",
                " @@@@@@@ ",
                "         "
            )

            set('C', guiItem(XMaterial.COMPASS, "<aqua><bold>返回", listOf("<!i> <yellow>点击 <green>返回好友列表"), model = "kangelitem:void")) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                FriendMenu().open(clicker)
            }

            slotsBy('@')

            elements { requests }

            onGenerate { _, request, _, _ ->
                buildRequestItem(request)
            }

            onClick { event, request ->
                event.isCancelled = true
                val clicker = event.clicker
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)

                when {
                    event.clickEvent().click.name == "LEFT" || event.clickEvent().click.name == "SHIFT_LEFT" -> {
                        if (FriendManager.accept(clicker, request.requester)) {
                            val requesterName = Bukkit.getOfflinePlayer(request.requester).name ?: "未知"
                            clicker.sendMessage("<green>已同意 <yellow>$requesterName <green>的好友申请".toComponent())
                            val requester = Bukkit.getPlayer(request.requester)
                            if (requester != null && requester.isOnline) {
                                requester.sendMessage("<green>✦ <yellow>${clicker.name} <green>同意了你的好友申请！".toComponent())
                            }
                        } else {
                            clicker.sendMessage("<red>无法添加该好友（好友数量已达上限或申请已失效）".toComponent())
                        }
                        clicker.closeInventory()
                        submit(delay = 1) { open(clicker) }
                    }
                    event.clickEvent().click.name == "RIGHT" || event.clickEvent().click.name == "SHIFT_RIGHT" -> {
                        if (FriendManager.deny(clicker, request.requester)) {
                            val requesterName = Bukkit.getOfflinePlayer(request.requester).name ?: "未知"
                            clicker.sendMessage("<gray>已拒绝 <white>$requesterName <gray>的好友申请".toComponent())
                            val requester = Bukkit.getPlayer(request.requester)
                            if (requester != null && requester.isOnline) {
                                requester.sendMessage("<gray>✦ <yellow>${clicker.name} <gray>拒绝了你的好友申请".toComponent())
                            }
                        }
                        clicker.closeInventory()
                        submit(delay = 1) { open(clicker) }
                    }
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

    private fun buildRequestItem(request: FriendRequest): ItemStack {
        val requesterName = Bukkit.getOfflinePlayer(request.requester).name ?: "未知"
        val requester = Bukkit.getPlayer(request.requester)
        val online = requester != null && requester.isOnline

        val item = ItemStack(Material.PLAYER_HEAD)
        if (online) {
            val skullMeta = item.itemMeta as? SkullMeta
            skullMeta?.owningPlayer = requester
            if (skullMeta != null) item.setItemMeta(skullMeta)
        }

        val meta = item.itemMeta ?: return item
        meta.displayName("<yellow>$requesterName".toComponent())
        meta.lore(listOf(
            loreInfo("留言", "<gray>想添加你为好友"),
            loreSpace(),
            loreInfo("状态", if (online) "<green>● 在线" else "<gray>● 离线"),
            loreSpace(),
            "<!i> <yellow>左键 <green>同意",
            "<!i> <yellow>右键 <red>拒绝"
        ).map { it.toComponent() })
        item.setItemMeta(meta)
        return item
    }
}
