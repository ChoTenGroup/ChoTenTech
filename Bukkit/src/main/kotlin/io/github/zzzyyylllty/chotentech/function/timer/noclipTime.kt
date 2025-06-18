package io.github.zzzyyylllty.chotentech.function.timer

import io.github.zzzyyylllty.chotentech.ChoTenTech.dataMap
import io.github.zzzyyylllty.chotentech.function.user.transferLevelTask
import me.clip.placeholderapi.PlaceholderAPI
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.entity.Player
import org.jetbrains.annotations.Nullable
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.info
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.function.warning
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlin.time.toJavaDuration

@Awake(LifeCycle.ENABLE)
fun noclipTime() {
    submitAsync (period = 20) {

        for (player in Bukkit.getOnlinePlayers()) {

            val k = player.uniqueId.toString()

            val data = dataMap[k] ?: run {
                warning("$k Data is null!")
                null
            }
            val cd = data?.noclipCooldown ?: 0.0
            val tl = data?.noclipTimeLeft ?: 0.0

            if (player != null && data != null) {

                if (cd > 0.0) dataMap[k]?.noclipCooldown = (cd - 1)

                if (data.noclipping) {

                    if (tl >= 0.0) {

                            sendRefactorTitle(
                                player,
                                " ",
                                "<gradient:green:#006600>在${tl.toInt()}秒后切出 <white>- <gray>${data.noclipPoint?.name}",
                                0,
                                10,
                                0
                            )
                            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_SNARE, 1F, 1F)
                            dataMap[k]?.noclipTimeLeft = (tl - 1)

                    } else {

                        if ((0..100).random() <= (data.noclipPoint?.basicSuccessRate ?: 60.0)) {

                            // 切出成功
                            submit {
                                player.teleport(dataMap[k]?.noclipPoint?.targetLocation ?: error("Null Location"))
                            }
                                sendRefactorMessages("<gradient:green:yellow>切出成功", player)
                                player.playSound(player, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1F, 0.5F)
                                sendRefactorTitle(player, " ", "<gradient:green:yellow>切出成功", 0, 10, 0)
                        } else {
                            // 切出失败
                            val damage = data.noclipPoint?.damage
                            submit {
                                if (damage != null && (data.noclipPoint?.damage ?: 0.0) > 0) {
                                    // 运行主线程任务
                                    player.damage(damage)
                                }
                            }
                                sendRefactorMessages(
                                    "<gradient:gold:red>切出失败，受到 ${data.noclipPoint?.damage} 伤害",
                                    player
                                )
                                sendRefactorTitle(player, " ", "<gradient:gold:red>切出失败", 0, 10, 0)
                                dataMap[player.uniqueId.toString()] = data.copy(
                                    noclipPoint = null,
                                    noclipping = false,
                                    noclipCooldown = data.noclipPoint?.cooldown ?: 10.0
                                )
                            }
                    }
                }
            }
        }
    }
}

fun sendRefactorMessages(message: String, player: Player) {

    val mm = MiniMessage.miniMessage()
    val placeholderadded = PlaceholderAPI.setPlaceholders(player, message)
    val refactored: Component = mm.deserialize(placeholderadded)
    val sendto = player as Audience
    sendto.sendMessage(refactored)
}


fun sendRefactorMessages(message: String, sendto: ConsoleCommandSender) {
    val mm = MiniMessage.miniMessage()
    val placeholderadded = PlaceholderAPI.setPlaceholders(null, message)
    val refactored: Component = mm.deserialize(placeholderadded)
    val sendto2 = sendto as Audience
    sendto2.sendMessage(refactored)
}

fun sendRefactorMessages(message: String, sendto: CommandSender) {

    val mm = MiniMessage.miniMessage()
    val placeholderadded = PlaceholderAPI.setPlaceholders(null, message)
    val refactored: Component = mm.deserialize(placeholderadded)
    val sendto2 = sendto as Audience
    sendto2.sendMessage(refactored)
}

fun sendRefactorTitle(
    player: Player,
    @Nullable var1: String,
    @Nullable var2: String,
    @Nullable var3: Number,
    @Nullable var4: Number,
    @Nullable var5: Number,
) {

    val mm = MiniMessage.miniMessage()
    val placeholderadded1 = PlaceholderAPI.setPlaceholders(player, var1)
    val placeholderadded2 = PlaceholderAPI.setPlaceholders(player, var2)
    val replayer = player as Audience
    val refatored1: Component = mm.deserialize(placeholderadded1)
    val refatored2: Component = mm.deserialize(placeholderadded2)
    replayer.showTitle(
        Title.title(
            refatored1,
            refatored2,
            Title.Times.times(
                var3.toDouble().toDuration(DurationUnit.SECONDS).toJavaDuration(),
                var4.toDouble().toDuration(DurationUnit.SECONDS).toJavaDuration(),
                var5.toDouble().toDuration(DurationUnit.SECONDS).toJavaDuration()
            )
        )
    )
}
