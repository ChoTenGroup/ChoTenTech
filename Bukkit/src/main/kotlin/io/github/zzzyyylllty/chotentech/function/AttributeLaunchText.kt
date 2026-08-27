package io.github.zzzyyylllty.chotentech.function

import io.github.zzzyyylllty.chotentech.ChoTenTech.console
import io.github.zzzyyylllty.chotentech.ChoTenTech.consoleSender
import io.github.zzzyyylllty.chotentech.logger.sendStringAsComponent
import io.github.zzzyyylllty.chotentech.util.VersionHelper
import org.bukkit.command.CommandSender
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.pluginVersion
import taboolib.common.platform.function.runningPlatform
import taboolib.module.lang.asLangText
import taboolib.module.nms.MinecraftVersion.versionId
import taboolib.platform.util.asLangText
import kotlin.collections.joinToString

@Awake(LifeCycle.ENABLE)
fun launchText() {

    val premiumDisplayName = if (VersionHelper().isLi2CO3Premium) {
        "<gradient:#EFDF8F:#DFBD7C>" + console.asLangText("PremiumVersion")
    } else {
        "<gradient:#8FD4A6:#84D1CB>" + console.asLangText("FreeVersion")
    }

    val specialThanks =
        listOf("MAORI", "NK_XingChen", "Jesuzi", "Blue_ruins(BlueIce)", "TheAchu", "CedricHunsen")

    consoleSender.sendStringAsComponent(" ")
    consoleSender.sendStringAsComponent("""<gradient:#F2F5FA:#E08A98>  _______      ______         ___ _________ """)
    consoleSender.sendStringAsComponent("""<gradient:#F2F5FA:#E08A98> / ___/ /  ___/_  __/__ ___  / _ /_  __/ _ )""")
    consoleSender.sendStringAsComponent("""<gradient:#F2F5FA:#E08A98>/ /__/ _ \/ _ \/ / / -_) _ \/ __ |/ / / _  |""")
    consoleSender.sendStringAsComponent("""<gradient:#F2F5FA:#E08A98>\___/_//_/\___/_/  \__/_//_/_/ |_/_/ /____/ """)
    consoleSender.sendStringAsComponent(" ")
    consoleSender.sendStringWithPrefix("<#5FA8A3>",consoleSender.asLangText("WelcomeSeries"))
    consoleSender.sendStringWithPrefix("<#5FA8A3>",consoleSender.asLangText("DesignBy", "<#e8bad9>AkaCandyKAngel</#e8bad9>"))
    consoleSender.sendStringWithPrefix("<#5FA8A3>",consoleSender.asLangText("SpecialThanks","<#84D1CB>[<#5FA8A3>${specialThanks.joinToString("<#6E7A8C>, </#6E7A8C>")}<#84D1CB>]"))
    consoleSender.sendStringWithPrefix("<#5FA8A3>",consoleSender.asLangText("PoweredBy", "<#bad9e8>TabooLib <#DFBD7C>6.2"))
    consoleSender.sendStringAsComponent(" ")
    consoleSender.sendStringWithPrefix("<#c6dbec>", console.asLangText("Welcome1"))
    consoleSender.sendStringWithPrefix("<#c6dbec>", console.asLangText("Welcome2", premiumDisplayName, "${pluginVersion}<reset>", "${runningPlatform.name} - ${versionId}"))
    consoleSender.sendStringAsComponent(" ")
    consoleSender.sendStringWithPrefix("<#bad4e8>", console.asLangText("Welcome3", "https://github.com/zzzyyylllty"))
    consoleSender.sendStringWithPrefix("<#bad4e8>", console.asLangText("Welcome4", "https://github.com/zzzyyylllty/Lithium-Carbonate"))
    consoleSender.sendStringWithPrefix("<#bad4e8>", console.asLangText("Welcome5", "https://chotengroup.gitbook.io/attribute"))
    consoleSender.sendStringAsComponent(" ")
    if (VersionHelper().isLi2CO3Premium) consoleSender.sendStringWithPrefix("<gradient:#E08A98:#EFDF8F:#8FD4A6:#84D1CB:#CDAEEF>", console.asLangText("PremiumVersionWelcome", premiumDisplayName))
    consoleSender.sendStringAsComponent(" ")

}

private fun CommandSender.sendStringWithPrefix(prefix: String, message: String) {
    this.sendStringAsComponent(prefix + message)
}