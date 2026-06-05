package io.github.zzzyyylllty.chotentech.hook
import io.github.zzzyyylllty.chotentech.ChoTenTech.config
import io.github.zzzyyylllty.chotentech.ChoTenTech.playerRegions
import io.github.zzzyyylllty.chotentech.data.HUDManager
import io.github.zzzyyylllty.chotentech.data.PlayerData
import io.github.zzzyyylllty.chotentech.data.DataManager
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.util.replacePlaceholderSafety
import org.bukkit.OfflinePlayer
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import io.github.zzzyyylllty.chotentech.function.AchievementManager
import io.github.zzzyyylllty.chotentech.function.BaseManager
import io.github.zzzyyylllty.chotentech.function.RankManager
import taboolib.platform.compat.PlaceholderExpansion
import taboolib.platform.compat.replacePlaceholder
import kotlin.math.floor
import kotlin.math.round

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
            "maxexternalinjury" -> return (round((player?.getData()?.effectiveMaxExternalInjury ?: 0.0)*100)/100).toString()
            "externalinjurypercent" -> {
                val data = player?.getData() ?: return "100"
                val current = data.externalInjury
                val max = data.effectiveMaxExternalInjury
                if (max <= 0) return "100"
                return (current/max*100).toString()
            }
            "internalinjury" -> return (round((player?.getData()?.internalInjury ?: 0.0)*100)/100).toString()
            "maxinternalinjury" -> return (round((player?.getData()?.effectiveMaxInternalInjury ?: 0.0)*100)/100).toString()
            "internalinjurypercent" -> {
                val data = player?.getData() ?: return "100"
                val current = data.internalInjury
                val max = data.effectiveMaxInternalInjury
                if (max <= 0) return "100"
                return (current/max*100).toString()
            }
            "formatted" -> return player?.let { "%${args.removePrefix("formatted_")}%".replacePlaceholder(it).numberFormat() } ?: "1"
            "hunger" -> return player?.let { (floor(getPercentHunger(it)*10.0)/10.0).toString() } ?: "100"
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
            "effect" -> {
                val sub = list.getOrNull(1)?.lowercase()
                val type = list.drop(2).joinToString("_") // 支持 heavy_bleeding 等多词ID
                when (sub) {
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

    return (base - penalty).coerceIn(0.0, 100.0)
}

fun String.numberFormat(): String {
    val double = this.toDoubleOrNull() ?: return this
    return (round(double*10.0)/10.0).toString().removeSuffix(".0")
}