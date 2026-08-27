package io.github.zzzyyylllty.chotentech.feature

import io.github.zzzyyylllty.chotentech.data.profile.MetaEntry
import io.github.zzzyyylllty.chotentech.data.profile.ProfileManager
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.player.sendComponent
import io.github.zzzyyylllty.chotentech.util.devLog
import io.github.zzzyyylllty.chotentech.util.toComponent
import net.kyori.adventure.title.Title
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.time.Duration
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.releaseResourceFolder
import taboolib.module.configuration.Configuration
import java.io.File

object EndManager {

    data class EndTemplate(
        val icon: String = "END_STONE",
        val name: String = "结束",
        val color: String = "<#c3c3bd>",
        val major: String,
        val message: String,
        val postmessage: String,
        val profileadded: Int = 0
    )

    private val ends = mutableMapOf<String, EndTemplate>()

    fun init() {
        ends.clear()
        val dir = File(getDataFolder(), "ends")
        if (!dir.exists()) {
            releaseResourceFolder("ends")
        }
        val file = File(dir, "ends.yml")
        if (!file.exists()) {
            devLog("EndManager: ends.yml not found, skipping")
            return
        }
        val config: Configuration
        try {
            config = Configuration.loadFromFile(file)
        } catch (e: Exception) {
            devLog("EndManager: failed to load ends.yml: ${e.message}")
            return
        }

        for (key in config.getKeys(false)) {
            val section = config.getConfigurationSection(key) ?: continue
            try {
                val meta = section.getConfigurationSection("meta")
                ends[key] = EndTemplate(
                    icon = section.getString("icon") ?: "END_STONE",
                    name = section.getString("name") ?: "结束",
                    color = section.getString("color") ?: "<#c3c3bd>",
                    major = section.getString("major") ?: "",
                    message = section.getString("message") ?: "",
                    postmessage = section.getString("postmessage") ?: "",
                    profileadded = meta?.getInt("profileadded") ?: 0
                )
            } catch (e: Exception) {
                devLog("EndManager: failed to load end '$key': ${e.message}")
            }
        }
        devLog("EndManager: loaded ${ends.size} end templates")
    }

    /**
     * End a player's current profile with the given end ID.
     * Falls back to "generic" if the specified end ID is not found.
     */
    fun endProfile(player: Player, endId: String = "generic"): Boolean {
        val template = ends[endId] ?: ends["generic"]
        if (template == null) {
            player.sendComponent("<#E08A98>未找到结档信息 (end ID: $endId)")
            return false
        }

        val data = player.getData() ?: run {
            player.sendComponent("<#E08A98>无法获取你的档案数据")
            return false
        }

        if (data.ended) {
            player.sendComponent("<#EFDF8F>你的当前档案已经结档")
            return false
        }

        // Mark profile as ended
        data.ended = true
        data.endedId = endId

        // Register unlocked end cross-profile
        val uuid = player.uniqueId.toString()
        val sharedData = ProfileManager.getSharedData(uuid)
        sharedData.unlockedEnds.add(endId)
        ProfileManager.saveSharedData(uuid)

        // Grant profile slot bonus
        if (template.profileadded > 0) {
            val currentBonus = sharedData.meta["ended_profile_bonus"]?.value?.toIntOrNull() ?: 0
            val newBonus = currentBonus + template.profileadded
            sharedData.meta["ended_profile_bonus"] = MetaEntry(newBonus.toString())
            ProfileManager.saveSharedData(uuid)
        }

        // Save profile data
        ProfileManager.saveProfileData(player.uniqueId.toString(), data.profileId, data)

        // Show title + blindness effect
        player.showTitle(Title.title(
            "${template.color}<bold>${template.name}".toComponent(),
            net.kyori.adventure.text.Component.empty(),
            Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))
        ))
        player.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 200, 0, false, false))

        // Build and send the end message
        val sb = StringBuilder()
        sb.append("<#F2F5FA><b>").append(template.major).appendLine()
        sb.appendLine()
        for (line in template.message.lines()) {
            sb.appendLine("  <#A9B4C3>-<#84D1CB> $line")
        }
        sb.appendLine()
        for (line in template.postmessage.lines()) {
            sb.appendLine("  <#A9B4C3>-<#F2F5FA> ${line.replace("{profileadded}", template.profileadded.toString())}")
        }
        player.sendComponent(sb.toString())
        return true
    }

    fun isEnded(player: Player): Boolean = player.getData()?.ended == true

    fun getEndId(player: Player): String = player.getData()?.endedId ?: "generic"

    // ==================== GUI query API ====================

    data class EndEntry(
        val id: String,
        val template: EndTemplate,
        val unlocked: Boolean
    )

    fun getEntries(player: Player): List<EndEntry> {
        val unlocked = ProfileManager.getSharedData(player.uniqueId.toString()).unlockedEnds
        return ends.map { (id, template) ->
            EndEntry(id = id, template = template, unlocked = id in unlocked)
        }
    }

    fun getTotalCount(): Int = ends.size

    fun getUnlockedCount(player: Player): Int =
        ProfileManager.getSharedData(player.uniqueId.toString()).unlockedEnds.size
}
