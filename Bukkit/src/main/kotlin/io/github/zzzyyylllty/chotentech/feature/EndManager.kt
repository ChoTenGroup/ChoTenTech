package io.github.zzzyyylllty.chotentech.feature

import io.github.zzzyyylllty.chotentech.data.profile.MetaEntry
import io.github.zzzyyylllty.chotentech.data.profile.ProfileManager
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.player.sendComponent
import io.github.zzzyyylllty.chotentech.util.devLog
import org.bukkit.entity.Player
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.releaseResourceFolder
import taboolib.module.configuration.Configuration
import java.io.File

object EndManager {

    data class EndTemplate(
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
            player.sendComponent("<red>未找到结档信息 (end ID: $endId)")
            return false
        }

        val data = player.getData() ?: run {
            player.sendComponent("<red>无法获取你的档案数据")
            return false
        }

        if (data.ended) {
            player.sendComponent("<yellow>你的当前档案已经结档")
            return false
        }

        // Mark profile as ended
        data.ended = true
        data.endedId = endId

        // Grant profile slot bonus
        if (template.profileadded > 0) {
            val uuid = player.uniqueId.toString()
            val sharedData = ProfileManager.getSharedData(uuid)
            val currentBonus = sharedData.meta["ended_profile_bonus"]?.value?.toIntOrNull() ?: 0
            val newBonus = currentBonus + template.profileadded
            sharedData.meta["ended_profile_bonus"] = MetaEntry(newBonus.toString())
            ProfileManager.saveSharedData(uuid)
        }

        // Save profile data
        ProfileManager.saveProfileData(player.uniqueId.toString(), data.profileId, data)

        // Build and send the end message
        val sb = StringBuilder()
        sb.append("<white><b>").append(template.major).appendLine()
        sb.appendLine()
        for (line in template.message.lines()) {
            sb.appendLine("  <gray>-<aqua> $line")
        }
        sb.appendLine()
        for (line in template.postmessage.lines()) {
            sb.appendLine("  <gray>-<white> ${line.replace("{profileadded}", template.profileadded.toString())}")
        }
        player.sendComponent(sb.toString())
        return true
    }

    fun isEnded(player: Player): Boolean = player.getData()?.ended == true

    fun getEndId(player: Player): String = player.getData()?.endedId ?: "generic"
}
