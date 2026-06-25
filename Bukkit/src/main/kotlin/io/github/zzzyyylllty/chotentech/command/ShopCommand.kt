package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.shop.GiftPackManager
import io.github.zzzyyylllty.chotentech.shop.ShopManager
import io.github.zzzyyylllty.chotentech.shop.ShopMenu
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.player
import taboolib.common.platform.command.subCommand

@CommandHeader(
    name = "chotentech-shop",
    aliases = ["cttshop", "shop"],
    description = "商店系统"
)
object ShopCommand {

    @CommandBody
    val main = mainCommand {
        createModernHelper()
    }

    @CommandBody
    val open = subCommand {
        dynamic("shopId") {
            suggestion<org.bukkit.command.CommandSender> { _, _ ->
                ShopManager.shops.keys.toList()
            }
            execute<Player> { sender, context, _ ->
                val shopId = context["shopId"]
                ShopMenu.open(sender, shopId)
            }
            player("player") {
                execute<org.bukkit.command.CommandSender> { sender, context, _ ->
                    val shopId = context["shopId"]
                    val target = context["player"]
                    ShopMenu.open(target, shopId)
                }
            }
        }
    }

    @CommandBody
    val reload = subCommand {
        execute<org.bukkit.command.CommandSender> { sender, _, _ ->
            if (!sender.hasPermission("chotentech.admin")) {
                sender.sendMessage("§c你没有权限执行此命令")
                return@execute
            }
            ShopManager.reload()
            GiftPackManager.loadAll()
            sender.sendMessage("§a[Shop] 商店配置已重载!")
        }
    }

    @CommandBody
    val unlock = subCommand {
        dynamic("unlockId") {
            suggestion<org.bukkit.command.CommandSender> { _, _ ->
                // Collect all unique unlock IDs from all shops
                ShopManager.shops.values.flatMap { shop ->
                    shop.items.values.mapNotNull { it.unlock }
                }.distinct()
            }
            execute<org.bukkit.command.CommandSender> { sender, context, _ ->
                if (!sender.hasPermission("chotentech.admin")) {
                    sender.sendMessage("§c你没有权限执行此命令")
                    return@execute
                }
                val unlockId = context["unlockId"]
                // Find all players online and unlock
                var count = 0
                for ((uuid, data) in playerDataMap) {
                    if (data.unlockedShopItems.add(unlockId)) {
                        count++
                    }
                }
                sender.sendMessage("§a[Shop] 已为 $count 名在线玩家解锁配方: $unlockId")
            }
        }
        dynamic("player") {
            dynamic("unlockId") {
                suggestion<org.bukkit.command.CommandSender> { _, _ ->
                    ShopManager.shops.values.flatMap { shop ->
                        shop.items.values.mapNotNull { it.unlock }
                    }.distinct()
                }
                execute<org.bukkit.command.CommandSender> { sender, context, _ ->
                    if (!sender.hasPermission("chotentech.admin")) {
                        sender.sendMessage("§c你没有权限执行此命令")
                        return@execute
                    }
                    val playerName = context["player"]
                    val unlockId = context["unlockId"]
                    val target = org.bukkit.Bukkit.getPlayerExact(playerName)
                    if (target == null) {
                        sender.sendMessage("§c玩家 $playerName 不在线")
                        return@execute
                    }
                    val data = playerDataMap[target.uniqueId.toString()]
                    if (data == null) {
                        sender.sendMessage("§c玩家 $playerName 没有数据")
                        return@execute
                    }
                    if (data.unlockedShopItems.add(unlockId)) {
                        sender.sendMessage("§a[Shop] 已为 $playerName 解锁配方: $unlockId")
                    } else {
                        sender.sendMessage("§e[Shop] $playerName 已解锁该配方: $unlockId")
                    }
                }
            }
        }
    }
}
