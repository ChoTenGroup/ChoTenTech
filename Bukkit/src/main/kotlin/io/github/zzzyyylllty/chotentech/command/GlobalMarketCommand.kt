package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.globalmarket.GlobalMarketManager
import io.github.zzzyyylllty.chotentech.globalmarket.GlobalMarketMenu
import io.github.zzzyyylllty.chotentech.util.toComponent
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand

@CommandHeader(
    name = "chotentech-market",
    aliases = ["cttm", "market", "globalmarket"],
    description = "全球市场系统"
)
object GlobalMarketCommand {

    @CommandBody
    val main = mainCommand {
        createModernHelper()
    }

    @CommandBody
    val open = subCommand {
        execute<Player> { sender, _, _ ->
            GlobalMarketMenu.openMain(sender)
        }
    }

    @CommandBody
    val claim = subCommand {
        execute<Player> { sender, _, _ ->
            val result = GlobalMarketManager.claimProceeds(sender)
            sender.sendMessage(result.message.toComponent())
        }
    }

    @CommandBody
    val reload = subCommand {
        execute<org.bukkit.command.CommandSender> { sender, _, _ ->
            if (!sender.hasPermission("chotentech.admin")) {
                sender.sendMessage("<red>你没有权限执行此命令".toComponent())
                return@execute
            }
            GlobalMarketManager.saveToDisk()
            sender.sendMessage("<green>[全球市场] 数据已保存!".toComponent())
        }
    }

    @CommandBody
    val info = subCommand {
        execute<org.bukkit.command.CommandSender> { sender, _, _ ->
            val total = GlobalMarketManager.getActiveListings().size
            val allListings = GlobalMarketManager.getActiveListings()
            val totalValue = allListings.sumOf { it.price.toLong() }
            sender.sendMessage("<gold>=== 全球市场信息 ===".toComponent())
            sender.sendMessage("<gray>活跃商品: <white>$total".toComponent())
            sender.sendMessage("<gray>总价值: <white>$totalValue".toComponent())
            sender.sendMessage("<gray>待领取收益玩家: <white>${GlobalMarketManager.getPendingBalancesCount()}".toComponent())
        }
    }
}
