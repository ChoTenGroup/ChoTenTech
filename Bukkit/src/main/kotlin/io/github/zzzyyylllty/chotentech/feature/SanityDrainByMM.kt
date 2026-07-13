package io.github.zzzyyylllty.chotentech.feature

import ink.ptms.um.Mythic
import io.github.zzzyyylllty.chotentech.ChoTenTech.config
import io.github.zzzyyylllty.chotentech.data.ChoTenDataHelper
import org.bukkit.Bukkit
import org.bukkit.GameMode
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.submit
import taboolib.common.platform.service.PlatformExecutor

private var mmSanityDrainTask: PlatformExecutor.PlatformTask? = null

@Awake(LifeCycle.ACTIVE)
fun startMMSanityDrain() {
    if (!config.getBoolean("mm-sanity-drain.enabled", true)) return

    val interval = config.getLong("mm-sanity-drain.interval", 20L)
    val range = config.getDouble("mm-sanity-drain.range", 8.0)
    val defaultAmount = config.getDouble("mm-sanity-drain.default-amount", 3.0)

    val mobDrainMap = mutableMapOf<String, Double>()
    val mobsSection = config.getConfigurationSection("mm-sanity-drain.mobs")
    if (mobsSection != null) {
        for (key in mobsSection.getKeys(false)) {
            mobDrainMap[key] = mobsSection.getDouble(key, defaultAmount)
        }
    }

    mmSanityDrainTask = submit(period = interval) {
        for (player in Bukkit.getOnlinePlayers()) {
            if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) continue
            if (player.isDead) continue

            val nearbyEntities = player.getNearbyEntities(range, range, range)
            for (entity in nearbyEntities) {
                val mob = Mythic.API.getMob(entity) ?: continue
                val amount = mobDrainMap[mob.id] ?: defaultAmount
                if (amount <= 0.0) continue

                submit {
                    ChoTenDataHelper(player).removeSanity(amount)
                }
                break
            }
        }
    }
}

@Awake(LifeCycle.DISABLE)
fun cancelMMSanityDrain() {
    mmSanityDrainTask?.cancel()
}
