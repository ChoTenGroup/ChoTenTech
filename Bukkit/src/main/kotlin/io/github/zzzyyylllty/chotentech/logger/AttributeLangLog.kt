package io.github.zzzyyylllty.chotentech.logger

import io.github.zzzyyylllty.chotentech.ChoTenTech
import io.github.zzzyyylllty.chotentech.ChoTenTech.console
import io.github.zzzyyylllty.chotentech.ChoTenTech.consoleSender
import io.github.zzzyyylllty.chotentech.ChoTenTech.console
import io.github.zzzyyylllty.chotentech.ChoTenTech.consoleSender
import io.github.zzzyyylllty.chotentech.logger.infoS
import io.github.zzzyyylllty.chotentech.logger.severeS
import io.github.zzzyyylllty.chotentech.logger.warningS
import io.github.zzzyyylllty.chotentech.util.minimessage.legacyToMiniMessage
import io.github.zzzyyylllty.chotentech.util.minimessage.mmUtil
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.submit
import taboolib.module.lang.asLangText

val prefix = "[<gradient:#bae8e8:#c6d9ec:#cfc9d9>attribute</gradient>]"


fun infoL(node: String,vararg args: Any) {
    infoS(console.asLangText(node,*args))
}
fun infoLSync(node: String,vararg args: Any) {
    infoSSync(console.asLangText(node,*args))
}
fun severeL(node: String,vararg args: Any) {
    severeS(console.asLangText(node,*args))
}
fun warningL(node: String,vararg args: Any) {
    warningS(console.asLangText(node,*args))
}


fun fineS(message: String) {
    submit(async = true) {
        consoleSender.sendStringAsComponent("<#A9B4C3>$prefix [<#bae8d9>FINE</#bae8d9>]</#A9B4C3> <reset>$message")
    }
}

fun debugS(message: String) {
    submit(async = true) {
        consoleSender.sendStringAsComponent("<#A9B4C3>$prefix [<#d9c9b8>DEBUG</#d9c9b8>]</#A9B4C3> <#c9c9c9>$message")
    }
}

fun infoS(message: String) {
    submit(async = true) {
        consoleSender.sendStringAsComponent("<#A9B4C3>$prefix [<#bad9e8>INFO</#bad9e8>]</#A9B4C3> <reset>$message")
    }
}

fun warningS(message: String) {
    submit(async = true) {
        consoleSender.sendStringAsComponent("<#A9B4C3>$prefix [<#e8e3ba>WARN</#e8e3ba>]</#A9B4C3> <#e6e6cc>$message")
    }
}

fun severeS(message: String) {
    submit(async = true) {
        consoleSender.sendStringAsComponent("<#A9B4C3>$prefix [<#cf946e>ERROR</#cf946e>]</#A9B4C3> <#eed5cc>$message")
    }
}
fun debugSSync(message: String) {
    submit(async = true) {
        consoleSender.sendStringAsComponent("<#A9B4C3>$prefix [<#d9c9b8>DEBUG</#d9c9b8>]</#A9B4C3> <#c9c9c9>$message")
    }
}

fun infoSSync(message: String) {
    submit(async = true) {
        consoleSender.sendStringAsComponent("<#A9B4C3>$prefix [<#bad9e8>INFO</#bad9e8>]</#A9B4C3> <reset>$message")
    }
}

fun warningSSync(message: String) {
    submit(async = true) {
        consoleSender.sendStringAsComponent("<#A9B4C3>$prefix [<#e8e3ba>WARN</#e8e3ba>]</#A9B4C3> <#e6e6cc>$message")
    }
}

fun severeSSync(message: String) {
    submit(async = true) {
        consoleSender.sendStringAsComponent("<#A9B4C3>$prefix [<#cf946e>ERROR</#cf946e>]</#A9B4C3> <#eed5cc>$message")
    }
}

fun CommandSender.sendStringAsComponent(message: String) {
    val sender = this
        (sender as Audience).sendMessage(mmUtil.deserialize(message.legacyToMiniMessage()))
}

fun CommandSender?.fineS(message: String, bothSendConsole: Boolean = false) {
    (this ?: consoleSender).sendStringAsComponent("<#A9B4C3>$prefix [<#bae8d9>FINE</#bae8d9>]</#A9B4C3> <reset>$message")
    if (this != null && bothSendConsole) consoleSender.sendStringAsComponent("<#A9B4C3>$prefix [<#bae8d9>FINES</#bae8d9>]</#A9B4C3> <reset>$message")
}

fun CommandSender?.debugS(message: String, bothSendConsole: Boolean = false) {
    (this ?: consoleSender).sendStringAsComponent("<#A9B4C3>$prefix [<#d9c9b8>DEBUG</#d9c9b8>]</#A9B4C3> <#c9c9c9>$message")
    if (this != null && bothSendConsole) consoleSender.sendStringAsComponent("<#A9B4C3>$prefix [<#d9c9b8>DEBUG</#d9c9b8>]</#A9B4C3> <#c9c9c9>$message")
}

fun CommandSender?.infoS(message: String, bothSendConsole: Boolean = false) {
    (this ?: consoleSender).sendStringAsComponent("<#A9B4C3>$prefix [<#bad9e8>INFO</#bad9e8>]</#A9B4C3> <reset>$message")
    if (this != null && bothSendConsole) consoleSender.sendStringAsComponent("<#A9B4C3>$prefix [<#bad9e8>INFOS</#bad9e8>]</#A9B4C3> <reset>$message")
}

fun CommandSender?.warningS(message: String, bothSendConsole: Boolean = false) {
    (this ?: consoleSender).sendStringAsComponent("<#A9B4C3>$prefix [<#e8e3ba>WARN</#e8e3ba>]</#A9B4C3> <#e6e6cc>$message")
    if (this != null && bothSendConsole) consoleSender.sendStringAsComponent("<#A9B4C3>$prefix [<#e8e3ba>WARNI</#e8e3ba>]</#A9B4C3> <#e6e6cc>$message")
}

fun CommandSender?.severeS(message: String, bothSendConsole: Boolean = false) {
    (this ?: consoleSender).sendStringAsComponent("<#A9B4C3>$prefix [<#cf946e>ERROR</#cf946e>]</#A9B4C3> <#eed5cc>$message")
    if (this != null && bothSendConsole) consoleSender.sendStringAsComponent("<#A9B4C3>$prefix [<#cf946e>ERROR</#cf946e>]</#A9B4C3> <#eed5cc>$message")
}
