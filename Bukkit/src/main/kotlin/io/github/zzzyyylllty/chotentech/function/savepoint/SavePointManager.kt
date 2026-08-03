package io.github.zzzyyylllty.chotentech.function.savepoint

import io.github.zzzyyylllty.chotentech.data.PlayerData
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.data.profile.ProfileManager
import io.github.zzzyyylllty.chotentech.util.devLog
import io.github.zzzyyylllty.chotentech.util.toComponent
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.entity.Player
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.service.PlatformExecutor
import java.util.Optional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object SavePointManager {

    /** Players in the progress of saving — uuid -> (savePointId, currentTick, maxTicks) */
    private val savingPlayers = ConcurrentHashMap<UUID, SaveProgress>()

    /** Recall progress (60s recall from base to normal) — uuid -> currentTick */
    private val recallingPlayers = ConcurrentHashMap<UUID, Int>()

    /** Players currently inside a save zone — uuid -> save point id */
    private val playersInZone = ConcurrentHashMap<UUID, String>()

    /** Players who have been hinted about the first-time mechanic */
    private val hintedPlayers = ConcurrentHashMap.newKeySet<UUID>()

    /** Created hologram IDs */
    private val hologramIds = ConcurrentHashMap.newKeySet<String>()

    private var tickTask: PlatformExecutor.PlatformTask? = null
    private var particleTask: PlatformExecutor.PlatformTask? = null

    private data class SaveProgress(
        val savePointId: String,
        var ticks: Int,
        val maxTicks: Int,
        val callback: () -> Unit
    )

    // ---- Hologram management ----

    fun createHolograms() {
        try {
            val fhPlugin = Bukkit.getPluginManager().getPlugin("FancyHolograms")
            if (fhPlugin == null || !fhPlugin.isEnabled) return

            val fhClass = Class.forName("de.oliver.fancyholograms.api.FancyHologramsPlugin")
            val api = fhClass.getMethod("get").invoke(null)
            val manager = api::class.java.getMethod("getHologramManager").invoke(api)

            // FancyHolograms 2.9.x API: TextHologramData(String name, Location location) + setText(List<String>) + manager.create(data)
            val textDataClass = Class.forName("de.oliver.fancyholograms.api.data.TextHologramData")
            val textDataCtor = textDataClass.getConstructor(String::class.java, Location::class.java)
            val setTextMethod = textDataClass.getMethod("setText", List::class.java)
            val createMethod = manager::class.java.getMethod(
                "create",
                Class.forName("de.oliver.fancyholograms.api.data.HologramData")
            )

            for (sp in SavePointConfig.getAll()) {
                val holoId = "chotentech_save_${sp.id}"
                // Remove existing
                try { removeSingleHologram(manager, holoId) } catch (_: Exception) {}

                val lines = mutableListOf(
                    if (sp.isBase) "<aqua><b>基地存档点</b></aqua>" else "<green><b>存档点</b></green>",
                    sp.name
                )

                val textData = textDataCtor.newInstance(holoId, sp.effectiveHoloPos)
                setTextMethod.invoke(textData, lines)
                createMethod.invoke(manager, textData)
                hologramIds.add(holoId)
            }

            devLog("<gray>[SavePoint] 创建了 ${hologramIds.size} 个存档点全息图")
        } catch (e: Exception) {
            devLog("<yellow>[SavePoint] FancyHolograms 不可用，跳过全息图: ${e.message}")
        }
    }

    private fun removeSingleHologram(manager: Any, holoId: String) {
        try {
            // getHologram(id) 返回 Optional<Hologram>
            val opt = manager::class.java.getMethod("getHologram", String::class.java)
                .invoke(manager, holoId) as? Optional<*> ?: return
            opt.ifPresent { holo ->
                runCatching {
                    manager::class.java.getMethod("removeHologram", holo::class.java).invoke(manager, holo)
                }
            }
        } catch (_: Exception) { /* FancyHolograms not available */ }
    }

    fun removeHolograms() {
        try {
            val fhClass = Class.forName("de.oliver.fancyholograms.api.FancyHologramsPlugin")
            val api = fhClass.getMethod("get").invoke(null)
            val manager = api::class.java.getMethod("getHologramManager").invoke(api)
            for (id in hologramIds) {
                try { removeSingleHologram(manager, id) } catch (_: Exception) {}
            }
            hologramIds.clear()
        } catch (_: Exception) { /* FancyHolograms not available */ }
    }

    // ---- Zone border particles ----

    private fun displayZoneParticles() {
        for (sp in SavePointConfig.getAll()) {
            val world = Bukkit.getWorld(sp.world) ?: continue

            // Only show particles if a player is within 48 blocks
            val hasNearby = world.players.any { p ->
                val midX = (sp.pos1.x + sp.pos2.x) / 2
                val midZ = (sp.pos1.z + sp.pos2.z) / 2
                val dx = p.location.x - midX
                val dz = p.location.z - midZ
                dx * dx + dz * dz < 2304.0
            }
            if (!hasNearby) continue

            // 彩色烟雾粒子（1.21.4 的 SMOKE 不支持着色，用 ENTITY_EFFECT 实现彩色烟雾）
            val color = if (sp.isBase) Color.fromRGB(0x55FFFF) else Color.fromRGB(0x55FF55)
            val step = 0.5
            fun spawn(loc: Location) {
                world.spawnParticle(Particle.ENTITY_EFFECT, loc, 1, 0.0, 0.0, 0.0, 1.0, color)
            }

            val minX = minOf(sp.pos1.x, sp.pos2.x)
            val maxX = maxOf(sp.pos1.x, sp.pos2.x)
            val minY = minOf(sp.pos1.y, sp.pos2.y)
            val maxY = maxOf(sp.pos1.y, sp.pos2.y)
            val minZ = minOf(sp.pos1.z, sp.pos2.z)
            val maxZ = maxOf(sp.pos1.z, sp.pos2.z)

            // Bottom and top horizontal edges (X axis)
            var x = minX
            while (x <= maxX) {
                spawn(Location(world, x, minY, minZ))
                spawn(Location(world, x, minY, maxZ))
                if (maxY > minY) {
                    spawn(Location(world, x, maxY, minZ))
                    spawn(Location(world, x, maxY, maxZ))
                }
                x += step
            }

            // Bottom and top horizontal edges (Z axis)
            var z = minZ
            while (z <= maxZ) {
                spawn(Location(world, minX, minY, z))
                spawn(Location(world, maxX, minY, z))
                if (maxY > minY) {
                    spawn(Location(world, minX, maxY, z))
                    spawn(Location(world, maxX, maxY, z))
                }
                z += step
            }

            // Vertical corner edges
            if (maxY > minY) {
                var y = minY
                while (y <= maxY) {
                    spawn(Location(world, minX, y, minZ))
                    spawn(Location(world, maxX, y, minZ))
                    spawn(Location(world, minX, y, maxZ))
                    spawn(Location(world, maxX, y, maxZ))
                    y += step
                }
            }
        }
    }

    // ---- Save logic ----

    /** Get the respawn location based on player saves */
    fun getRespawnLocation(player: Player): Location? {
        val data = player.getData() ?: return defaultSpawn()
        val saveId = data.baseSave ?: data.normalSave ?: return defaultSpawn()
        val sp = SavePointConfig.get(saveId) ?: return defaultSpawn()
        return sp.respawnPos?.toLocation() ?: sp.effectiveHoloPos
    }

    private fun defaultSpawn(): Location? {
        return Bukkit.getWorld("Area1")?.let { Location(it, 10176.0, 3.0, 269.0) }
    }

    /** Called every second: check player zone and handle input */
    fun tickPlayer(player: Player) {
        val data = player.getData() ?: return

        val loc = player.location
        val sp = SavePointConfig.findContaining(loc)
        val prevSpId = playersInZone[player.uniqueId]

        // Zone enter/exit
        if (sp != null) {
            if (prevSpId == null || prevSpId != sp.id) {
                playersInZone[player.uniqueId] = sp.id
                onEnterSaveZone(player, sp)
            }
        } else {
            if (prevSpId != null) {
                playersInZone.remove(player.uniqueId)
                savingPlayers.remove(player.uniqueId)
            }
        }

        val inZone = sp != null

        // 60s recall takes priority over saving (limited time window)
        val canRecall = inZone && sp!!.isBase && data.baseSaveSpawnTime != 0L && data.normalSave != null

        if (inZone && player.isSneaking && player.location.pitch <= -80) {
            if (canRecall) {
                tickRecall(player, data, sp!!)
                // Don't start saving while recall is in progress
                if (recallingPlayers.containsKey(player.uniqueId)) {
                    savingPlayers.remove(player.uniqueId)
                    return
                }
            }

            // Normal save progress
            val prog = savingPlayers.computeIfAbsent(player.uniqueId) {
                SaveProgress(sp!!.id, 0, if (sp.isBase) 5 else 3) {
                    completeSave(player, sp!!, data)
                }
            }
            if (prog.savePointId != sp!!.id) {
                savingPlayers.remove(player.uniqueId)
                return
            }
            prog.ticks++
            val pct = prog.ticks * 100 / prog.maxTicks
            player.sendActionBar("<green>✦ 正在建立存档... $pct%".toComponent())
            if (prog.ticks >= prog.maxTicks) {
                prog.callback()
                savingPlayers.remove(player.uniqueId)
            }
        } else {
            // Look up without sneak → hint
            if (inZone && player.location.pitch <= -80 && !savingPlayers.containsKey(player.uniqueId)) {
                player.sendActionBar("<gray>蹲下并抬头 <white>${if (sp!!.isBase) 5 else 3}秒<gray> 建立存档".toComponent())
            }
            savingPlayers.remove(player.uniqueId)

            if (!canRecall) {
                recallingPlayers.remove(player.uniqueId)
            }
        }
    }

    private fun onEnterSaveZone(player: Player, sp: SavePoint) {
        // First-time mechanic hint
        if (hintedPlayers.add(player.uniqueId)) {
            player.sendMessage("<gold>■ <yellow>你发现了一个存档点！".toComponent())
            player.sendMessage("<gray>蹲下并抬头 <white>${if (sp.isBase) 5 else 3}秒<gray> 可在此建立存档。".toComponent())
        }

        val typeName = if (sp.isBase) "<aqua>基地存档点" else "<green>存档点"
        player.showTitle(net.kyori.adventure.title.Title.title(
            typeName.toComponent(),
            "<gray>${sp.name}".toComponent(),
            net.kyori.adventure.title.Title.Times.times(
                java.time.Duration.ofMillis(500),
                java.time.Duration.ofMillis(2000),
                java.time.Duration.ofMillis(500)
            )
        ))
    }

    private fun completeSave(player: Player, sp: SavePoint, data: PlayerData) {
        if (sp.isBase) {
            data.baseSave = sp.id
            player.sendMessage("<aqua>■ 基地存档点已更新: <yellow>${sp.name}".toComponent())
        } else {
            data.normalSave = sp.id
            player.sendMessage("<green>■ 存档点已更新: <yellow>${sp.name}".toComponent())
        }
        player.showTitle(net.kyori.adventure.title.Title.title(
            "<green>✅ 存档成功".toComponent(),
            "<gray>${sp.name}".toComponent(),
            net.kyori.adventure.title.Title.Times.times(
                java.time.Duration.ofMillis(200),
                java.time.Duration.ofMillis(1500),
                java.time.Duration.ofMillis(500)
            )
        ))
    }

    // ---- 60s recall ----

    fun onBaseSpawn(player: Player, data: PlayerData) {
        if (data.baseSave == null) return
        data.baseSaveSpawnTime = System.currentTimeMillis()
        if (data.normalSave != null) {
            val setting = ProfileManager.getSharedData(player.uniqueId.toString()).settings
            if (setting.getBoolean("recallMessage")) {
                player.sendMessage("<yellow>✦ 你可在60秒内，在当前的基地存档点内抬头3秒，返回上个普通存档点。".toComponent())
            }
        }
    }

    private fun tickRecall(player: Player, data: PlayerData, sp: SavePoint) {
        if (data.baseSaveSpawnTime == 0L || data.normalSave == null) {
            recallingPlayers.remove(player.uniqueId)
            return
        }
        if (System.currentTimeMillis() - data.baseSaveSpawnTime > 60_000) {
            data.baseSaveSpawnTime = 0L
            recallingPlayers.remove(player.uniqueId)
            return
        }
        if (sp.id != data.baseSave) {
            recallingPlayers.remove(player.uniqueId)
            return
        }

        val ticks = recallingPlayers.merge(player.uniqueId, 1) { old, _ -> old + 1 }!!
        val pct = ticks * 100 / 3
        player.sendActionBar("<yellow>⏪ 返回上个存档点... $pct%".toComponent())

        if (ticks >= 3) {
            doRecall(player, data)
            recallingPlayers.remove(player.uniqueId)
        }
    }

    private fun doRecall(player: Player, data: PlayerData) {
        val normalSp = SavePointConfig.get(data.normalSave ?: return) ?: return
        player.teleportAsync(normalSp.effectiveHoloPos)
        data.baseSaveSpawnTime = 0L
        player.sendMessage("<green>■ 已返回普通存档点: <yellow>${normalSp.name}".toComponent())
    }

    // ---- Lifecycle ----

    @Awake(LifeCycle.ACTIVE)
    fun start() {
        SavePointConfig.load()
        createHolograms()

        tickTask = submitAsync(period = 20) {
            for (player in Bukkit.getOnlinePlayers()) {
                tickPlayer(player)
            }
        }

        particleTask = submitAsync(period = 10) {
            displayZoneParticles()
        }
    }

    @Awake(LifeCycle.DISABLE)
    fun stop() {
        tickTask?.cancel()
        tickTask = null
        particleTask?.cancel()
        particleTask = null
        removeHolograms()
        savingPlayers.clear()
        recallingPlayers.clear()
        playersInZone.clear()
        hintedPlayers.clear()
    }

    fun clearPlayerState(player: Player) {
        savingPlayers.remove(player.uniqueId)
        recallingPlayers.remove(player.uniqueId)
        playersInZone.remove(player.uniqueId)
    }
}
