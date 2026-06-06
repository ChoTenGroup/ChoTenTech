package io.github.zzzyyylllty.chotentech.punish

import io.github.zzzyyylllty.chotentech.data.ChoTenDataHelper
import io.github.zzzyyylllty.chotentech.player.sendComponent
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.player

@CommandHeader(
    name = "chotentech-sanity",
    aliases = ["cttsanity", "sanity"],
    permission = "chotentech.command.sanity",
    permissionDefault = PermissionDefault.OP,
    newParser = false,
)
object SanityCommand {
    @CommandBody
    val main = mainCommand {
        player("target") {
            dynamic("action") {
                dynamic("amount") {
                    execute<CommandSender> { sender, context, _ ->
                        val player = context.player("target").castSafely<Player>()
                        if (player == null) {
                            sender.sendComponent("<red>玩家不在线")
                            return@execute
                        }
                        val helper = ChoTenDataHelper(player)
                        val action = context["action"]?.lowercase()
                        val amount = context["amount"]?.toDoubleOrNull()

                        when (action) {
                            "add" -> {
                                if (amount == null) { sender.sendComponent("<red>用法: /sanity <玩家> add <数量>"); return@execute }
                                helper.addSanity(amount)
                                sender.sendComponent("<green>已为 <yellow>${player.name}<green> 增加 <yellow>$amount<green> 精神值，当前: <yellow>${"%.1f".format(helper.data.sanity)}")
                            }
                            "set" -> {
                                if (amount == null) { sender.sendComponent("<red>用法: /sanity <玩家> set <数量>"); return@execute }
                                helper.setSanity(amount)
                                sender.sendComponent("<green>已将 <yellow>${player.name}<green> 的精神值设为 <yellow>${"%.1f".format(helper.data.sanity)}")
                            }
                            "reset" -> {
                                helper.resetSanity()
                                sender.sendComponent("<green>已重置 <yellow>${player.name}<green> 的精神值，当前: <yellow>${"%.1f".format(helper.data.sanity)}")
                            }
                            "get" -> {
                                sender.sendComponent("<yellow>${player.name}<green> 精神值: <yellow>${"%.1f".format(helper.data.sanity)}<green> / <yellow>${"%.1f".format(helper.data.effectiveMaxSanity)}")
                            }
                            "full" -> {
                                helper.fullSanity()
                                sender.sendComponent("<green>已回满 <yellow>${player.name}<green> 的精神值")
                            }
                            "maxadd" -> {
                                if (amount == null) { sender.sendComponent("<red>用法: /sanity <玩家> maxadd <数量>"); return@execute }
                                helper.addMaxSanity(amount)
                                sender.sendComponent("<green>已为 <yellow>${player.name}<green> 增加 <yellow>$amount<green> 最大精神值，当前: <yellow>${"%.1f".format(helper.data.effectiveMaxSanity)}")
                            }
                            "maxset" -> {
                                if (amount == null) { sender.sendComponent("<red>用法: /sanity <玩家> maxset <数量>"); return@execute }
                                helper.setMaxSanity(amount)
                                sender.sendComponent("<green>已将 <yellow>${player.name}<green> 的最大精神值设为 <yellow>${"%.1f".format(helper.data.effectiveMaxSanity)}")
                            }
                            "maxreset" -> {
                                helper.resetMaxSanity()
                                sender.sendComponent("<green>已重置 <yellow>${player.name}<green> 的最大精神值，当前: <yellow>${"%.1f".format(helper.data.effectiveMaxSanity)}")
                            }
                            "maxget" -> {
                                sender.sendComponent("<yellow>${player.name}<green> 最大精神值: <yellow>${"%.1f".format(helper.data.effectiveMaxSanity)}")
                            }
                            "regenadd" -> {
                                if (amount == null) { sender.sendComponent("<red>用法: /sanity <玩家> regenadd <数量>"); return@execute }
                                helper.addSanityRegeneration(amount)
                                sender.sendComponent("<green>已为 <yellow>${player.name}<green> 增加 <yellow>$amount<green> 精神恢复，当前: <yellow>${"%.1f".format(helper.data.sanityRegeneration)}")
                            }
                            "regenset" -> {
                                if (amount == null) { sender.sendComponent("<red>用法: /sanity <玩家> regenset <数量>"); return@execute }
                                helper.setSanityRegeneration(amount)
                                sender.sendComponent("<green>已将 <yellow>${player.name}<green> 的精神恢复设为 <yellow>${"%.1f".format(helper.data.sanityRegeneration)}")
                            }
                            "regenreset" -> {
                                helper.resetSanityRegeneration()
                                sender.sendComponent("<green>已重置 <yellow>${player.name}<green> 的精神恢复，当前: <yellow>${"%.1f".format(helper.data.sanityRegeneration)}")
                            }
                            "regenget" -> {
                                sender.sendComponent("<yellow>${player.name}<green> 精神恢复: <yellow>${"%.1f".format(helper.data.sanityRegeneration)}")
                            }
                            else -> {
                                sender.sendComponent("<red>未知操作: $action。可用: add, set, reset, get, full, maxadd, maxset, maxreset, maxget, regenadd, regenset, regenreset, regenget")
                            }
                        }
                    }
                    suggestion<CommandSender> { _, _ -> listOf("100") }
                }
                suggestion<CommandSender> { _, _ ->
                    listOf("add", "set", "reset", "get", "full", "maxadd", "maxset", "maxreset", "maxget", "regenadd", "regenset", "regenreset", "regenget")
                }
            }
        }
    }
}

@CommandHeader(
    name = "chotentech-stress",
    aliases = ["cttstress", "stress"],
    permission = "chotentech.command.stress",
    permissionDefault = PermissionDefault.OP,
    newParser = false,
)
object StressCommand {
    @CommandBody
    val main = mainCommand {
        player("target") {
            dynamic("action") {
                dynamic("amount") {
                    execute<CommandSender> { sender, context, _ ->
                        val player = context.player("target").castSafely<Player>()
                        if (player == null) {
                            sender.sendComponent("<red>玩家不在线")
                            return@execute
                        }
                        val helper = ChoTenDataHelper(player)
                        val action = context["action"]?.lowercase()
                        val amount = context["amount"]?.toDoubleOrNull()

                        when (action) {
                            "add" -> {
                                if (amount == null) { sender.sendComponent("<red>用法: /stress <玩家> add <数量>"); return@execute }
                                helper.addStress(amount)
                                sender.sendComponent("<green>已为 <yellow>${player.name}<green> 增加 <yellow>$amount<green> 压力，当前: <yellow>${"%.1f".format(helper.data.stress)}")
                            }
                            "set" -> {
                                if (amount == null) { sender.sendComponent("<red>用法: /stress <玩家> set <数量>"); return@execute }
                                helper.setStress(amount)
                                sender.sendComponent("<green>已将 <yellow>${player.name}<green> 的压力设为 <yellow>${"%.1f".format(helper.data.stress)}")
                            }
                            "reset" -> {
                                helper.resetStress()
                                sender.sendComponent("<green>已重置 <yellow>${player.name}<green> 的压力，当前: <yellow>${"%.1f".format(helper.data.stress)}")
                            }
                            "get" -> {
                                sender.sendComponent("<yellow>${player.name}<green> 压力: <yellow>${"%.1f".format(helper.data.stress)}<green> / 100.0")
                            }
                            else -> {
                                sender.sendComponent("<red>未知操作: $action。可用: add, set, reset, get")
                            }
                        }
                    }
                    suggestion<CommandSender> { _, _ -> listOf("100") }
                }
                suggestion<CommandSender> { _, _ -> listOf("add", "set", "reset", "get") }
            }
        }
    }
}

@CommandHeader(
    name = "chotentech-darkness",
    aliases = ["cttdarkness", "darkness"],
    permission = "chotentech.command.darkness",
    permissionDefault = PermissionDefault.OP,
    newParser = false,
)
object DarknessCommand {
    @CommandBody
    val main = mainCommand {
        player("target") {
            dynamic("action") {
                dynamic("amount") {
                    execute<CommandSender> { sender, context, _ ->
                        val player = context.player("target").castSafely<Player>()
                        if (player == null) {
                            sender.sendComponent("<red>玩家不在线")
                            return@execute
                        }
                        val helper = ChoTenDataHelper(player)
                        val action = context["action"]?.lowercase()
                        val amount = context["amount"]?.toDoubleOrNull()

                        when (action) {
                            "add" -> {
                                if (amount == null) { sender.sendComponent("<red>用法: /darkness <玩家> add <数量>"); return@execute }
                                helper.addDarkness(amount)
                                sender.sendComponent("<green>已为 <yellow>${player.name}<green> 增加 <yellow>$amount<green> 暗影值，当前: <yellow>${"%.1f".format(helper.data.darkness)}")
                            }
                            "set" -> {
                                if (amount == null) { sender.sendComponent("<red>用法: /darkness <玩家> set <数量>"); return@execute }
                                helper.setDarkness(amount)
                                sender.sendComponent("<green>已将 <yellow>${player.name}<green> 的暗影值设为 <yellow>${"%.1f".format(helper.data.darkness)}")
                            }
                            "reset" -> {
                                helper.resetDarkness()
                                sender.sendComponent("<green>已重置 <yellow>${player.name}<green> 的暗影值，当前: <yellow>${"%.1f".format(helper.data.darkness)}")
                            }
                            "get" -> {
                                sender.sendComponent("<yellow>${player.name}<green> 暗影值: <yellow>${"%.1f".format(helper.data.darkness)}<green> / <yellow>${"%.1f".format(helper.data.effectiveMaxDarkness)}")
                            }
                            "full" -> {
                                helper.fullDarkness()
                                sender.sendComponent("<green>已填满 <yellow>${player.name}<green> 的暗影值")
                            }
                            "maxadd" -> {
                                if (amount == null) { sender.sendComponent("<red>用法: /darkness <玩家> maxadd <数量>"); return@execute }
                                helper.addMaxDarkness(amount)
                                sender.sendComponent("<green>已为 <yellow>${player.name}<green> 增加 <yellow>$amount<green> 最大暗影值，当前: <yellow>${"%.1f".format(helper.data.effectiveMaxDarkness)}")
                            }
                            "maxset" -> {
                                if (amount == null) { sender.sendComponent("<red>用法: /darkness <玩家> maxset <数量>"); return@execute }
                                helper.setMaxDarkness(amount)
                                sender.sendComponent("<green>已将 <yellow>${player.name}<green> 的最大暗影值设为 <yellow>${"%.1f".format(helper.data.effectiveMaxDarkness)}")
                            }
                            "maxreset" -> {
                                helper.resetMaxDarkness()
                                sender.sendComponent("<green>已重置 <yellow>${player.name}<green> 的最大暗影值，当前: <yellow>${"%.1f".format(helper.data.effectiveMaxDarkness)}")
                            }
                            "maxget" -> {
                                sender.sendComponent("<yellow>${player.name}<green> 最大暗影值: <yellow>${"%.1f".format(helper.data.effectiveMaxDarkness)}")
                            }
                            else -> {
                                sender.sendComponent("<red>未知操作: $action。可用: add, set, reset, get, full, maxadd, maxset, maxreset, maxget")
                            }
                        }
                    }
                    suggestion<CommandSender> { _, _ -> listOf("100") }
                }
                suggestion<CommandSender> { _, _ ->
                    listOf("add", "set", "reset", "get", "full", "maxadd", "maxset", "maxreset", "maxget")
                }
            }
        }
    }
}
