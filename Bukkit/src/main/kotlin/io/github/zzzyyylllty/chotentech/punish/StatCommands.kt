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
                        sender.sendComponent("<red>玩家不在线")
                        return@execute
                    }
                    val helper = ChoTenDataHelper(player)
                    val amount = context["amount"]?.toDoubleOrNull()
                    if (amount == null) { sender.sendComponent("<red>用法: /sanity add <玩家> <数量>"); return@execute }
                    helper.addSanity(amount)
                    sender.sendComponent("<green>已为 <yellow>${player.name}<green> 增加 <yellow>$amount<green> 精神值，当前: <yellow>${"%.1f".format(helper.data.sanity)}")
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
                        sender.sendComponent("<red>玩家不在线")
                        return@execute
                    }
                    val helper = ChoTenDataHelper(player)
                    val amount = context["amount"]?.toDoubleOrNull()
                    if (amount == null) { sender.sendComponent("<red>用法: /sanity set <玩家> <数量>"); return@execute }
                    helper.setSanity(amount)
                    sender.sendComponent("<green>已将 <yellow>${player.name}<green> 的精神值设为 <yellow>${"%.1f".format(helper.data.sanity)}")
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
                    sender.sendComponent("<red>玩家不在线")
                    return@execute
                }
                val helper = ChoTenDataHelper(player)
                helper.resetSanity()
                sender.sendComponent("<green>已重置 <yellow>${player.name}<green> 的精神值，当前: <yellow>${"%.1f".format(helper.data.sanity)}")
            }
        }
    }

    @CommandBody
    val get = subCommand {
        player("target") {
            execute<CommandSender> { sender, context, _ ->
                val player = context.player("target").castSafely<Player>()
                if (player == null) {
                    sender.sendComponent("<red>玩家不在线")
                    return@execute
                }
                val helper = ChoTenDataHelper(player)
                sender.sendComponent("<yellow>${player.name}<green> 精神值: <yellow>${"%.1f".format(helper.data.sanity)}<green> / <yellow>${"%.1f".format(helper.data.effectiveMaxSanity)}")
            }
        }
    }

    @CommandBody
    val full = subCommand {
        player("target") {
            execute<CommandSender> { sender, context, _ ->
                val player = context.player("target").castSafely<Player>()
                if (player == null) {
                    sender.sendComponent("<red>玩家不在线")
                    return@execute
                }
                val helper = ChoTenDataHelper(player)
                helper.fullSanity()
                sender.sendComponent("<green>已回满 <yellow>${player.name}<green> 的精神值")
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
                        sender.sendComponent("<red>玩家不在线")
                        return@execute
                    }
                    val helper = ChoTenDataHelper(player)
                    val amount = context["amount"]?.toDoubleOrNull()
                    if (amount == null) { sender.sendComponent("<red>用法: /sanity maxadd <玩家> <数量>"); return@execute }
                    helper.addMaxSanity(amount)
                    sender.sendComponent("<green>已为 <yellow>${player.name}<green> 增加 <yellow>$amount<green> 最大精神值，当前: <yellow>${"%.1f".format(helper.data.effectiveMaxSanity)}")
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
                        sender.sendComponent("<red>玩家不在线")
                        return@execute
                    }
                    val helper = ChoTenDataHelper(player)
                    val amount = context["amount"]?.toDoubleOrNull()
                    if (amount == null) { sender.sendComponent("<red>用法: /sanity maxset <玩家> <数量>"); return@execute }
                    helper.setMaxSanity(amount)
                    sender.sendComponent("<green>已将 <yellow>${player.name}<green> 的最大精神值设为 <yellow>${"%.1f".format(helper.data.effectiveMaxSanity)}")
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
                    sender.sendComponent("<red>玩家不在线")
                    return@execute
                }
                val helper = ChoTenDataHelper(player)
                helper.resetMaxSanity()
                sender.sendComponent("<green>已重置 <yellow>${player.name}<green> 的最大精神值，当前: <yellow>${"%.1f".format(helper.data.effectiveMaxSanity)}")
            }
        }
    }

    @CommandBody
    val maxget = subCommand {
        player("target") {
            execute<CommandSender> { sender, context, _ ->
                val player = context.player("target").castSafely<Player>()
                if (player == null) {
                    sender.sendComponent("<red>玩家不在线")
                    return@execute
                }
                val helper = ChoTenDataHelper(player)
                sender.sendComponent("<yellow>${player.name}<green> 最大精神值: <yellow>${"%.1f".format(helper.data.effectiveMaxSanity)}")
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
                        sender.sendComponent("<red>玩家不在线")
                        return@execute
                    }
                    val helper = ChoTenDataHelper(player)
                    val amount = context["amount"]?.toDoubleOrNull()
                    if (amount == null) { sender.sendComponent("<red>用法: /sanity regenadd <玩家> <数量>"); return@execute }
                    helper.addSanityRegeneration(amount)
                    sender.sendComponent("<green>已为 <yellow>${player.name}<green> 增加 <yellow>$amount<green> 精神恢复，当前: <yellow>${"%.1f".format(helper.data.sanityRegeneration)}")
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
                        sender.sendComponent("<red>玩家不在线")
                        return@execute
                    }
                    val helper = ChoTenDataHelper(player)
                    val amount = context["amount"]?.toDoubleOrNull()
                    if (amount == null) { sender.sendComponent("<red>用法: /sanity regenset <玩家> <数量>"); return@execute }
                    helper.setSanityRegeneration(amount)
                    sender.sendComponent("<green>已将 <yellow>${player.name}<green> 的精神恢复设为 <yellow>${"%.1f".format(helper.data.sanityRegeneration)}")
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
                    sender.sendComponent("<red>玩家不在线")
                    return@execute
                }
                val helper = ChoTenDataHelper(player)
                helper.resetSanityRegeneration()
                sender.sendComponent("<green>已重置 <yellow>${player.name}<green> 的精神恢复，当前: <yellow>${"%.1f".format(helper.data.sanityRegeneration)}")
            }
        }
    }

    @CommandBody
    val regenget = subCommand {
        player("target") {
            execute<CommandSender> { sender, context, _ ->
                val player = context.player("target").castSafely<Player>()
                if (player == null) {
                    sender.sendComponent("<red>玩家不在线")
                    return@execute
                }
                val helper = ChoTenDataHelper(player)
                sender.sendComponent("<yellow>${player.name}<green> 精神恢复: <yellow>${"%.1f".format(helper.data.sanityRegeneration)}")
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
                        sender.sendComponent("<red>玩家不在线")
                        return@execute
                    }
                    val helper = ChoTenDataHelper(player)
                    val amount = context["amount"]?.toDoubleOrNull()
                    if (amount == null) { sender.sendComponent("<red>用法: /stress add <玩家> <数量>"); return@execute }
                    helper.addStress(amount)
                    sender.sendComponent("<green>已为 <yellow>${player.name}<green> 增加 <yellow>$amount<green> 压力，当前: <yellow>${"%.1f".format(helper.data.stress)}")
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
                        sender.sendComponent("<red>玩家不在线")
                        return@execute
                    }
                    val helper = ChoTenDataHelper(player)
                    val amount = context["amount"]?.toDoubleOrNull()
                    if (amount == null) { sender.sendComponent("<red>用法: /stress set <玩家> <数量>"); return@execute }
                    helper.setStress(amount)
                    sender.sendComponent("<green>已将 <yellow>${player.name}<green> 的压力设为 <yellow>${"%.1f".format(helper.data.stress)}")
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
                    sender.sendComponent("<red>玩家不在线")
                    return@execute
                }
                val helper = ChoTenDataHelper(player)
                helper.resetStress()
                sender.sendComponent("<green>已重置 <yellow>${player.name}<green> 的压力，当前: <yellow>${"%.1f".format(helper.data.stress)}")
            }
        }
    }

    @CommandBody
    val get = subCommand {
        player("target") {
            execute<CommandSender> { sender, context, _ ->
                val player = context.player("target").castSafely<Player>()
                if (player == null) {
                    sender.sendComponent("<red>玩家不在线")
                    return@execute
                }
                val helper = ChoTenDataHelper(player)
                sender.sendComponent("<yellow>${player.name}<green> 压力: <yellow>${"%.1f".format(helper.data.stress)}<green> / 100.0")
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
                        sender.sendComponent("<red>玩家不在线")
                        return@execute
                    }
                    val helper = ChoTenDataHelper(player)
                    val amount = context["amount"]?.toDoubleOrNull()
                    if (amount == null) { sender.sendComponent("<red>用法: /darkness add <玩家> <数量>"); return@execute }
                    helper.addDarkness(amount)
                    sender.sendComponent("<green>已为 <yellow>${player.name}<green> 增加 <yellow>$amount<green> 阴暗值，当前: <yellow>${"%.1f".format(helper.data.darkness)}")
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
                        sender.sendComponent("<red>玩家不在线")
                        return@execute
                    }
                    val helper = ChoTenDataHelper(player)
                    val amount = context["amount"]?.toDoubleOrNull()
                    if (amount == null) { sender.sendComponent("<red>用法: /darkness set <玩家> <数量>"); return@execute }
                    helper.setDarkness(amount)
                    sender.sendComponent("<green>已将 <yellow>${player.name}<green> 的阴暗值设为 <yellow>${"%.1f".format(helper.data.darkness)}")
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
                    sender.sendComponent("<red>玩家不在线")
                    return@execute
                }
                val helper = ChoTenDataHelper(player)
                helper.resetDarkness()
                sender.sendComponent("<green>已重置 <yellow>${player.name}<green> 的阴暗值，当前: <yellow>${"%.1f".format(helper.data.darkness)}")
            }
        }
    }

    @CommandBody
    val get = subCommand {
        player("target") {
            execute<CommandSender> { sender, context, _ ->
                val player = context.player("target").castSafely<Player>()
                if (player == null) {
                    sender.sendComponent("<red>玩家不在线")
                    return@execute
                }
                val helper = ChoTenDataHelper(player)
                sender.sendComponent("<yellow>${player.name}<green> 阴暗值: <yellow>${"%.1f".format(helper.data.darkness)}<green> / <yellow>${"%.1f".format(helper.data.effectiveMaxDarkness)}")
            }
        }
    }

    @CommandBody
    val full = subCommand {
        player("target") {
            execute<CommandSender> { sender, context, _ ->
                val player = context.player("target").castSafely<Player>()
                if (player == null) {
                    sender.sendComponent("<red>玩家不在线")
                    return@execute
                }
                val helper = ChoTenDataHelper(player)
                helper.fullDarkness()
                sender.sendComponent("<green>已填满 <yellow>${player.name}<green> 的阴暗值")
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
                        sender.sendComponent("<red>玩家不在线")
                        return@execute
                    }
                    val helper = ChoTenDataHelper(player)
                    val amount = context["amount"]?.toDoubleOrNull()
                    if (amount == null) { sender.sendComponent("<red>用法: /darkness maxadd <玩家> <数量>"); return@execute }
                    helper.addMaxDarkness(amount)
                    sender.sendComponent("<green>已为 <yellow>${player.name}<green> 增加 <yellow>$amount<green> 最大阴暗值，当前: <yellow>${"%.1f".format(helper.data.effectiveMaxDarkness)}")
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
                        sender.sendComponent("<red>玩家不在线")
                        return@execute
                    }
                    val helper = ChoTenDataHelper(player)
                    val amount = context["amount"]?.toDoubleOrNull()
                    if (amount == null) { sender.sendComponent("<red>用法: /darkness maxset <玩家> <数量>"); return@execute }
                    helper.setMaxDarkness(amount)
                    sender.sendComponent("<green>已将 <yellow>${player.name}<green> 的最大阴暗值设为 <yellow>${"%.1f".format(helper.data.effectiveMaxDarkness)}")
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
                    sender.sendComponent("<red>玩家不在线")
                    return@execute
                }
                val helper = ChoTenDataHelper(player)
                helper.resetMaxDarkness()
                sender.sendComponent("<green>已重置 <yellow>${player.name}<green> 的最大阴暗值，当前: <yellow>${"%.1f".format(helper.data.effectiveMaxDarkness)}")
            }
        }
    }

    @CommandBody
    val maxget = subCommand {
        player("target") {
            execute<CommandSender> { sender, context, _ ->
                val player = context.player("target").castSafely<Player>()
                if (player == null) {
                    sender.sendComponent("<red>玩家不在线")
                    return@execute
                }
                val helper = ChoTenDataHelper(player)
                sender.sendComponent("<yellow>${player.name}<green> 最大阴暗值: <yellow>${"%.1f".format(helper.data.effectiveMaxDarkness)}")
            }
        }
    }
}
