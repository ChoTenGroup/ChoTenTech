package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.data.backpack.BackpackConfig
import io.github.zzzyyylllty.chotentech.data.backpack.BackpackManager
import io.github.zzzyyylllty.chotentech.data.backpack.PlayerBackpack
import org.bukkit.Material
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.subCommand
import taboolib.module.nms.getItemTag

@CommandHeader(
    name = "chotenbackpack",
    aliases = ["cb", "cho"],
    description = "ChoTenBackpack 管理命令",
    permission = "chotentech.command.backpack"
)
object BackpackCommand {

    @CommandBody
    val backpack = subCommand {

        literal("unlock") {

            // /chotenbackpack backpack unlock <uuid> — 按 UUID 解锁
            dynamic("uuid") {
                execute<org.bukkit.command.CommandSender> { sender, context, _ ->
                    val uuid = context["uuid"]
                    val bp = BackpackManager.mapper.findOneByKey(PlayerBackpack(uuid, "", "", 0))
                    if (bp == null) {
                        sender.sendMessage("§c未找到 UUID 为 $uuid 的背包")
                        return@execute
                    }
                    if (!bp.disabled) {
                        sender.sendMessage("§e该背包未被禁用")
                        return@execute
                    }
                    BackpackManager.updateBackpack(bp.copy(disabled = false))
                    sender.sendMessage("§a已解锁背包 $uuid")
                }
            }

            // /chotenbackpack backpack unlock — 从手持物品读取 UUID 解锁
            execute<org.bukkit.command.CommandSender> { sender, _, _ ->
                if (sender !is Player) {
                    sender.sendMessage("§c请手持背包物品或指定 UUID")
                    return@execute
                }
                val item = sender.inventory.itemInMainHand
                if (item.type == Material.AIR) {
                    sender.sendMessage("§c请手持背包物品")
                    return@execute
                }
                val uuid = item.getItemTag()[BackpackConfig.uuidTag]?.asString()
                if (uuid == null) {
                    sender.sendMessage("§c手中物品不是背包物品")
                    return@execute
                }
                val bp = BackpackManager.mapper.findOneByKey(PlayerBackpack(uuid, "", "", 0))
                if (bp == null) {
                    sender.sendMessage("§c未找到背包数据")
                    return@execute
                }
                if (!bp.disabled) {
                    sender.sendMessage("§e该背包未被禁用")
                    return@execute
                }
                BackpackManager.updateBackpack(bp.copy(disabled = false))
                sender.sendMessage("§a已解锁背包 $uuid")
            }
        }

        // /chotenbackpack backpack info — 查看手持背包的信息
        literal("info") {
            execute<org.bukkit.command.CommandSender> { sender, _, _ ->
                if (sender !is Player) {
                    sender.sendMessage("§c请手持背包物品")
                    return@execute
                }
                val item = sender.inventory.itemInMainHand
                if (item.type == Material.AIR) return@execute
                val uuid = item.getItemTag()[BackpackConfig.uuidTag]?.asString()
                if (uuid == null) {
                    sender.sendMessage("§c手中物品不是背包物品")
                    return@execute
                }
                val bp = BackpackManager.mapper.findOneByKey(PlayerBackpack(uuid, "", "", 0))
                if (bp == null) {
                    sender.sendMessage("§c该背包尚无数据")
                    return@execute
                }
                sender.sendMessage("§6=== 背包信息 ===")
                sender.sendMessage("§7UUID: §f${bp.backpackId}")
                sender.sendMessage("§7类型: §f${bp.type}")
                sender.sendMessage("§7格子: §f${bp.slotCount}")
                sender.sendMessage("§7状态: ${if (bp.disabled) "§c已禁用" else "§a正常"}")
                sender.sendMessage("§7打开记录: §f${bp.openedBy}")
                if (bp.suspectedPlayers != "[]") {
                    sender.sendMessage("§c嫌疑玩家: §f${bp.suspectedPlayers}")
                }
            }
        }
    }
}
