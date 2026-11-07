package io.github.zzzyyylllty.chotentech.client

import io.github.zzzyyylllty.chotentech.logger.infoS
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand
import taboolib.common.platform.function.submit

/**
 * `/ulsclient` —— 查看已连接的客户端模组信息。
 *
 * 这是排障入口：玩家反馈「装了模组但没效果」时，先用它确认
 * 服务端是否真的收到了握手。
 *
 * 权限：`chotentech.uls.client`
 *
 * 注意：TabooLib 的 `subCommand { }` **不支持** `permission =` 参数
 * （本插件里所有 permission 都写在 `@CommandHeader` 上，
 * 需要不同权限的子命令会另开一个 object）。
 * 因此 `reload` 子命令改为在运行时自行校验权限。
 */
@CommandHeader(
    name = "ulsclient",
    aliases = ["uls"],
    permission = "chotentech.uls.client",
    description = "查看已连接的 ULS 客户端模组信息",
    permissionMessage = "",
    permissionDefault = PermissionDefault.OP,
    newParser = false,
)
object ULSClientCommand {

    /** `reload` 子命令所需的更高权限。 */
    private const val ADMIN_PERMISSION = "chotentech.uls.admin"

    @CommandBody
    val main = mainCommand {
        execute<CommandSender> { sender, _, _ ->
            val count = ULSClientManager.count()
            sender.sendMessage("§7[§bULS§7] §f当前在线且完成握手的客户端: §b$count§f 人")
            sender.sendMessage("§7协议版本: §b${ULSProtocol.PROTOCOL_VERSION}§7, 通道: §b${ULSProtocol.CHANNEL_MAIN}")
            if (count == 0) {
                sender.sendMessage("§7没有玩家使用客户端模组。这在纯插件环境下是正常的。")
            } else {
                sender.sendMessage("§7使用 §b/ulsclient list §7查看详情。")
            }
        }
    }

    /** 列出所有已连接的模组客户端。 */
    @CommandBody
    val list = subCommand {
        execute<CommandSender> { sender, _, _ ->
            val records = ULSClientManager.all()
            if (records.isEmpty()) {
                sender.sendMessage("§7[§bULS§7] §7当前没有已连接的模组客户端。")
                return@execute
            }
            sender.sendMessage("§7[§bULS§7] §f已连接的模组客户端 (§b${records.size}§f):")
            for (record in records) {
                sender.sendMessage(" §8- §b${record.name} §7| §f${record.modVersion} §7| MC §f${record.gameVersion}")
                sender.sendMessage(
                    "     §7Java §f${record.javaVersion} §7| §f${record.osName}/${record.osArch}" +
                            " §7| 语言 §f${record.locale} §7| mod §f${record.mods.size}" +
                            if (record.modsTruncated) "§7(+${record.modCount - record.mods.size})" else ""
                )
                if (record.usernameMismatch) {
                    sender.sendMessage("     §e注意: 客户端自述用户名 '${record.reportedUsername}' 与登录名不符")
                }
            }
        }
    }

    /** 查看单个玩家的详细信息，含完整 mod 列表。 */
    @CommandBody
    val info = subCommand {
        dynamic("player") {
            suggestion<CommandSender> { _, _ ->
                Bukkit.getOnlinePlayers().map { it.name }
            }
            execute<CommandSender> { sender, context, _ ->
                val target = Bukkit.getPlayerExact(context["player"])
                val record = target?.let { ULSClientManager.getRecord(it) }

                if (record == null) {
                    sender.sendMessage("§7[§bULS§7] §7该玩家未使用客户端模组，或尚未完成握手。")
                    return@execute
                }

                sender.sendMessage("§7[§bULS§7] §f客户端详情:")
                sender.sendMessage(" §7玩家: §b${record.name}")
                sender.sendMessage(" §7连接地址: §f${record.address}")
                sender.sendMessage(" §7协议版本: §f${record.protocolVersion}")
                sender.sendMessage(" §7模组版本: §f${record.modVersion}")
                sender.sendMessage(" §7游戏版本: §f${record.gameVersion}")
                sender.sendMessage(" §7Java: §f${record.javaVersion}")
                sender.sendMessage(" §7系统: §f${record.osName}/${record.osArch}")
                sender.sendMessage(" §7语言: §f${record.locale}")
                sender.sendMessage(" §7自述用户名: §f${record.reportedUsername}" +
                        if (record.usernameMismatch) " §e(与登录名不符)" else "")
                sender.sendMessage(" §7mod 列表 (§f${record.mods.size}§7" +
                        (if (record.modsTruncated) " + ${record.modCount - record.mods.size} 已裁剪" else "") + "):")

                record.mods.entries
                    .sortedBy { it.key }
                    .chunked(4)
                    .forEach { chunk ->
                        sender.sendMessage("   §8" + chunk.joinToString("  ") { "§f${it.key}§8@§7${it.value}" })
                    }
            }
        }
    }

    /**
     * 清理某名玩家的服务端侧记录，用于排障。
     *
     * 清理不会令客户端重新握手；只有客户端重连或主动发送握手
     * 才能恢复增强能力。该命令不会「让客户端立刻重连」。
     */
    @CommandBody
    val reload = subCommand {
        dynamic("player") {
            suggestion<CommandSender> { _, _ ->
                Bukkit.getOnlinePlayers().map { it.name }
            }
            execute<CommandSender> { sender, context, _ ->
                if (!sender.hasPermission(ADMIN_PERMISSION)) {
                    sender.sendMessage("§7[§bULS§7] §c你没有权限执行此操作。")
                    return@execute
                }
                val name = context["player"]
                val player: Player? = Bukkit.getPlayerExact(name)
                if (player == null) {
                    sender.sendMessage("§7[§bULS§7] §c找不到在线玩家 $name")
                    return@execute
                }
                ULSClientManager.clearPlayer(player)
                submit {
                    infoS("已清理 ${player.name} 的 ULS 客户端记录，等待其重新上报。")
                }
                sender.sendMessage("§7[§bULS§7] §f已清理 §b${player.name} §f的客户端记录。")
            }
        }
    }
}
