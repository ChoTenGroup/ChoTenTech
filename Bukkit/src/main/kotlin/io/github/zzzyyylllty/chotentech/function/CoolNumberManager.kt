package io.github.zzzyyylllty.chotentech.function

import io.github.zzzyyylllty.chotentech.data.CoolNumberCategory
import io.github.zzzyyylllty.chotentech.data.CoolNumberInfo
import io.github.zzzyyylllty.chotentech.data.CoolNumberTier
import io.github.zzzyyylllty.chotentech.data.PlayerData
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.util.CurrencyUtil
import kotlin.math.pow
import kotlin.math.roundToInt
import org.bukkit.entity.Player

object CoolNumberManager {

    val bannedNumbers: Set<String> = setOf("1952")

    private var takenCids: Set<String> = emptySet()
    private var lastRefresh: Long = 0
    private val cacheTtlMs = 60_000L

    fun refreshTakenCids(allCids: Set<String>) {
        takenCids = allCids
        lastRefresh = System.currentTimeMillis()
    }

    fun isCacheStale(): Boolean = System.currentTimeMillis() - lastRefresh > cacheTtlMs

    fun getTakenCids(): Set<String> = takenCids

    fun isTaken(number: String): Boolean = number in takenCids

    // ── Scoring ──────────────────────────────────────────────

    fun calculateScore(number: String): Int {
        val digits = number.length
        var score = (10 - digits) * 10

        score += consecutiveScore(number)
        score += ababScore(number)
        score += aabbScore(number)
        score += romanceScore(number)
        score += luckyDigitScore(number)

        return score
    }

    // 连号评分
    private fun consecutiveScore(num: String): Int {
        // build run-length info
        val runs = mutableListOf<Pair<Int, Int>>() // (digit, length)
        var i = 0
        while (i < num.length) {
            var j = i
            while (j < num.length && num[j] == num[i]) j++
            val len = j - i
            if (len >= 3) {
                runs.add(num[i].digitToInt() to len)
            }
            i = j
        }
        if (runs.isEmpty()) return 0

        // use the longest run
        val (digit, runLen) = runs.maxBy { it.second }
        val total = num.length
        val nonConsecutive = total - runLen // 缺少的位数

        if (nonConsecutive == 0) return 20 // 全部连号

        val ratio = total.toDouble() / nonConsecutive

        val multiplier = when (digit) {
            7 -> 20
            0 -> 20
            6, 9 -> 30
            8 -> 20 // follows example: 88888812 -> (8/2)*20 = 80
            else -> 10
        }

        return (ratio * multiplier).roundToInt()
    }

    // ABAB / ABABAB
    private fun ababScore(num: String): Int {
        val positions = mutableListOf<Int>()
        var i = 0
        while (i <= num.length - 4) {
            if (num[i] == num[i + 2] && num[i + 1] == num[i + 3] && num[i] != num[i + 1]) {
                positions.add(i)
                i += 4
            } else {
                i++
            }
        }
        return when {
            positions.size >= 2 || num.length >= 6 && hasABABAB(num) -> 66
            positions.size == 1 -> 16
            else -> 0
        }
    }

    private fun hasABABAB(num: String): Boolean {
        for (i in 0..num.length - 6) {
            if (num[i] == num[i + 2] && num[i] == num[i + 4] &&
                num[i + 1] == num[i + 3] && num[i + 1] == num[i + 5] &&
                num[i] != num[i + 1]
            ) return true
        }
        return false
    }

    // AABB / AABBCC / AABBCCDD / AABBCCDDEE
    private fun aabbScore(num: String): Int {
        val pairs = mutableListOf<Int>()
        var i = 0
        while (i <= num.length - 4) {
            if (num[i] == num[i + 1] && num[i + 2] == num[i + 3] && num[i] != num[i + 2]) {
                pairs.add(i)
                i += 4
            } else {
                i++
            }
        }
        return when {
            pairs.size >= 4 -> 166 // AABBCCDDEE
            pairs.size == 3 -> 106 // AABBCCDD
            pairs.size == 2 -> 66  // AABBCC
            pairs.size == 1 -> 16  // AABB
            else -> 0
        }
    }

    // 爱情号 520 / 1314 / 521
    private fun romanceScore(num: String): Int {
        val has520 = num.contains("520")
        val has1314 = num.contains("1314")
        val has521 = num.contains("521")

        return when {
            has520 && has1314 -> 152 // combined, not 50+20
            has1314 -> 50
            has520 || has521 -> 20
            else -> 0
        }
    }

    // 单数字加分
    private fun luckyDigitScore(num: String): Int {
        var score = 0
        for (ch in num) {
            when (ch) {
                '7' -> score += 1
                '6', '9' -> score += 3
                '8' -> score += 5
            }
        }
        return score
    }

    // ── Tier ─────────────────────────────────────────────────

    fun getTierFromScore(score: Int): CoolNumberTier = CoolNumberTier.fromScore(score)

    fun getPlayerCurrentTier(player: Player): CoolNumberTier {
        val data = player.getData() ?: return CoolNumberTier.NORMAL
        return CoolNumberTier.fromTag(data.cid.type)
    }

    /**
     * When purchasing, the resulting tier = max(currentTier, scoreTier)
     */
    fun getResultingTier(currentTier: CoolNumberTier, newScore: Int, upgradeLevels: Int = 0): CoolNumberTier {
        val scoreTier = CoolNumberTier.fromScore(newScore)
        // apply tape upgrades
        val upgradedTier = if (upgradeLevels > 0) {
            val idx = CoolNumberTier.entries.indexOf(scoreTier)
            val target = (idx + upgradeLevels).coerceAtMost(CoolNumberTier.entries.lastIndex)
            CoolNumberTier.entries[target]
        } else scoreTier

        return if (currentTier.ordinal > upgradedTier.ordinal) currentTier else upgradedTier
    }

    // ── Pricing ──────────────────────────────────────────────

    fun calculatePointPrice(score: Int): Int {
        val price = score.toDouble().pow(1.2) * 10 + 1000
        return price.roundToInt()
    }

    fun calculateTapePrice(score: Int): Int {
        val price = score.toDouble().pow(1.1) * 10
        return price.roundToInt().coerceAtLeast(0)
    }

    /** 升级磁带费用（固定）：+1888 磁带升一级 */
    const val TAPE_UPGRADE_COST = 1888

    /** 贸易点购买上限 */
    const val MAX_POINT_PRICE = 25600

    fun canAffordPoint(player: Player, price: Int): Boolean {
        return CurrencyUtil.hasBalance(player, "point", price.toDouble()) && price <= MAX_POINT_PRICE
    }

    fun canAffordTape(player: Player, price: Int): Boolean {
        return CurrencyUtil.hasBalance(player, "tape", price.toDouble())
    }

    fun deductPoint(player: Player, amount: Int): Boolean {
        return CurrencyUtil.remove(player, "point", amount.toDouble())
    }

    fun deductTape(player: Player, amount: Int): Boolean {
        return CurrencyUtil.remove(player, "tape", amount.toDouble())
    }

    // ── Number Generation ────────────────────────────────────

    fun generateNumbers(
        digitCount: Int,
        category: CoolNumberCategory?,
        page: Int,
        pageSize: Int = 21
    ): List<String> {
        val results = mutableSetOf<String>()
        val seed = page * pageSize

        when (category) {
            CoolNumberCategory.AAA -> fillAAANumbers(digitCount, results, seed)
            CoolNumberCategory.AAAA -> fillAAAANumbers(digitCount, results, seed)
            CoolNumberCategory.AABB -> fillAABBNumbers(digitCount, results, seed)
            CoolNumberCategory.AABBCC -> fillAABBCCNumbers(digitCount, results, seed)
            CoolNumberCategory.LOVE -> fillLoveNumbers(digitCount, results, seed)
            CoolNumberCategory.CONSECUTIVE -> fillConsecutiveNumbers(digitCount, results, seed)
            CoolNumberCategory.ALL_SAME -> fillAllSameNumbers(digitCount, results, seed)
            null -> fillRandomNumbers(digitCount, results, seed)
        }

        return results
            .filter { it !in takenCids && !bannedNumbers.any { b -> it.contains(b) } }
            .filter { it.length == digitCount }
            .toList()
    }

    // ── AAA ──
    private fun fillAAANumbers(digits: Int, pool: MutableSet<String>, seed: Int) {
        val positions = 0..(digits - 3)
        var offset = 0
        for (d in 0..9) {
            for (pos in positions) {
                val sb = StringBuilder()
                for (i in 0 until digits) {
                    if (i in pos until pos + 3) sb.append(d)
                    else sb.append(((i + d + offset + seed) % 10))
                }
                pool.add(sb.toString())
                offset++
            }
        }
    }

    // ── AAAA ──
    private fun fillAAAANumbers(digits: Int, pool: MutableSet<String>, seed: Int) {
        val positions = 0..(digits - 4)
        var offset = 0
        for (d in 0..9) {
            for (pos in positions) {
                val sb = StringBuilder()
                for (i in 0 until digits) {
                    if (i in pos until pos + 4) sb.append(d)
                    else sb.append(((i * 3 + d * 7 + offset + seed) % 10))
                }
                pool.add(sb.toString())
                offset++
            }
        }
    }

    // ── AABB ──
    private fun fillAABBNumbers(digits: Int, pool: MutableSet<String>, seed: Int) {
        val positions = 0..(digits - 4)
        var offset = 0
        for (a in 0..9) {
            for (b in 0..9) {
                if (a == b) continue
                for (pos in positions) {
                    val sb = StringBuilder()
                    for (i in 0 until digits) {
                        when (i) {
                            pos, pos + 1 -> sb.append(a)
                            pos + 2, pos + 3 -> sb.append(b)
                            else -> sb.append(((i + a + b + offset + seed) % 10))
                        }
                    }
                    pool.add(sb.toString())
                    offset++
                }
            }
        }
    }

    // ── AABBCC ──
    private fun fillAABBCCNumbers(digits: Int, pool: MutableSet<String>, seed: Int) {
        val positions = 0..(digits - 6)
        var offset = 0
        for (a in 0..9) {
            for (b in 0..9) {
                if (a == b) continue
                for (c in 0..9) {
                    if (c == a || c == b) continue
                    for (pos in positions) {
                        val sb = StringBuilder()
                        for (i in 0 until digits) {
                            when (i) {
                                pos, pos + 1 -> sb.append(a)
                                pos + 2, pos + 3 -> sb.append(b)
                                pos + 4, pos + 5 -> sb.append(c)
                                else -> sb.append(((i * 7 + offset + seed) % 10))
                            }
                        }
                        pool.add(sb.toString())
                        offset++
                    }
                }
            }
        }
    }

    // ── Love Numbers (520 / 1314 / 521) ──
    private fun fillLoveNumbers(digits: Int, pool: MutableSet<String>, seed: Int) {
        val patterns = listOf("520", "1314", "521")
        for (pattern in patterns) {
            if (pattern.length > digits) continue
            val maxPos = digits - pattern.length
            var offset = 0
            for (pos in 0..maxPos) {
                val sb = StringBuilder()
                for (i in 0 until digits) {
                    if (i in pos until pos + pattern.length) {
                        sb.append(pattern[i - pos])
                    } else {
                        sb.append(((i * 3 + offset + seed) % 10))
                    }
                }
                pool.add(sb.toString())
                offset++
            }
        }
    }

    // ── Consecutive numbers (顺子) ──
    private fun fillConsecutiveNumbers(digits: Int, pool: MutableSet<String>, seed: Int) {
        // generate ascending runs like 123, 234, 345, etc.
        var offset = 0
        for (start in 0..7) {
            val runLen = minOf(4, digits) // up to 4 consecutive
            val maxPos = digits - runLen
            for (pos in 0..maxPos) {
                val sb = StringBuilder()
                for (i in 0 until digits) {
                    if (i in pos until pos + runLen) {
                        sb.append(start + (i - pos))
                    } else {
                        sb.append(((i * 5 + offset + seed) % 10))
                    }
                }
                pool.add(sb.toString())
                offset++
            }
        }
        // descending
        for (start in 9 downTo 3) {
            val runLen = minOf(4, digits)
            val maxPos = digits - runLen
            for (pos in 0..maxPos) {
                val sb = StringBuilder()
                for (i in 0 until digits) {
                    if (i in pos until pos + runLen) {
                        sb.append(start - (i - pos))
                    } else {
                        sb.append(((i * 7 + offset + seed) % 10))
                    }
                }
                pool.add(sb.toString())
                offset++
            }
        }
    }

    // ── All same digits (豹子) ──
    private fun fillAllSameNumbers(digits: Int, pool: MutableSet<String>, seed: Int) {
        for (d in 0..9) {
            pool.add(d.toString().repeat(digits))
        }
    }

    // ── Random fallback ──
    private fun fillRandomNumbers(digits: Int, pool: MutableSet<String>, seed: Int) {
        var offset = 0
        repeat(200) {
            val sb = StringBuilder()
            for (i in 0 until digits) {
                sb.append(((i * 7 + offset + seed * 3 + it * 13) % 10))
            }
            pool.add(sb.toString())
            offset++
        }
    }

    // ── Utility ──────────────────────────────────────────────

    fun buildCoolNumberInfo(number: String): CoolNumberInfo {
        val score = calculateScore(number)
        val tier = getTierFromScore(score)
        val pointPrice = calculatePointPrice(score)
        val tapePrice = calculateTapePrice(score)
        return CoolNumberInfo(number, score, tier, pointPrice, tapePrice)
    }

    fun canUpgradeTier(currentTier: CoolNumberTier): Boolean {
        return currentTier.ordinal < CoolNumberTier.BLACKGOLD.ordinal
    }

    /** Check a searched number: available, not banned, valid format */
    fun isSearchValid(number: String): CoolNumberSearchResult {
        if (number.length < 4 || number.length > 10) {
            return CoolNumberSearchResult.Invalid("号码长度需在4-10位之间")
        }
        if (!number.all { it.isDigit() }) {
            return CoolNumberSearchResult.Invalid("号码只能包含数字")
        }
        if (bannedNumbers.any { number.contains(it) }) {
            return CoolNumberSearchResult.Invalid("该号码被禁止选择")
        }
        if (isTaken(number)) {
            return CoolNumberSearchResult.Invalid("该号码已被使用")
        }
        return CoolNumberSearchResult.Available(buildCoolNumberInfo(number))
    }

    sealed class CoolNumberSearchResult {
        data class Available(val info: CoolNumberInfo) : CoolNumberSearchResult()
        data class Invalid(val reason: String) : CoolNumberSearchResult()
    }
}
