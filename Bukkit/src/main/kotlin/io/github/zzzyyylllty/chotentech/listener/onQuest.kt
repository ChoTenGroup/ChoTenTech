package io.github.zzzyyylllty.chotentech.listener

import ink.ptms.chemdah.api.event.collect.ObjectiveEvents
import ink.ptms.chemdah.api.event.collect.QuestEvents
import ink.ptms.chemdah.core.quest.addon.AddonTrack.Companion.trackQuest
import ink.ptms.chemdah.core.quest.meta.Meta
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

/** 玩家是否处于未选择档案(强制选择)状态 —— 此时不显示任何任务提示 */
private fun isProfilePending(player: org.bukkit.entity.Player): Boolean =
    ProfileListener.pendingSelection.containsKey(player.uniqueId.toString())

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
fun onQuestAcceptPre(e: QuestEvents.Accept.Pre) {
    val player = e.playerProfile.player
    // 不在主世界且档案未载入时阻止接受教学任务:加载期数据不完整,接受会写入脏数据
    if (!player.world.name.equals("world", ignoreCase = true) &&
        io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap[player.uniqueId.toString()] == null
    ) {
        val quest = e.quest
        val isTutorial = quest.id.equals("tutorial", ignoreCase = true) ||
            quest.meta<Meta<String>>("name")?.source?.equals("Tutorial", ignoreCase = true) == true
        if (isTutorial) {
            e.isCancelled = true
            e.reason = "<#E08A98>档案未载入,暂时无法接受任务"
        }
    }
}

@SubscribeEvent
fun onQuestAccept(e: QuestEvents.Accept.Post) {
    val profile = e.playerProfile
    val player = profile.player
    if (isProfilePending(player)) return
    val quest = e.quest
    player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BANJO, SoundCategory.MASTER, 1f, 0.5f)
    val name = quest.template.meta<MetaName>("name")?.displayName?.legacyToMiniMessage() ?: "未知"
    sendQuestNotification(player, "<gradient:#e8cfba:#c0a78d>任务已接受", "<#F2F5FA>$name")
    if (ProfileManager.getSharedData(player.uniqueId.toString()).settings.getBoolean("autoTrackQuest")) {
        profile.trackQuest = quest.template
    }
}


@SubscribeEvent
fun onQuestComplete(e: QuestEvents.Complete.Post) {
    val player = e.playerProfile.player
    val quest = e.quest
    // 未选择档案时只屏蔽提示,任务完成的数据标记(如教学)不受影响
    if (!isProfilePending(player)) {
        player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 1f, 0.8f)
        val name = quest.template.meta<MetaName>("name")?.displayName?.legacyToMiniMessage() ?: "未知"
        sendQuestNotification(player, "<gradient:#c6ecec:#bad9e8>任务已完成", "<#F2F5FA>$name")
    }

    // 完成教学任务后标记共享数据(任一 profile 完成即全局生效)
    val rawName = quest.template.meta<Meta<String>>("name")?.source
    if (quest.template.id.equals("tutorial", ignoreCase = true) || rawName?.equals("Tutorial", ignoreCase = true) == true) {
        val shared = ProfileManager.getSharedData(player.uniqueId.toString())
        if (!shared.isCompletedTutorial) {
            shared.isCompletedTutorial = true
            ProfileManager.saveSharedData(player.uniqueId.toString())
        }
    }
}

@SubscribeEvent
fun onQuestFail(e: QuestEvents.Fail.Post) {
    val player = e.playerProfile.player
    if (isProfilePending(player)) return
    val quest = e.quest
    player.playSound(player, Sound.BLOCK_LAVA_EXTINGUISH, SoundCategory.MASTER, 1f, 0.5f)
    val name = quest.template.meta<MetaName>("name")?.displayName?.legacyToMiniMessage() ?: "未知"
    sendQuestNotification(player, "<gradient:#cf946e:#c25c47>任务已失败", "<#F2F5FA>$name")
}


@SubscribeEvent
fun onTaskComplete(e: ObjectiveEvents.Complete.Post) {
    val player = e.playerProfile.player
    if (isProfilePending(player)) return
    val quest = e.quest
    val task = e.task
    player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.MASTER, 1f, 1.2f)
    val questName = quest.template.meta<MetaName>("name")?.displayName?.legacyToMiniMessage() ?: "Unknown"
    val taskName = task.meta<MetaName>("name")?.displayName?.legacyToMiniMessage() ?: "Unknown"
    val msg = "<#A9B4C3>任务 <#F2F5FA>$questName<reset> <#A9B4C3>的 <#F2F5FA>$taskName<reset><#A9B4C3> 目标已完成。"
    when (questStatusMode(player)) {
        "hide" -> {}
        "sidebar", "show" -> NotificationQueue.enqueue(player.uniqueId, msg)
        "chat" -> player.sendMessage(msg.toComponent())
    }
}
