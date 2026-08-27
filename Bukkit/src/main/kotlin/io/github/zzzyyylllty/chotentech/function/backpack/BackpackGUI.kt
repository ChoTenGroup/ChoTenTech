package io.github.zzzyyylllty.chotentech.function.backpack

import io.github.zzzyyylllty.chotentech.data.backpack.BackpackConfig
import io.github.zzzyyylllty.chotentech.data.backpack.BackpackManager
import io.github.zzzyyylllty.chotentech.data.backpack.BackpackType
import io.github.zzzyyylllty.chotentech.util.minimessage.mmUtil
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.ClickType
import taboolib.module.ui.applyAmountConditionShiftIfNeeded
import taboolib.module.ui.conditionSlot
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import taboolib.platform.util.buildItem
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object BackpackGUI {

    private val mm = mmUtil

    /**
     * 已打开的背包记录，防止同一玩家重复打开同一背包物品
     * 防刷物品第一道防线：禁止并发打开
     */
    private val openBackpacks = ConcurrentHashMap<UUID, MutableSet<String>>()

    /** 玩家 -> 打开中的背包会话，用于玩家退出/插件关闭时兜底保存仍在打开状态的背包 */
    private val openSessions = ConcurrentHashMap<UUID, OpenSession>()

    private data class OpenSession(
        val backpackId: String,
        val typeId: String,
        val slotCount: Int,
        /** 储物格物理槽位列表，顺序即内容数组下标（保存/加载按此一一对应） */
        val storageSlots: List<Int>,
        /** 打开中的 GUI 物品栏引用，onBuild 时写入，用于退出/关服时兜底保存 */
        var inventory: Inventory? = null,
    )

    // ==================== 核心：打开背包 GUI ====================

    fun Player.openBackpack(type: BackpackType, itemUUID: String) {
        val player = this
        val backpackId = itemUUID

        // ===== 防重复打开（防刷物品）=====
        val types = openBackpacks.getOrPut(uniqueId) { mutableSetOf() }
        if (!types.add(backpackId)) {
            player.sendMessage(mm.deserialize("<#E08A98>背包已在打开中！</#E08A98>"))
            return
        }

        try {
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

            // ===== 记录会话（玩家退出/插件关闭时兜底保存）=====
            openSessions[uniqueId] = OpenSession(backpackId, type.id, slotCount, storageSlots)

            val denyComponent: Component = mm.deserialize(BackpackConfig.denySlotMessage)
            val title = type.display.replace('&', '§')

            val hasCondition = type.condition.rules.isNotEmpty()

            player.openMenu<Chest>(title) {
                rows(BackpackConfig.rows)
                handLocked(true)

                // ---------- 构建界面 ----------
                onBuild { _, inventory ->
                    // 记录物品栏引用，供退出/关服兜底保存使用
                    openSessions[player.uniqueId]?.inventory = inventory
                    val items = BackpackManager.deserializeContents(bp.content, slotCount)
                    for ((index, slot) in storageSlots.withIndex()) {
                        if (index < items.size) {
                            val item = items[index]
                            if (item != null) inventory.setItem(slot, item)
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

                    // 锁定格 / 顶部未定义的格：禁止一切交互（含拖拽）
                    if (rawSlot in lockedSlots || (rawSlot in 0 until totalSlots && rawSlot !in storageSlots)) {
                        event.isCancelled = true
                        return@onClick
                    }

                    // 无物品条件限制的背包：不做任何拦截
                    if (!hasCondition) return@onClick

                    val condition: (ItemStack?, ItemStack?) -> Boolean = { put, _ ->
                        put == null || put.type == Material.AIR
                                || type.condition.evaluate(put)
                    }
                    val deny: () -> Unit = { event.clicker.sendMessage(denyComponent) }

                    when {
                        // 点击 / 单格拖拽落在储物格：只对该格做条件判定
                        rawSlot in storageSlots ->
                            event.conditionSlot(rawSlot, condition, deny)

                        // 点击玩家背包区（Shift+点击移入）或任意拖拽：对全部储物格判定
                        rawSlot >= totalSlots || event.clickType == ClickType.DRAG ->
                            storageSlots.forEach { event.conditionSlot(it, condition, deny) }
                    }

                    // Shift+点击移入：基于已注册的条件执行受限移动（无 shift 点击时仅清理临时状态，幂等）
                    event.applyAmountConditionShiftIfNeeded()
                }

                // ---------- 关闭：保存到数据库 + 清理 ----------
                onClose {
                    val inv = it.inventory
                    saveBackpackContents(backpackId, type.id, slotCount, storageSlots, inv)

                    openSessions.remove(player.uniqueId)
                    openBackpacks[player.uniqueId]?.remove(backpackId)
                    BackpackManager.activeSessions.remove(backpackId)
                }
            }
        } catch (ex: Throwable) {
            // 打开流程异常（如数据库读写失败）：释放全部会话锁，避免背包被卡死
            openSessions.remove(uniqueId)
            openBackpacks[uniqueId]?.remove(backpackId)
            BackpackManager.activeSessions.remove(backpackId)
            player.sendMessage("§c背包打开失败：${ex.message ?: ex.javaClass.simpleName}")
            ex.printStackTrace()
        }
    }

    // ==================== 布局解析 ====================

    /**
     * 根据配置的 layout 或默认逻辑计算储物格和锁定格。
     * @return Triple<storageSlots, lockedSlots, effectiveSlotCount>
     * storageSlots 为有序列表（顺序即内容数组下标），保证保存/加载按位置一一对应。
     * 布局中仅标记为 'O' 的格子是储物格，其余顶部格子一律锁定，防止物品放进无法保存的格子而丢失。
     */
    private fun buildLayout(type: BackpackType, totalSlots: Int): Triple<List<Int>, Set<Int>, Int> {
        val parsed = type.parseLayout()
        if (parsed != null) {
            val allStorage = parsed.filterValues { it == 'O' }.keys.filter { it in 0 until totalSlots }
            val effectiveCount = allStorage.size.coerceAtMost(type.slots)
            // 超出容量的 'O' 视作锁定格，避免「能放入但无法保存」导致物品丢失
            val storageSlots = allStorage.take(effectiveCount)
            val lockedSlots = (0 until totalSlots).filterTo(mutableSetOf()) { it !in storageSlots }
            return Triple(storageSlots, lockedSlots, effectiveCount)
        }
        // 默认：前 N 格可用，其余锁定
        val slotCount = type.slots.coerceIn(1, totalSlots)
        return Triple((0 until slotCount).toList(), (slotCount until totalSlots).toSet(), slotCount)
    }

    // ==================== 保存 ====================

    /**
     * 按储物格物理槽位保存背包内容：
     * 内容数组下标 i 对应 storageSlots[i]，避免把锁定格/装饰格写入数据库。
     */
    private fun saveBackpackContents(
        backpackId: String,
        typeId: String,
        slotCount: Int,
        storageSlots: List<Int>,
        inv: Inventory,
    ) {
        val contents = Array(slotCount) { idx ->
            val slot = storageSlots.getOrNull(idx)
            if (slot != null && slot < inv.size) inv.getItem(slot) else null
        }
        BackpackManager.saveBackpack(backpackId, typeId, contents, slotCount)
    }

    // ==================== 生命周期 ====================

    /** 玩家退出时：先兜底保存再清理其所有活跃会话 */
    fun removePlayerSessions(playerUuid: UUID) {
        val session = openSessions.remove(playerUuid)
        if (session != null) {
            val inv = session.inventory
            if (inv != null) {
                saveBackpackContents(session.backpackId, session.typeId, session.slotCount, session.storageSlots, inv)
            }
        }
        openBackpacks.remove(playerUuid)
        BackpackManager.activeSessions.entries.removeIf { it.value == playerUuid }
    }

    /** 插件关闭时保存仍处于打开状态的背包 */
    fun saveAll() {
        for ((_, session) in openSessions) {
            val inv = session.inventory ?: continue
            saveBackpackContents(session.backpackId, session.typeId, session.slotCount, session.storageSlots, inv)
        }
        openSessions.clear()
        openBackpacks.clear()
        BackpackManager.activeSessions.clear()
    }
}
