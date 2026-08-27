package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.data.AttributeModifier
import io.github.zzzyyylllty.chotentech.data.ModifierSource
import io.github.zzzyyylllty.chotentech.data.ModifierType
import io.github.zzzyyylllty.chotentech.manager.AttributeManager
import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.data.HUDManager
import io.github.zzzyyylllty.chotentech.data.ResultLevel
import io.github.zzzyyylllty.chotentech.data.conflictConfigs
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.function.ReputationManager
import io.github.zzzyyylllty.chotentech.player.sendComponent
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.entity.Player
import taboolib.common.platform.command.*
import taboolib.common.platform.function.submitAsync
import java.util.UUID

@CommandHeader(
    name = "chotentech-data",
    aliases = ["cttdata"],
    permission = "chotentech.command.data",
    description = "Data Command for ChoTenTech.",
    permissionMessage = "",
    permissionDefault = PermissionDefault.OP,
    newParser = false,
)
object ChoTenDataCommand {

    @CommandBody
    val main = mainCommand {
        createModernHelper()
    }

    @CommandBody
    val help = subCommand {
        createModernHelper()
    }

    @CommandBody
    val editor  = subCommand {
        execute<Player> { sender, context, argument ->
            val target = !(sender.getData()?.editorMode ?: false)
            sender.getData()?.editorMode = target
            sender.sendComponent("<#84D1CB>编辑器模式已设为 $target。")
        }
    }

    @CommandBody
    val hud = subCommand {
        player("user") {
            dynamic("key") {
                dynamic("content") {
                    decimal("time", listOf("10.0")) {
                        execute<CommandSender> { sender, context, argument ->
                            submitAsync {
                                val player = context.player("user").castSafely<Player>()!!

                                HUDManager(player!!).addHUD(context["key"], context["content"], context["time"].toDouble())

                                if (sender !is ConsoleCommandSender) sender.sendComponent("<#E08A98>${context["key"]+","+ context["content"]+","+ context["time"].toDouble()}")

                            }
                        }
                    }
                    suggestion<CommandSender> { sender, context ->
                        listOf("content")
                    }
                }
                suggestion<CommandSender> { sender, context ->
                    listOf("key")
                }
            }
        }
    }

    @CommandBody
    val sanity = subCommand {
        player("user") {
            dynamic("type") {
                dynamic("operation") {
                    decimal("value") {
                        execute<CommandSender> { sender, context, argument ->
                            submitAsync {
                                val player = context.player("user").castSafely<Player>()!!
                                val data = playerDataMap[player.uniqueId.toString()] ?: run {
                                    sender.sendComponent("玩家数据为空")
                                    return@submitAsync
                                }

                                val varValue = when (context["type"]) {
                                    "SANITY" -> data.sanity
                                    "MAXSANITY" -> data.effectiveMaxSanity
                                    "SANITYREGEN" -> data.sanityRegeneration
                                    "STRESS" -> data.stress
                                    "DARKNESS" -> data.darkness
                                    "MAXDARKNESS" -> data.effectiveMaxDarkness
                                    else -> {
                                        sender.sendComponent("未知的变量类型")
                                        return@submitAsync
                                    }
                                }

                                val finalValue = when (context["operation"]) {
                                    "+" -> varValue + context["value"].toDouble()
                                    "-" -> varValue - context["value"].toDouble()
                                    "=" -> context["value"].toDouble()
                                    else -> {
                                        sender.sendComponent("未知的运算类型")
                                        return@submitAsync
                                    }
                                }

                                when (context["type"]) {
                                    "SANITY" -> data.sanity = minOf(maxOf(finalValue, 0.0), data.effectiveMaxSanity)
                                    "MAXSANITY" -> data.maxSanity = maxOf(finalValue, 0.0)
                                    "SANITYREGEN" -> {
                                        val modUuid = UUID.fromString("00000000-0000-0000-0000-000000000001")
                                        AttributeManager.removeModifierByUuid(player.uniqueId, modUuid)
                                        val v = maxOf(finalValue, 0.0)
                                        if (v > 0.0) {
                                            AttributeManager.addModifier(player.uniqueId, AttributeModifier(
                                                attributeId = "sanity_regeneration",
                                                value = v,
                                                type = ModifierType.ADD,
                                                source = ModifierSource.OTHER,
                                                uuid = modUuid
                                            ))
                                        }
                                    }
                                    "STRESS" -> data.stress = minOf(maxOf(finalValue, 0.0), 100.0)
                                    "DARKNESS" -> data.darkness = minOf(maxOf(finalValue, 0.0), data.effectiveMaxDarkness)
                                    "MAXDARKNESS" -> data.maxDarkness = maxOf(finalValue, 0.0)
                                    else -> {

                                    }
                                }

                                if (sender !is ConsoleCommandSender) sender.sendComponent("<#E08A98>${context["type"]} = <#EFDF8F>$finalValue")

                            }
                        }
                    }
                    suggestion<CommandSender> { sender, context ->
                        listOf("+","-","=")
                    }
                }
                suggestion<CommandSender> { sender, context ->
                    listOf("STRESS","SANITY","MAXSANITY","DARKNESS","MAXDARKNESS")
                }
            }
        }
    }

    @CommandBody
    val warehouse = subCommand {
        player("user") {
            dynamic("action") {
                int("value", listOf("36", "54", "72")) {
                    execute<CommandSender> { sender, context, _ ->
                        val player = context.player("user").castSafely<Player>()!!
                        val data = playerDataMap[player.uniqueId.toString()] ?: run {
                            sender.sendComponent("<#E08A98>玩家数据为空")
                            return@execute
                        }
                        when (context["action"]) {
                            "set" -> {
                                val slots = context["value"].toIntOrNull()
                                if (slots == null || slots < 1) {
                                    sender.sendComponent("<#E08A98>格数必须为大于 0 的整数")
                                    return@execute
                                }
                                data.warehouseBaseSlots = slots
                                sender.sendComponent("<#8FD4A6>已将 ${player.name} 的仓库基础格数设为 <#EFDF8F>$slots")
                            }
                            "reset" -> {
                                data.warehouseBaseSlots = null
                                sender.sendComponent("<#8FD4A6>已重置 ${player.name} 的仓库基础格数为默认值")
                            }
                            else -> sender.sendComponent("<#E08A98>未知操作，可用: set <数量>, reset")
                        }
                    }
                }
                suggestion<CommandSender> { _, _ ->
                    listOf("set", "reset")
                }
            }
        }
    }

    @CommandBody
    val reputation = subCommand {
        player("user") {
            dynamic("faction") {
                execute<CommandSender> { sender, context, _ ->
                    val player = context.player("user").castSafely<Player>()!!
                    val faction = context["faction"].uppercase()
                    val value = ReputationManager.getReputation(player, faction)
                    sender.sendComponent("<#DFBD7C>$faction <#A9B4C3>- 信誉: <#EFDF8F>$value")
                }
                dynamic("operation") {
                    int("value", listOf("100", "500", "1000", "-500")) {
                        execute<CommandSender> { sender, context, _ ->
                            val player = context.player("user").castSafely<Player>()!!
                            val faction = context["faction"].uppercase()
                            val value = context["value"].toLongOrNull()
                            if (value == null) {
                                sender.sendComponent("<#E08A98>数值必须为整数")
                                return@execute
                            }
                            when (context["operation"]) {
                                "+" -> ReputationManager.addReputation(player, faction, value)
                                "-" -> ReputationManager.addReputation(player, faction, -value)
                                "=" -> ReputationManager.setReputation(player, faction, value)
                                else -> {
                                    sender.sendComponent("<#E08A98>未知操作，可用: + - =")
                                    return@execute
                                }
                            }
                            val current = ReputationManager.getReputation(player, faction)
                            sender.sendComponent("<#8FD4A6>已将 ${player.name} 的 $faction 信誉设为 <#EFDF8F>$current")
                        }
                    }
                    suggestion<CommandSender> { _, _ ->
                        listOf("+", "-", "=")
                    }
                }
            }
            suggestion<CommandSender> { _, _ ->
                listOf("MEG", "BNTG", "UEC")
            }
        }
    }

    @CommandBody
    val affection = subCommand {
        player("user") {
            dynamic("npc") {
                execute<CommandSender> { sender, context, _ ->
                    val player = context.player("user").castSafely<Player>()!!
                    val npc = context["npc"]
                    val value = ReputationManager.getAffection(player, npc)
                    sender.sendComponent("<#DFBD7C>$npc <#A9B4C3>- 信任度: <#EFDF8F>$value")
                }
                dynamic("operation") {
                    int("value", listOf("100", "500", "1000", "-500")) {
                        execute<CommandSender> { sender, context, _ ->
                            val player = context.player("user").castSafely<Player>()!!
                            val npc = context["npc"]
                            val value = context["value"].toLongOrNull()
                            if (value == null) {
                                sender.sendComponent("<#E08A98>数值必须为整数")
                                return@execute
                            }
                            when (context["operation"]) {
                                "+" -> ReputationManager.addAffection(player, npc, value)
                                "-" -> ReputationManager.addAffection(player, npc, -value)
                                "=" -> ReputationManager.setAffection(player, npc, value)
                                else -> {
                                    sender.sendComponent("<#E08A98>未知操作，可用: + - =")
                                    return@execute
                                }
                            }
                            val current = ReputationManager.getAffection(player, npc)
                            sender.sendComponent("<#8FD4A6>已将 ${player.name} 对 $npc 的信任度设为 <#EFDF8F>$current")
                        }
                    }
                    suggestion<CommandSender> { _, _ ->
                        listOf("+", "-", "=")
                    }
                }
            }
            suggestion<CommandSender> { _, _ ->
                listOf("NPC_A", "NPC_B")
            }
        }
    }

    @CommandBody
    val conflict = subCommand {
        player("user") {
            dynamic("conflictId") {
                dynamic("level") {
                    execute<CommandSender> { sender, context, _ ->
                        val player = context.player("user").castSafely<Player>()!!
                        val conflictId = context["conflictId"]
                        val levelName = context["level"].uppercase()
                        val level = runCatching { ResultLevel.valueOf(levelName) }.getOrNull()
                        if (level == null) {
                            val names = ResultLevel.entries.joinToString(" ") { "${it.displayName}(${it.name})" }
                            sender.sendComponent("<#E08A98>未知结果等级，可用: $names")
                            return@execute
                        }
                        val config = conflictConfigs[conflictId]
                        if (config == null) {
                            sender.sendComponent("<#E08A98>未找到冲突事件: $conflictId")
                            return@execute
                        }
                        val changes = ReputationManager.applyConflictReputation(player, conflictId, level)
                        if (changes.isEmpty()) {
                            sender.sendComponent("<#E08A98>该结果等级未配置声望变化")
                            return@execute
                        }
                        val report = ReputationManager.formatReputationReport(player, config.name, level, changes)
                        for (line in report.lines()) {
                            if (line.isNotBlank()) sender.sendComponent(line)
                        }
                    }
                    suggestion<CommandSender> { _, _ ->
                        listOf("FULL_SUCCESS", "SUCCESS", "NEUTRAL", "FAIL", "FULL_FAIL")
                    }
                }
            }
            suggestion<CommandSender> { _, _ ->
                conflictConfigs.keys.toList()
            }
        }
    }

    @CommandBody
    val hunger = subCommand {
        player("user") {
            dynamic("type") {
                dynamic("operation") {
                    decimal("value", listOf("100.0", "50.0", "10.0", "1.0", "-10.0")) {
                        execute<CommandSender> { sender, context, _ ->
                            submitAsync {
                                val player = context.player("user").castSafely<Player>()!!
                                val data = playerDataMap[player.uniqueId.toString()] ?: run {
                                    sender.sendComponent("<#E08A98>玩家数据为空")
                                    return@submitAsync
                                }

                                val varValue = when (context["type"]) {
                                    "HUNGER" -> data.customHunger
                                    "SATURATION" -> data.customSaturation
                                    "CONSUMPTION" -> data.consumption
                                    else -> {
                                        sender.sendComponent("<#E08A98>未知的变量类型，可用: HUNGER SATURATION CONSUMPTION")
                                        return@submitAsync
                                    }
                                }

                                val finalValue = when (context["operation"]) {
                                    "+" -> varValue + context["value"].toDouble()
                                    "-" -> varValue - context["value"].toDouble()
                                    "=" -> context["value"].toDouble()
                                    else -> {
                                        sender.sendComponent("<#E08A98>未知的运算类型，可用: + - =")
                                        return@submitAsync
                                    }
                                }

                                when (context["type"]) {
                                    "HUNGER" -> data.customHunger = maxOf(finalValue, 0.0)
                                    "SATURATION" -> data.customSaturation = maxOf(finalValue, 0.0)
                                    "CONSUMPTION" -> data.consumption = maxOf(finalValue, 0.0)
                                }

                                if (sender !is ConsoleCommandSender) sender.sendComponent("<#E08A98>${context["type"]} = <#EFDF8F>$finalValue")
                            }
                        }
                    }
                    suggestion<CommandSender> { _, _ ->
                        listOf("+", "-", "=")
                    }
                }
                suggestion<CommandSender> { _, _ ->
                    listOf("HUNGER", "SATURATION", "CONSUMPTION")
                }
            }
        }
    }

    @CommandBody
    val nutrition = subCommand {
        player("user") {
            dynamic("type") {
                dynamic("operation") {
                    decimal("value", listOf("100.0", "50.0", "10.0", "1.0", "-10.0")) {
                        execute<CommandSender> { sender, context, _ ->
                            submitAsync {
                                val player = context.player("user").castSafely<Player>()!!
                                val data = playerDataMap[player.uniqueId.toString()] ?: run {
                                    sender.sendComponent("<#E08A98>玩家数据为空")
                                    return@submitAsync
                                }

                                val varValue = when (context["type"]) {
                                    "GRAIN" -> data.grain
                                    "VITAMIN" -> data.vitamin
                                    "PROTEIN" -> data.protein
                                    "FAT" -> data.fat
                                    else -> {
                                        sender.sendComponent("<#E08A98>未知的变量类型，可用: GRAIN VITAMIN PROTEIN FAT")
                                        return@submitAsync
                                    }
                                }

                                val finalValue = when (context["operation"]) {
                                    "+" -> varValue + context["value"].toDouble()
                                    "-" -> varValue - context["value"].toDouble()
                                    "=" -> context["value"].toDouble()
                                    else -> {
                                        sender.sendComponent("<#E08A98>未知的运算类型，可用: + - =")
                                        return@submitAsync
                                    }
                                }

                                when (context["type"]) {
                                    "GRAIN" -> data.grain = maxOf(finalValue, 0.0)
                                    "VITAMIN" -> data.vitamin = maxOf(finalValue, 0.0)
                                    "PROTEIN" -> data.protein = maxOf(finalValue, 0.0)
                                    "FAT" -> data.fat = maxOf(finalValue, 0.0)
                                }

                                if (sender !is ConsoleCommandSender) sender.sendComponent("<#E08A98>${context["type"]} = <#EFDF8F>$finalValue")
                            }
                        }
                    }
                    suggestion<CommandSender> { _, _ ->
                        listOf("+", "-", "=")
                    }
                }
                suggestion<CommandSender> { _, _ ->
                    listOf("GRAIN", "VITAMIN", "PROTEIN", "FAT")
                }
            }
        }
    }

    @CommandBody
    val injury = subCommand {
        player("user") {
            dynamic("type") {
                dynamic("operation") {
                    decimal("value", listOf("100.0", "50.0", "10.0", "1.0", "-10.0")) {
                        execute<CommandSender> { sender, context, _ ->
                            submitAsync {
                                val player = context.player("user").castSafely<Player>()!!
                                val data = playerDataMap[player.uniqueId.toString()] ?: run {
                                    sender.sendComponent("<#E08A98>玩家数据为空")
                                    return@submitAsync
                                }

                                val varValue = when (context["type"]) {
                                    "EXTERNAL" -> data.externalInjury
                                    "INTERNAL" -> data.internalInjury
                                    else -> {
                                        sender.sendComponent("<#E08A98>未知的变量类型，可用: EXTERNAL INTERNAL")
                                        return@submitAsync
                                    }
                                }

                                val finalValue = when (context["operation"]) {
                                    "+" -> varValue + context["value"].toDouble()
                                    "-" -> varValue - context["value"].toDouble()
                                    "=" -> context["value"].toDouble()
                                    else -> {
                                        sender.sendComponent("<#E08A98>未知的运算类型，可用: + - =")
                                        return@submitAsync
                                    }
                                }

                                when (context["type"]) {
                                    "EXTERNAL" -> data.externalInjury = maxOf(finalValue, 0.0)
                                    "INTERNAL" -> data.internalInjury = maxOf(finalValue, 0.0)
                                }

                                if (sender !is ConsoleCommandSender) sender.sendComponent("<#E08A98>${context["type"]} = <#EFDF8F>$finalValue")
                            }
                        }
                    }
                    suggestion<CommandSender> { _, _ ->
                        listOf("+", "-", "=")
                    }
                }
                suggestion<CommandSender> { _, _ ->
                    listOf("EXTERNAL", "INTERNAL")
                }
            }
        }
    }

    @CommandBody
    val stamina = subCommand {
        player("user") {
            dynamic("type") {
                dynamic("operation") {
                    decimal("value", listOf("100.0", "50.0", "10.0", "1.0", "-10.0")) {
                        execute<CommandSender> { sender, context, _ ->
                            submitAsync {
                                val player = context.player("user").castSafely<Player>()!!
                                val data = playerDataMap[player.uniqueId.toString()] ?: run {
                                    sender.sendComponent("<#E08A98>玩家数据为空")
                                    return@submitAsync
                                }

                                val varValue = when (context["type"]) {
                                    "STAMINA" -> data.stamina
                                    "HANDSTAMINA" -> data.handStamina
                                    else -> {
                                        sender.sendComponent("<#E08A98>未知的变量类型，可用: STAMINA HANDSTAMINA")
                                        return@submitAsync
                                    }
                                }

                                val finalValue = when (context["operation"]) {
                                    "+" -> varValue + context["value"].toDouble()
                                    "-" -> varValue - context["value"].toDouble()
                                    "=" -> context["value"].toDouble()
                                    else -> {
                                        sender.sendComponent("<#E08A98>未知的运算类型，可用: + - =")
                                        return@submitAsync
                                    }
                                }

                                when (context["type"]) {
                                    "STAMINA" -> data.stamina = maxOf(finalValue, 0.0)
                                    "HANDSTAMINA" -> data.handStamina = maxOf(finalValue, 0.0)
                                }

                                if (sender !is ConsoleCommandSender) sender.sendComponent("<#E08A98>${context["type"]} = <#EFDF8F>$finalValue")
                            }
                        }
                    }
                    suggestion<CommandSender> { _, _ ->
                        listOf("+", "-", "=")
                    }
                }
                suggestion<CommandSender> { _, _ ->
                    listOf("STAMINA", "HANDSTAMINA")
                }
            }
        }
    }
}

