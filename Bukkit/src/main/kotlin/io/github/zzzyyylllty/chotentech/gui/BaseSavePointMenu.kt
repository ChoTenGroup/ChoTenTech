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

        player.openMenu<Chest>(mmJsonUtil.serialize("<gray>⚐ ${base.name} 存档点".toComponent())) {
            rows(1)

            map("ABCDE")

            // 存档基地
            set('A', guiItem(XMaterial.WRITABLE_BOOK, "<green><b>📂 存档基地", listOf(
                loreSection("说明"),
                "<!i><gray>将当前基地添加到你的已存档列表",
                "<!i><gray>已存档的基地将保存在你的档案中",
                loreSpace(),
                if (data.savedBases.contains(base.idef)) loreInfo("状态", "<green>✓ 已存档") else "<!i> <yellow>点击 <green>存档"
            ))) {
                if (data.savedBases.contains(base.idef)) {
                    player.sendActionBar("<yellow>该基地已存档".toComponent())
                    player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1f, 1f)
                    return@set
                }
                data.savedBases.add(base.idef)
                player.sendMessage("<green>✅ 已存档基地: <yellow>${base.name}")
                player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.7f)
                player.closeInventory()
            }

            // 保存出生点
            set('C', guiItem(XMaterial.COMPASS, "<aqua><b>📍 保存出生点", listOf(
                loreSection("说明"),
                "<!i><gray>将基地的出生点设为你的重生点",
                "<!i><gray>死亡后将在此处复活",
                loreSpace(),
                "<!i> <yellow>点击 <green>保存"
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
                player.sendMessage("<green>✅ 重生点已设置为 <yellow>${base.name}")
                player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.7f)
                player.closeInventory()
            }

            // 设为主基地
            set('E', guiItem(XMaterial.NETHER_STAR, "<gold><b>⭐ 设为主基地", listOf(
                loreSection("说明"),
                "<!i><gray>将当前基地设为主基地",
                "<!i><gray>显示在称号和相关信息中",
                loreSpace(),
                if (data.subTag == base.idef) loreInfo("状态", "<green>✓ 当前主基地") else "<!i> <yellow>点击 <green>设置"
            ))) {
                if (data.subTag == base.idef) {
                    player.sendActionBar("<yellow>这已经是你的主基地".toComponent())
                    player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1f, 1f)
                    return@set
                }
                data.mainTag = base.faction
                data.subTag = base.idef
                player.sendMessage("<green>✅ 已将 <yellow>${base.name} <green>设为主基地")
                player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f)
                player.showTitle(Title.title(
                    "<gold>⭐ ${base.name}".toComponent(),
                    "<gray>已设为主基地".toComponent(),
                    Title.Times.times(Duration.ofMillis(0), Duration.ofMillis(2000), Duration.ofMillis(500))
                ))
                player.closeInventory()
            }
        }
    }
}
