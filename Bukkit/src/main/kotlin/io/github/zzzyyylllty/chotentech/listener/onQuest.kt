package io.github.zzzyyylllty.chotentech.listener

import ink.ptms.chemdah.api.event.collect.ObjectiveEvents
import ink.ptms.chemdah.api.event.collect.QuestEvents
import ink.ptms.chemdah.core.quest.meta.MetaName
import io.github.zzzyyylllty.chotentech.player.sendComponent
import io.github.zzzyyylllty.chotentech.util.minimessage.legacyToMiniMessage
import io.github.zzzyyylllty.chotentech.util.toComponent
import net.kyori.adventure.title.Title
import org.bukkit.Sound
import org.bukkit.SoundCategory
import taboolib.common.platform.event.SubscribeEvent

@SubscribeEvent
fun onQuestAccept(e: QuestEvents.Accept.Post) {
    val player = e.playerProfile.player
    val quest = e.quest
    player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BANJO, SoundCategory.MASTER, 1f, 0.5f)
    player.showTitle(Title.title("<gradient:#ffaa66:#cc8844>任务已接受".toComponent(), "<white>${quest.template.meta<MetaName>("name")?.displayName?.legacyToMiniMessage()}".toComponent()))
}


@SubscribeEvent
fun onQuestComplete(e: QuestEvents.Complete.Post) {
    val player = e.playerProfile.player
    val quest = e.quest
    player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 1f, 0.8f)
    player.showTitle(Title.title("<gradient:#99ffff:#66ccff>任务已完成".toComponent(), "<white>${quest.template.meta<MetaName>("name")?.displayName?.legacyToMiniMessage()}".toComponent()))
}

@SubscribeEvent
fun onQuestFail(e: QuestEvents.Fail.Post) {
    val player = e.playerProfile.player
    val quest = e.quest
    player.playSound(player, Sound.BLOCK_LAVA_EXTINGUISH, SoundCategory.MASTER, 1f, 0.5f)
    player.showTitle(Title.title("<gradient:#ff6600:#cc2200>任务已失败".toComponent(), "<white>${quest.template.meta<MetaName>("name")?.displayName?.legacyToMiniMessage()}".toComponent()))
}


@SubscribeEvent
fun onTaskComplete(e: ObjectiveEvents.Complete.Post) {
    val player = e.playerProfile.player
    val quest = e.quest
    val task = e.task
    player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.MASTER, 1f, 1.2f)
    player.sendComponent("<gray>任务 <white>${quest.template.meta<MetaName>("name")?.displayName?.legacyToMiniMessage()}<reset> <gray>的 <white>${task.meta<MetaName>("name")?.displayName?.legacyToMiniMessage()}<reset><gray> 目标已完成。")
}
