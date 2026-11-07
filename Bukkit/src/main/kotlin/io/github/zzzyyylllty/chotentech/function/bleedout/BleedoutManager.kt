package io.github.zzzyyylllty.chotentech.function.bleedout

import io.github.zzzyyylllty.chotentech.ChoTenTech.config
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.data.SHOCK_SEQUELA
import io.github.zzzyyylllty.chotentech.function.body.BodyHealthManager
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
import org.bukkit.entity.Projectile
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerGameModeChangeEvent
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

/** 判断伤害是否由玩家(或其弹射物)造成:倒地者对玩家攻击免疫用 */
fun isPlayerInflicted(event: EntityDamageEvent): Boolean {
    if (event !is EntityDamageByEntityEvent) return false
    val damager = event.damager
    if (damager is Player) return true
    return damager is Projectile && damager.shooter is Player
}

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
    private val GIVE_UP_TICKS = 5 // 2.5s (5 ticks × 0.5s)

    data class DownState(
        var bleedoutTicks: Int,
        var maxBleedoutTicks: Int,
        var beingRevivedBy: UUID? = null,
        var revivalTicks: Int = 0,
        val revivalMaxTicks: Int,
        val downLocation: Location,
        var giveUpTicks: Int = 0,
        var phase: String = "DOWNED",
        val body: io.github.zzzyyylllty.chotentech.data.PlayerBodyHealth? = null
    )

    fun isDowned(uuid: UUID): Boolean = Bukkit.getPlayer(uuid)?.getData()?.bodyHealth?.downPhase != null
    fun getDownState(uuid: UUID): DownState? = downedPlayers[uuid]
    fun isShocked(uuid: UUID): Boolean = Bukkit.getPlayer(uuid)?.getData()?.bodyHealth?.downPhase == "SHOCK"

    fun forceDeath(player: Player) {
        pendingDeath.add(player.uniqueId)
        cleanupState(player.uniqueId)
        player.health = 0.0
        pendingDeath.remove(player.uniqueId)
    }

    private fun enterShock(player: Player, state: DownState) {
        state.phase = "SHOCK"
        state.maxBleedoutTicks = config.getInt("bleedout.shock-duration", 120).coerceAtLeast(1) * 2
        state.bleedoutTicks = state.maxBleedoutTicks
        player.velocity = org.bukkit.util.Vector()
        player.getData()?.bodyHealth?.let { it.downPhase = "SHOCK"; it.downTicks = state.bleedoutTicks }
        player.sendMessage("<#E08A98>你已进入休克，无法移动。等待救援或按住Shift放弃。".toComponent())
    }

    /**
     * 调试用：直接推入休克阶段。未倒地时先补一次 [enterBleedout]，使状态机与计时器保持一致。
     * 正常流程不应调用这里——倒地在计时结束后由 tick 自然转入休克。
     */
    fun debugEnterShock(player: Player): Boolean {
        if (!downedPlayers.containsKey(player.uniqueId)) enterBleedout(player)
        val state = downedPlayers[player.uniqueId] ?: return false
        enterShock(player, state)
        return true
    }

    fun damageDowned(player: Player) {
        val state = downedPlayers[player.uniqueId] ?: return
        if (state.phase == "SHOCK") return
        if (state.beingRevivedBy != null) cancelRevival(player.uniqueId, "目标再次受伤，救援中断")
        if (config.getBoolean("bleedout.damage-accelerates-bleedout", true)) {
            state.bleedoutTicks -= config.getInt("bleedout.damage-penalty", 10).coerceAtLeast(0) * 2
            if (state.bleedoutTicks <= 0) enterShock(player, state)
        }
    }


    fun enterBleedout(player: Player) {
        if (!config.getBoolean("bleedout.enabled", true)) { forceDeath(player); return }
        val uuid = player.uniqueId
        if (downedPlayers.containsKey(uuid) || pendingDeath.contains(uuid)) return

        // Eject from vehicle so speed modifier applies properly
        if (player.isInsideVehicle || player.isGliding) {
            player.leaveVehicle()
        }

        val duration = config.getInt("bleedout.duration", 60).coerceAtLeast(1)
        val revivalTime = config.getInt("bleedout.revival-time", 5).coerceAtLeast(1)

        // Slow movement to a crawl (configurable %, default -70%)
        val slowPct = config.getDouble("bleedout.crawl-speed-penalty", 0.7).coerceIn(0.0, 1.0)
        AttributeManager.addModifier(uuid, AttributeModifier(
            attributeId = "movement_speed",
            value = -slowPct,
            type = ModifierType.PERCENT,
            source = ModifierSource.OTHER,
            slot = AttributeSlot.OTHER,
            uuid = BLEEDOUT_SPEED_MOD_UUID,
            deathMark = true
        ))

        downedPlayers[uuid] = DownState(
            bleedoutTicks = duration * 2,
            maxBleedoutTicks = duration * 2,
            revivalMaxTicks = revivalTime * 2,
            downLocation = player.location.clone(),
            body = player.getData()?.bodyHealth
        )

        player.getData()?.bodyHealth?.let { it.downPhase = "DOWNED"; it.downTicks = duration * 2 }
        setDownedPose(player, true)

        player.health = minOf(1.0, player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: 20.0)
        player.allowFlight = false
        player.isFlying = false

        // 倒地限制：中断进行中的治疗
        io.github.zzzyyylllty.chotentech.function.HealingManager.cancel(
            player, io.github.zzzyyylllty.chotentech.function.PostReason.CANCELLED
        )

        player.showTitle(net.kyori.adventure.title.Title.title(
            "<#E08A98>⛔ 倒地".toComponent(),
            "<#A9B4C3>等待队友救援或流血而死...".toComponent()
        ))
        player.sendMessage("<#E08A98>⛔ 你受到了致命伤！<#A9B4C3>倒地期间行动缓慢，等待队友救援吧。".toComponent())
    }

    // ---- Revival ----

    fun startRevival(reviver: Player, downed: Player) {
        val state = downedPlayers[downed.uniqueId] ?: return
        if (state.beingRevivedBy != null) {
            reviver.sendActionBar("<#EFDF8F>该玩家正在被其他人救援中".toComponent())
            return
        }
        if (reviver == downed || isDowned(reviver.uniqueId)) {
            reviver.sendActionBar("<#E08A98>你不能救援自己！".toComponent())
            return
        }
        state.beingRevivedBy = reviver.uniqueId
        state.revivalTicks = 0
        // 救援时暂停剩余倒计时，不重置，避免反复救援延长休克
        state.giveUpTicks = 0
        reviver.sendMessage("<#8FD4A6>✦ 开始救援 ${downed.name}... 请保持靠近。".toComponent())
        downed.sendMessage("<#8FD4A6>✦ ${reviver.name} 正在救援你...".toComponent())
    }

    fun cancelRevival(downedUuid: UUID, reason: String = "") {
        val state = downedPlayers[downedUuid] ?: return
        val reviverUuid = state.beingRevivedBy ?: return
        state.beingRevivedBy = null
        state.revivalTicks = 0
        if (reason.isNotEmpty()) {
            Bukkit.getPlayer(reviverUuid)?.sendActionBar("<#EFDF8F>$reason".toComponent())
            Bukkit.getPlayer(downedUuid)?.sendActionBar("<#E08A98>救援中断 — $reason".toComponent())
        }
    }

    private fun completeRevival(uuid: UUID) {
        val state = downedPlayers[uuid] ?: return
        val player = Bukkit.getPlayer(uuid) ?: return

        cleanupState(uuid)
        if (state.phase == "SHOCK") {
            player.getData()?.bodyHealth?.let {
                it.applyEffect("HEAD", SHOCK_SEQUELA)
                BodyHealthManager.syncPenalties(player, it)
            }
        }

        val pct = config.getDouble("bleedout.revive-health", 0.2)
        val maxHp = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: 20.0
        player.health = (maxHp * pct).coerceIn(minOf(1.0, maxHp), maxHp)

        player.showTitle(net.kyori.adventure.title.Title.title(
            "<#8FD4A6>✅ 已复苏".toComponent(),
            "<#A9B4C3>你被队友从死亡线上拉了回来".toComponent()
        ))
        player.sendMessage("<#8FD4A6>✅ 你已被成功救援！".toComponent())

        val reviverUuid = state.beingRevivedBy
        if (reviverUuid != null) {
            Bukkit.getPlayer(reviverUuid)?.sendMessage("<#8FD4A6>✅ 成功救援 ${player.name}！".toComponent())
        }
    }

    /** 指令直接拉起倒地玩家:跳过救援进度,立即清理倒地状态并恢复血量(管理员/运营用) */
    fun reviveDowned(uuid: UUID): Boolean {
        val state = downedPlayers[uuid] ?: return false
        val player = Bukkit.getPlayer(uuid) ?: return false
        val reviverUuid = state.beingRevivedBy

        cleanupState(uuid)
        if (state.phase == "SHOCK") {
            player.getData()?.bodyHealth?.let {
                it.applyEffect("HEAD", SHOCK_SEQUELA)
                BodyHealthManager.syncPenalties(player, it)
            }
        }

        val pct = config.getDouble("bleedout.revive-health", 0.2)
        val maxHp = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: 20.0
        player.health = (maxHp * pct).coerceIn(minOf(1.0, maxHp), maxHp)

        player.showTitle(net.kyori.adventure.title.Title.title(
            "<#8FD4A6>✅ 已复苏".toComponent(),
            "<#A9B4C3>你被拉了起来".toComponent()
        ))
        player.sendMessage("<#8FD4A6>✅ 你已被拉起！".toComponent())

        if (reviverUuid != null) {
            Bukkit.getPlayer(reviverUuid)?.sendMessage("<#A9B4C3>${player.name} 已被拉起，救援结束".toComponent())
        }
        return true
    }

    // ---- Bleedout expiry ----

    private fun expireBleedout(uuid: UUID) {
        pendingDeath.add(uuid)    // set death flag BEFORE cleanup to prevent re-entry
        cleanupState(uuid)

        val player = Bukkit.getPlayer(uuid) ?: return

        player.sendMessage("<#E08A98>💀 你因失血过多而死亡...".toComponent())
        submit {
            // Guard: player may have been revived, healed, or reconnected since the submit was queued
            if (player.isOnline && !player.isDead && player.health > 0.0 && player.health < 2.0) {
                player.health = 0.0
            }
            pendingDeath.remove(uuid)
        }
    }

    private fun cleanupState(uuid: UUID, clearPersisted: Boolean = true) {
        val removed = downedPlayers.remove(uuid)
        if (clearPersisted) removed?.body?.let { it.downPhase = null; it.downTicks = 0 }
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
        barrierPositions.forEach { (_, loc) ->
            sendRealBlock(loc)
        }
        barrierPositions.clear()
        downedPlayers.clear()
        pendingDeath.clear()
    }

    private fun tick() {
        for (player in Bukkit.getOnlinePlayers()) {
            val oldState = downedPlayers[player.uniqueId]
            if (oldState != null && oldState.body !== player.getData()?.bodyHealth) cleanupState(player.uniqueId, false)
            if (player.isDead || downedPlayers.containsKey(player.uniqueId)) continue
            val body = player.getData()?.bodyHealth ?: continue
            if (body.downPhase == null) continue
            val phase = body.downPhase!!
            val remaining = body.downTicks
            enterBleedout(player)
            downedPlayers[player.uniqueId]?.let { state ->
                if (phase == "SHOCK") enterShock(player, state)
                state.bleedoutTicks = remaining.coerceAtLeast(1)
            }
        }
        val toRemove = mutableListOf<UUID>()

        for ((uuid, state) in downedPlayers) {
            val player = Bukkit.getPlayer(uuid) ?: continue

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
                    reviver.world != player.world || reviver.location.distanceSquared(player.location) > 4.0 -> cancelRevival(uuid, "救援者距离过远")
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
                    if (state.phase == "SHOCK") {
                        expireBleedout(uuid)
                        toRemove.add(uuid)
                        continue
                    } else enterShock(player, state)
                }
            }
            player.getData()?.bodyHealth?.let { it.downPhase = state.phase; it.downTicks = state.bleedoutTicks }

            // --- 进度条（BossBar） ---
            var bar = bleedoutBossBars[uuid]
            if (bar == null) {
                bar = BossBar.bossBar(
                    "<#E08A98>🩸 失血中...".toComponent(),
                    1.0f, BossBar.Color.RED, BossBar.Overlay.PROGRESS
                )
                bar.addViewer(player)
                bleedoutBossBars[uuid] = bar
            }
            // 救援中显示救援进度，否则显示失血进度
            if (state.beingRevivedBy != null) {
                val pct = state.revivalTicks * 100 / state.revivalMaxTicks
                bar.name("<#8FD4A6>✦ 救援中... $pct%".toComponent())
                bar.progress((state.revivalTicks.toFloat() / state.revivalMaxTicks).coerceIn(0f, 1f))
                bar.color(BossBar.Color.GREEN)
            } else {
                val pct = state.bleedoutTicks * 100 / state.maxBleedoutTicks
                bar.name("<#E08A98>🩸 ${if (state.phase == "SHOCK") "休克中" else "倒地中"}... $pct%".toComponent())
                bar.progress((state.bleedoutTicks.toFloat() / state.maxBleedoutTicks).coerceIn(0f, 1f))
                bar.color(BossBar.Color.RED)
            }

            // --- ActionBar ---
            val phaseName = if (state.phase == "SHOCK") "休克中" else "倒地中"
            val nextPhase = if (state.phase == "SHOCK") "死亡" else "进入休克"
            val sec = state.bleedoutTicks / 2
            when {
                state.giveUpTicks > 0 -> {
                    val pct = state.giveUpTicks * 100 / GIVE_UP_TICKS
                    player.sendActionBar("<#E08A98>⛔ 放弃中... ${pct}% <#A9B4C3>(松开Shift取消)".toComponent())
                }
                state.beingRevivedBy != null -> {
                    val name = Bukkit.getOfflinePlayer(state.beingRevivedBy!!).name ?: "未知"
                    val pct = state.revivalTicks * 100 / state.revivalMaxTicks
                    player.sendActionBar("<#E08A98>⛔ $phaseName <#A9B4C3>| <#8FD4A6>$name <#A9B4C3>救援中 $pct%".toComponent())
                }
                else -> {
                    player.sendActionBar("<#E08A98>⛔ $phaseName <#A9B4C3>| <#F2F5FA>${sec}秒 <#A9B4C3>后$nextPhase  <#6E7A8C>[Shift放弃]".toComponent())
                }
            }
        }

        // Clean up players that were removed during the loop
        for (uuid in toRemove) {
            if (downedPlayers.containsKey(uuid)) cleanupState(uuid)
        }

        // Remove stale boss bars（需同步移除观众，否则 BossBar 残留显示）
        bleedoutBossBars.entries.removeIf { (uuid, bar) ->
            if (uuid !in downedPlayers.keys && uuid !in pendingDeath) {
                Bukkit.getPlayer(uuid)?.let { bar.removeViewer(it) }
                true
            } else {
                false
            }
        }
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
        // 倒地头部屏障发给所有玩家,其他玩家也能看到倒地者位置
        Bukkit.getOnlinePlayers().forEach { it.sendBlockChange(loc, barrier) }
    }

    private fun sendRealBlock(loc: Location) {
        val real = loc.world?.getBlockAt(loc)?.blockData ?: return
        Bukkit.getOnlinePlayers().forEach { it.sendBlockChange(loc, real) }
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
        val player = event.entity as Player
        val uuid = player.uniqueId
        if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return
        if (pendingDeath.contains(uuid)) return
        if (isShocked(uuid)) { event.isCancelled = true; return }
        BodyHealthManager.mitigate(event, player)
        if (event.finalDamage <= 0.0) return
        val state = downedPlayers[uuid]
        if (state != null) {
            event.isCancelled = true
            damageDowned(player)
            return
        }
        if (!config.getBoolean("bleedout.enabled", true)) return
        if (event.cause == EntityDamageEvent.DamageCause.VOID || event.cause == EntityDamageEvent.DamageCause.KILL) return

        // finalDamage has already deducted absorption; do not count those hearts twice.
        val effectiveHp = player.health
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
        if (state.phase == "SHOCK" && event.hasExplicitlyChangedPosition()) {
            event.to = event.from.clone().apply { yaw = event.to.yaw; pitch = event.to.pitch }
            event.player.velocity = org.bukkit.util.Vector()
        }
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
            event.player.sendActionBar("<#EFDF8F>该玩家正在被其他人救援中".toComponent())
            return
        }
        startRevival(event.player, target)
    }

    @SubscribeEvent
    fun onPlayerQuitClear(event: PlayerQuitEvent) {
        val uuid = event.player.uniqueId
        downedPlayers.remove(uuid)?.let { state ->
            event.player.getData()?.bodyHealth?.let { it.downPhase = state.phase; it.downTicks = state.bleedoutTicks }
        }
        AttributeManager.removeModifierByUuid(uuid, BLEEDOUT_SPEED_MOD_UUID)
        removeBossBar(uuid)
        pendingDeath.remove(uuid)
        setDownedPose(event.player, false)
    }

    @SubscribeEvent
    fun onRespawnClear(event: PlayerRespawnEvent) {
        pendingDeath.remove(event.player.uniqueId)
    }

    /** 玩家切换到创造/旁观时取消倒地状态 */
    @SubscribeEvent
    fun onGameModeChange(event: PlayerGameModeChangeEvent) {
        val uuid = event.player.uniqueId
        if (!downedPlayers.containsKey(uuid)) return
        if (event.newGameMode == GameMode.CREATIVE || event.newGameMode == GameMode.SPECTATOR) {
            cleanupState(uuid)
            event.player.sendActionBar("<#8FD4A6>已取消倒地状态".toComponent())
        }
    }

    /** If a downed player somehow dies via non-damage means, clean up */
    @SubscribeEvent
    fun onDeathCleanup(event: PlayerDeathEvent) {
        val uuid = event.player.uniqueId
        downedPlayers.remove(uuid)
        Bukkit.getPlayer(uuid)?.getData()?.bodyHealth?.let { it.downPhase = null; it.downTicks = 0 }
        AttributeManager.removeModifierByUuid(uuid, BLEEDOUT_SPEED_MOD_UUID)
        removeBossBar(uuid)
        setDownedPose(event.player, false)
    }
}
