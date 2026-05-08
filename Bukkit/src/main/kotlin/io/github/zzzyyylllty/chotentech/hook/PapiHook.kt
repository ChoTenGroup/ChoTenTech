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
            "maxsanity" -> return (round((player?.getData()?.maxSanity ?: 0.0)*100)/100).toString()
            "darkness" -> return (round((player?.getData()?.darkness ?: 0.0)*100)/100).toString()
            "maxdarkness" -> return (round((player?.getData()?.maxDarkness ?: 0.0)*100)/100).toString()
            "hppercent" -> return player?.let { (it.health/(it.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0)*100).toString() } ?: "50"
            "sanpercent" -> {
                val data = player?.getData() ?: return "0"
                val current = data.sanity
                val max = data.maxSanity
                return (current/max*100).toString()
            }
            "darkpercent" -> {
                val data = player?.getData() ?: return "0"
                val current = data.darkness
                val max = data.maxDarkness
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
    val food = player.foodLevel.toDouble()       // 0 to 20
    val saturation = player.saturation.toDouble() // 0 to 20 (Float in NMS)
    val exhaustion = player.exhaustion.toDouble() // 0 to 4.0

    // 1. 计算基础分数
    val foodValue = food * 3.5
    val saturationValue = saturation * 1.5
    val baseValue = foodValue + saturationValue

    // 2. 计算消耗度（Exhaustion）即将扣除的价值
    // 根据 MC 机制，消耗度满 4.0 时执行扣除
    val currentExhaustionRatio = (exhaustion / 4.0)

    val penalty = when {
        // 情况 A：饱和度大于或等于 1
        // 消耗度达到 4 时，会扣除 1.0 饱和度（价值 1.5）
        saturation >= 1.0 -> {
            currentExhaustionRatio * 1.5
        }

        // 情况 B：饱和度在 0 到 1 之间
        // 消耗度达到 4 时，只会把剩余的饱和度扣完，不会扣饥饿值
        // 所以这 4 点消耗度的总价值仅等于剩余饱和度的价值
        saturation > 0.0 -> {
            currentExhaustionRatio * (saturation * 1.5)
        }

        // 情况 C：饱和度已经为 0
        // 消耗度达到 4 时，会扣除 1 点饥饿值（价值 3.5）
        else -> {
            currentExhaustionRatio * 3.5
        }
    }

    // 3. 最终得分 = (饥饿+饱和) - 正在消耗的分值
    val result = baseValue - penalty

    // 理论上由于 saturation <= foodLevel，最大值为 20*3.5 + 20*1.5 = 100.0
    // 最小值为 0.0
    return result.coerceIn(0.0, 100.0)
}

fun String.numberFormat(): String {
    val double = this.toDoubleOrNull() ?: return this
    return (round(double*10.0)/10.0).toString().removeSuffix(".0")
}