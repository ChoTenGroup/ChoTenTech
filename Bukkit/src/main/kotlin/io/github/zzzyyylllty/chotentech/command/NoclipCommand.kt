package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.ChoTenTech.levelsList
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.function.noclip.RandomNoclipSelector
import io.github.zzzyyylllty.sertraline.logger.sendStringAsComponent
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand

@CommandHeader(
    name = "noclips",
    aliases = ["noclip"],
    permission = "chotentech.command.checknoclips",
    description = "checknoclips Command for ChoTenTech.",
    permissionMessage = "",
    permissionDefault = PermissionDefault.OP,
    newParser = false,
)
object NoclipsCommand {

    @CommandBody
    val main = mainCommand {
        execute<CommandSender> { sender, _, _ ->
            val player = sender as? Player ?: return@execute
            val data = player.getData() ?: return@execute
            val activeLevel = data.activeLevel
            val str = StringBuilder()
            str.appendLine("<#F2F5FA><st>                                                                      <reset>")
            str.append("<#8FD4A6>${player.name} - 层级 ${activeLevel?.name ?: "???"}")
            activeLevel?.randomNoclip?.let { mode ->
                str.append(" <#6E7A8C>[随机: ${mode.name}]")
            }
            str.appendLine()
            str.appendLine("<#F2F5FA><st>                                                                      <reset>")
            val noclips = activeLevel?.noclips
            if (noclips.isNullOrEmpty()) {
                str.appendLine(" <#E08A98>未找到任何可用切出点。")
                str.appendLine()
                sender.sendStringAsComponent(str.toString())
                return@execute
            }

            val enabledNames = activeLevel?.let { RandomNoclipSelector.getEnabledNoclips(player.uniqueId.toString(), it) } ?: emptySet()
            val exploredLevel = data.exploreredNoclips[data.activeLevelStr]

            var num = 1
            for (noclip in noclips) {
                val randomDisabled = noclip.name != null && noclip.name !in enabledNames
                val autoTag = if (noclip.autoNoclip) " <#A9B4C3>[自动]" else ""
                val condTag = buildConditionTag(noclip)

                if (player.gameMode.isInvulnerable) {
                    val disabledMark = if (randomDisabled) " <#e0a1a1>[随机禁用]" else ""
                    str.appendLine(" <#6E7A8C>| <#76a76d>NOCLIP$num</#76a76d> <#dad8d8>${noclip.name}</#dad8d8> <#cfae6e>→ ${targetLevelDisplay(noclip.targetLevel)}</#cfae6e>$autoTag$disabledMark")
                } else {
                    if (randomDisabled) {
                        str.appendLine(" <#6E7A8C>| <#858585>NOCLIP$num</#858585> <#747474>${noclip.name}</#747474> <#6E7A8C>[随机禁用]<reset>")
                    } else if (exploredLevel?.contains(noclip.name) == true) {
                        str.appendLine(" <#6E7A8C>| <#76a76d>NOCLIP$num</#76a76d> <#dad8d8>${noclip.name}</#dad8d8> <#5FA8A3>→ ${targetLevelDisplay(noclip.targetLevel)}</#5FA8A3>$autoTag")
                    } else {
                        str.appendLine(" <#6E7A8C>| <#76a76d>NOCLIP$num</#76a76d> <#dad8d8>${noclip.name}</#dad8d8> <#6E7A8C>[没有数据]<reset>$autoTag")
                    }
                }
                if (condTag.isNotEmpty()) {
                    str.appendLine("   <#6E7A8C>$condTag")
                }
                num++
            }

            str.appendLine("<#F2F5FA><st>                                                                      ")
            sender.sendStringAsComponent(str.toString())
        }
    }

    private fun targetLevelDisplay(targetLevel: String?): String {
        if (targetLevel == null) return "未知"
        if (targetLevel.contains(";")) return "多个可能层级"
        return levelsList[targetLevel]?.name ?: "未知"
    }

    private fun buildConditionTag(noclip: io.github.zzzyyylllty.chotentech.data.NoclipPoint): String {
        val cond = noclip.conditions ?: return ""
        val parts = mutableListOf<String>()
        if (cond.item != null) parts.add("物品")
        if (cond.car != null) parts.add("费用")
        if (cond.coop != null) parts.add("协作")
        if (cond.signal != null) parts.add("信号")
        if (cond.dropbag != null) parts.add("限制")
        if (cond.condition != null) parts.add("条件")
        if (parts.isEmpty()) return ""
        return "条件: " + parts.joinToString(" ")
    }
}
