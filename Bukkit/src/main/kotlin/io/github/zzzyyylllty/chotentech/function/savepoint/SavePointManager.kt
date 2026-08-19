package io.github.zzzyyylllty.chotentech.function.savepoint

import io.github.zzzyyylllty.chotentech.data.PlayerData
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.data.profile.ProfileManager
import de.oliver.fancyholograms.api.FancyHologramsPlugin
import de.oliver.fancyholograms.api.HologramManager
import de.oliver.fancyholograms.api.data.TextHologramData
import io.github.zzzyyylllty.chotentech.util.devLog
import io.github.zzzyyylllty.chotentech.util.toComponent
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.entity.Player
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.info
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.function.warning
import taboolib.common.platform.service.PlatformExecutor
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom

object SavePointManager {

    /** Players in the progress of saving — uuid -> (savePointId, currentTick, maxTicks) */
    private val savingPlayers = ConcurrentHashMap<UUID, SaveProgress>()

    /** Recall progress (60s recall from base to normal) — uuid -> currentTick */
    private val recallingPlayers = ConcurrentHashMap<UUID, Int>()

    /** Players currently inside a save zone — uuid -> save point id */
    private val playersInZone = ConcurrentHashMap<UUID, String>()

    /** Players who have been hinted about the first-time mechanic */

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
        val fhPlugin = Bukkit.getPluginManager().getPlugin("FancyHolograms")
        if (fhPlugin == null || !fhPlugin.isEnabled) {
            info("[SavePoint] FancyHolograms 未启用，跳过全息图")
            return
        }
        val manager = FancyHologramsPlugin.get().hologramManager
        for (sp in SavePointConfig.getAll()) {
            val holoId = "chotentech_save_${sp.id}"
            try {
                removeSingleHologram(manager, holoId)
                val pos = sp.effectiveHoloPos
                if (pos.world == null) {
                    warning("[SavePoint] 存档点 ${sp.id} 的世界 ${sp.world} 不存在，跳过全息图")
                    continue
                }
                val textData = TextHologramData(holoId, pos)
                textData.setText(
                    listOf(
                        if (sp.isBase) "<aqua><b>基地存档点</b></aqua>" else "<green><b>存档点</b></green>",
                        sp.name
                    )
                )
                manager.create(textData)
                hologramIds.add(holoId)
            } catch (e: Exception) {
                warning("[SavePoint] 创建存档点 ${sp.id} 全息图失败: ${e.message}")
            }
        }
        info("[SavePoint] 创建了 ${hologramIds.size} 个存档点全息图")
    }

    private fun removeSingleHologram(manager: HologramManager, holoId: String) {
        manager.getHologram(holoId).ifPresent { holo ->
            runCatching { manager.removeHologram(holo) }
        }
    }

    fun removeHolograms() {
        try {
            val manager = FancyHologramsPlugin.get().hologramManager
            for (id in hologramIds) {
                removeSingleHologram(manager, id)
            }
            hologramIds.clear()
        } catch (e: Exception) {
            warning("[SavePoint] 移除全息图失败: ${e.message}")
        }
    }

    // ---- Zone border particles ----

    /** FALLING_DUST 着色 BlockData 缓存:不可变对象,复用避免每 10 tick 重复创建 */
    private val baseShades = arrayOf(
        Material.LIGHT_BLUE_CONCRETE.createBlockData(),
        Material.CYAN_CONCRETE.createBlockData()
    )
    private val normalShades = arrayOf(
        Material.LIME_CONCRETE.createBlockData(),
        Material.GREEN_CONCRETE.createBlockData()
    )

    /** ENTITY_EFFECT 粒子颜色缓存 */
    private val baseParticleColor = Color.fromRGB(80, 190, 255)
    private val normalParticleColor = Color.fromRGB(80, 255, 80)

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

            // FALLING_DUST 粒子以方块颜色着色，每次生成随机浅/深色：
            // 普通存档点绿色（浅绿/深绿混凝土）、基地存档点青色（浅蓝/青色混凝土）
            val shades = if (sp.isBase) baseShades else normalShades
            val step = 0.5
            fun spawn(loc: Location) {
                val blockData = shades[ThreadLocalRandom.current().nextInt(shades.size)]
                world.spawnParticle(Particle.FALLING_DUST, loc, 1, 0.0, 0.0, 0.0, 1.0, blockData)
            }

            val minX = minOf(sp.pos1.x, sp.pos2.x)
            val maxX = maxOf(sp.pos1.x, sp.pos2.x)
            val minY = minOf(sp.pos1.y, sp.pos2.y)
            val maxY = maxOf(sp.pos1.y, sp.pos2.y)
            val minZ = minOf(sp.pos1.z, sp.pos2.z)
            val maxZ = maxOf(sp.pos1.z, sp.pos2.z)

            // 存档点中心向外发散粒子:普通存档点亮绿,基地存档点亮蓝
            val center = Location(
                world,
                (sp.pos1.x + sp.pos2.x) / 2,
                (minY + maxY) / 2,
                (sp.pos1.z + sp.pos2.z) / 2
            )
            val color = if (sp.isBase) baseParticleColor else normalParticleColor
            for (i in 0 until 10) {
                val theta = ThreadLocalRandom.current().nextDouble(0.0, 2.0 * Math.PI)
                val vx = Math.cos(theta) * 0.5
                val vz = Math.sin(theta) * 0.5
                val vy = ThreadLocalRandom.current().nextDouble(0.5)
                world.spawnParticle(Particle.ENTITY_EFFECT, center, 5, vx, vy, vz, 1.0, color)
            }

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

    /** 复活优先级:基地存档点 -> 普通存档点 -> Area0 兜底;记录失效(配置删除/世界不存在)自动降级 */
    fun getRespawnLocation(player: Player): Location? {
        val data = player.getData() ?: return defaultSpawn()
        for (saveId in listOfNotNull(data.baseSave, data.normalSave)) {
            val sp = SavePointConfig.get(saveId) ?: continue
            if (Bukkit.getWorld(sp.world) == null) continue
            return sp.respawnPos?.toLocation() ?: sp.effectiveHoloPos
        }
        return defaultSpawn()
    }

    private fun defaultSpawn(): Location? {
        return Bukkit.getWorld("Area0")?.let { Location(it, -6.0, 14.0, 9.0) }
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

        // Recall (no time limit) — takes priority over saving
        val canRecall = inZone && sp!!.isBase && data.normalSave != null

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
            val totalBars = 10
            val filled = (prog.ticks * totalBars / prog.maxTicks).toInt().coerceIn(0, totalBars)
            val bar = "<green>${"▌".repeat(filled)}<gray>${"▌".repeat(totalBars - filled)}"
            player.sendActionBar("<green>✦ 正在建立存档... $bar <white>$pct%".toComponent())
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
        // 每次进入存档点区域都显示提示
        player.sendMessage("<gold>■ <yellow>你发现了一个存档点！".toComponent())
        player.sendMessage("<gray>蹲下并抬头 <white>${if (sp.isBase) 5 else 3}秒<gray> 可在此建立存档。".toComponent())

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

    // ---- Recall ----

    fun onBaseSpawn(player: Player, data: PlayerData) {
        if (data.baseSave == null) return
        if (data.normalSave != null) {
            val setting = ProfileManager.getSharedData(player.uniqueId.toString()).settings
            if (setting.getBoolean("recallMessage")) {
                player.sendMessage("<red>✦ 看起来你不太走运...".toComponent())
                player.sendMessage("<yellow>✦ 在蓝色的基地存档点内抬头，即可返回上个普通的绿色存档点。".toComponent())
            }
        }
    }

    private fun tickRecall(player: Player, data: PlayerData, sp: SavePoint) {
        if (data.normalSave == null) {
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
        player.sendMessage("<green>■ 已返回普通存档点: <yellow>${normalSp.name}".toComponent())
    }

    // ---- Lifecycle ----

    @Awake(LifeCycle.ACTIVE)
    fun start() {
        SavePointConfig.load()
        createHolograms()

        tickTask = submit(period = 20) {
            for (player in Bukkit.getOnlinePlayers()) {
                tickPlayer(player)
            }
        }

        particleTask = submit(period = 10) {
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
    }

    fun clearPlayerState(player: Player) {
        savingPlayers.remove(player.uniqueId)
        recallingPlayers.remove(player.uniqueId)
        playersInZone.remove(player.uniqueId)
    }
}
