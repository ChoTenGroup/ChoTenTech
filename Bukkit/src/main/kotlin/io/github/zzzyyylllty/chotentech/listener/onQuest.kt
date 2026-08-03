package io.github.zzzyyylllty.chotentech.listener

import ink.ptms.chemdah.api.event.collect.ObjectiveEvents
import ink.ptms.chemdah.api.event.collect.QuestEvents
import ink.ptms.chemdah.core.quest.addon.AddonTrack.Companion.trackQuest
import ink.ptms.chemdah.core.quest.meta.MetaName
import io.github.zzzyyylllty.chotentech.data.profile.ProfileManager
import io.github.zzzyyylllty.chotentech.util.NotificationQueue
import io.github.zzzyyylllty.chotentech.util.minimessage.legacyToMiniMessage
import io.github.zzzyyylllty.chotentech.util.toComponent
import net.kyori.adventure.title.Title
import org.bukkit.Sound
import org.bukkit.SoundCategory
import taboolib.common.platform.event.SubscribeEvent

private fun questStatusMode(player: org.bukkit.entity.Player): String =
    ProfileManager.getSharedData(player.uniqueId.toString()).settings.get("showQuestStatus")

private fun sendQuestNotification(player: org.bukkit.entity.Player, titleText: String, subtitleText: String) {
    when (questStatusMode(player)) {
        "hide" -> {}
        "sidebar" -> {
            NotificationQueue.enqueue(player.uniqueId, "$titleText — $subtitleText")
        }
        "chat" -> {
            player.sendMessage("$titleText — $subtitleText".toComponent())
        }
        else -> { // "show"
            player.showTitle(Title.title(titleText.toComponent(), subtitleText.toComponent()))
        }
    }
}

@SubscribeEvent
fun onQuestAccept(e: QuestEvents.Accept.Post) {
    val profile = e.playerProfile
    val player = profile.player
    val quest = e.quest
    player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BANJO, SoundCategory.MASTER, 1f, 0.5f)
    val name = quest.template.meta<MetaName>("name")?.displayName?.legacyToMiniMessage() ?: "未知"
    sendQuestNotification(player, "<gradient:#ffaa66:#cc8844>任务已接受", "<white>$name")
    if (ProfileManager.getSharedData(player.uniqueId.toString()).settings.getBoolean("autoTrackQuest")) {
        profile.trackQuest = quest.template
    }
}


@SubscribeEvent
fun onQuestComplete(e: QuestEvents.Complete.Post) {
    val player = e.playerProfile.player
    val quest = e.quest
    player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 1f, 0.8f)
    val name = quest.template.meta<MetaName>("name")?.displayName?.legacyToMiniMessage() ?: "未知"
    sendQuestNotification(player, "<gradient:#99ffff:#66ccff>任务已完成", "<white>$name")
}

@SubscribeEvent
fun onQuestFail(e: QuestEvents.Fail.Post) {
    val player = e.playerProfile.player
    val quest = e.quest
    player.playSound(player, Sound.BLOCK_LAVA_EXTINGUISH, SoundCategory.MASTER, 1f, 0.5f)
    val name = quest.template.meta<MetaName>("name")?.displayName?.legacyToMiniMessage() ?: "未知"
    sendQuestNotification(player, "<gradient:#ff6600:#cc2200>任务已失败", "<white>$name")
}


@SubscribeEvent
fun onTaskComplete(e: ObjectiveEvents.Complete.Post) {
    val player = e.playerProfile.player
    val quest = e.quest
    val task = e.task
    player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.MASTER, 1f, 1.2f)
    val questName = quest.template.meta<MetaName>("name")?.displayName?.legacyToMiniMessage() ?: "Unknown"
    val taskName = task.meta<MetaName>("name")?.displayName?.legacyToMiniMessage() ?: "Unknown"
    val msg = "<gray>任务 <white>$questName<reset> <gray>的 <white>$taskName<reset><gray> 目标已完成。"
    when (questStatusMode(player)) {
        "hide" -> {}
        "sidebar", "show" -> NotificationQueue.enqueue(player.uniqueId, msg)
        "chat" -> player.sendMessage(msg.toComponent())
    }
}
