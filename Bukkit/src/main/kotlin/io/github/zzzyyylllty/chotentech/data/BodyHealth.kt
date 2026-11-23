package io.github.zzzyyylllty.chotentech.data

import kotlin.math.min

/** IDs and states deliberately remain strings so other body implementations can add parts. */
object BodyParts {
    const val HEAD = "HEAD"
    const val BODY = "BODY"
    const val STOMACH = "STOMACH"
    const val LARM = "LARM"
    const val RARM = "RARM"
    const val LLEG = "LLEG"
    const val RLEG = "RLEG"
    val defaults = linkedMapOf(HEAD to 45.0, BODY to 75.0, STOMACH to 40.0,
        LARM to 50.0, RARM to 50.0, LLEG to 50.0, RLEG to 50.0)
    val names = mapOf(HEAD to "头部", BODY to "身体", STOMACH to "胃部",
        LARM to "左臂", RARM to "右臂", LLEG to "左腿", RLEG to "右腿")
    val limbs = setOf(LARM, RARM, LLEG, RLEG)
    val multipliers = mapOf(HEAD to 2.0, STOMACH to 1.4, BODY to 1.0,
        LARM to 1.0, RARM to 1.0, LLEG to 0.6, RLEG to 0.6)
    val priorities = mapOf(HEAD to 2.0, BODY to 1.5, STOMACH to 1.2)
}

data class BodyPart(
    val id: String,
    var toughness: Double,
    var maxToughness: Double,
    val effects: MutableMap<String, PlayerStatusEffect> = linkedMapOf(),
    var destructionPenalty: Double = 0.0,
    var penaltyTicks: Int = 0,
    var wasDestroyed: Boolean = false
) {
    val percentage: Double get() = if (maxToughness > 0) (toughness / maxToughness).coerceIn(0.0, 1.0) else 0.0
    val state: String get() = when {
        toughness <= 0 -> "DESTROYED"
        toughness >= maxToughness -> "NORMAL"
        else -> "DAMAGED"
    }
    val destroyed: Boolean get() = toughness <= 0.0
    fun resize(max: Double) {
        val ratio = percentage
        maxToughness = max.coerceAtLeast(0.001)
        toughness = ratio * maxToughness
    }
    fun heal(amount: Double): Double {
        if (destroyed || !amount.isFinite() || amount <= 0) return 0.0
        val healed = min(amount, maxToughness - toughness)
        toughness += healed
        return healed
    }

    fun tickDestructionPenalty(elapsedTicks: Int) {
        if (id != "HEAD" && id != "BODY") return
        if (destroyed && !wasDestroyed) {
            destructionPenalty = (destructionPenalty + .10).coerceAtMost(.30)
            penaltyTicks = 0
        } else if (!destroyed && wasDestroyed) {
            destructionPenalty = (destructionPenalty - if (id == "HEAD") .15 else .10).coerceAtLeast(0.0)
            penaltyTicks = 0
        }
        wasDestroyed = destroyed
        penaltyTicks += elapsedTicks
        val interval = if (destroyed) { if (id == "HEAD") 100 else 400 }
            else { if (id == "HEAD") 80 else 200 }
        while (penaltyTicks >= interval) {
            penaltyTicks -= interval
            destructionPenalty = (destructionPenalty + if (destroyed) .01 else -.01).coerceIn(0.0, .30)
        }
    }
}

interface BodyHealth {
    val parts: Map<String, BodyPart>
    val toughness: Double get() = parts.values.sumOf { it.toughness }
    val maxToughness: Double get() = parts.values.sumOf { it.maxToughness }
    /** Percentage points, 0..100, weighted by each part's maximum. */
    val toughnessPercentage: Double get() = if (maxToughness > 0) toughness / maxToughness * 100 else 0.0
    fun damage(partId: String, amount: Double): Double
}

class PlayerBodyHealth : BodyHealth {
    override var parts: MutableMap<String, BodyPart> = linkedMapOf()
    var water: Double = 100.0
    var maxWater: Double = 100.0
    var dehydrationTicks: Int = 0
    var filterValue: Double = 0.0
    var filterTicks: Int = 0
    var filterDelay: Int = 0
    var onlineTicks: Long = 0
    // Persisted remaining ticks, never wall-clock timestamps: offline time does not advance them.
    var downPhase: String? = null
    var downTicks: Int = 0

    init { normalize() }

    fun normalize(): PlayerBodyHealth {
        if (parts == null) parts = linkedMapOf()
        BodyParts.defaults.forEach { (id, max) -> parts.putIfAbsent(id, BodyPart(id, max, max)) }
        return this
    }

    /** Returns HP damage using the PRE-HIT ratio; overflow only damages toughness. */
    override fun damage(partId: String, amount: Double): Double {
        if (!amount.isFinite() || amount <= 0) return 0.0
        val part = parts[partId.uppercase()] ?: return amount
        val hp = amount * (1.0 - min(part.percentage, 0.75))
        damageToughness(part.id, amount)
        return hp
    }

    /** Each wave is distributed simultaneously; newly destroyed parts propagate their overflow. */
    fun damageToughness(partId: String, amount: Double) {
        if (!amount.isFinite() || amount <= 0) return
        var wave = mapOf(partId.uppercase() to amount)
        repeat(parts.size + 1) {
            var overflow = 0.0
            for ((id, value) in wave) {
                val part = parts[id] ?: continue
                val taken = min(part.toughness, value)
                part.toughness = (part.toughness - taken).coerceAtLeast(0.0)
                val excess = value - taken
                if (excess > 0) overflow += excess * (BodyParts.multipliers[id] ?: 1.0)
            }
            val alive = parts.values.filter { !it.destroyed }
            if (overflow <= 0 || alive.isEmpty()) return
            wave = alive.associate { it.id to overflow / alive.size }
        }
    }

    fun quickTreatmentPart(): BodyPart? = parts.values.filter { !it.destroyed && it.percentage < 1 }
        .maxByOrNull { (1.0 - it.percentage) * (BodyParts.priorities[it.id] ?: 1.0) }

    fun applyEffect(partId: String, type: String, level: Int = 1, durationMs: Long = Long.MAX_VALUE): Boolean {
        val part = parts[partId.uppercase()] ?: return false
        if (level <= 0 || durationMs <= 0) return false
        if (type == FRACTURE && part.id !in BodyParts.limbs) return false
        if (type == PENETRATION && part.id !in setOf("HEAD", "BODY", "STOMACH")) return false
        if (type in HEAD_ONLY_EFFECTS && part.id != "HEAD") return false
        if (type in setOf(MINOR_BLEED, MAJOR_BLEED, WOUND)) {
            if (type == MINOR_BLEED && part.effects.containsKey(MAJOR_BLEED)) return false
            part.effects.keys.removeAll(setOf(MINOR_BLEED, MAJOR_BLEED, WOUND) - type)
        }
        val old = part.effects[type]
        part.effects[type] = PlayerStatusEffect(type, maxOf(old?.level ?: 0, level),
            maxOf(old?.remainingDuration ?: 0, durationMs))
        return true
    }

    fun hasEffect(type: String): Boolean = parts.values.any { it.effects.containsKey(type) }
    fun removeEffect(partId: String, type: String): Boolean {
        val part = parts[partId.uppercase()] ?: return false
        return part.effects.remove(type) != null
    }
    fun bandage(partId: String): Boolean {
        val part = parts[partId.uppercase()] ?: return false
        if (part.destroyed) return false
        if (removeEffect(part.id, MAJOR_BLEED)) {
            applyEffect(part.id, WOUND, durationMs = 240_000)
            return true
        }
        return removeEffect(part.id, MINOR_BLEED)
    }
    fun armPenalties(painAim: Double = .10, painAccuracy: Double = .10): ArmPenalties {
        var aim = 0.0; var accuracy = 0.0; var search = 0.0; var melee = 0.0
        for (id in listOf("LARM", "RARM")) {
            val part = parts.getValue(id)
            val fracture = part.effects.containsKey(FRACTURE) && !hasEffect(PAIN_RELIEF)
            val destroyed = part.destroyed
            val row = when {
                fracture && destroyed -> doubleArrayOf(.25, .25, .40, .25)
                destroyed -> doubleArrayOf(.20, .15, .25, .20)
                fracture -> doubleArrayOf(.10, .10, .25, .15)
                else -> doubleArrayOf(0.0, 0.0, 0.0, 0.0)
            }
            aim += row[0]; accuracy += row[1]; search += row[2]
            if (part.effects.containsKey(PAIN) && !hasEffect(PAIN_RELIEF)) {
                aim += painAim
                accuracy += painAccuracy
            }
            if (id == "RARM") melee = row[3]
        }
        return ArmPenalties(aim, accuracy, search, melee)
    }
}

data class ArmPenalties(val aim: Double, val accuracy: Double, val search: Double, val melee: Double)
