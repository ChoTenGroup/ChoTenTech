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
            str.appendLine("<white><st>                                                                      <reset>")
            str.append("<green>${player.name} - 层级 ${activeLevel?.name ?: "???"}")
            activeLevel?.randomNoclip?.let { mode ->
                str.append(" <dark_gray>[随机: ${mode.name}]")
            }
            str.appendLine()
            str.appendLine("<white><st>                                                                      <reset>")
            val noclips = activeLevel?.noclips
            if (noclips.isNullOrEmpty()) {
                str.appendLine(" <red>未找到任何可用切出点。")
                str.appendLine()
                sender.sendStringAsComponent(str.toString())
                return@execute
            }

            val enabledNames = activeLevel?.let { RandomNoclipSelector.getEnabledNoclips(player.uniqueId.toString(), it) } ?: emptySet()
            val exploredLevel = data.exploreredNoclips[data.activeLevelStr]

            var num = 1
            for (noclip in noclips) {
                val randomDisabled = noclip.name != null && noclip.name !in enabledNames
                val autoTag = if (noclip.autoNoclip) " <gray>[自动]" else ""
                val condTag = buildConditionTag(noclip)

                if (player.gameMode.isInvulnerable) {
                    val disabledMark = if (randomDisabled) " <#FF4444>[随机禁用]" else ""
                    str.appendLine(" <dark_gray>| <#47A036>NOCLIP$num</#47A036> <#CECACA>${noclip.name}</#CECACA> <#FFAA00>→ ${targetLevelDisplay(noclip.targetLevel)}</#FFAA00>$autoTag$disabledMark")
                } else {
                    if (randomDisabled) {
                        str.appendLine(" <dark_gray>| <#666666>NOCLIP$num</#666666> <#555555>${noclip.name}</#555555> <dark_gray>[随机禁用]<reset>")
                    } else if (exploredLevel?.contains(noclip.name) == true) {
                        str.appendLine(" <dark_gray>| <#47A036>NOCLIP$num</#47A036> <#CECACA>${noclip.name}</#CECACA> <dark_aqua>→ ${targetLevelDisplay(noclip.targetLevel)}</dark_aqua>$autoTag")
                    } else {
                        str.appendLine(" <dark_gray>| <#47A036>NOCLIP$num</#47A036> <#CECACA>${noclip.name}</#CECACA> <dark_gray>[没有数据]<reset>$autoTag")
                    }
                }
                if (condTag.isNotEmpty()) {
                    str.appendLine("   <dark_gray>$condTag")
                }
                num++
            }

            str.appendLine("<white><st>                                                                      ")
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
