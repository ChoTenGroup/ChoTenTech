package io.github.zzzyyylllty.chotentech.shop

import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class FilterRequirement(val displayName: String) { ALL("全部"), MET("满足条件"), NOT_MET("未满足条件") }
enum class FilterInventory(val displayName: String) { ALL("全部"), HAVE("拥有"), NOT_HAVE("未拥有") }
enum class FilterAffordability(val displayName: String) { ALL("全部"), CAN_BUY("可购买"), CANNOT_BUY("不足") }

class PlayerShopSession(val player: Player) {

    var currentShopId: String = ""
    var isBuyMode: Boolean = true
    var currentPage: Int = 0

    var filterReq: FilterRequirement = FilterRequirement.ALL
    var filterInv: FilterInventory = FilterInventory.ALL
    var filterAfford: FilterAffordability = FilterAffordability.ALL

    /** Maps itemKey -> quantity selected */
    val selectedItems = mutableMapOf<String, Long>()

    fun addItem(itemKey: String, amount: Long = 1) {
        selectedItems.merge(itemKey, amount, Long::plus)
    }

    fun removeItem(itemKey: String, amount: Long = 1) {
        val current = selectedItems[itemKey] ?: return
        val newVal = current - amount
        if (newVal <= 0) {
            selectedItems.remove(itemKey)
        } else {
            selectedItems[itemKey] = newVal
        }
    }

    fun deselectItem(itemKey: String) {
        selectedItems.remove(itemKey)
    }

    fun isSelected(itemKey: String): Boolean = selectedItems.containsKey(itemKey)

    fun getQuantity(itemKey: String): Long = selectedItems[itemKey] ?: 0L

    fun clearSelection() {
        selectedItems.clear()
    }

    fun getSelectedCount(): Int = selectedItems.size

    companion object {
        private val sessions = ConcurrentHashMap<UUID, PlayerShopSession>()

        fun get(player: Player): PlayerShopSession {
            return sessions.computeIfAbsent(player.uniqueId) { PlayerShopSession(player) }
        }

        fun remove(player: Player) {
            sessions.remove(player.uniqueId)
        }
    }
}
