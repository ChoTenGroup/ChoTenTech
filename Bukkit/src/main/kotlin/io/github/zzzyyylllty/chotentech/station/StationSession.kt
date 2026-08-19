package io.github.zzzyyylllty.chotentech.station

import org.bukkit.entity.Player
import taboolib.common.platform.service.PlatformExecutor
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class StationView { RECIPES, QUEUE }

/** 条件筛选:可制作 = 材料+货币全部满足 */
enum class StationFilterReq(val displayName: String) { ALL("全部"), MET("可制作"), NOT_MET("材料不足") }

/** 物品筛选:材料充足 = 仅看材料物品 */
enum class StationFilterItem(val displayName: String) { ALL("全部"), HAVE("材料充足"), NOT_HAVE("材料不足") }

class StationSession(val player: Player) {

    var stationId: String = ""
    var view: StationView = StationView.RECIPES
    var currentPage: Int = 0

    var filterReq: StationFilterReq = StationFilterReq.ALL
    var filterItem: StationFilterItem = StationFilterItem.ALL

    /** 打开期间每 20 tick 就地刷新显示区域的实时任务 */
    var refreshTask: PlatformExecutor.PlatformTask? = null

    /** 当前是否打开了该合成站的菜单(用于刷新任务守卫,避免误刷其它界面) */
    var menuOpen: Boolean = false

    companion object {
        private val sessions = ConcurrentHashMap<UUID, StationSession>()

        fun get(player: Player): StationSession {
            return sessions.computeIfAbsent(player.uniqueId) { StationSession(player) }
        }

        /** 显式注册(重开/模式切换后会话被 remove 过, 需重新放回) */
        fun put(player: Player, session: StationSession) {
            sessions[player.uniqueId] = session
        }

        fun remove(player: Player) {
            sessions.remove(player.uniqueId)?.refreshTask?.cancel()
        }
    }
}
