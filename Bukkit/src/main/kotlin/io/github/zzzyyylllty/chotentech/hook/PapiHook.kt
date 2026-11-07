package io.github.zzzyyylllty.chotentech.hook
import io.github.zzzyyylllty.chotentech.ChoTenTech.config
import io.github.zzzyyylllty.chotentech.ChoTenTech.playerRegions
import io.github.zzzyyylllty.chotentech.data.*
import io.github.zzzyyylllty.chotentech.util.minimessage.legacyToMiniMessage
import io.github.zzzyyylllty.chotentech.feature.SideInfoManager
import io.github.zzzyyylllty.chotentech.util.replacePlaceholderSafety
import ink.ptms.chemdah.api.ChemdahAPI
import ink.ptms.chemdah.core.quest.addon.AddonTrack.Companion.trackQuest
import ink.ptms.chemdah.core.quest.meta.MetaName
import io.github.zzzyyylllty.chotentech.chemdah.addon.AddonGameTimeout
import io.github.zzzyyylllty.chotentech.listener.GameTimeManager
import org.bukkit.OfflinePlayer
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffectType
import io.github.zzzyyylllty.chotentech.data.profile.ProfileManager
import io.github.zzzyyylllty.chotentech.data.profile.SettingRegistry
import io.github.zzzyyylllty.chotentech.function.AchievementManager
import io.github.zzzyyylllty.chotentech.function.BaseManager
import io.github.zzzyyylllty.chotentech.function.CurrencyManager
import io.github.zzzyyylllty.chotentech.function.HealthAnimationManager
import io.github.zzzyyylllty.chotentech.function.MetaManager
import io.github.zzzyyylllty.chotentech.function.RankManager
import io.github.zzzyyylllty.chotentech.function.WeightManager
import io.github.zzzyyylllty.chotentech.data.PlayerDataManager
import taboolib.platform.compat.PlaceholderExpansion
import taboolib.platform.compat.replacePlaceholder
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.roundToInt

object ExamplePapiHook : PlaceholderExpansion {

    override val identifier: String = "chotentech"

    // 处理在线玩家请求 (占位符输出已统一为 HEX 色,外部消费方可直接解析)
    override fun onPlaceholderRequest(player: Player?, args: String): String {
        return resolvePlaceholder(player, args)
    }

    private fun resolvePlaceholder(player: Player?, args: String): String {
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
            "hpanimated" -> return player?.let { "%.1f".format(HealthAnimationManager.getDisplayHealth(it)).removeSuffix(".0") } ?: "0"
            "hpanimatedpercent" -> return player?.let {
                val max = it.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
                "%.1f".format(HealthAnimationManager.getDisplayHealth(it) / max * 100).removeSuffix(".0")
            } ?: "0"
            "healthanimatedmax" -> return player?.let {
                "%.1f".format(maxOf(it.health, HealthAnimationManager.getDisplayHealth(it))).removeSuffix(".0")
            } ?: "0"
            "healthanimatedmaxpercent" -> return player?.let {
                val max = it.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
                "%.1f".format(maxOf(it.health, HealthAnimationManager.getDisplayHealth(it)) / max * 100).removeSuffix(".0")
            } ?: "0"
            "healthanimatedmin" -> return player?.let {
                "%.1f".format(minOf(it.health, HealthAnimationManager.getDisplayHealth(it))).removeSuffix(".0")
            } ?: "0"
            "healthanimatedminpercent" -> return player?.let {
                val max = it.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
                "%.1f".format(minOf(it.health, HealthAnimationManager.getDisplayHealth(it)) / max * 100).removeSuffix(".0")
            } ?: "0"
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
            "toughness" -> {
                val body = player?.getData()?.bodyHealth ?: return "0"
                val partId = list.getOrNull(1)?.uppercase()
                if (partId == null) return body.toughnessPercentage.toString()
                val part = body.parts[partId] ?: return "0"
                return (if (list.getOrNull(2)?.lowercase() == "max") part.maxToughness else part.toughness).toString()
            }
            "water" -> {
                val body = player?.getData()?.bodyHealth ?: return "0"
                return when (list.getOrNull(1)?.lowercase()) {
                    "max" -> body.maxWater.toString()
                    "percent" -> (body.water / body.maxWater.coerceAtLeast(.001) * 100).toString()
                    else -> body.water.toString()
                }
            }
            "blackfilter" -> return (player?.getData()?.bodyHealth?.filterValue ?: 0.0).toString()
            "bodyeffects" -> {
                val data = player?.getData() ?: return ""
                val id = list.getOrNull(1)?.uppercase() ?: "HEAD"
                val part = data.bodyHealth.parts[id] ?: return ""
                val effects = part.effects.values.toList() + (if (id == "HEAD") data.allStatusEffects else emptyList())
                val labels = effects.map { STATUS_EFFECT_DISPLAY[it.type] ?: it.type }.toMutableList()
                if (id == "HEAD") labels += player.activePotionEffects.map { VANILLA_EFFECT_DISPLAY[it.type] ?: it.type.key.value() }
                return labels.distinct().joinToString(", ")
            }
            "formatted" -> return player?.let { "%${args.removePrefix("formatted_")}%".replacePlaceholder(it).numberFormat() } ?: "1"
            "hunger" -> return player?.let { floor(getPercentHunger(it)).toString() } ?: "100"
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
                        "sub" -> if (data.noclipping) "<#95c27d>${data.noclipPoint?.name ?: "..."}" else level?.name ?: "???"
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
            "chatcolor" -> return player?.let { chatRankDisplay(it, "chatcolor", list.getOrNull(1)?.lowercase() ?: "current") } ?: ""
            "namecolor" -> return player?.let { chatRankDisplay(it, "namecolor", list.getOrNull(1)?.lowercase() ?: "current") } ?: ""
            "prefix" -> return player?.let { chatRankDisplay(it, "prefix", list.getOrNull(1)?.lowercase() ?: "current") } ?: ""
            "suffix" -> return player?.let { chatRankDisplay(it, "suffix", list.getOrNull(1)?.lowercase() ?: "current") } ?: ""
            "chatsuffix" -> return player?.let { chatRankDisplay(it, "chatsuffix", list.getOrNull(1)?.lowercase() ?: "current") } ?: ""
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
                        val customEffects = data.allStatusEffects

                        val coveredVanilla = customEffects.flatMap {
                            CUSTOM_COVERED_VANILLA[it.type] ?: emptySet()
                        }.toSet()

                        val groupedCustom = customEffects
                            .groupBy { effect -> (data.bodyHealth.parts.values.firstOrNull { it.effects.values.any { stored -> stored === effect } }?.id ?: "HEAD") to effect.type }
                            .mapValues { (_, list) -> list.maxBy { it.level } }
                            .values

                        val activeVanilla = player.activePotionEffects
                            .filter { it.type !in coveredVanilla }

                        val hasMalnutrition = data.grain < 20 || data.vitamin < 20 || data.protein < 20 || data.fat < 20

                        if (groupedCustom.isEmpty() && activeVanilla.isEmpty() && !hasMalnutrition) return "无效果"

                        val parts = mutableListOf<String>()

                        for (effect in groupedCustom) {
                            val name = STATUS_EFFECT_DISPLAY[effect.type] ?: effect.type
                            val color = EFFECT_COLOR[effect.type] ?: "<#F2F5FA>"
                            val levelStr = if (effect.level > 1) " ${effect.level.toRoman()}" else ""
                            val timeStr = if (effect.remainingDuration >= 1000) "${effect.remainingDuration / 1000}s" else ""
                            parts += "${BodyParts.names[data.bodyHealth.parts.values.firstOrNull { it.effects.values.any { stored -> stored === effect } }?.id ?: "HEAD"]}: $color$name$levelStr${if (timeStr.isNotEmpty()) " <#c9c9c9>$timeStr" else ""}"
                        }

                        if (hasMalnutrition) {
                            parts += "头部: <#cfae6e>营养不良"
                        }

                        for (effect in activeVanilla) {
                            val name = VANILLA_EFFECT_DISPLAY[effect.type] ?: effect.type.key.value()
                            val color = VANILLA_EFFECT_COLOR[effect.type] ?: "<#F2F5FA>"
                            val levelStr = if (effect.amplifier > 0) " ${(effect.amplifier + 1).toRoman()}" else ""
                            val timeStr = if (effect.duration >= 20) "${effect.duration / 20}s" else ""
                            parts += "头部: $color$name$levelStr${if (timeStr.isNotEmpty()) " $timeStr" else ""}"
                        }

                        parts.joinToString(", ")
                    }
                    "multiline" -> {
                        if (player == null) return ""
                        val data = player.getData() ?: return ""
                        val customEffects = data.allStatusEffects

                        val coveredVanilla = customEffects.flatMap {
                            CUSTOM_COVERED_VANILLA[it.type] ?: emptySet()
                        }.toSet()

                        val groupedCustom = customEffects
                            .groupBy { effect -> (data.bodyHealth.parts.values.firstOrNull { it.effects.values.any { stored -> stored === effect } }?.id ?: "HEAD") to effect.type }
                            .mapValues { (_, list) -> list.maxBy { it.level } }
                            .values

                        val activeVanilla = player.activePotionEffects
                            .filter { it.type !in coveredVanilla }

                        val hasMalnutrition = data.grain < 20 || data.vitamin < 20 || data.protein < 20 || data.fat < 20

                        if (groupedCustom.isEmpty() && activeVanilla.isEmpty() && !hasMalnutrition) return ""

                        val parts = mutableListOf<String>()

                        for (effect in groupedCustom) {
                            val name = STATUS_EFFECT_DISPLAY[effect.type] ?: effect.type
                            val color = EFFECT_COLOR[effect.type] ?: "<#F2F5FA>"
                            val levelStr = if (effect.level > 1) " ${effect.level.toRoman()}" else ""
                            val timeStr = if (effect.remainingDuration >= 1000) "${effect.remainingDuration / 1000}s" else ""
                            parts += "${BodyParts.names[data.bodyHealth.parts.values.firstOrNull { it.effects.values.any { stored -> stored === effect } }?.id ?: "HEAD"]}: $color$name$levelStr${if (timeStr.isNotEmpty()) " <#c9c9c9>$timeStr" else ""}"
                        }

                        if (hasMalnutrition) {
                            parts += "头部: <#cfae6e>营养不良"
                        }

                        for (effect in activeVanilla) {
                            val name = VANILLA_EFFECT_DISPLAY[effect.type] ?: effect.type.key.value()
                            val color = VANILLA_EFFECT_COLOR[effect.type] ?: "<#F2F5FA>"
                            val levelStr = if (effect.amplifier > 0) " ${(effect.amplifier + 1).toRoman()}" else ""
                            val timeStr = if (effect.duration >= 20) "${effect.duration / 20}s" else ""
                            parts += "头部: $color$name$levelStr${if (timeStr.isNotEmpty()) " $timeStr" else ""}"
                        }

                        parts.joinToString("\n")
                    }
                    "list" -> {
                        player?.getData()?.allStatusEffects?.joinToString(", ") {
                            "${it.type}:${it.level}(${it.remainingDuration / 1000}s)"
                        } ?: ""
                    }
                    "has" -> (player?.getData()?.allStatusEffects?.any { it.type == type } == true).toString()
                    "level" -> player?.getData()?.allStatusEffects?.filter { it.type == type }?.maxOfOrNull { it.level }?.toString() ?: "0"
                    "time" -> {
                        val remaining = player?.getData()?.allStatusEffects?.firstOrNull { it.type == type }?.remainingDuration ?: 0L
                        (remaining / 1000).toString()
                    }
                    else -> "Error"
                }
            }
            "skill" -> {
                if (player == null) return ""
                val skillName = list.getOrNull(1)?.lowercase() ?: return "Error"
                val field = list.getOrNull(2)?.lowercase() ?: "level"
                val pm = try { PlayerDataManager(player) } catch (_: Exception) { return "0" }
                return when (field) {
                    "level" -> (pm.getLevel(skillName) ?: 0L).toString()
                    "exp" -> "%.1f".format(pm.getExp(skillName)).removeSuffix(".0")
                    "maxxp" -> pm.getNextLevelExp(skillName)?.let { "%.1f".format(it).removeSuffix(".0") } ?: "0"
                    "progress" -> {
                        val current = pm.getExp(skillName)
                        val required = pm.getNextLevelExp(skillName) ?: return "100"
                        if (required <= 0) return "100"
                        "%.1f".format((current / required * 100).coerceAtMost(100.0)).removeSuffix(".0")
                    }
                    else -> "Error"
                }
            }
            "stamina" -> {
                val data = player?.getData() ?: return "0"
                val field = list.getOrNull(1)?.lowercase() ?: "current"
                return when (field) {
                    "current" -> "%.1f".format(data.stamina).removeSuffix(".0")
                    "max" -> "%.1f".format(data.maxStamina).removeSuffix(".0")
                    "percent" -> {
                        val max = data.maxStamina
                        if (max <= 0) return "0"
                        "%.1f".format((data.stamina / max * 100).coerceIn(0.0, 100.0)).removeSuffix(".0")
                    }
                    "hand" -> "%.1f".format(data.handStamina).removeSuffix(".0")
                    "maxhand" -> "%.1f".format(data.maxHandStamina).removeSuffix(".0")
                    else -> "Error"
                }
            }
            "stress" -> {
                return "%.1f".format(player?.getData()?.stress ?: 0.0).removeSuffix(".0")
            }
            "weight" -> {
                if (player == null) return "0"
                val field = list.getOrNull(1)?.lowercase() ?: "current"
                return when (field) {
                    "current" -> "%.1f".format(WeightManager.getPlayerTotalWeight(player)).removeSuffix(".0")
                    "hud" -> formatWeightDisplay(player)
                    else -> "Error"
                }
            }
            "money" -> {
                if (player == null) return "0"
                return CurrencyManager.getBalance(player, "money").toString()
            }
            "point" -> {
                if (player == null) return "0"
                return CurrencyManager.getBalance(player, "point").toString()
            }
            "tape" -> {
                if (player == null) return "0"
                return CurrencyManager.getBalance(player, "tape").toString()
            }
            "ltoken" -> {
                if (player == null) return "0"
                return CurrencyManager.getBalance(player, "ltoken").toString()
            }
            "ptoken" -> {
                if (player == null) return "0"
                return CurrencyManager.getBalance(player, "ptoken").toString()
            }
            "tokentotal" -> {
                if (player == null) return "0"
                return CurrencyManager.getTokenTotal(player).toString()
            }
            "profilename" -> return player?.getData()?.profileName ?: ""
            "profileid" -> return player?.getData()?.profileId ?: ""
            "profilelp" -> {
                val profileId = player?.getData()?.profileId
                profileId?.let { "profile=$it" } ?: return ""
                }
            "playtime" -> {
                val ms = player?.getData()?.playTime ?: return "0"
                val hours = ms / 3600000
                val minutes = (ms % 3600000) / 60000
                return "${hours}h${minutes}m"
            }
            "consumption" -> return "%.1f".format(player?.getData()?.consumption ?: 0.0).removeSuffix(".0")
            "pvp" -> return (player?.getData()?.pvpEnabled == true).toString()
            "ended" -> return (player?.getData()?.ended == true).toString()
            "endedshow" -> return if (player?.getData()?.ended == true) "<#8FD4A6>已结档" else "<#EFDF8F>未结档"
            "endedid" -> return player?.getData()?.endedId ?: "generic"
            "gametime" -> {
                val hours = player?.getData()?.gameTimeHours ?: return "2150.01.01 12AM"
                return when (list.getOrNull(1)) {
                    null, "format" -> io.github.zzzyyylllty.chotentech.listener.GameTimeManager.formatOneLine(hours)
                    "year" -> io.github.zzzyyylllty.chotentech.listener.GameTimeManager.getYear(hours).toString()
                    "month" -> io.github.zzzyyylllty.chotentech.listener.GameTimeManager.getMonth(hours).toString().padStart(2, '0')
                    "day" -> io.github.zzzyyylllty.chotentech.listener.GameTimeManager.getDay(hours).toString().padStart(2, '0')
                    "hour" -> io.github.zzzyyylllty.chotentech.listener.GameTimeManager.getHour(hours).toString().padStart(2, '0')
                    "total" -> hours.toString()
                    else -> "?"
                }
            }
            "reputation" -> {
                val data = player?.getData() ?: return "0"
                val faction = list.getOrNull(1)?.lowercase() ?: return "0"
                return data.reputation[faction]?.toString() ?: "0"
            }
            "affection" -> {
                val data = player?.getData() ?: return "0"
                // NPC ID is case-sensitive in PlayerData; preserve the placeholder argument
                // and fall back to a case-insensitive match for compatibility with old data.
                val npcId = list.getOrNull(1) ?: return "0"
                return data.affection[npcId]?.toString()
                    ?: data.affection.entries
                        .firstOrNull { it.key.equals(npcId, ignoreCase = true) }
                        ?.value?.toString()
                    ?: "0"
            }
            "meta" -> {
                if (player == null) return ""
                val scope = list.getOrNull(1)?.lowercase() ?: return "Error"
                val key = list.drop(2).joinToString("_")
                if (key.isEmpty()) return "Error"
                val uuid = player.uniqueId.toString()
                val entry = when (scope) {
                    "shared" -> MetaManager.getShared(uuid, key)
                    "independent" -> MetaManager.getIndependent(uuid, key)
                    else -> return "Error"
                }
                return entry?.value ?: ""
            }
            "metaexists" -> {
                if (player == null) return "false"
                val scope = list.getOrNull(1)?.lowercase() ?: return "Error"
                val key = list.drop(2).joinToString("_")
                if (key.isEmpty()) return "Error"
                val uuid = player.uniqueId.toString()
                return when (scope) {
                    "shared" -> MetaManager.hasShared(uuid, key).toString()
                    "independent" -> MetaManager.hasIndependent(uuid, key).toString()
                    else -> "Error"
                }
            }
            "formatdata" -> {
                if (player == null) return ""
                val data = player.getData() ?: return ""
                val sub = list.getOrNull(1)?.lowercase() ?: return "Error"
                return when (sub) {
                    "sanity" -> formatStatDisplay(player, data.sanity, data.effectiveMaxSanity, 90.0, 30.0, 10.0) + " " + ChangeTracker.getArrows(player.uniqueId, "sanity")
                    "health" -> formatHealthDisplay(player)
                    "food" -> formatFoodDisplay(player) + " " + ChangeTracker.getArrows(player.uniqueId, "food")
                    else -> "Error"
                }
            }
            "change" -> {
                if (player == null) return ""
                val key = list.getOrNull(1)?.lowercase() ?: return "Error"
                return ChangeTracker.getArrows(player.uniqueId, key)
            }
            "sidebar" -> {
                return getQuestSidebar(player) ?: ""
            }
            "sideinfo" -> {
                if (player == null) return ""
                val mode = ProfileManager.getSharedData(player.uniqueId.toString()).settings.get("sideInfoMode")
                return when (mode) {
                    "always_noclip" -> SideInfoManager.getClipDataRaw(player)
                    "always_quest" -> getQuestSidebar(player) ?: ""
                    "noclip_priority" -> {
                        if (player.location.pitch >= 85) {
                            getQuestSidebar(player) ?: SideInfoManager.getClipDataRaw(player)
                        } else {
                            SideInfoManager.getClipDataRaw(player)
                        }
                    }
                    else -> { // quest_priority (default)
                        if (SideInfoManager.isShowing(player)) {
                            SideInfoManager.getClipData(player)
                        } else {
                            getQuestSidebar(player) ?: ""
                        }
                    }
                }
            }
            "sidedata" -> {
                if (player == null) return "idle"
                return SideInfoManager.getSideData(player)
            }
            "clipdata" -> {
                if (player == null) return ""
                return SideInfoManager.getClipData(player)
            }
            "setting" -> {
                if (player == null || list.size < 2) return ""
                val key = list[1].lowercase()
                // 大小写不敏感匹配注册表 id;未注册的 key 返回空而不是 "unknown"
                val settingId = SettingRegistry.byId.keys.firstOrNull { it.lowercase() == key }
                    ?: return ""
                return ProfileManager.getSharedData(player.uniqueId.toString()).settings.get(settingId)
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

    /** 聊天类称号显示:自动应用生效称号(激活优先,否则最高优先级条件匹配) */
    private fun chatRankDisplay(player: Player, type: String, field: String): String {
        return RankManager.getEffectiveRankDisplay(player, type, field)
    }

    private fun getQuestSidebar(player: Player?): String? {
        if (player == null) return null
        val profile = ChemdahAPI.playerProfile[player.name] ?: return null
        val trackTemplate = profile.trackQuest ?: return null
        val quest = profile.getQuests().firstOrNull { it.template === trackTemplate } ?: return null

        val questName = quest.template.meta<MetaName>("name")?.displayName?.legacyToMiniMessage() ?: quest.id

        val lines = mutableListOf<String>()
        lines.add("<#84D1CB>正在追踪任务")
        lines.add("<#EFDF8F>$questName")
        lines.add("")

        for ((index, task) in quest.tasks.withIndex()) {
            val taskName = task.meta<MetaName>("name")?.displayName?.legacyToMiniMessage() ?: task.id
            val isCompleted = task.isCompleted(profile)

            if (isCompleted) {
                lines.add("<#6E7A8C>#${index + 1} $taskName<reset> <#8FD4A6>已完成")
            } else {
                val progress = task.objective.getProgress(profile, task)
                val value = progress.value.toString().toDouble().roundToInt().toString()
                val target = progress.target.toString().toDouble().roundToInt().toString()
                val percent = (progress.percent * 100).roundToInt()

                if (progress.value.toString().toDoubleOrNull()?.takeIf { it > 0 } != null) {
                    lines.add("<#6E7A8C>#${index + 1} <#A9B4C3>$taskName <#EFDF8F>$value/$target <#A9B4C3>($percent%)")
                } else {
                    lines.add("<#6E7A8C>#${index + 1} <#A9B4C3>$taskName <#E08A98>$value/$target")
                }
            }
        }

        // Game timeout remaining time
        val addon = quest.template.addon<AddonGameTimeout>("gametimeout")
        if (addon != null && addon.gameDurationHours > 0) {
            val startHours = quest.persistentDataContainer["gametimeout_start_hours", 0L].toLong()
            if (startHours > 0L) {
                val currentHours = player.getData()?.gameTimeHours ?: 0L
                val remaining = (startHours + addon.gameDurationHours) - currentHours
                if (remaining > 0) {
                    val y = remaining / 8640
                    val rem1 = remaining % 8640
                    val M = rem1 / 720
                    val rem2 = rem1 % 720
                    val d = rem2 / 24
                    val h = rem2 % 24
                    val parts = mutableListOf<String>()
                    if (y > 0) parts += "${y}年"
                    if (M > 0) parts += "${M}月"
                    if (d > 0) parts += "${d}天"
                    if (h > 0 || parts.isEmpty()) parts += "${h}小时"
                    lines.add("")
                    lines.add("<#E08A98>剩余 ${parts.joinToString("")}")
                } else {
                    lines.add("")
                    lines.add("<#E08A98>已超时")
                }
            }
        }

        return lines.joinToString("\n")
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
    if (max <= 0) return "<#00FF00>100 <#FFFFFF>%"   // 分隔符%改为白色
    val pct = (current / max * 100).coerceIn(0.0, 100.0)
    val showValue = player.isSneaking
    val flash = (System.currentTimeMillis() / 500) % 2 == 0L
    val color = when {
        pct >= greenMin -> "<#00FF00>"
        pct >= whiteMin -> "<#F2F5FA>"               // 恢复为原来的白色
        pct >= yellowMin -> if (flash) "<#FFD700>" else "<#FFA500>"
        else -> if (flash) "<#FF0000>" else "<#FF4444>"
    }
    val num = "%.1f".format(if (showValue) current else pct).removeSuffix(".0")
    return if (showValue) {
        "$color$num <#FFFFFF>/${"%.1f".format(max).removeSuffix(".0")}"  // 斜杠和最大值用白色
    } else {
        "$color$num <#FFFFFF>%"                      // 百分号用白色
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
        pct >= 100.0 -> "<#00FF00>"
        pct >= 60.0 -> "<#F2F5FA>"                  // 恢复为白色
        pct >= 20.0 -> if (flash) "<#FFD700>" else "<#FFA500>"
        else -> if (flash) "<#FF0000>" else "<#FF4444>"
    }
    // 最大值、斜杠固定为白色，不再随闪烁变化
    return "$color${"%.1f".format(health).removeSuffix(".0")} <#FFFFFF>/${"%.1f".format(maxHealth).removeSuffix(".0")}"
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
        pct >= 90.0 -> "<#00FF00>"
        pct >= 50.0 -> "<#F2F5FA>"                  // 恢复为白色
        pct >= 20.0 -> if (flash) "<#FFD700>" else "<#FFA500>"
        else -> if (flash) "<#FF0000>" else "<#FF4444>"
    }
    val num = "%.1f".format(pct).removeSuffix(".0")
    return if (showValue) {
        "$color$num <#FFFFFF>/100"                  // 斜杠和100用白色
    } else {
        "$color$num <#FFFFFF>%"                    // 百分号用白色
    }
}

/**
 * 负重 HUD 显示 —— 颜色 + 数值 + KG 单位。
 * <20KG 绿 / 20-30KG 白（舒适区间） / 30-45KG 黄 / >=45KG 红（重度惩罚）
 */
private fun formatWeightDisplay(player: Player): String {
    val weight = WeightManager.getPlayerTotalWeight(player)
    val color = when {
        weight < 20 -> "<#00FF00>"
        weight <= 30 -> "<#F2F5FA>"                 // 恢复为白色
        weight <= 45 -> "<#FFD700>"
        else -> "<#FF0000>"
    }
    return "$color${"%.2f".format(weight)} <#FFFFFF>kg"  // kg单位用白色
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
    STAMINA_REGEN to "体力恢复",
    PENETRATION to "贯穿", DIZZINESS to "晕眩", SHOCK_SEQUELA to "休克后遗症",
    MINOR_BLEED to "小出血", MAJOR_BLEED to "大出血", WOUND to "创口",
    TINNITUS to "耳鸣", BLACKOUT to "眼部发黑", TREMOR to "颤动",
    DEHYDRATION to "脱水", SEVERE_DEHYDRATION to "严重脱水", FLASH to "闪光"
)

/** 自定义状态效果颜色 */
private val EFFECT_COLOR = mapOf(
    SANITY_REGEN to "<#ade4ad>",
    SANITY_DRAIN to "<#e4adad>",
    STRESS_RESISTANCE to "<#ade4ad>",
    STRESS_IMMUNITY to "<#ade4ad>",
    FRACTURE to "<#cfae6e>",
    PAIN to "<#e4adad>",
    MUSCLE_WEAKNESS to "<#c9c9c9>",
    PAIN_RELIEF to "<#ade4ad>",
    HOUND_VIRUS to "<#e4ade4>",
    IMMUNITY to "<#ade4ad>",
    STAMINA_REGEN to "<#ade4ad>"
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
    PotionEffectType.SPEED to "<#ade4ad>",
    PotionEffectType.SLOWNESS to "<#c9c9c9>",
    PotionEffectType.HASTE to "<#ade4ad>",
    PotionEffectType.MINING_FATIGUE to "<#c9c9c9>",
    PotionEffectType.STRENGTH to "<#e4adad>",
    PotionEffectType.INSTANT_HEALTH to "<#ade4ad>",
    PotionEffectType.INSTANT_DAMAGE to "<#e4adad>",
    PotionEffectType.JUMP_BOOST to "<#ade4ad>",
    PotionEffectType.NAUSEA to "<#ad3aad>",
    PotionEffectType.REGENERATION to "<#e4ade4>",
    PotionEffectType.RESISTANCE to "<#ade4ad>",
    PotionEffectType.FIRE_RESISTANCE to "<#cfae6e>",
    PotionEffectType.WATER_BREATHING to "<#ade4e4>",
    PotionEffectType.INVISIBILITY to "<#c9c9c9>",
    PotionEffectType.BLINDNESS to "<#747474>",
    PotionEffectType.NIGHT_VISION to "<#ade4e4>",
    PotionEffectType.HUNGER to "<#e4adad>",
    PotionEffectType.WEAKNESS to "<#c9c9c9>",
    PotionEffectType.POISON to "<#ade4ad>",
    PotionEffectType.WITHER to "<#747474>",
    PotionEffectType.HEALTH_BOOST to "<#e4ade4>",
    PotionEffectType.ABSORPTION to "<#cfae6e>",
    PotionEffectType.SATURATION to "<#e4adad>",
    PotionEffectType.GLOWING to "<#e4e4ad>",
    PotionEffectType.LEVITATION to "<#e4e4ad>",
    PotionEffectType.LUCK to "<#ade4ad>",
    PotionEffectType.UNLUCK to "<#e4adad>",
    PotionEffectType.SLOW_FALLING to "<#ade4e4>",
    PotionEffectType.CONDUIT_POWER to "<#ade4e4>",
    PotionEffectType.DOLPHINS_GRACE to "<#ade4e4>",
    PotionEffectType.BAD_OMEN to "<#e4adad>",
    PotionEffectType.HERO_OF_THE_VILLAGE to "<#ade4ad>",
    PotionEffectType.DARKNESS to "<#747474>"
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