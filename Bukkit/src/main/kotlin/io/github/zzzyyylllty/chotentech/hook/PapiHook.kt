package io.github.zzzyyylllty.chotentech.hook
import io.github.zzzyyylllty.chotentech.ChoTenTech.config
import io.github.zzzyyylllty.chotentech.ChoTenTech.playerRegions
import io.github.zzzyyylllty.chotentech.data.*
import io.github.zzzyyylllty.chotentech.util.replacePlaceholderSafety
import org.bukkit.OfflinePlayer
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffectType
import io.github.zzzyyylllty.chotentech.function.AchievementManager
import io.github.zzzyyylllty.chotentech.function.BaseManager
import io.github.zzzyyylllty.chotentech.function.CurrencyManager
import io.github.zzzyyylllty.chotentech.function.RankManager
import taboolib.platform.compat.PlaceholderExpansion
import taboolib.platform.compat.replacePlaceholder
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.roundToInt

object ExamplePapiHook : PlaceholderExpansion {

    override val identifier: String = "chotentech"

    // 处理在线玩家请求
    override fun onPlaceholderRequest(player: Player?, args: String): String {

        var list = args.split("_")

        when (list[0].lowercase()) {
            "idexists" -> return DataManager.isIDExist(list[1]).toString()
            "playername" -> return DataManager.getDataByCID(list[1])?.playerName.toString()
            "uuidbycid" -> return DataManager.getDataByCID(list[1])?.uuid ?: "null"
            "uuidbyname" -> return DataManager.getDataByName(list[1])?.uuid ?: "null"
            "cid" -> return if (list.size == 1) player?.getData()?.cid?.id.toString()
            else requestDataByName(list[1])?.cid?.id.toString()
            "randomname" -> {
                val first = config.getStringList("names.first").random()
                val last = config.getStringList("names.last").random()
                return "$first $last"
            }
            "sanity" -> return (round((player?.getData()?.sanity ?: 0.0)*100)/100).toString()
            "maxsanity" -> return (round((player?.getData()?.effectiveMaxSanity ?: 0.0)*100)/100).toString()
            "sanityregen" -> return (round((player?.getData()?.effectiveSanityRegeneration ?: 0.0)*100)/100).toString()
            "darkness" -> return (round((player?.getData()?.darkness ?: 0.0)*100)/100).toString()
            "maxdarkness" -> return (round((player?.getData()?.effectiveMaxDarkness ?: 0.0)*100)/100).toString()
            "hppercent" -> return player?.let { (it.health/(it.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0)*100).toString() } ?: "50"
            "sanpercent" -> {
                val data = player?.getData() ?: return "0"
                val current = data.sanity
                val max = data.effectiveMaxSanity
                return (current/max*100).toString()
            }
            "darkpercent" -> {
                val data = player?.getData() ?: return "0"
                val current = data.darkness
                val max = data.effectiveMaxDarkness
                return (current/max*100).toString()
            }
            "externalinjury" -> return (round((player?.getData()?.externalInjury ?: 0.0)*100)/100).toString()
            "maxexternalinjury" -> return (round((player?.getData()?.maxExternalInjury ?: 0.0)*100)/100).toString()
            "externalinjurypercent" -> {
                val data = player?.getData() ?: return "100"
                val current = data.externalInjury
                val max = data.maxExternalInjury
                if (max <= 0) return "100"
                return (current/max*100).toString()
            }
            "internalinjury" -> return (round((player?.getData()?.internalInjury ?: 0.0)*100)/100).toString()
            "maxinternalinjury" -> return (round((player?.getData()?.maxInternalInjury ?: 0.0)*100)/100).toString()
            "internalinjurypercent" -> {
                val data = player?.getData() ?: return "100"
                val current = data.internalInjury
                val max = data.maxInternalInjury
                if (max <= 0) return "100"
                return (current/max*100).toString()
            }
            "formatted" -> return player?.let { "%${args.removePrefix("formatted_")}%".replacePlaceholder(it).numberFormat() } ?: "1"
            "hunger" -> return player?.let { (floor(getPercentHunger(it)/* *1.0)/1.0*/ )).toString() } ?: "100"
            "maxhunger" -> return player?.let { (floor(it.getData()?.maxHunger ?: 100.0)).toString() } ?: "100"
            "maxsaturation" -> return player?.let { (floor(it.getData()?.maxSaturation ?: 100.0)).toString() } ?: "100"
            "grain" -> return (round((player?.getData()?.grain ?: 0.0)*100)/100).toString()
            "vitamin" -> return (round((player?.getData()?.vitamin ?: 0.0)*100)/100).toString()
            "protein" -> return (round((player?.getData()?.protein ?: 0.0)*100)/100).toString()
            "fat" -> return (round((player?.getData()?.fat ?: 0.0)*100)/100).toString()
            "nutrition" -> {
                val data = player?.getData() ?: return "0"
                val grain = data.grain
                val vitamin = data.vitamin
                val protein = data.protein
                val fat = data.fat
                val lowest = minOf(grain, vitamin, protein, fat)
                val avg = (grain + vitamin + protein + fat) / 4.0
                when (list.getOrNull(1)?.lowercase()) {
                    "lowest" -> return "%.1f".format(lowest).removeSuffix(".0")
                    "avg" -> return "%.1f".format(avg).removeSuffix(".0")
                    "malnutrition" -> return (lowest < 20.0).toString()
                    "deficient" -> return listOfNotNull(
                        if (grain < 20) "谷物" else null,
                        if (vitamin < 20) "维生素" else null,
                        if (protein < 20) "蛋白质" else null,
                        if (fat < 20) "脂肪" else null
                    ).joinToString(",").ifEmpty { "正常" }
                    "grain" -> return "%.1f".format(grain).removeSuffix(".0")
                    "vitamin" -> return "%.1f".format(vitamin).removeSuffix(".0")
                    "protein" -> return "%.1f".format(protein).removeSuffix(".0")
                    "fat" -> return "%.1f".format(fat).removeSuffix(".0")
                    else -> return "%.1f".format(lowest).removeSuffix(".0")
                }
            }
            "level" -> {
                if (player == null) return "Player Not Found"
                val data = player.getData() ?: return "..."
                val level = data.activeLevel
                return (if (list.size == 1) {
                    level?.number
                }
                else {
                    when (list[1]) {
                        "major" -> {
                            val time = data.getTimeLeftLong()?.toDouble()?.div(100)?.let { round(it)/10 }
                            val time2 = if ((time ?: 0.0) > 0.0) time else "..."
                            if (level == null) return "???"
                            val number = level.number
                            return HUDManager(player).getHUDContent("levelname",if (number == null) "Enigmatic" else "Level $number")
                        }
                        "noclip" -> {
                            val time = data.getTimeLeftLong()?.toDouble()?.div(100)?.let { round(it)/10 }
                            val time2 = if ((time ?: 0.0) > 0.0) time else "..."
                            if (data.noclipping) return "切出倒计时 ${time2 ?: "..."}" else ""
                        }
                        "noclipname" -> {
                            if (data.noclipping) data.noclipPoint?.name ?: "..." else ""
                        }
                        "noclipping" -> {
                            if (data.noclipping) 1 else 0
                        }
                        "sub" -> if (data.noclipping) "<#66DD25>${data.noclipPoint?.name ?: "..."}" else level?.name ?: "???"
                        "fullraw" -> {
                            if (level == null) return "???"
                            val number = level.number
                            if (number == null) "Enigmatic" else "Level $number"
                        }
                        "number" -> if (level == null) "???" else level.number ?: "Enigmatic"
                        "name" -> level?.name ?: "???"
                        "sdclass" -> level?.survivalDifficulty?.classname ?: "N/A"
                        "one" -> level?.survivalDifficulty?.one ?: "N/A"
                        "two" -> level?.survivalDifficulty?.two ?: "N/A"
                        "three" -> level?.survivalDifficulty?.three ?: "N/A"
                        "sdprefix" -> level?.survivalDifficulty?.prefix ?: ""
                        "prefix" -> level?.prefix ?: ""
                        "tip" -> level?.tip ?: ""
                        "tip1" -> level?.tip?.take(16) ?: ""
                        "tip2" -> level?.tip?.drop(16)?.take(16) ?: ""
                        "tip3" -> level?.tip?.drop(32)?.take(16) ?: ""
                        else -> "Unknown"
                    }
                }).toString()
            }
            "rank" -> {
                if (player == null) return ""
                val type = list.getOrNull(1)?.lowercase() ?: return ""
                val field = list.getOrNull(2)?.lowercase() ?: "current"
                val rank = RankManager.getPlayerActiveRank(player, type)
                when (field) {
                    "name" -> rank?.name ?: ""
                    "desc" -> rank?.desc ?: ""
                    "get" -> rank?.get ?: ""
                    "id" -> rank?.id ?: ""
                    else -> rank?.display?.get(field) ?: ""
                }
            }
            "hud" -> {
                val split = list[1].split("?:")
                val first = split[0]
                val last = if (split.size >= 2) split[1] else "null"
                return player?.let { HUDManager(it) }?.getHUDContent(first, last.replace("@","%").replacePlaceholderSafety(player)) ?: last.replace("@","%").replacePlaceholderSafety(player)
            }
            "explored" -> {
                when (list.getOrNull(1)?.lowercase()) {
                    "levels" -> return (player?.getData()?.exploreredLevels?.size ?: 0).toString()
                    else -> return "0"
                }
            }
            "achievement" -> {
                val sub = list.getOrNull(1)?.lowercase()
                if (sub == "count") {
                    return player?.let { AchievementManager.getCompletedCount(it).toString() } ?: "0"
                } else {
                    val achId = args.removePrefix("achievement_")
                    return if (player != null && AchievementManager.isCompleted(player, achId)) "yes" else "no"
                }
            }
            "maintag" -> {
                return player?.getData()?.mainTag ?: "无组织"
            }
            "subtag" -> {
                return player?.getData()?.subTag ?: "无基地"
            }
            "basename" -> {
                return BaseManager.baseMap[player?.getData()?.subTag]?.name ?: "没有组织"
            }
            "currentbasemajor" -> {
                if (player == null) return "?"
                val regions = playerRegions[player.uniqueId.toString()] ?: return "野外"
                val baseRegion = regions.firstOrNull { it.startsWith("base-") } ?: return "野外"
                val base = BaseManager.getBaseByRegion(baseRegion) ?: return "?"
                return base.faction.uppercase()
            }
            "currentbaseminor" -> {
                if (player == null) return "?"
                val regions = playerRegions[player.uniqueId.toString()] ?: return "野外"
                val baseRegion = regions.firstOrNull { it.startsWith("base-") } ?: return "野外"
                val base = BaseManager.getBaseByRegion(baseRegion) ?: return "?"
                return base.idef.uppercase()
            }
            "balance" -> {
                if (player == null) return "0"
                val currency = list.getOrNull(1)?.lowercase() ?: return "0"
                return CurrencyManager.getBalance(player, currency).toString()
            }
            "effect" -> {
                val sub = list.getOrNull(1)?.lowercase()
                val type = list.drop(2).joinToString("_") // 支持 poison 等多词ID
                return when (sub) {
                    null, "display" -> {
                        if (player == null) return ""
                        val data = player.getData() ?: return ""
                        val customEffects = data.statusEffects

                        val coveredVanilla = customEffects.flatMap {
                            CUSTOM_COVERED_VANILLA[it.type] ?: emptySet()
                        }.toSet()

                        val groupedCustom = customEffects
                            .groupBy { it.type }
                            .mapValues { (_, list) -> list.maxBy { it.level } }
                            .values

                        val activeVanilla = player.activePotionEffects
                            .filter { it.type !in coveredVanilla }

                        val hasMalnutrition = data.grain < 20 || data.vitamin < 20 || data.protein < 20 || data.fat < 20

                        if (groupedCustom.isEmpty() && activeVanilla.isEmpty() && !hasMalnutrition) return "无效果"

                        val parts = mutableListOf<String>()

                        for (effect in groupedCustom) {
                            val name = STATUS_EFFECT_DISPLAY[effect.type] ?: effect.type
                            val color = EFFECT_COLOR[effect.type] ?: "<white>"
                            val levelStr = if (effect.level > 1) " ${effect.level.toRoman()}" else ""
                            val timeStr = if (effect.remainingDuration >= 1000) "${effect.remainingDuration / 1000}s" else ""
                            parts += "$color$name$levelStr${if (timeStr.isNotEmpty()) " <#AAAAAA>$timeStr" else ""}"
                        }

                        if (hasMalnutrition) {
                            parts += "<#FFAA00>营养不良"
                        }

                        for (effect in activeVanilla) {
                            val name = VANILLA_EFFECT_DISPLAY[effect.type] ?: effect.type.key.value()
                            val color = VANILLA_EFFECT_COLOR[effect.type] ?: "<white>"
                            val levelStr = if (effect.amplifier > 0) " ${(effect.amplifier + 1).toRoman()}" else ""
                            val timeStr = if (effect.duration >= 20) "${effect.duration / 20}s" else ""
                            parts += "$color$name$levelStr${if (timeStr.isNotEmpty()) " $timeStr" else ""}"
                        }

                        parts.joinToString(", ")
                    }
                    "multiline" -> {
                        if (player == null) return ""
                        val data = player.getData() ?: return ""
                        val customEffects = data.statusEffects

                        val coveredVanilla = customEffects.flatMap {
                            CUSTOM_COVERED_VANILLA[it.type] ?: emptySet()
                        }.toSet()

                        val groupedCustom = customEffects
                            .groupBy { it.type }
                            .mapValues { (_, list) -> list.maxBy { it.level } }
                            .values

                        val activeVanilla = player.activePotionEffects
                            .filter { it.type !in coveredVanilla }

                        val hasMalnutrition = data.grain < 20 || data.vitamin < 20 || data.protein < 20 || data.fat < 20

                        if (groupedCustom.isEmpty() && activeVanilla.isEmpty() && !hasMalnutrition) return ""

                        val parts = mutableListOf<String>()

                        for (effect in groupedCustom) {
                            val name = STATUS_EFFECT_DISPLAY[effect.type] ?: effect.type
                            val color = EFFECT_COLOR[effect.type] ?: "<white>"
                            val levelStr = if (effect.level > 1) " ${effect.level.toRoman()}" else ""
                            val timeStr = if (effect.remainingDuration >= 1000) "${effect.remainingDuration / 1000}s" else ""
                            parts += "$color$name$levelStr${if (timeStr.isNotEmpty()) " <#AAAAAA>$timeStr" else ""}"
                        }

                        if (hasMalnutrition) {
                            parts += "<#FFAA00>营养不良"
                        }

                        for (effect in activeVanilla) {
                            val name = VANILLA_EFFECT_DISPLAY[effect.type] ?: effect.type.key.value()
                            val color = VANILLA_EFFECT_COLOR[effect.type] ?: "<white>"
                            val levelStr = if (effect.amplifier > 0) " ${(effect.amplifier + 1).toRoman()}" else ""
                            val timeStr = if (effect.duration >= 20) "${effect.duration / 20}s" else ""
                            parts += "$color$name$levelStr${if (timeStr.isNotEmpty()) " $timeStr" else ""}"
                        }

                        parts.joinToString("\n")
                    }
                    "list" -> {
                        player?.getData()?.statusEffects?.joinToString(", ") {
                            "${it.type}:${it.level}(${it.remainingDuration / 1000}s)"
                        } ?: ""
                    }
                    "has" -> (player?.getData()?.statusEffects?.any { it.type == type } == true).toString()
                    "level" -> player?.getData()?.statusEffects?.firstOrNull { it.type == type }?.level?.toString() ?: "0"
                    "time" -> {
                        val remaining = player?.getData()?.statusEffects?.firstOrNull { it.type == type }?.remainingDuration ?: 0L
                        (remaining / 1000).toString()
                    }
                    else -> "Error"
                }
            }
            "formatdata" -> {
                if (player == null) return ""
                val data = player.getData() ?: return ""
                val sub = list.getOrNull(1)?.lowercase() ?: return "Error"
                return when (sub) {
                    "sanity" -> formatStatDisplay(player, data.sanity, data.effectiveMaxSanity, 90.0, 30.0, 10.0)
                    "internalinjury" -> formatStatDisplay(player, data.internalInjury, data.maxInternalInjury, 99.0, 75.0, 50.0)
                    "externalinjury" -> formatStatDisplay(player, data.externalInjury, data.maxExternalInjury, 99.0, 75.0, 50.0)
                    "health" -> formatHealthDisplay(player)
                    "food" -> formatFoodDisplay(player)
                    else -> "Error"
                }
            }
        }
        return "Error"
    }

    // 处理离线玩家请求
    override fun onPlaceholderRequest(player: OfflinePlayer?, args: String): String {
        return onPlaceholderRequest(player?.player, args)
    }

    fun requestDataByUUID(uuid: String): PlayerData? {
        return DataManager.getData(uuid)
    }

    fun requestDataByName(name: String): PlayerData? {
        return DataManager.getDataByName(name)
    }

    fun requestDataByCID(cid: String): PlayerData? {
        return DataManager.getDataByCID(cid)
    }
}

fun getPercentHunger(player: Player): Double {
    val data = player.getData() ?: return 100.0
    val max = data.maxCustomHunger
    if (max <= 0.0) return 100.0

    // Blend customHunger (70%) and customSaturation (30%) into a 0-100 display
    val hungerScore = (data.customHunger / max).coerceIn(0.0, 1.0) * 70.0
    val saturationScore = (data.customSaturation / max).coerceIn(0.0, 1.0) * 30.0
    val base = hungerScore + saturationScore

    // Subtract pending consumption's effective drain
    val consumptionRatio = (data.consumption / 100.0).coerceIn(0.0, 1.0)
    val penalty = if (data.customSaturation >= 5.0) {
        consumptionRatio * data.consumption * 0.015
    } else {
        consumptionRatio * data.consumption * 0.014
    }

    return (base - penalty).coerceIn(0.0, 100.0).roundToInt().toDouble()
}

fun String.numberFormat(): String {
    val double = this.toDoubleOrNull() ?: return this
    return (round(double*10.0)/10.0).toString().removeSuffix(".0")
}

/**
 * 格式化状态显示。
 * 正常站立时显示百分比，下蹲时显示具体数值。
 * 颜色阈值适用范围:
 *   injury(内外伤): >=99绿 / 75白 / 50黄 / <50红
 *   sanity(精神):  >90绿 / 30白 / 10黄 / <10红
 */
private fun formatStatDisplay(player: Player, current: Double, max: Double,
                               greenMin: Double, whiteMin: Double, yellowMin: Double): String {
    if (max <= 0) return "<green>100 <gray>%"
    val pct = (current / max * 100).coerceIn(0.0, 100.0)
    val showValue = player.isSneaking
    val flash = (System.currentTimeMillis() / 500) % 2 == 0L
    val color = when {
        pct >= greenMin -> "<green>"
        pct >= whiteMin -> "<white>"
        pct >= yellowMin -> if (flash) "<yellow>" else "<gold>"
        else -> if (flash) "<red>" else "<dark_red>"
    }
    val num = "%.1f".format(if (showValue) current else pct).removeSuffix(".0")
    return if (showValue) {
        "$color$num <gray>/${"%.1f".format(max).removeSuffix(".0")}"
    } else {
        "$color$num <gray>%"
    }
}

/**
 * 血量 —— 始终显示具体数值（不转为百分比），颜色基于 health/maxHealth 比例。
 * 100%绿 / 60%白 / 30%黄闪 / <30%红闪
 */
private fun formatHealthDisplay(player: Player): String {
    val maxHealth = player.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
    val health = player.health.coerceIn(0.0, maxHealth)
    val pct = health / maxHealth * 100
    val flash = (System.currentTimeMillis() / 500) % 2 == 0L
    val color = when {
        pct >= 100.0 -> "<green>"
        pct >= 60.0 -> "<white>"
        pct >= 20.0 -> if (flash) "<yellow>" else "<gold>"
        else -> if (flash) "<red>" else "<dark_red>"
    }
    val maxColor = if (flash && (pct < 60.0)) color else "<gray>"
    return "$color${"%.1f".format(health).removeSuffix(".0")} $maxColor/${"%.1f".format(maxHealth).removeSuffix(".0")}"
}

/**
 * 食物 —— 基于 getPercentHunger() 的混合值。
 * 正常显示百分比，下蹲显示数值/100。
 * >=90绿 / 50白 / 20黄闪 / <20红闪
 */
private fun formatFoodDisplay(player: Player): String {
    val pct = getPercentHunger(player).coerceIn(0.0, 100.0)
    val showValue = player.isSneaking
    val flash = (System.currentTimeMillis() / 500) % 2 == 0L
    val color = when {
        pct >= 90.0 -> "<green>"
        pct >= 50.0 -> "<white>"
        pct >= 20.0 -> if (flash) "<yellow>" else "<gold>"
        else -> if (flash) "<red>" else "<dark_red>"
    }
    val num = "%.1f".format(pct).removeSuffix(".0")
    return if (showValue) {
        "$color$num <gray>/100"
    } else {
        "$color$num <gray>%"
    }
}

/** 自定义状态效果中文显示名 */
private val STATUS_EFFECT_DISPLAY = mapOf(
    SANITY_REGEN to "精神恢复",
    SANITY_DRAIN to "精神流失",
    STRESS_RESISTANCE to "抗压",
    STRESS_IMMUNITY to "压力免疫",
    FRACTURE to "骨折",
    PAIN to "疼痛",
    MUSCLE_WEAKNESS to "肌肉无力",
    PAIN_RELIEF to "止痛",
    HOUND_VIRUS to "猎犬病毒",
    IMMUNITY to "免疫",
    STAMINA_REGEN to "体力恢复"
)

/** 自定义状态效果颜色 */
private val EFFECT_COLOR = mapOf(
    SANITY_REGEN to "<#55FF55>",
    SANITY_DRAIN to "<#FF5555>",
    STRESS_RESISTANCE to "<#55FF55>",
    STRESS_IMMUNITY to "<#55FF55>",
    FRACTURE to "<#FFAA00>",
    PAIN to "<#FF5555>",
    MUSCLE_WEAKNESS to "<#AAAAAA>",
    PAIN_RELIEF to "<#55FF55>",
    HOUND_VIRUS to "<#FF55FF>",
    IMMUNITY to "<#55FF55>",
    STAMINA_REGEN to "<#55FF55>"
)

/** 原版药水效果中文显示名 */
private val VANILLA_EFFECT_DISPLAY = mapOf(
    PotionEffectType.SPEED to "速度",
    PotionEffectType.SLOWNESS to "缓慢",
    PotionEffectType.HASTE to "急迫",
    PotionEffectType.MINING_FATIGUE to "挖掘疲劳",
    PotionEffectType.STRENGTH to "力量",
    PotionEffectType.INSTANT_HEALTH to "瞬间治疗",
    PotionEffectType.INSTANT_DAMAGE to "瞬间伤害",
    PotionEffectType.JUMP_BOOST to "跳跃提升",
    PotionEffectType.NAUSEA to "反胃",
    PotionEffectType.REGENERATION to "生命恢复",
    PotionEffectType.RESISTANCE to "抗性",
    PotionEffectType.FIRE_RESISTANCE to "防火",
    PotionEffectType.WATER_BREATHING to "水下呼吸",
    PotionEffectType.INVISIBILITY to "隐身",
    PotionEffectType.BLINDNESS to "失明",
    PotionEffectType.NIGHT_VISION to "夜视",
    PotionEffectType.HUNGER to "饥饿",
    PotionEffectType.WEAKNESS to "虚弱",
    PotionEffectType.POISON to "中毒",
    PotionEffectType.WITHER to "凋零",
    PotionEffectType.HEALTH_BOOST to "生命提升",
    PotionEffectType.ABSORPTION to "伤害吸收",
    PotionEffectType.SATURATION to "饱和",
    PotionEffectType.GLOWING to "发光",
    PotionEffectType.LEVITATION to "飘浮",
    PotionEffectType.LUCK to "幸运",
    PotionEffectType.UNLUCK to "霉运",
    PotionEffectType.SLOW_FALLING to "缓降",
    PotionEffectType.CONDUIT_POWER to "潮涌能量",
    PotionEffectType.DOLPHINS_GRACE to "海豚的恩惠",
    PotionEffectType.BAD_OMEN to "不祥之兆",
    PotionEffectType.HERO_OF_THE_VILLAGE to "村庄英雄",
    PotionEffectType.DARKNESS to "黑暗"
)

/** 原版药水效果颜色 */
private val VANILLA_EFFECT_COLOR = mapOf(
    PotionEffectType.SPEED to "<#55FF55>",
    PotionEffectType.SLOWNESS to "<#AAAAAA>",
    PotionEffectType.HASTE to "<#55FF55>",
    PotionEffectType.MINING_FATIGUE to "<#AAAAAA>",
    PotionEffectType.STRENGTH to "<#FF5555>",
    PotionEffectType.INSTANT_HEALTH to "<#55FF55>",
    PotionEffectType.INSTANT_DAMAGE to "<#FF5555>",
    PotionEffectType.JUMP_BOOST to "<#55FF55>",
    PotionEffectType.NAUSEA to "<#AA00AA>",
    PotionEffectType.REGENERATION to "<#FF55FF>",
    PotionEffectType.RESISTANCE to "<#55FF55>",
    PotionEffectType.FIRE_RESISTANCE to "<#FFAA00>",
    PotionEffectType.WATER_BREATHING to "<#55FFFF>",
    PotionEffectType.INVISIBILITY to "<#AAAAAA>",
    PotionEffectType.BLINDNESS to "<#555555>",
    PotionEffectType.NIGHT_VISION to "<#55FFFF>",
    PotionEffectType.HUNGER to "<#FF5555>",
    PotionEffectType.WEAKNESS to "<#AAAAAA>",
    PotionEffectType.POISON to "<#55FF55>",
    PotionEffectType.WITHER to "<#555555>",
    PotionEffectType.HEALTH_BOOST to "<#FF55FF>",
    PotionEffectType.ABSORPTION to "<#FFAA00>",
    PotionEffectType.SATURATION to "<#FF5555>",
    PotionEffectType.GLOWING to "<#FFFF55>",
    PotionEffectType.LEVITATION to "<#FFFF55>",
    PotionEffectType.LUCK to "<#55FF55>",
    PotionEffectType.UNLUCK to "<#FF5555>",
    PotionEffectType.SLOW_FALLING to "<#55FFFF>",
    PotionEffectType.CONDUIT_POWER to "<#55FFFF>",
    PotionEffectType.DOLPHINS_GRACE to "<#55FFFF>",
    PotionEffectType.BAD_OMEN to "<#FF5555>",
    PotionEffectType.HERO_OF_THE_VILLAGE to "<#55FF55>",
    PotionEffectType.DARKNESS to "<#555555>"
)

/** 自定义效果覆盖的原版效果（避免重复显示） */
private val CUSTOM_COVERED_VANILLA = mapOf(
    PAIN to setOf(PotionEffectType.SLOWNESS, PotionEffectType.MINING_FATIGUE),
    MUSCLE_WEAKNESS to setOf(PotionEffectType.WEAKNESS)
)

/** 整数转罗马数字（用于效果等级显示） */
private fun Int.toRoman(): String {
    if (this <= 0) return ""
    val values = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
    val symbols = arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")
    val sb = StringBuilder()
    var remaining = this
    for (i in values.indices) {
        while (remaining >= values[i]) {
            sb.append(symbols[i])
            remaining -= values[i]
        }
    }
    return sb.toString()
}