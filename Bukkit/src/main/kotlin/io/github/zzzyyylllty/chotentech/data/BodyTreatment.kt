package io.github.zzzyyylllty.chotentech.data

/** Pure treatment transaction, shared by the runtime and regression tests. */
object BodyTreatment {
    data class Result(val durability: Double, val treated: Double, val hpHealed: Double,
                      val changed: Boolean, val blocked: Boolean = false) {
        /**
         * [treated] 是本次**已占用**的 `maxheal` / `healspeed` 额度，血量部分已经含在里面；
         * 这里把韧性那一段单独拆出来以便阅读，不要用它再去做额度累加（会重复计算血量）。
         */
        val toughnessHealed: Double get() = treated - hpHealed
    }

    fun apply(body: PlayerBodyHealth, partId: String?, healHealth: Boolean, hpMissing: Double,
              durability: Double, alreadyTreated: Double, maxHeal: Double, healSpeed: Double,
              healRate: Double, hpLeast: Double, removeDebuff: Map<String, Double>): Result {
        val part = partId?.let { body.parts[it] }
        if (partId != null && (part == null || part.destroyed)) return Result(durability, 0.0, 0.0, false, true)
        var dur = durability.coerceAtLeast(0.0)
        var changed = false
        for ((type, cost) in removeDebuff) {
            if (part?.effects?.containsKey(type) != true) continue
            if (dur + 1e-9 < cost) return Result(dur, 0.0, 0.0, changed, true)
            dur = (dur - cost).coerceAtLeast(0.0)
            part.effects.remove(type)
            if (type == MAJOR_BLEED) body.applyEffect(part.id, WOUND, durationMs = 240_000)
            changed = true
        }
        var budget = minOf(healSpeed, (maxHeal - alreadyTreated).coerceAtLeast(0.0)).coerceAtLeast(0.0)
        var treated = 0.0
        fun take(missing: Double): Double {
            val amount = minOf(missing.coerceAtLeast(0.0), budget,
                if (healRate > 0) dur / healRate else Double.MAX_VALUE)
            dur = (dur - amount * healRate).coerceAtLeast(0.0)
            budget -= amount
            treated += amount
            if (amount > 0) changed = true
            return amount
        }
        if (part != null) part.heal(take(part.maxToughness - part.toughness))
        val hp = if (healHealth && hpLeast >= 0 && body.toughnessPercentage > hpLeast &&
            (part == null || part.percentage >= 1)) take(hpMissing) else 0.0
        return Result(dur, treated, hp, changed)
    }
}
