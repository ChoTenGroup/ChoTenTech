package io.github.zzzyyylllty.chotentech.gui

import ink.ptms.chemdah.api.ChemdahAPI.chemdahProfile
import ink.ptms.chemdah.api.ChemdahAPI.isChemdahProfileLoaded
import ink.ptms.chemdah.core.quest.Quest
import ink.ptms.chemdah.core.quest.Task
import io.github.zzzyyylllty.chotentech.event.SubmitItemEvent
import io.github.zzzyyylllty.chotentech.util.minimessage.mmJsonUtil
import io.github.zzzyyylllty.chotentech.util.toComponent
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.function.submit
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import io.github.zzzyyylllty.chotentech.util.guiItem
import io.github.zzzyyylllty.chotentech.util.loreInfo
import io.github.zzzyyylllty.chotentech.util.loreSection
import kotlin.math.min

class SubmitMenu {

    companion object {
        private val DEPOSIT_SLOTS = (19..25) + (28..34) + (37..43)
        private const val BATCH_SIZE = 7
    }

    /** 从任务菜单打开 — 指定 quest + task */
    fun open(player: Player, quest: Quest, task: Task) {
        openInternal(player, quest, task, null)
    }

    /** 从 /submititem 指令打开 — 仅 NPCID */
    fun open(player: Player, npcId: String) {
        openInternal(player, null, null, npcId)
    }

    private fun openInternal(player: Player, quest: Quest?, task: Task?, npcId: String?) {
        player.openMenu<Chest>(mmJsonUtil.serialize("<white><shift:-16><image:kangelicon:list><shift:-176><aqua><font:minecraft:fusionpixel10>提交物品".toComponent())) {
            rows(6)

            map(
                "CCCXIXXXX",
                "XXXXXXXXX",
                "X@@@@@@@X",
                "X@@@@@@@X",
                "X@@@@@@@X",
                if (quest != null) " B  S  B " else "    S    "
            )

            set('C', guiItem(XMaterial.COMPASS, "<aqua><bold>菜单", listOf("<!i> <yellow>点击 <green>关闭"), model = "kangelitem:void")) {
                clicker.closeInventory()
            }


            set('X', guiItem(XMaterial.STONE, "<dark_gray>-", listOf("<!i><white><b>|<reset> <!i><gray>这里什么都没有"), model = "kangelitem:void"))

            onBuild { _, inv ->
                if (task != null) {
                    val amount = task.condition["amount"]?.toInt() ?: 1
                    inv.setItem(4, guiItem(XMaterial.BOOK, "<gold>提交说明", listOf(
                        loreSection("说明"),
                        "<!i><gray>将需要提交的物品放入中间区域",
                        "<!i> <yellow>点击 <green>确认提交",
                        loreInfo("所需数量", "<yellow>$amount")
                    )))
                } else {
                    inv.setItem(4, guiItem(XMaterial.BOOK, "<gold>提交说明", listOf(
                        loreSection("说明"),
                        "<!i><gray>将需要提交的物品放入中间区域",
                        "<!i> <yellow>点击 <green>确认提交",
                        loreInfo("NPCID", "<yellow>$npcId")
                    )))
                }
            }

            set(49, guiItem(XMaterial.ANVIL, "<gold>■ 确认提交", listOf("<!i> <yellow>点击 <green>提交中间区域的所有物品")))

            if (quest != null) {
                set(45, guiItem(XMaterial.BARRIER, "<red>返回", model = "kangelitem:void"))
                set(53, guiItem(XMaterial.BARRIER, "<red>返回", model = "kangelitem:void"))
            }

            onClick(lock = false) { event ->
                val slot = event.rawSlot
                val topSize = event.view.topInventory.size
                if (slot !in 0 until topSize) {
                    return@onClick
                }

                if (slot == 49) {
                    event.isCancelled = true
                    player.playSound(player.location, Sound.BLOCK_ANVIL_USE, 1f, 1f)
                    if (task != null) {
                        processSubmit(player, quest!!, task, event.view.topInventory)
                    } else {
                        processSubmitWithNpc(player, npcId!!, event.view.topInventory)
                    }
                    return@onClick
                }

                if (slot == 45 || slot == 53) {
                    event.isCancelled = true
                    player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                    player.closeInventory()
                    if (quest != null) {
                        QuestMenu().open(player, quest)
                    }
                    return@onClick
                }

                if (slot in DEPOSIT_SLOTS) return@onClick

                event.isCancelled = true
            }

            onClose { event ->
                val p = event.player as? Player ?: return@onClose
                val topInv = event.view.topInventory
                for (slot in DEPOSIT_SLOTS) {
                    val item = topInv.getItem(slot)
                    if (item != null && !item.type.isAir) {
                        p.inventory.addItem(item).values.forEach { leftover ->
                            p.world.dropItemNaturally(p.location, leftover)
                        }
                        topInv.setItem(slot, ItemStack(Material.AIR))
                    }
                }
            }
        }
    }

    // ── 模式一：从任务菜单进入，指定 quest → task ──

    private fun processSubmit(player: Player, quest: Quest, task: Task, inventory: org.bukkit.inventory.Inventory) {
        data class Entry(val slot: Int, var stack: ItemStack)
        val entries = DEPOSIT_SLOTS.mapNotNull { slot ->
            val item = inventory.getItem(slot) ?: return@mapNotNull null
            if (item.type.isAir) null else Entry(slot, item.clone())
        }

        if (entries.isEmpty()) {
            player.sendMessage("<red>没有可提交的物品！".toComponent())
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1f, 1f)
            return
        }

        var submitted = 0
        var cursor = 0

        fun done() {
            if (submitted > 0) {
                player.sendMessage("<green>✔ 成功提交 <yellow>$submitted</yellow> 个物品！".toComponent())
                player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
                player.closeInventory()
                QuestMenu().open(player, quest)
            } else {
                player.sendMessage("<red>没有可提交的物品！".toComponent())
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1f, 1f)
            }
        }

        fun tick() {
            if (!player.isOnline) return
            val end = min(cursor + BATCH_SIZE, entries.size)
            for (i in cursor until end) {
                val entry = entries[i]
                val before = entry.stack.amount
                val event = SubmitItemEvent(player, quest, task, entry.stack)
                event.call()
                if (!event.isCancelled) {
                    val consumed = before - entry.stack.amount
                    if (consumed > 0) {
                        submitted += consumed
                        if (entry.stack.amount <= 0) {
                            inventory.setItem(entry.slot, ItemStack(Material.AIR))
                        } else {
                            inventory.setItem(entry.slot, entry.stack)
                        }
                    }
                }
            }
            cursor = end
            if (cursor < entries.size) {
                submit(delay = 1) { tick() }
            } else {
                done()
            }
        }

        tick()
    }

    // ── 模式二：从 /submititem 指令进入，仅 NPCID ──

    private fun processSubmitWithNpc(player: Player, npcId: String, inventory: org.bukkit.inventory.Inventory) {
        val profile = if (player.isChemdahProfileLoaded) player.chemdahProfile else null
        if (profile == null) {
            player.sendMessage("<red>数据尚未加载完成！".toComponent())
            return
        }

        // 收集所有活跃 quest 中匹配 npc 条件的 choten-submit 任务
        data class Match(val quest: Quest, val task: Task)
        val matches = mutableListOf<Match>()
        for (quest in profile.getQuests()) {
            for (task in quest.tasks) {
                if (task.objective.name == "choten submit" && task.condition["npc"]?.toString() == npcId) {
                    matches.add(Match(quest, task))
                }
            }
        }

        if (matches.isEmpty()) {
            player.sendMessage("<red>没有找到匹配的任务！".toComponent())
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1f, 1f)
            return
        }

        data class Entry(val slot: Int, var stack: ItemStack)
        val entries = DEPOSIT_SLOTS.mapNotNull { slot ->
            val item = inventory.getItem(slot) ?: return@mapNotNull null
            if (item.type.isAir) null else Entry(slot, item.clone())
        }

        if (entries.isEmpty()) {
            player.sendMessage("<red>没有可提交的物品！".toComponent())
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1f, 1f)
            return
        }

        var totalSubmitted = 0
        var entryIndex = 0
        var matchIndex = 0

        fun done() {
            if (totalSubmitted > 0) {
                player.sendMessage("<green>✔ 成功提交 <yellow>$totalSubmitted</yellow> 个物品！".toComponent())
                player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
            } else {
                player.sendMessage("<red>没有符合要求的物品！".toComponent())
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1f, 1f)
            }
        }

        fun tick() {
            if (!player.isOnline) return

            val entryEnd = min(entryIndex + 1, entries.size)
            for (ei in entryIndex until entryEnd) {
                val entry = entries[ei]
                if (entry.stack.amount <= 0) continue

                for (mi in matchIndex until matches.size) {
                    val match = matches[mi]
                    val before = entry.stack.amount
                    val event = SubmitItemEvent(player, match.quest, match.task, entry.stack, npcId)
                    event.call()
                    if (!event.isCancelled) {
                        val consumed = before - entry.stack.amount
                        if (consumed > 0) {
                            totalSubmitted += consumed
                            if (entry.stack.amount <= 0) {
                                inventory.setItem(entry.slot, ItemStack(Material.AIR))
                            } else {
                                inventory.setItem(entry.slot, entry.stack)
                            }
                            break
                        }
                    }
                }
            }

            entryIndex++
            if (entryIndex < entries.size) {
                submit(delay = 1) { tick() }
            } else {
                done()
            }
        }

        tick()
    }
}
