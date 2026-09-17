package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.data.DataManager
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.data.getPos
import io.github.zzzyyylllty.chotentech.player.sendComponent
import io.github.zzzyyylllty.chotentech.util.toComponent
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.ChunkUnloadEvent
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.submitAsync

//@SubscribeEvent
//fun onChunkLoadEvent(event: ChunkLoadEvent) {
//    val chunk = event.chunk
//    DataManager.loadChunkDataQuery(chunk)
//}
//
//@SubscribeEvent
//fun onChunkUnloadEvent(event: ChunkUnloadEvent) {
//    val chunk = event.chunk
//    DataManager.unloadChunkData(chunk)
//}
//
//val mineableBlocks = "(ORE|WOOD|LOG)".toRegex()
//val unplaceableBlocks = "(ORE|WOOD|LOG|BUCKET|WATER|LAVA|POWDER_SNOW)".toRegex()
//
//

//@SubscribeEvent(priority = EventPriority.HIGHEST)
//fun onBlockBreakEvent(event: BlockBreakEvent) {
//    val player = event.player
//    val chunk = DataManager.getChunkData(event.block.chunk) ?: run {
//        event.isCancelled = true
//        player.sendComponent("<#E08A98>这个区块的数据正在加载，请稍等片刻!")
//        return
//    }
//
//    if (player.gameMode == GameMode.SPECTATOR || player.gameMode == GameMode.CREATIVE) {
//        if (player.getData()?.editorMode == true) {
//            player.sendActionBar("<#8FD4A6><b>编辑模式，破坏的方块已从记录中取消。".toComponent())
//            chunk.data.unbreakableBlock.remove(event.block.getPos())
//            chunk.data.breakableBlock.remove(event.block.getPos())
//            chunk.dirty = true
//        } else {
//            player.sendActionBar("<#EFDF8F><b>非编辑模式，破坏的方块将不做处理。".toComponent())
//        }
//        return
//    }
//
//    // 普通玩家破坏矿物/木头/原木时，不记录
//    if (mineableBlocks.containsMatchIn(event.block.type.name)) return
//
//    // 如果你希望普通玩家破坏后也要从记录里清掉，可以保留下面两行
//    chunk.data.unbreakableBlock.remove(event.block.getPos())
//    chunk.data.breakableBlock.remove(event.block.getPos())
//    chunk.dirty = true
//}
//
//

//@SubscribeEvent(priority = EventPriority.HIGHEST)
//fun onBlockPlaceEvent(event: BlockPlaceEvent) {
//    val player = event.player
//    val chunk = DataManager.getChunkData(event.block.chunk) ?: run {
//        event.isCancelled = true
//        player.sendComponent("<#E08A98>这个区块的数据正在加载，请稍等片刻!")
//        return
//    }
//
//    if (player.gameMode == GameMode.SPECTATOR || player.gameMode == GameMode.CREATIVE) {
//        if (player.getData()?.editorMode == true) {
//            player.sendActionBar("<#8FD4A6><b>编辑模式，放置的方块不可被破坏。".toComponent())
//            chunk.data.unbreakableBlock.add(event.block.getPos())
//            chunk.dirty = true
//        } else {
//            player.sendActionBar("<#EFDF8F><b>非编辑模式，放置的方块将不做处理。".toComponent())
//        }
//        return
//    }
//
//    if (unplaceableBlocks.containsMatchIn(event.block.type.name)) {
//        event.isCancelled = true
//        player.sendComponent("<#E08A98>不允许放置这个方块")
//        return
//    }
//
//    chunk.data.breakableBlock.add(event.block.getPos())
//    chunk.dirty = true
//}