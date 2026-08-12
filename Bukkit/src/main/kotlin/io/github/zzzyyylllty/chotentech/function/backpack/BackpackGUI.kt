package io.github.zzzyyylllty.chotentech.function.backpack

import io.github.zzzyyylllty.chotentech.data.backpack.BackpackConfig
import io.github.zzzyyylllty.chotentech.data.backpack.BackpackManager
import io.github.zzzyyylllty.chotentech.data.backpack.BackpackType
import io.github.zzzyyylllty.sertraline.data.defaultData
import io.github.zzzyyylllty.chotentech.function.javascript.compileJs
import io.github.zzzyyylllty.chotentech.function.javascript.eval
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XMaterial
import taboolib.module.nms.getItemTag
import taboolib.module.ui.ClickType
import taboolib.module.ui.conditionSlot
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import taboolib.platform.util.buildItem
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.script.CompiledScript

object BackpackGUI {

    private val mm = MiniMessage.miniMessage()

    /** JS 条件编译缓存 */
    private val conditionCache = ConcurrentHashMap<String, CompiledScript>()

    /**
     * 已打开的背包记录，防止同一玩家重复打开同一背包物品
     * 防刷物品第一道防线：禁止并发打开
     */
    private val openBackpacks = ConcurrentHashMap<UUID, MutableSet<String>>()

    /** 玩家 -> 打开中的背包会话，用于插件关闭时保存仍在打开状态的背包 */
    private val openSessions = ConcurrentHashMap<UUID, OpenSession>()

    private data class OpenSession(val backpackId: String, val typeId: String, val slotCount: Int)

    // ==================== 核心：打开背包 GUI ====================

    fun Player.openBackpack(type: BackpackType, itemUUID: String) {
        val player = this
        val backpackId = itemUUID

        // ===== 防重复打开（防刷物品）=====
        val types = openBackpacks.getOrPut(uniqueId) { mutableSetOf() }
        if (!types.add(backpackId)) {
            player.sendMessage(mm.deserialize("<red>背包已在打开中！</red>"))
            return
        }

        // ===== 计算布局 =====
        val totalSlots = BackpackConfig.rows * 9
        val (storageSlots, lockedSlots, slotCount) = buildLayout(type, totalSlots)

        // ===== 从数据库加载 =====
        val bp = BackpackManager.getOrCreateBackpack(backpackId, type.id, slotCount)

        // ===== 刷物检测：同一 UUID 被两个不同玩家同时打开 =====
        val previousHolder = BackpackManager.activeSessions.put(backpackId, uniqueId)
        if (previousHolder != null && previousHolder != uniqueId) {
            val flagged = BackpackManager.flagDupe(bp, uniqueId, previousHolder)
            BackpackManager.updateBackpack(flagged)
            BackpackManager.activeSessions.remove(backpackId)
            openBackpacks[uniqueId]?.remove(backpackId)
            player.sendMessage("§c此背包已被禁用（检测到疑似刷物行为）")
            return
        }

        // ===== 检查是否已被禁用 =====
        if (bp.disabled) {
            BackpackManager.activeSessions.remove(backpackId)
            openBackpacks[uniqueId]?.remove(backpackId)
            player.sendMessage("§c此背包已被管理员禁用")
            return
        }

        // ===== 记录打开者 =====
        val updatedBp = BackpackManager.addOpener(bp, uniqueId)
        if (updatedBp !== bp) {
            BackpackManager.updateBackpack(updatedBp)
        }

        // ===== 记录会话（插件关闭时兜底保存）=====
        openSessions[uniqueId] = OpenSession(backpackId, type.id, slotCount)

        val denyComponent: Component = mm.deserialize(BackpackConfig.denySlotMessage)
        val title = type.display.replace('&', '§')

        val hasCondition = type.condition.isNotBlank() && type.condition != "true"

        player.openMenu<Chest>(title) {
            rows(BackpackConfig.rows)
            handLocked(true)

            // ---------- 构建界面 ----------
            onBuild { _, inventory ->
                val items = BackpackManager.deserializeContents(bp.content, slotCount)
                for (slot in storageSlots) {
                    if (slot < items.size && items[slot] != null) {
                        inventory.setItem(slot, items[slot])
                    }
                }
                if (lockedSlots.isNotEmpty()) {
                    val lockedItem = buildItem(XMaterial.BARRIER) {
                        name = "§c🔒"
                        lore += "§7该背包仅有 $slotCount 格"
                    }
                    for (slot in lockedSlots) {
                        inventory.setItem(slot, lockedItem)
                    }
                }
            }

            // ---------- 点击处理 ----------
            onClick(lock = false) { event ->
                val rawSlot = event.rawSlot
                if (rawSlot < 0 || rawSlot >= totalSlots + 36) return@onClick

                if (rawSlot in lockedSlots) {
                    event.isCancelled = true
                    return@onClick
                }

                if (!hasCondition) return@onClick

                when {
                    rawSlot in storageSlots -> {
                        event.conditionSlot(rawSlot,
                            condition = { put, _ ->
                                put == null || put.type == Material.AIR
                                        || evalCondition(type.condition, put, event.clicker)
                            },
                            failedCallback = { event.clicker.sendMessage(denyComponent) }
                        )
                    }

                    rawSlot >= totalSlots || event.clickType == ClickType.DRAG -> {
                        val clickEvent = event.clickEventOrNull()
                        val isShift = clickEvent?.isShiftClick == true
                        val isDrag = event.clickType == ClickType.DRAG

                        if (isShift || isDrag) {
                            for (slot in storageSlots) {
                                event.conditionSlot(slot,
                                    condition = { put, _ ->
                                        put == null || put.type == Material.AIR
                                                || evalCondition(type.condition, put, event.clicker)
                                    },
                                    failedCallback = { event.clicker.sendMessage(denyComponent) }
                                )
                            }
                        }
                    }
                }
            }

            // ---------- 关闭：保存到数据库 + 清理 ----------
            onClose {
                val inv = it.inventory
                val contents = Array(slotCount) { idx ->
                    if (idx < inv.size) inv.getItem(idx) else null
                }
                BackpackManager.saveBackpack(backpackId, type.id, contents, slotCount)

                openSessions.remove(player.uniqueId)
                openBackpacks[player.uniqueId]?.remove(backpackId)
                BackpackManager.activeSessions.remove(backpackId)
            }
        }
    }

    // ==================== 布局解析 ====================

    /**
     * 根据配置的 layout 或默认逻辑计算储物格和锁定格
     * @return Triple<storageSlots, lockedSlots, effectiveSlotCount>
     */
    private fun buildLayout(type: BackpackType, totalSlots: Int): Triple<Set<Int>, Set<Int>, Int> {
        val parsed = type.parseLayout()
        if (parsed != null) {
            val storageSlots = parsed.filterValues { it == 'O' }.keys
            val lockedSlots = parsed.filterValues { it == 'X' }.keys
            val effectiveCount = storageSlots.size.coerceAtMost(type.slots)
            return Triple(storageSlots, lockedSlots, effectiveCount)
        }
        // 默认：前 N 格可用，其余锁定
        val slotCount = type.slots.coerceIn(1, totalSlots)
        return Triple(
            (0 until slotCount).toSet(),
            (slotCount until totalSlots).toSet(),
            slotCount
        )
    }

    // ==================== JS 条件判断 ====================

    private fun evalCondition(script: String, item: ItemStack, player: Player): Boolean {
        if (script.isBlank() || script == "true") return true

        val compiled = conditionCache.computeIfAbsent(script) { it.compileJs()!! }
        return try {
            val result = compiled.eval(mapOf("item" to item, "player" to player, "tag" to item.getItemTag()) + defaultData, player)
            result == true || result == "true" || (result is Number && result.toInt() != 0)
        } catch (_: Exception) {
            true
        }
    }

    /** 玩家退出时清理其所有活跃会话 */
    fun removePlayerSessions(playerUuid: UUID) {
        openSessions.remove(playerUuid)
        openBackpacks.remove(playerUuid)
        BackpackManager.activeSessions.entries.removeIf { it.value == playerUuid }
    }

    // ==================== 生命周期 ====================

    /** 插件关闭时保存仍处于打开状态的背包 */
    fun saveAll() {
        for ((playerUuid, session) in openSessions) {
            val player = Bukkit.getPlayer(playerUuid) ?: continue
            val inv = player.openInventory?.topInventory ?: continue
            val contents = Array(session.slotCount) { idx ->
                if (idx < inv.size) inv.getItem(idx) else null
            }
            BackpackManager.saveBackpack(session.backpackId, session.typeId, contents, session.slotCount)
        }
        openSessions.clear()
        openBackpacks.clear()
        BackpackManager.activeSessions.clear()
    }
}
