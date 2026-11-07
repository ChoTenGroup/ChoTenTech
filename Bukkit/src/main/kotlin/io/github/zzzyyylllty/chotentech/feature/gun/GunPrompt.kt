package io.github.zzzyyylllty.chotentech.feature.gun

import io.github.zzzyyylllty.chotentech.util.minimessage.legacyToMiniMessage
import io.github.zzzyyylllty.chotentech.util.minimessage.mmUtil
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import org.bukkit.entity.Player
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 枪械系统的**临时提示**统一出口（副标题）。
 *
 * ### 为什么不是 ActionBar
 * ActionBar 那一行已经被 [GunHud] 当成常驻信息栏占用了
 * （`名称 | 弹药 | 膛内 | 热量 | 耐久 | 模式 | 体力`）。
 * 如果「手部体力不足，无法出枪」这类一次性提示也写 ActionBar，
 * 就会把那一排信息顶掉、下一次 HUD 刷新（5 tick 后）又被顶回来，闪得很难看。
 *
 * 所以约定：
 * - **常驻状态** → ActionBar，只有 [GunHud] 一个写入者；
 * - **一次性提示 / 报错 / 读条反馈** → 副标题（主标题留空），走这里。
 *
 * ### 节流
 * 同一玩家 400ms 内只显示一条副标题：连点切枪、连续扣扳机空仓时不会把屏幕刷成白噪。
 */
object GunPrompt {

    /** 副标题停留时间（毫秒）。 */
    private const val STAY_MS = 1200L

    /** 淡入 / 淡出（毫秒）。 */
    private const val FADE_IN_MS = 100L
    private const val FADE_OUT_MS = 200L

    /** 同一玩家的最小提示间隔（毫秒）。 */
    private const val THROTTLE_MS = 400L

    /** uuid → 上次提示时间戳。 */
    private val lastShown = ConcurrentHashMap<UUID, Long>()

    /**
     * 显示一条副标题提示。
     *
     * @param force true = 跳过节流（用于「操作完成」这类必须让人看到的反馈）
     */
    fun show(player: Player, message: String, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force) {
            val last = lastShown[player.uniqueId]
            if (last != null && now - last < THROTTLE_MS) return
        }
        lastShown[player.uniqueId] = now

        try {
            val subtitle = mmUtil.deserialize(message.legacyToMiniMessage())
            player.showTitle(
                Title.title(
                    Component.empty(),
                    subtitle,
                    Title.Times.times(
                        Duration.ofMillis(FADE_IN_MS),
                        Duration.ofMillis(STAY_MS),
                        Duration.ofMillis(FADE_OUT_MS),
                    ),
                ),
            )
        } catch (_: Exception) {
        }
    }

    /** 清理玩家提示缓存（下线时调用）。 */
    fun forget(player: Player) {
        lastShown.remove(player.uniqueId)
    }

    /** 清空全部缓存（关服时调用）。 */
    fun clearAll() {
        lastShown.clear()
    }
}
