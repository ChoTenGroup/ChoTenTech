package io.github.zzzyyylllty.chotentech.data

import io.github.zzzyyylllty.chotentech.util.toComponent
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import org.bukkit.entity.Player
import taboolib.common.platform.function.getDataFolder
import taboolib.module.configuration.Configuration
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 手术每次结算恢复的韧性。
 *
 * 配置既可以是绝对值 `10`，也可以是百分比 `"10%"`（按该部位**最大韧性**折算），
 * 两种写法共用同一份 yml，因此这里用字符串作为中间表示再解析。
 */
sealed interface ToughnessRestore {
    /** 固定数值。 */
    data class Flat(val value: Double) : ToughnessRestore
    /** 占该部位最大韧性的百分比（`percent` 已是 0..100 的百分数）。 */
    data class Percent(val percent: Double) : ToughnessRestore

    /** 折算成对某个部位的实际恢复量。 */
    fun amountFor(maxToughness: Double): Double = when (this) {
        is Flat -> value
        is Percent -> maxToughness * percent / 100.0
    }

    companion object {
        /**
         * 解析 `10` / `10.0` / `"10"` / `"10%"` / `10%`。
         * 无法解析时回落到 0，由调用方决定是否禁用该手术。
         */
        fun parse(raw: Any?): ToughnessRestore = when (raw) {
            is Number -> Flat(raw.toDouble())
            is String -> {
                val text = raw.trim()
                if (text.endsWith("%")) {
                    Percent(text.dropLast(1).trim().toDoubleOrNull() ?: 0.0)
                } else Flat(text.toDoubleOrNull() ?: 0.0)
            }
            else -> Flat(0.0)
        }
    }
}

data class SurgeryConfig(
    /** 每次有效治疗的固定耐久消耗。 */
    val durabilityCost: Double,
    val costFood: Double,
    val costSan: Double,
    val costWater: Double,
    val preTime: Double,
    val perTime: Double,
    val postTime: Double,
    /** `per` 阶段开始后是否还能取消。 */
    val cancellable: Boolean,
    /** `per` 阶段是否允许移动。 */
    val moveable: Boolean,
    /** `per` 阶段是否允许跳跃。 */
    val jumpable: Boolean,
    val toughness: ToughnessRestore,
    /** 移除对应部位 debuff 所需的独立耐久。 */
    val removeDebuff: Map<String, Double>,
    val soundPre: HealSound = HealSound.DEFAULT_PRE,
    val soundStart: HealSound = HealSound.DEFAULT_START,
    val soundHeal: HealSound = HealSound.DEFAULT_HEAL,
    val soundComplete: HealSound = HealSound.DEFAULT_COMPLETE,
    val soundCancel: HealSound = HealSound.DEFAULT_CANCEL,
    val soundExhaust: HealSound = HealSound.DEFAULT_EXHAUST,
    val soundEnd: HealSound = HealSound.DEFAULT_END
) {
    /** 该配置是否具备可执行的手术内容。 */
    val operable: Boolean get() = toughness.amountFor(1.0) > 0 || removeDebuff.isNotEmpty()
}

private val surgeryConfigs = ConcurrentHashMap<String, SurgeryConfig>()

/** 手术配置表（只读视图，供指令与热重载检查使用）。 */
val surgeryConfigIds: Set<String> get() = surgeryConfigs.keys

fun loadSurgeryConfig() {
    surgeryConfigs.clear()
    val file = File(getDataFolder(), "medkit/surgery.yml")
    if (!file.exists()) return
    val config = Configuration.loadFromFile(file)
    for (key in config.getKeys(false)) {
        val sec = config.getConfigurationSection(key) ?: continue
        val removals = sec.getConfigurationSection("removedebuff")
        surgeryConfigs[key] = SurgeryConfig(
            durabilityCost = sec.getDouble("durabilitycost", 1.0).coerceAtLeast(0.0),
            costFood = sec.getDouble("cost.food", 0.0).coerceAtLeast(0.0),
            costSan = sec.getDouble("cost.san", 0.0).coerceAtLeast(0.0),
            costWater = sec.getDouble("cost.water", 0.0).coerceAtLeast(0.0),
            // 手术至少要有一次结算，否则 pre 之后永远不会进入 per。
            preTime = sec.getDouble("time.pre", 3.0).coerceAtLeast(0.0),
            perTime = sec.getDouble("time.per", 10.0).coerceAtLeast(0.05),
            postTime = sec.getDouble("time.post", 1.5).coerceAtLeast(0.0),
            cancellable = sec.getBoolean("cancellable", false),
            moveable = sec.getBoolean("moveable", false),
            jumpable = sec.getBoolean("jumpable", false),
            toughness = ToughnessRestore.parse(sec.get("toughness") ?: 0),
            removeDebuff = removals?.getKeys(false)
                ?.associateWith { removals.getDouble(it).coerceAtLeast(0.0) } ?: emptyMap()
        )
    }
}

/**
 * 物品当前耐久已知时取配置；与 [getHealConfigFromYaml] 一样按物品 ID 查表，
 * 返回 null 表示「这不是手术刀」。
 */
fun getSurgeryConfig(sertralineId: String): SurgeryConfig? = surgeryConfigs[sertralineId]

/** 手术台/手术刀的通用提示音，复用医疗包音效的默认值。 */
fun HealSound.playFor(player: Player) {
    player.playSound(Sound.sound(Key.key(key), source, volume, pitch))
}

/** 手术类提示文本使用同一套 MiniMessage 颜色。 */
internal fun surgeryBar(message: String) = "<#EFDF8F>$message".toComponent()
