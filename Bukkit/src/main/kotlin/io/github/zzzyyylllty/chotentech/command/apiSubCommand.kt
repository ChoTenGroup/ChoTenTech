package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.command.createModernHelper
import io.github.zzzyyylllty.chotentech.logger.infoS
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.player
import taboolib.common.platform.command.subCommand
import taboolib.common.platform.function.submitAsync

@CommandHeader(
    name = "quetiapineapi",
    aliases = ["rpgapi", "chotentechapi", "cttapi"],
    permission = "quetiapine.command.api",
    description = "api Command of Quetiapine.",
    permissionMessage = "",
    // 安全加固：eval* 子命令本质是「以任意 sender 身份执行任意 Kether 脚本」，
    // 等价于 RCE 控制台。PermissionDefault.OP 意味着所有 OP（含被误提权的
    // 管理/半信任账号）默认可用；改为 OP 之外显式授权（默认拒绝），
    // 只有真正需要运维调试的账号才应被授予 quetiapine.command.api。
    permissionDefault = PermissionDefault.FALSE,
    newParser = true,
)
object ApiCommand {

    /**
     * eval* 子命令的脚本长度上限。
     * Kether 脚本在 submitAsync 线程池里解释执行，一条超长脚本
     * （数 MB 的拼接字符串）会拖垮异步线程池并挤占内存。正常调试
     * 脚本远低于这个量级。
     */
    private const val MAX_SCRIPT_LEN = 8192

    /** eval* 共用入口：先做长度检查，再执行 Kether 并回显。 */
    private fun evalScript(sender: CommandSender, content: String, scriptTarget: CommandSender?): Any? {
        if (content.length > MAX_SCRIPT_LEN) {
            sender.infoS("<#E08A98>脚本过长 (${content.length} > $MAX_SCRIPT_LEN)，已拒绝执行。")
            return null
        }
        return io.github.zzzyyylllty.chotentech.function.kether.runKether(listOf(content), scriptTarget ?: sender).get()
    }

    @CommandBody
    val main = mainCommand {
        createModernHelper()
    }

    @CommandBody
    val help = subCommand {
        createModernHelper()
    }

    /** 解析 Minimessage */
    @CommandBody
    val minimessage = subCommand {
        dynamic("content") {
            execute<CommandSender> { sender, context, argument ->
                submitAsync {
                    val content = context["content"]
                    sender.infoS(content) }
            }
        }
    }

    /** Kether */
    @CommandBody
    val eval = subCommand {
        dynamic("script") {
            execute<CommandSender> { sender, context, argument ->
                submitAsync {
                    // 获取参数的值
                    val content = context["script"]
                    val ret = evalScript(sender, content, null)
                    if (ret != null || content.length <= MAX_SCRIPT_LEN) {
                        sender.infoS("<#EFDF8F>Kether: <#A9B4C3>$content")
                        sender.infoS("<#EFDF8F>Return: <#A9B4C3>$ret") }
                }
            }
        }
    }

    /** Kether */
    @CommandBody
    val evalByPlayer = subCommand {
        player("player") {
            dynamic("script") {
                execute<CommandSender> { sender, context, argument ->
                    submitAsync {
                        val tabooPlayer = context.player("player")
                        val bukkitPlayer = tabooPlayer.castSafely<Player>() as CommandSender
                        // 获取参数的值
                        val content = context["script"]
                        val ret = evalScript(sender, content, bukkitPlayer)
                        if (ret != null || content.length <= MAX_SCRIPT_LEN) {
                            sender.infoS("<#EFDF8F>Kether: <#A9B4C3>$content")
                            sender.infoS("<#EFDF8F>Return: <#A9B4C3>$ret") }
                    }
                }
            }
        }
    }
    /** Kether */
    @CommandBody
    val evalSilent = subCommand {
        dynamic("script") {
            execute<CommandSender> { sender, context, argument ->
                submitAsync {
                    // 获取参数的值
                    val content = context["script"]
                    evalScript(sender, content, null)
                }
            }
        }
    }

    /** Kether */
    @CommandBody
    val evalByPlayerSilent = subCommand {
        player("player") {
            dynamic("script") {
                execute<CommandSender> { sender, context, argument ->
                    submitAsync {
                        val tabooPlayer = context.player("player")
                        val bukkitPlayer = tabooPlayer.castSafely<Player>() as CommandSender
                        // 获取参数的值
                        val content = context["script"]
                        evalScript(sender, content, bukkitPlayer)
                    }
                }
            }
        }
    }


}
