@file:Suppress("UNUSED")

package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.ChoTenTech.regenTemplatesByBlock
import io.github.zzzyyylllty.chotentech.function.player.getPlayerLevel
import io.github.zzzyyylllty.chotentech.function.player.sendComponent
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks
import net.momirealms.craftengine.bukkit.api.CraftEngineItems
import org.bukkit.GameMode
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent

private val ALLOWED_CE_BLOCKS = setOf(
    "kangelblocks:sandstone",
    "kangelblocks:dirt",
    "kangelblocks:stone",
    "kangelblocks:cobblestone",
    "kangelblocks:obsidian",
    "kangelblocks:glass",
)

@SubscribeEvent(priority = EventPriority.LOWEST)
fun onPlayerPlaceBlock(event: BlockPlaceEvent) {
    val player = event.player
    if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return

    val level = player.getPlayerLevel()
    if (level == null || !level.isPlaceable) {
        event.isCancelled = true
        player.sendComponent("<red>当前层级不允许放置方块!")
        return
    }

    if (!event.blockReplacedState.type.isAir()) {
        event.isCancelled = true
        player.sendComponent("<red>你不能在非空气方块上放置方块!")
        return
    }

    // Let CE handle placement if the held item is an allowed CE custom block
    val ceBlockId = CraftEngineItems.getCustomItemId(event.itemInHand)
    if (ceBlockId != null && ceBlockId.asString() in ALLOWED_CE_BLOCKS) return

    // Cancel all other block placement
    event.isCancelled = true
    player.sendComponent("<red>不允许放置这个方块!")
}

@SubscribeEvent(priority = EventPriority.LOWEST)
fun onPlayerBreakBlock(event: BlockBreakEvent) {
    val player = event.player
    if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return

    val block = event.block

    // Allow breaking the 6 allowed CE custom blocks (player-placed)
    val ceState = CraftEngineBlocks.getCustomBlockState(block)
    if (ceState != null) {
        val ceId = ceState.customBlockState()?.ownerId()?.asString()
        if (ceId in ALLOWED_CE_BLOCKS) return
    }

    // Allow breaking regen system blocks (ores, logs, crops)
    val blockId = getBlockID(block)
    if (regenTemplatesByBlock.containsKey(blockId)) return

    // Protect natural terrain
    event.isCancelled = true
}
