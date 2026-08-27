package io.github.zzzyyylllty.chotentech.punish

import io.github.zzzyyylllty.chotentech.data.ChoTenDataHelper
import io.github.zzzyyylllty.chotentech.player.sendComponent
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import io.github.zzzyyylllty.chotentech.command.createModernHelper
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.player
import taboolib.common.platform.command.subCommand

@CommandHeader(
    name = "chotentech-sanity",
    aliases = ["cttsanity", "sanity"],
    permission = "chotentech.command.sanity",
    permissionDefault = PermissionDefault.OP,
    newParser = true,
)
object SanityCommand {
    @CommandBody
    val main = mainCommand {
        createModernHelper()
    }

    @CommandBody
    val add = subCommand {
        player("target") {
            dynamic("amount") {
                execute<CommandSender> { sender, context, _ ->
                    val player = context.player("target").castSafely<Player>()
                    if (player == null) {
                        sender.sendComponent("<#E08A98>玩家不在线")
                        return@execute
                    }
                    val helper = ChoTenDataHelper(player)
                    val amount = context["amount"]?.toDoubleOrNull()
                    if (amount == null) { sender.sendComponent("<#E08A98>用法: /sanity add <玩家> <数量>"); return@execute }
                    helper.addSanity(amount)
                    sender.sendComponent("<#8FD4A6>已为 <#EFDF8F>${player.name}<#8FD4A6> 增加 <#EFDF8F>$amount<#8FD4A6> 精神值，当前: <#EFDF8F>${"%.1f".format(helper.data.sanity)}")
                }
                suggestion<CommandSender> { _, _ -> listOf("100") }
            }
        }
    }

    @CommandBody
    val set = subCommand {
        player("target") {
            dynamic("amount") {
                execute<CommandSender> { sender, context, _ ->
                    val player = context.player("target").castSafely<Player>()
                    if (player == null) {
                        sender.sendComponent("<#E08A98>玩家不在线")
                        return@execute
                    }
                    val helper = ChoTenDataHelper(player)
                    val amount = context["amount"]?.toDoubleOrNull()
                    if (amount == null) { sender.sendComponent("<#E08A98>用法: /sanity set <玩家> <数量>"); return@execute }
                    helper.setSanity(amount)
                    sender.sendComponent("<#8FD4A6>已将 <#EFDF8F>${player.name}<#8FD4A6> 的精神值设为 <#EFDF8F>${"%.1f".format(helper.data.sanity)}")
                }
                suggestion<CommandSender> { _, _ -> listOf("100") }
            }
        }
    }

    @CommandBody
    val reset = subCommand {
        player("target") {
            execute<CommandSender> { sender, context, _ ->
                val player = context.player("target").castSafely<Player>()
                if (player == null) {
                    sender.sendComponent("<#E08A98>玩家不在线")
                    return@execute
                }
                val helper = ChoTenDataHelper(player)
                helper.resetSanity()
                sender.sendComponent("<#8FD4A6>已重置 <#EFDF8F>${player.name}<#8FD4A6> 的精神值，当前: <#EFDF8F>${"%.1f".format(helper.data.sanity)}")
            }
        }
    }

    @CommandBody
    val get = subCommand {
        player("target") {
            execute<CommandSender> { sender, context, _ ->
                val player = context.player("target").castSafely<Player>()
                if (player == null) {
                    sender.sendComponent("<#E08A98>玩家不在线")
                    return@execute
                }
                val helper = ChoTenDataHelper(player)
                sender.sendComponent("<#EFDF8F>${player.name}<#8FD4A6> 精神值: <#EFDF8F>${"%.1f".format(helper.data.sanity)}<#8FD4A6> / <#EFDF8F>${"%.1f".format(helper.data.effectiveMaxSanity)}")
            }
        }
    }

    @CommandBody
    val full = subCommand {
        player("target") {
            execute<CommandSender> { sender, context, _ ->
                val player = context.player("target").castSafely<Player>()
                if (player == null) {
                    sender.sendComponent("<#E08A98>玩家不在线")
                    return@execute
                }
                val helper = ChoTenDataHelper(player)
                helper.fullSanity()
                sender.sendComponent("<#8FD4A6>已回满 <#EFDF8F>${player.name}<#8FD4A6> 的精神值")
            }
        }
    }

    @CommandBody
    val maxadd = subCommand {
        player("target") {
            dynamic("amount") {
                execute<CommandSender> { sender, context, _ ->
                    val player = context.player("target").castSafely<Player>()
                    if (player == null) {
                        sender.sendComponent("<#E08A98>玩家不在线")
                        return@execute
                    }
                    val helper = ChoTenDataHelper(player)
                    val amount = context["amount"]?.toDoubleOrNull()
                    if (amount == null) { sender.sendComponent("<#E08A98>用法: /sanity maxadd <玩家> <数量>"); return@execute }
                    helper.addMaxSanity(amount)
                    sender.sendComponent("<#8FD4A6>已为 <#EFDF8F>${player.name}<#8FD4A6> 增加 <#EFDF8F>$amount<#8FD4A6> 最大精神值，当前: <#EFDF8F>${"%.1f".format(helper.data.effectiveMaxSanity)}")
                }
                suggestion<CommandSender> { _, _ -> listOf("100") }
            }
        }
    }

    @CommandBody
    val maxset = subCommand {
        player("target") {
            dynamic("amount") {
                execute<CommandSender> { sender, context, _ ->
                    val player = context.player("target").castSafely<Player>()
                    if (player == null) {
                        sender.sendComponent("<#E08A98>玩家不在线")
                        return@execute
                    }
                    val helper = ChoTenDataHelper(player)
                    val amount = context["amount"]?.toDoubleOrNull()
                    if (amount == null) { sender.sendComponent("<#E08A98>用法: /sanity maxset <玩家> <数量>"); return@execute }
                    helper.setMaxSanity(amount)
                    sender.sendComponent("<#8FD4A6>已将 <#EFDF8F>${player.name}<#8FD4A6> 的最大精神值设为 <#EFDF8F>${"%.1f".format(helper.data.effectiveMaxSanity)}")
                }
                suggestion<CommandSender> { _, _ -> listOf("100") }
            }
        }
    }

    @CommandBody
    val maxreset = subCommand {
        player("target") {
            execute<CommandSender> { sender, context, _ ->
                val player = context.player("target").castSafely<Player>()
                if (player == null) {
                    sender.sendComponent("<#E08A98>玩家不在线")
                    return@execute
                }
                val helper = ChoTenDataHelper(player)
                helper.resetMaxSanity()
                sender.sendComponent("<#8FD4A6>已重置 <#EFDF8F>${player.name}<#8FD4A6> 的最大精神值，当前: <#EFDF8F>${"%.1f".format(helper.data.effectiveMaxSanity)}")
            }
        }
    }

    @CommandBody
    val maxget = subCommand {
        player("target") {
            execute<CommandSender> { sender, context, _ ->
                val player = context.player("target").castSafely<Player>()
                if (player == null) {
                    sender.sendComponent("<#E08A98>玩家不在线")
                    return@execute
                }
                val helper = ChoTenDataHelper(player)
                sender.sendComponent("<#EFDF8F>${player.name}<#8FD4A6> 最大精神值: <#EFDF8F>${"%.1f".format(helper.data.effectiveMaxSanity)}")
            }
        }
    }

    @CommandBody
    val regenadd = subCommand {
        player("target") {
            dynamic("amount") {
                execute<CommandSender> { sender, context, _ ->
                    val player = context.player("target").castSafely<Player>()
                    if (player == null) {
                        sender.sendComponent("<#E08A98>玩家不在线")
                        return@execute
                    }
                    val helper = ChoTenDataHelper(player)
                    val amount = context["amount"]?.toDoubleOrNull()
                    if (amount == null) { sender.sendComponent("<#E08A98>用法: /sanity regenadd <玩家> <数量>"); return@execute }
                    helper.addSanityRegeneration(amount)
                    sender.sendComponent("<#8FD4A6>已为 <#EFDF8F>${player.name}<#8FD4A6> 增加 <#EFDF8F>$amount<#8FD4A6> 精神恢复，当前: <#EFDF8F>${"%.1f".format(helper.data.sanityRegeneration)}")
                }
                suggestion<CommandSender> { _, _ -> listOf("100") }
            }
        }
    }

    @CommandBody
    val regenset = subCommand {
        player("target") {
            dynamic("amount") {
                execute<CommandSender> { sender, context, _ ->
                    val player = context.player("target").castSafely<Player>()
                    if (player == null) {
                        sender.sendComponent("<#E08A98>玩家不在线")
                        return@execute
                    }
                    val helper = ChoTenDataHelper(player)
                    val amount = context["amount"]?.toDoubleOrNull()
                    if (amount == null) { sender.sendComponent("<#E08A98>用法: /sanity regenset <玩家> <数量>"); return@execute }
                    helper.setSanityRegeneration(amount)
                    sender.sendComponent("<#8FD4A6>已将 <#EFDF8F>${player.name}<#8FD4A6> 的精神恢复设为 <#EFDF8F>${"%.1f".format(helper.data.sanityRegeneration)}")
                }
                suggestion<CommandSender> { _, _ -> listOf("100") }
            }
        }
    }

    @CommandBody
    val regenreset = subCommand {
        player("target") {
            execute<CommandSender> { sender, context, _ ->
                val player = context.player("target").castSafely<Player>()
                if (player == null) {
                    sender.sendComponent("<#E08A98>玩家不在线")
                    return@execute
                }
                val helper = ChoTenDataHelper(player)
                helper.resetSanityRegeneration()
                sender.sendComponent("<#8FD4A6>已重置 <#EFDF8F>${player.name}<#8FD4A6> 的精神恢复，当前: <#EFDF8F>${"%.1f".format(helper.data.sanityRegeneration)}")
            }
        }
    }

    @CommandBody
    val regenget = subCommand {
        player("target") {
            execute<CommandSender> { sender, context, _ ->
                val player = context.player("target").castSafely<Player>()
                if (player == null) {
                    sender.sendComponent("<#E08A98>玩家不在线")
                    return@execute
                }
                val helper = ChoTenDataHelper(player)
                sender.sendComponent("<#EFDF8F>${player.name}<#8FD4A6> 精神恢复: <#EFDF8F>${"%.1f".format(helper.data.sanityRegeneration)}")
            }
        }
    }
}

@CommandHeader(
    name = "chotentech-stress",
    aliases = ["cttstress", "stress"],
    permission = "chotentech.command.stress",
    permissionDefault = PermissionDefault.OP,
    newParser = true,
)
object StressCommand {
    @CommandBody
    val main = mainCommand {
        createModernHelper()
    }

    @CommandBody
    val add = subCommand {
        player("target") {
            dynamic("amount") {
                execute<CommandSender> { sender, context, _ ->
                    val player = context.player("target").castSafely<Player>()
                    if (player == null) {
                        sender.sendComponent("<#E08A98>玩家不在线")
                        return@execute
                    }
                    val helper = ChoTenDataHelper(player)
                    val amount = context["amount"]?.toDoubleOrNull()
                    if (amount == null) { sender.sendComponent("<#E08A98>用法: /stress add <玩家> <数量>"); return@execute }
                    helper.addStress(amount)
                    sender.sendComponent("<#8FD4A6>已为 <#EFDF8F>${player.name}<#8FD4A6> 增加 <#EFDF8F>$amount<#8FD4A6> 压力，当前: <#EFDF8F>${"%.1f".format(helper.data.stress)}")
                }
                suggestion<CommandSender> { _, _ -> listOf("100") }
            }
        }
    }

    @CommandBody
    val set = subCommand {
        player("target") {
            dynamic("amount") {
                execute<CommandSender> { sender, context, _ ->
                    val player = context.player("target").castSafely<Player>()
                    if (player == null) {
                        sender.sendComponent("<#E08A98>玩家不在线")
                        return@execute
                    }
                    val helper = ChoTenDataHelper(player)
                    val amount = context["amount"]?.toDoubleOrNull()
                    if (amount == null) { sender.sendComponent("<#E08A98>用法: /stress set <玩家> <数量>"); return@execute }
                    helper.setStress(amount)
                    sender.sendComponent("<#8FD4A6>已将 <#EFDF8F>${player.name}<#8FD4A6> 的压力设为 <#EFDF8F>${"%.1f".format(helper.data.stress)}")
                }
                suggestion<CommandSender> { _, _ -> listOf("100") }
            }
        }
    }

    @CommandBody
    val reset = subCommand {
        player("target") {
            execute<CommandSender> { sender, context, _ ->
                val player = context.player("target").castSafely<Player>()
                if (player == null) {
                    sender.sendComponent("<#E08A98>玩家不在线")
                    return@execute
                }
                val helper = ChoTenDataHelper(player)
                helper.resetStress()
                sender.sendComponent("<#8FD4A6>已重置 <#EFDF8F>${player.name}<#8FD4A6> 的压力，当前: <#EFDF8F>${"%.1f".format(helper.data.stress)}")
            }
        }
    }

    @CommandBody
    val get = subCommand {
        player("target") {
            execute<CommandSender> { sender, context, _ ->
                val player = context.player("target").castSafely<Player>()
                if (player == null) {
                    sender.sendComponent("<#E08A98>玩家不在线")
                    return@execute
                }
                val helper = ChoTenDataHelper(player)
                sender.sendComponent("<#EFDF8F>${player.name}<#8FD4A6> 压力: <#EFDF8F>${"%.1f".format(helper.data.stress)}<#8FD4A6> / 100.0")
            }
        }
    }
}

@CommandHeader(
    name = "chotentech-darkness",
    aliases = ["cttdarkness", "darkness"],
    permission = "chotentech.command.darkness",
    permissionDefault = PermissionDefault.OP,
    newParser = true,
)
object DarknessCommand {
    @CommandBody
    val main = mainCommand {
        createModernHelper()
    }

    @CommandBody
    val add = subCommand {
        player("target") {
            dynamic("amount") {
                execute<CommandSender> { sender, context, _ ->
                    val player = context.player("target").castSafely<Player>()
                    if (player == null) {
                        sender.sendComponent("<#E08A98>玩家不在线")
                        return@execute
                    }
                    val helper = ChoTenDataHelper(player)
                    val amount = context["amount"]?.toDoubleOrNull()
                    if (amount == null) { sender.sendComponent("<#E08A98>用法: /darkness add <玩家> <数量>"); return@execute }
                    helper.addDarkness(amount)
                    sender.sendComponent("<#8FD4A6>已为 <#EFDF8F>${player.name}<#8FD4A6> 增加 <#EFDF8F>$amount<#8FD4A6> 阴暗值，当前: <#EFDF8F>${"%.1f".format(helper.data.darkness)}")
                }
                suggestion<CommandSender> { _, _ -> listOf("100") }
            }
        }
    }

    @CommandBody
    val set = subCommand {
        player("target") {
            dynamic("amount") {
                execute<CommandSender> { sender, context, _ ->
                    val player = context.player("target").castSafely<Player>()
                    if (player == null) {
                        sender.sendComponent("<#E08A98>玩家不在线")
                        return@execute
                    }
                    val helper = ChoTenDataHelper(player)
                    val amount = context["amount"]?.toDoubleOrNull()
                    if (amount == null) { sender.sendComponent("<#E08A98>用法: /darkness set <玩家> <数量>"); return@execute }
                    helper.setDarkness(amount)
                    sender.sendComponent("<#8FD4A6>已将 <#EFDF8F>${player.name}<#8FD4A6> 的阴暗值设为 <#EFDF8F>${"%.1f".format(helper.data.darkness)}")
                }
                suggestion<CommandSender> { _, _ -> listOf("100") }
            }
        }
    }

    @CommandBody
    val reset = subCommand {
        player("target") {
            execute<CommandSender> { sender, context, _ ->
                val player = context.player("target").castSafely<Player>()
                if (player == null) {
                    sender.sendComponent("<#E08A98>玩家不在线")
                    return@execute
                }
                val helper = ChoTenDataHelper(player)
                helper.resetDarkness()
                sender.sendComponent("<#8FD4A6>已重置 <#EFDF8F>${player.name}<#8FD4A6> 的阴暗值，当前: <#EFDF8F>${"%.1f".format(helper.data.darkness)}")
            }
        }
    }

    @CommandBody
    val get = subCommand {
        player("target") {
            execute<CommandSender> { sender, context, _ ->
                val player = context.player("target").castSafely<Player>()
                if (player == null) {
                    sender.sendComponent("<#E08A98>玩家不在线")
                    return@execute
                }
                val helper = ChoTenDataHelper(player)
                sender.sendComponent("<#EFDF8F>${player.name}<#8FD4A6> 阴暗值: <#EFDF8F>${"%.1f".format(helper.data.darkness)}<#8FD4A6> / <#EFDF8F>${"%.1f".format(helper.data.effectiveMaxDarkness)}")
            }
        }
    }

    @CommandBody
    val full = subCommand {
        player("target") {
            execute<CommandSender> { sender, context, _ ->
                val player = context.player("target").castSafely<Player>()
                if (player == null) {
                    sender.sendComponent("<#E08A98>玩家不在线")
                    return@execute
                }
                val helper = ChoTenDataHelper(player)
                helper.fullDarkness()
                sender.sendComponent("<#8FD4A6>已填满 <#EFDF8F>${player.name}<#8FD4A6> 的阴暗值")
            }
        }
    }

    @CommandBody
    val maxadd = subCommand {
        player("target") {
            dynamic("amount") {
                execute<CommandSender> { sender, context, _ ->
                    val player = context.player("target").castSafely<Player>()
                    if (player == null) {
                        sender.sendComponent("<#E08A98>玩家不在线")
                        return@execute
                    }
                    val helper = ChoTenDataHelper(player)
                    val amount = context["amount"]?.toDoubleOrNull()
                    if (amount == null) { sender.sendComponent("<#E08A98>用法: /darkness maxadd <玩家> <数量>"); return@execute }
                    helper.addMaxDarkness(amount)
                    sender.sendComponent("<#8FD4A6>已为 <#EFDF8F>${player.name}<#8FD4A6> 增加 <#EFDF8F>$amount<#8FD4A6> 最大阴暗值，当前: <#EFDF8F>${"%.1f".format(helper.data.effectiveMaxDarkness)}")
                }
                suggestion<CommandSender> { _, _ -> listOf("100") }
            }
        }
    }

    @CommandBody
    val maxset = subCommand {
        player("target") {
            dynamic("amount") {
                execute<CommandSender> { sender, context, _ ->
                    val player = context.player("target").castSafely<Player>()
                    if (player == null) {
                        sender.sendComponent("<#E08A98>玩家不在线")
                        return@execute
                    }
                    val helper = ChoTenDataHelper(player)
                    val amount = context["amount"]?.toDoubleOrNull()
                    if (amount == null) { sender.sendComponent("<#E08A98>用法: /darkness maxset <玩家> <数量>"); return@execute }
                    helper.setMaxDarkness(amount)
                    sender.sendComponent("<#8FD4A6>已将 <#EFDF8F>${player.name}<#8FD4A6> 的最大阴暗值设为 <#EFDF8F>${"%.1f".format(helper.data.effectiveMaxDarkness)}")
                }
                suggestion<CommandSender> { _, _ -> listOf("100") }
            }
        }
    }

    @CommandBody
    val maxreset = subCommand {
        player("target") {
            execute<CommandSender> { sender, context, _ ->
                val player = context.player("target").castSafely<Player>()
                if (player == null) {
                    sender.sendComponent("<#E08A98>玩家不在线")
                    return@execute
                }
                val helper = ChoTenDataHelper(player)
                helper.resetMaxDarkness()
                sender.sendComponent("<#8FD4A6>已重置 <#EFDF8F>${player.name}<#8FD4A6> 的最大阴暗值，当前: <#EFDF8F>${"%.1f".format(helper.data.effectiveMaxDarkness)}")
            }
        }
    }

    @CommandBody
    val maxget = subCommand {
        player("target") {
            execute<CommandSender> { sender, context, _ ->
                val player = context.player("target").castSafely<Player>()
                if (player == null) {
                    sender.sendComponent("<#E08A98>玩家不在线")
                    return@execute
                }
                val helper = ChoTenDataHelper(player)
                sender.sendComponent("<#EFDF8F>${player.name}<#8FD4A6> 最大阴暗值: <#EFDF8F>${"%.1f".format(helper.data.effectiveMaxDarkness)}")
            }
        }
    }
}
