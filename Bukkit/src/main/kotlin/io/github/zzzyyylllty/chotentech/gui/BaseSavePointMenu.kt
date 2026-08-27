package io.github.zzzyyylllty.chotentech.gui

import io.github.zzzyyylllty.chotentech.data.Base
import io.github.zzzyyylllty.chotentech.data.RespawnPoint
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.util.guiItem
import io.github.zzzyyylllty.chotentech.util.loreInfo
import io.github.zzzyyylllty.chotentech.util.loreSection
import io.github.zzzyyylllty.chotentech.util.loreSpace
import io.github.zzzyyylllty.chotentech.util.minimessage.mmJsonUtil
import io.github.zzzyyylllty.chotentech.util.toComponent
import net.kyori.adventure.title.Title
import org.bukkit.Sound
import org.bukkit.entity.Player
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import java.time.Duration

class BaseSavePointMenu(private val base: Base) {

    fun open(player: Player) {
        val data = player.getData() ?: return

        player.openMenu<Chest>(mmJsonUtil.serialize("<#A9B4C3>⚐ ${base.name} 存档点".toComponent())) {
            rows(1)

            map("ABCDE")

            // 存档基地
            set('A', guiItem(XMaterial.WRITABLE_BOOK, "<#8FD4A6><b>📂 存档基地", listOf(
                loreSection("说明"),
                "<!i><#A9B4C3>将当前基地添加到你的已存档列表",
                "<!i><#A9B4C3>已存档的基地将保存在你的档案中",
                loreSpace(),
                if (data.savedBases.contains(base.idef)) loreInfo("状态", "<#8FD4A6>✓ 已存档") else "<!i> <#EFDF8F>点击 <#8FD4A6>存档"
            ))) {
                if (data.savedBases.contains(base.idef)) {
                    player.sendActionBar("<#EFDF8F>该基地已存档".toComponent())
                    player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1f, 1f)
                    return@set
                }
                data.savedBases.add(base.idef)
                player.sendMessage("<#8FD4A6>✅ 已存档基地: <#EFDF8F>${base.name}")
                player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.7f)
                player.closeInventory()
            }

            // 保存出生点
            set('C', guiItem(XMaterial.COMPASS, "<#84D1CB><b>📍 保存出生点", listOf(
                loreSection("说明"),
                "<!i><#A9B4C3>将基地的出生点设为你的重生点",
                "<!i><#A9B4C3>死亡后将在此处复活",
                loreSpace(),
                "<!i> <#EFDF8F>点击 <#8FD4A6>保存"
            ))) {
                data.baseSave = null // clear base save, let player set it via save point system
                data.respawnPoint = RespawnPoint(
                    world = base.spawn.world,
                    x = base.spawn.x,
                    y = base.spawn.y,
                    z = base.spawn.z,
                    yaw = base.spawn.yaw,
                    pitch = base.spawn.pitch
                )
                player.sendMessage("<#8FD4A6>✅ 重生点已设置为 <#EFDF8F>${base.name}")
                player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.7f)
                player.closeInventory()
            }

            // 设为主基地
            set('E', guiItem(XMaterial.NETHER_STAR, "<#DFBD7C><b>⭐ 设为主基地", listOf(
                loreSection("说明"),
                "<!i><#A9B4C3>将当前基地设为主基地",
                "<!i><#A9B4C3>显示在称号和相关信息中",
                loreSpace(),
                if (data.subTag == base.idef) loreInfo("状态", "<#8FD4A6>✓ 当前主基地") else "<!i> <#EFDF8F>点击 <#8FD4A6>设置"
            ))) {
                if (data.subTag == base.idef) {
                    player.sendActionBar("<#EFDF8F>这已经是你的主基地".toComponent())
                    player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1f, 1f)
                    return@set
                }
                data.mainTag = base.faction
                data.subTag = base.idef
                player.sendMessage("<#8FD4A6>✅ 已将 <#EFDF8F>${base.name} <#8FD4A6>设为主基地")
                player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f)
                player.showTitle(Title.title(
                    "<#DFBD7C>⭐ ${base.name}".toComponent(),
                    "<#A9B4C3>已设为主基地".toComponent(),
                    Title.Times.times(Duration.ofMillis(0), Duration.ofMillis(2000), Duration.ofMillis(500))
                ))
                player.closeInventory()
            }
        }
    }
}
