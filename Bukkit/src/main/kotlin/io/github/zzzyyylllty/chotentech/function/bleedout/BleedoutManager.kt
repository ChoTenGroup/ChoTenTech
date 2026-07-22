package io.github.zzzyyylllty.chotentech.function.bleedout

import io.github.zzzyyylllty.chotentech.ChoTenTech.config
import io.github.zzzyyylllty.chotentech.data.AttributeModifier
import io.github.zzzyyylllty.chotentech.data.AttributeSlot
import io.github.zzzyyylllty.chotentech.data.ModifierSource
import io.github.zzzyyylllty.chotentech.data.ModifierType
import io.github.zzzyyylllty.chotentech.manager.AttributeManager
import io.github.zzzyyylllty.chotentech.util.devLog
import io.github.zzzyyylllty.chotentech.util.toComponent
import net.kyori.adventure.bossbar.BossBar
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.service.PlatformExecutor
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor

object BleedoutManager {

    private val downedPlayers = ConcurrentHashMap<UUID, DownState>()
    private val bleedoutBossBars = ConcurrentHashMap<UUID, BossBar>()
    private var bleedoutTask: PlatformExecutor.PlatformTask? = null
    private var poseTask: PlatformExecutor.PlatformTask? = null
    private val barrierPositions = ConcurrentHashMap<UUID, Location>()

    /** Players whose death was triggered by bleedout expiry — prevents re-entry into bleedout */
    private val pendingDeath = ConcurrentHashMap.newKeySet<UUID>()

    private val BLEEDOUT_SPEED_MOD_UUID = UUID.fromString("00000000-0000-0000-0000-00000000BED0")
    private val TICK_INTERVAL = 10L
    private val GIVE_UP_TICKS = 10 // 5s (10 ticks × 0.5s)

    data class DownState(
        var bleedoutTicks: Int,
        val maxBleedoutTicks: Int,
        var beingRevivedBy: UUID? = null,
        var revivalTicks: Int = 0,
        val revivalMaxTicks: Int,
        val downLocation: Location,
        var giveUpTicks: Int = 0
    )

    fun isDowned(uuid: UUID): Boolean = downedPlayers.containsKey(uuid)
    fun getDownState(uuid: UUID): DownState? = downedPlayers[uuid]

    fun enterBleedout(player: Player) {
        val uuid = player.uniqueId
        if (downedPlayers.containsKey(uuid) || pendingDeath.contains(uuid)) return

        // Eject from vehicle so speed modifier applies properly
        if (player.isInsideVehicle || player.isGliding) {
            player.leaveVehicle()
        }

        val duration = config.getInt("bleedout.duration", 60)
        val revivalTime = config.getInt("bleedout.revival-time", 15)

        // Slow movement to a crawl (configurable %, default -70%)
        val slowPct = config.getDouble("bleedout.crawl-speed-penalty", 0.7).coerceIn(0.0, 1.0)
        AttributeManager.addModifier(uuid, AttributeModifier(
            attributeId = "movement_speed",
            value = -slowPct,
            type = ModifierType.PERCENT,
            source = ModifierSource.OTHER,
            slot = AttributeSlot.OTHER,
            uuid = BLEEDOUT_SPEED_MOD_UUID
        ))

        downedPlayers[uuid] = DownState(
            bleedoutTicks = duration * 2,
            maxBleedoutTicks = duration * 2,
            revivalMaxTicks = revivalTime * 2,
            downLocation = player.location.clone()
        )

        setDownedPose(player, true)

        player.health = 1.0
        player.allowFlight = false
        player.isFlying = false

        player.showTitle(net.kyori.adventure.title.Title.title(
            "<red>⛔ 倒地".toComponent(),
            "<gray>等待队友救援或流血而死...".toComponent()
        ))
        player.sendMessage("<red>⛔ 你受到了致命伤！<gray>倒地期间行动缓慢，等待队友救援吧。".toComponent())
    }

    // ---- Revival ----

    fun startRevival(reviver: Player, downed: Player) {
        val state = downedPlayers[downed.uniqueId] ?: return
        if (state.beingRevivedBy != null) {
            reviver.sendActionBar("<yellow>该玩家正在被其他人救援中".toComponent())
            return
        }
        if (reviver == downed) {
            reviver.sendActionBar("<red>你不能救援自己！".toComponent())
            return
        }
        state.beingRevivedBy = reviver.uniqueId
        state.revivalTicks = 0
        state.bleedoutTicks = state.maxBleedoutTicks // 重置倒地倒计时并暂停
        state.giveUpTicks = 0
        reviver.sendMessage("<green>✦ 开始救援 ${downed.name}... 请保持靠近。".toComponent())
        downed.sendMessage("<green>✦ ${reviver.name} 正在救援你...".toComponent())
    }

    fun cancelRevival(downedUuid: UUID, reason: String = "") {
        val state = downedPlayers[downedUuid] ?: return
        val reviverUuid = state.beingRevivedBy ?: return
        state.beingRevivedBy = null
        state.revivalTicks = 0
        if (reason.isNotEmpty()) {
            Bukkit.getPlayer(reviverUuid)?.sendActionBar("<yellow>$reason".toComponent())
            Bukkit.getPlayer(downedUuid)?.sendActionBar("<red>救援中断 — $reason".toComponent())
        }
    }

    private fun completeRevival(uuid: UUID) {
        val state = downedPlayers[uuid] ?: return
        val player = Bukkit.getPlayer(uuid) ?: return

        cleanupState(uuid)

        val pct = config.getDouble("bleedout.revive-health", 0.2)
        val maxHp = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: 20.0
        player.health = (maxHp * pct).coerceAtLeast(1.0)

        player.showTitle(net.kyori.adventure.title.Title.title(
            "<green>✅ 已复苏".toComponent(),
            "<gray>你被队友从死亡线上拉了回来".toComponent()
        ))
        player.sendMessage("<green>✅ 你已被成功救援！".toComponent())

        val reviverUuid = state.beingRevivedBy
        if (reviverUuid != null) {
            Bukkit.getPlayer(reviverUuid)?.sendMessage("<green>✅ 成功救援 ${player.name}！".toComponent())
        }
    }

    // ---- Bleedout expiry ----

    private fun expireBleedout(uuid: UUID) {
        pendingDeath.add(uuid)    // set death flag BEFORE cleanup to prevent re-entry
        cleanupState(uuid)

        val player = Bukkit.getPlayer(uuid) ?: return

        player.sendMessage("<red>💀 你因失血过多而死亡...".toComponent())
        submit {
            // Guard: player may have been revived, healed, or reconnected since the submit was queued
            if (player.isOnline && !player.isDead && player.health > 0.0 && player.health < 2.0) {
                player.health = 0.0
            }
            pendingDeath.remove(uuid)
        }
    }

    private fun cleanupState(uuid: UUID) {
        downedPlayers.remove(uuid)
        AttributeManager.removeModifierByUuid(uuid, BLEEDOUT_SPEED_MOD_UUID)
        removeBossBar(uuid)
        Bukkit.getPlayer(uuid)?.let { setDownedPose(it, false) }
    }

    private fun removeBossBar(uuid: UUID) {
        bleedoutBossBars.remove(uuid)?.let { bar ->
            Bukkit.getPlayer(uuid)?.let { bar.removeViewer(it) }
        }
    }

    // ---- Tick ----

    @Awake(LifeCycle.ACTIVE)
    fun startBleedoutTick() {
        if (!config.getBoolean("bleedout.enabled", true)) return
        bleedoutTask = submit(period = TICK_INTERVAL) { tick() }
        poseTask = submit(period = TICK_INTERVAL) { tickPose() }
    }

    @Awake(LifeCycle.DISABLE)
    fun stopBleedoutTick() {
        bleedoutTask?.cancel()
        bleedoutTask = null
        poseTask?.cancel()
        poseTask = null
        bleedoutBossBars.values.forEach { bar ->
            bar.viewers().toSet().forEach { bar.removeViewer(it as net.kyori.adventure.audience.Audience) }
        }
        bleedoutBossBars.clear()
        barrierPositions.values.forEach { sendRealBlock(it) }
        barrierPositions.clear()
        downedPlayers.clear()
        pendingDeath.clear()
    }

    private fun tick() {
        val toRemove = mutableListOf<UUID>()

        for ((uuid, state) in downedPlayers) {
            val player = Bukkit.getPlayer(uuid) ?: run { toRemove.add(uuid); continue }

            // --- 放弃（长按Shift 5s） ---
            if (player.isSneaking) {
                state.giveUpTicks++
                if (state.giveUpTicks >= GIVE_UP_TICKS) {
                    expireBleedout(uuid)
                    toRemove.add(uuid)
                    continue
                }
            } else {
                state.giveUpTicks = 0
            }

            // --- 救援进度 ---
            val reviverId = state.beingRevivedBy
            if (reviverId != null) {
                val reviver = Bukkit.getPlayer(reviverId)
                when {
                    reviver == null || !reviver.isOnline -> cancelRevival(uuid, "救援者已离开")
                    reviver.location.distanceSquared(player.location) > 4.0 -> cancelRevival(uuid, "救援者距离过远")
                    else -> {
                        state.revivalTicks++
                        if (state.revivalTicks >= state.revivalMaxTicks) {
                            completeRevival(uuid)
                            toRemove.add(uuid)
                            continue
                        }
                    }
                }
            }

            // --- 倒地倒计时（救援中暂停） ---
            if (state.beingRevivedBy == null) {
                state.bleedoutTicks--
                if (state.bleedoutTicks <= 0) {
                    expireBleedout(uuid)
                    toRemove.add(uuid)
                    continue
                }
            }

            // --- 进度条（BossBar） ---
            var bar = bleedoutBossBars[uuid]
            if (bar == null) {
                bar = BossBar.bossBar(
                    "<red>🩸 失血中...".toComponent(),
                    1.0f, BossBar.Color.RED, BossBar.Overlay.PROGRESS
                )
                bar.addViewer(player)
                bleedoutBossBars[uuid] = bar
            }
            // 救援中显示救援进度，否则显示失血进度
            if (state.beingRevivedBy != null) {
                val pct = state.revivalTicks * 100 / state.revivalMaxTicks
                bar.name("<green>✦ 救援中... $pct%".toComponent())
                bar.progress((state.revivalTicks.toFloat() / state.revivalMaxTicks).coerceIn(0f, 1f))
                bar.color(BossBar.Color.GREEN)
            } else {
                val pct = state.bleedoutTicks * 100 / state.maxBleedoutTicks
                bar.name("<red>🩸 失血中... $pct%".toComponent())
                bar.progress((state.bleedoutTicks.toFloat() / state.maxBleedoutTicks).coerceIn(0f, 1f))
                bar.color(BossBar.Color.RED)
            }

            // --- ActionBar ---
            val sec = state.bleedoutTicks / 2
            when {
                state.giveUpTicks > 0 -> {
                    val pct = state.giveUpTicks * 100 / GIVE_UP_TICKS
                    player.sendActionBar("<red>⛔ 放弃中... ${pct}% <gray>(松开Shift取消)".toComponent())
                }
                state.beingRevivedBy != null -> {
                    val name = Bukkit.getOfflinePlayer(state.beingRevivedBy!!).name ?: "未知"
                    val pct = state.revivalTicks * 100 / state.revivalMaxTicks
                    player.sendActionBar("<red>⛔ 倒地中 <gray>| <green>$name <gray>救援中 $pct%".toComponent())
                }
                else -> {
                    player.sendActionBar("<red>⛔ 倒地中 <gray>| <white>${sec}秒 <gray>后失血死亡  <dark_gray>[Shift放弃]".toComponent())
                }
            }
        }

        // Clean up players that were removed during the loop
        for (uuid in toRemove) {
            if (downedPlayers.containsKey(uuid)) cleanupState(uuid)
        }

        // Remove stale boss bars
        bleedoutBossBars.keys.removeIf { it !in downedPlayers.keys && it !in pendingDeath }
    }

    private fun tickPose() {
        for (uuid in downedPlayers.keys) {
            val player = Bukkit.getPlayer(uuid) ?: continue
            val headLoc = player.location.clone().apply { y = floor(y + 1.8).toDouble() }
            val old = barrierPositions[uuid]

            // 位置变了 → 还原旧位置，设置新位置
            if (old != null && (old.blockX != headLoc.blockX || old.blockY != headLoc.blockY || old.blockZ != headLoc.blockZ)) {
                sendRealBlock(old)
            }
            if (old == null || old.blockX != headLoc.blockX || old.blockY != headLoc.blockY || old.blockZ != headLoc.blockZ) {
                sendFakeBarrier(headLoc)
                barrierPositions[uuid] = headLoc
            }
        }
    }

    private fun sendFakeBarrier(loc: Location) {
        val barrier = org.bukkit.Material.BARRIER.createBlockData()
        for (p in Bukkit.getOnlinePlayers()) {
            p.sendBlockChange(loc, barrier)
        }
    }

    private fun sendRealBlock(loc: Location) {
        val real = loc.world?.getBlockAt(loc)?.blockData ?: return
        for (p in Bukkit.getOnlinePlayers()) {
            p.sendBlockChange(loc, real)
        }
    }

    private fun setDownedPose(player: Player, downed: Boolean) {
        if (downed) {
            // 进入倒地 → 在头部位置放屏障
            val headLoc = player.location.clone().apply { y = floor(y + 1.8).toDouble() }
            sendFakeBarrier(headLoc)
            barrierPositions[player.uniqueId] = headLoc
        } else {
            // 恢复 → 还原方块
            barrierPositions.remove(player.uniqueId)?.let { sendRealBlock(it) }
        }
    }

    // ---- Event handlers ----

    /** Intercept lethal damage → enter bleedout instead of dying */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onFatalDamage(event: EntityDamageEvent) {
        if (event.entity !is Player) return
        if (event.isCancelled) return
        if (!config.getBoolean("bleedout.enabled", true)) return

        val player = event.entity as Player
        val uuid = player.uniqueId
        if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return
        if (event.finalDamage <= 0.0) return

        // Bypass bleedout for certain damage causes (void, poison, wither, kill command)
        if (event.cause == EntityDamageEvent.DamageCause.VOID
            || event.cause == EntityDamageEvent.DamageCause.POISON
            || event.cause == EntityDamageEvent.DamageCause.WITHER
            || event.cause == EntityDamageEvent.DamageCause.KILL) return

        // Player is about to die from bleedout expiry — let death proceed
        if (pendingDeath.contains(uuid)) return

        // Already downed — cancel damage, optionally accelerate bleedout
        val state = downedPlayers[uuid]
        if (state != null) {
            event.isCancelled = true
            if (config.getBoolean("bleedout.damage-accelerates-bleedout", true)) {
                val penalty = config.getInt("bleedout.damage-penalty", 10) * 2
                state.bleedoutTicks = maxOf(state.bleedoutTicks - penalty, 0)
                if (state.beingRevivedBy != null) cancelRevival(uuid, "目标再次受伤，救援中断")
                if (state.bleedoutTicks <= 0) expireBleedout(uuid)
            }
            return
        }

        // Check if damage would kill (account for absorption hearts)
        val effectiveHp = player.health + player.absorptionAmount
        if (effectiveHp - event.finalDamage <= 0.0) {
            event.isCancelled = true
            enterBleedout(player)
        }
    }

    /** Allow downed players to crawl slowly anywhere */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onDownedMove(event: PlayerMoveEvent) {
        if (event.isCancelled) return
        val state = downedPlayers[event.player.uniqueId] ?: return
        // Allow looking around without position change
        if (event.from.blockX == event.to.blockX
            && event.from.blockY == event.to.blockY
            && event.from.blockZ == event.to.blockZ) return
        // Movement is restricted by speed modifier only — no radius limit
    }

    /** Right-click a downed player to start revival */
    @SubscribeEvent
    fun onReviveInteract(event: PlayerInteractEntityEvent) {
        if (event.player.gameMode == GameMode.SPECTATOR) return
        val target = event.rightClicked as? Player ?: return
        if (!downedPlayers.containsKey(target.uniqueId)) return
        event.isCancelled = true
        val state = downedPlayers[target.uniqueId] ?: return
        if (state.beingRevivedBy != null) {
            event.player.sendActionBar("<yellow>该玩家正在被其他人救援中".toComponent())
            return
        }
        startRevival(event.player, target)
    }

    @SubscribeEvent
    fun onPlayerQuitClear(event: PlayerQuitEvent) {
        val uuid = event.player.uniqueId
        downedPlayers.remove(uuid)
        AttributeManager.removeModifierByUuid(uuid, BLEEDOUT_SPEED_MOD_UUID)
        removeBossBar(uuid)
        pendingDeath.remove(uuid)
        setDownedPose(event.player, false)
    }

    @SubscribeEvent
    fun onRespawnClear(event: PlayerRespawnEvent) {
        pendingDeath.remove(event.player.uniqueId)
    }

    /** If a downed player somehow dies via non-damage means, clean up */
    @SubscribeEvent
    fun onDeathCleanup(event: PlayerDeathEvent) {
        val uuid = event.player.uniqueId
        downedPlayers.remove(uuid)
        AttributeManager.removeModifierByUuid(uuid, BLEEDOUT_SPEED_MOD_UUID)
        removeBossBar(uuid)
        setDownedPose(event.player, false)
    }
}
