package io.github.zzzyyylllty.chotentech.data

import kotlin.math.min

/**
 * 手术单轮结算的纯逻辑，与运行时（耐久 NBT、饱食/精神/水分、音效、Dialog）解耦，
 * 便于用单元测试固定「损毁部位可被恢复」「debuff 不抢占韧性额度」等关键语义。
 *
 * 每轮顺序固定为：
 * ① 移除该部位可移除的 debuff（各消耗自己的独立耐久）；
 * ② 恢复韧性（`DESTROYED` 部位直接赋值，这是手术与常规治疗的唯一区别）；
 * ③ 本轮确有工作时，扣一次 [durabilityCost]。
 */
object SurgeryTreatment {

    enum class Stop {
        /** 还能继续下一轮。 */
        NONE,
        /** 该部位韧性已满。 */
        PART_FULL,
        /** 耐久耗尽。 */
        NO_DURABILITY,
        /** 配置要求移除的 debuff 负担不起：按规矩中止，不绕过它去修韧性。 */
        UNAFFORDABLE_DEBUFF,
        /** 该部位没有任何可执行内容。 */
        NOTHING
    }

    data class Result(
        val durability: Double,
        val restored: Double,
        val removed: List<String>,
        val worked: Boolean,
        val stop: Stop
    ) {
        val finished: Boolean get() = stop != Stop.NONE
    }

    fun apply(
        body: PlayerBodyHealth,
        partId: String,
        durability: Double,
        durabilityCost: Double,
        toughness: ToughnessRestore,
        removeDebuff: Map<String, Double>
    ): Result {
        val part = body.parts[partId] ?: return Result(durability, 0.0, emptyList(), false, Stop.NOTHING)
        var dur = durability.coerceAtLeast(0.0)
        if (dur <= 0) return Result(0.0, 0.0, emptyList(), false, Stop.NO_DURABILITY)

        val removed = mutableListOf<String>()

        // ① debuff：独立耐久，不占用 durabilitycost。
        for ((type, cost) in removeDebuff) {
            if (!part.effects.containsKey(type)) continue
            if (dur + 1e-9 < cost) {
                // 已经扣掉的 debuff 保留（它们是真的移除了），但不再继续修韧性。
                return Result(dur, 0.0, removed, removed.isNotEmpty(), Stop.UNAFFORDABLE_DEBUFF)
            }
            dur -= cost
            part.effects.remove(type)
            if (type == MAJOR_BLEED) body.applyEffect(part.id, WOUND, durationMs = 240_000)
            removed += type
        }

        // ② 韧性：损毁部位直接赋值，BodyPart.heal() 会拒绝 destroyed，不能走它。
        var restored = 0.0
        if (part.percentage < 1) {
            val missing = part.maxToughness - part.toughness
            val amount = min(toughness.amountFor(part.maxToughness), missing).coerceAtLeast(0.0)
            if (amount > 0) {
                part.toughness = (part.toughness + amount).coerceAtMost(part.maxToughness)
                restored = amount
            }
        }

        val worked = removed.isNotEmpty() || restored > 0
        if (!worked) {
            return Result(dur, 0.0, removed, false, Stop.NOTHING)
        }

        // ③ 每轮有效治疗统一扣一次固定耐久。
        dur = (dur - durabilityCost).coerceAtLeast(0.0)
        val stop = when {
            dur <= 1e-9 -> Stop.NO_DURABILITY
            part.percentage >= 1 -> Stop.PART_FULL
            else -> Stop.NONE
        }
        return Result(dur, restored, removed, true, stop)
    }
}
