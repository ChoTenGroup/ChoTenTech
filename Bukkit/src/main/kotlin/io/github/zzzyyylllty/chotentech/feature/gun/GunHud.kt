package io.github.zzzyyylllty.chotentech.feature.gun

import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.util.minimessage.legacyToMiniMessage
import io.github.zzzyyylllty.chotentech.util.minimessage.mmPlainUtil
import io.github.zzzyyylllty.chotentech.util.minimessage.mmUtil
import net.kyori.adventure.audience.Audience
import org.bukkit.entity.Player
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 枪械 HUD（ActionBar）。
 *
 * 只在手持枪械时显示，由 [GunStateManager] 的 1 tick 循环每 5 tick 调一次 [update]：
 * ```
 * OFN-12 | 7/8 | 膛内 0/1 | 热量 安全 | 耐久 92% | 模式 SEMI | 体力 82.4
 * ```
 *
 * P2 追加：
 * - 有读条动作时，**热量段**被替换成读条进度（`换弹中 ▋▋▋▋▋▋ 62%`），
 *   与 [GunAction] 每 tick 推的 ActionBar 文案完全同源，所以不会出现两种样式来回闪；
 * - 故障状态按 `jam` 显示 `卡壳` / `需排障`；
 * - 弹药显示固定为 `弹匣内发数 / 弹匣容量`；**`mag` 为空（没装弹匣）时直接显示「无弹匣」**，
 *   不再显示成 `0/0` 那种看起来像「空弹匣」的误导性数字。
 *
 * P3 追加：
 * - **耐久段**：`耐久 92%`（<50% 黄、<20% 红、0 直接显示「损坏」）；
 * - **热量四档颜色**：安全白 / 轻微黄 / 过热橙 / 严重红；
 * - 故障文案改为 `卡壳`（jam=1）/ `严重故障`（jam=2）；
 * - 强制停火期间显示 `强制停火 1.4s`（`forcedStopUntil` 是毫秒时间戳）。
 */
object GunHud {

    fun update(player: Player) {
        val snapshot = GunStateManager.heldGunSnapshot(player) ?: return
        val held = snapshot.item
        val gun = snapshot.gun
        val state = GunInstance.readEnsured(held, gun, AddonManager.effectiveAttributes(gun, held)) ?: return

        val magazineSize = state.magAmmo.size
        val hasMag = state.mag.isNotEmpty()
        val configuredCapacity = GunRegistry.mag(state.mag)?.capacity
        // 弹匣配置丢了（弹匣 id 还在、配置被删）时不要把容量回落到「当前弹数」：
        // 那会把 3 发显示成满弹 3/3，把「配置缺失」伪装成「弹匣是满的」。
        val capacity = configuredCapacity ?: magazineSize
        val capacityMissing = hasMag && (configuredCapacity == null || configuredCapacity <= 0)
        val chamber = if (state.chamber > 0) 1 else 0
        // 用内存镜像算档位：镜像里有「已冷却到但还没落盘」的进度，
        // 直接读 NBT 会让 HUD 落后一档（枪已经凉了，界面还显示危险）。
        val tier = GunSettings.heatTier(GunStateManager.currentHeat(player, gun, state))
        val stamina = player.getData()?.handStamina ?: 0.0
        val action = GunAction.snapshot(player)

        val name = gunName(player, gun)
        // 没有弹匣（`mag` 为空）时不显示 `0/0`，直接给「无弹匣」——比数字更好读
        // 每种颜色都用同名闭合标签，避免 MiniMessage 标签不匹配
        val ammoText = when {
            !hasMag -> "<#6E7A8C>无弹匣</#6E7A8C>"
            capacityMissing -> "<#DFBD7C>弹匣配置缺失</#DFBD7C>"
            capacity > 0 && magazineSize * 4 <= capacity -> "<#E08A98>$magazineSize/$capacity</#E08A98>"
            capacity > 0 && magazineSize * 2 <= capacity -> "<#DFBD7C>$magazineSize/$capacity</#DFBD7C>"
            else -> "<#EFDF8F>$magazineSize/$capacity</#EFDF8F>"
        }
        val jam = when {
            state.jam >= 2 -> " <#E08A98>严重故障"
            state.jam == 1 -> " <#E08A98>卡壳"
            else -> ""
        }
        val forcedStop = forcedStopText(GunStateManager.existing(player)?.forcedStopUntil ?: 0L)

        val text = buildString {
            append("<gold>").append(name).append("</gold> <gray>|</gray> ")
            append(ammoText).append(" <gray>|</gray> ")
            append("膛内 $chamber/1 <gray>|</gray> ")
            // 读条中：用进度替换热量段（省一行宽度，也避免玩家一边换弹一边盯热量）
            if (action != null) {
                append(GunAction.progressText(action.type, action.progress)).append(" <gray>|</gray> ")
            } else {
                append("热量 ").append(heatTierColored(tier)).append(" <gray>|</gray> ")
            }
            append("耐久 ").append(durabilityText(AddonManager.effectiveAttributes(gun, held).durability, state.durability)).append(" <gray>|</gray> ")
            append("模式 ").append(GunAction.fireModeName(gun, state.fireMode)).append(" <gray>|</gray> ")
            append("体力 ").append(fmt(stamina))
            append(jam)
            if (forcedStop != null) append(" <#E08A98>").append(forcedStop)
        }
        sendActionBar(player, text)
    }

    // ==================== 耐久 / 热量 / 强制停火 ====================

    /**
     * 耐久段：`92%` 上色（<50% 黄、<20% 红），归零显示「损坏」。
     *
     * 百分比按 `1%` 四舍五入（`92.6%` 显示 `93%`）；归零时不显示数字，
     * 直接是红色「损坏」，对应「耐久归零 = 只能近战」的设计。
     */
    private fun durabilityText(max: Double, durability: Double): String {
        if (durability <= 0.0) return "<#E08A98>损坏</#E08A98>"
        if (max <= 0.0) return "<#8FD4A6>100%</#8FD4A6>"
        val ratio = (durability / max).coerceIn(0.0, 1.0)
        val percent = (ratio * 100.0).roundToInt().coerceIn(0, 100)
        val color = when {
            percent < 20 -> "#E08A98"
            percent < 50 -> "#EFDF8F"
            else -> "#8FD4A6"
        }
        return "<$color>$percent%</$color>"
    }

    /** 热量四档配色：安全白 / 轻微黄 / 过热橙 / 严重红。 */
    private fun heatTierColored(tier: Int): String {
        val color = when (tier) {
            1 -> "#EFDF8F"
            2 -> "#DFBD7C"
            3 -> "#E08A98"
            else -> "#F2F5FA"
        }
        return "<$color>${GunSettings.heatTierText(tier)}</$color>"
    }

    /**
     * 强制停火剩余时间文本（没有强制停火返回 null）。
     *
     * [PlayerGunState.forcedStopUntil] 与 `System.currentTimeMillis()` 同基准（毫秒），
     * 所以这里直接做毫秒差，**不要**再按 tick 换算。
     */
    private fun forcedStopText(forcedStopUntil: Long): String? {
        if (forcedStopUntil <= 0L) return null
        val left = forcedStopUntil - System.currentTimeMillis()
        if (left <= 0L) return null
        return "强制停火 ${String.format(Locale.US, "%.1f", left / 1000.0)}s"
    }

    /**
     * 清掉 HUD 那一行（切离枪械 / 收枪 / 下线时调用）。
     *
     * 不这样做的话，`update` 只在手持枪械时写 ActionBar，切走后最后一帧会**一直挂在屏幕上**
     * （ActionBar 不会自己消失），玩家拿着一把剑还在看「耐久 92% | 体力 82」。
     */
    fun clear(player: Player) {
        try {
            player.sendActionBar(net.kyori.adventure.text.Component.empty())
        } catch (_: Throwable) {
        }
    }

    // ==================== 名称 ====================

    /**
     * 显示名优先取物品自身的 MiniMessage 名称（纯文本化后套 `<gold>`），
     * 取不到就用 Sertraline ID。
     *
     * **必须剥掉耐久后缀**：[GunNameplate] 会把 `[873/1000]` 写进物品名，
     * 不剥的话 HUD 就会显示成 `OFN-12手枪 [873/1000] | 7/8 | ... | 耐久 87%`，
     * 既有重复信息，又因为整段被 `<gold>` 包住而丢掉后缀原本的红黄绿配色。
     */
    private fun gunName(player: Player, gun: GunData): String {
        try {
            val meta = player.inventory.itemInMainHand.itemMeta
            if (meta != null) {
                val component = meta.displayName()
                if (component != null) {
                    val plain = GunNameplate.stripDurabilitySuffix(mmPlainUtil.serialize(component).trim())
                    if (plain.isNotEmpty()) return plain
                }
            }
        } catch (_: Throwable) {
        }
        return gun.id
    }

    // ==================== 文本 ====================

    private fun sendActionBar(player: Player, message: String) {
        try {
            (player as Audience).sendActionBar(mmUtil.deserialize(message.legacyToMiniMessage()))
        } catch (_: Exception) {
        }
    }

    private fun fmt(value: Double): String {
        val rounded = Math.round(value * 10.0) / 10.0
        return if (rounded == rounded.toLong().toDouble()) {
            rounded.toLong().toString()
        } else {
            String.format(Locale.US, "%.1f", rounded)
        }
    }
}
