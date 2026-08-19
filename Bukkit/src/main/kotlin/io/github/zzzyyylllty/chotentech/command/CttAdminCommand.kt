package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.ChoTenTech.levelsList
import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.ChoTenTech.playerRegions
import io.github.zzzyyylllty.chotentech.data.PlayerData
import io.github.zzzyyylllty.chotentech.data.profile.ProfileManager
import io.github.zzzyyylllty.chotentech.function.BaseManager
import io.github.zzzyyylllty.chotentech.function.RankManager
import io.github.zzzyyylllty.chotentech.function.bleedout.BleedoutManager
import io.github.zzzyyylllty.chotentech.function.savepoint.SaveIdMigrator
import io.github.zzzyyylllty.chotentech.function.savepoint.SavePoint
import io.github.zzzyyylllty.chotentech.function.savepoint.SavePointConfig
import io.github.zzzyyylllty.chotentech.function.savepoint.SavePointManager
import io.github.zzzyyylllty.chotentech.function.savepoint.SavePointType
import io.github.zzzyyylllty.chotentech.player.sendComponent
import io.github.zzzyyylllty.chotentech.util.devLog
import io.github.zzzyyylllty.chotentech.util.jsonUtils
import io.github.zzzyyylllty.chotentech.util.toComponent
import org.bukkit.Bukkit
import org.bukkit.Chunk
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.player
import taboolib.common.platform.command.subCommand
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.submitAsync
import taboolib.module.configuration.Configuration
import java.io.File

@CommandHeader(
    name = "chotentech-admin",
    aliases = ["cttadmin"],
    permission = "chotentech.command.admin",
    description = "Admin Command for ChoTenTech.",
    permissionMessage = "",
    permissionDefault = PermissionDefault.OP,
    newParser = false,
)
object CttAdminCommand {

    @CommandBody
    val main = mainCommand {
        createModernHelper()
    }

    @CommandBody
    val feature = subCommand {
        literal("createspawn") {
            dynamic("name") {
                dynamic("type") {
                    execute<CommandSender> { sender, context, _ ->
                        val player = sender as? Player
                        if (player == null) {
                            sender.sendComponent("<red>该指令只能由玩家执行")
                            return@execute
                        }
                        createSpawn(player, context["name"], context["type"])
                    }
                    suggestion<CommandSender> { _, _ ->
                        listOf("NORMAL", "BASE")
                    }
                }
            }
        }
        literal("fixsavelocation") {
            execute<CommandSender> { sender, _, _ ->
                val player = sender as? Player
                if (player == null) {
                    sender.sendComponent("<red>该指令只能由玩家执行")
                    return@execute
                }
                fixSaveLocations(player)
            }
        }
        literal("tpsave") {
            dynamic("saveId") {
                execute<CommandSender> { sender, context, _ ->
                    val player = sender as? Player
                    if (player == null) {
                        sender.sendComponent("<red>该指令只能由玩家执行")
                        return@execute
                    }
                    teleportToSave(player, context["saveId"])
                }
                suggestion<CommandSender> { _, _ ->
                    SavePointConfig.getAll().map { it.id }.toList()
                }
            }
        }
        literal("movesavetohere") {
            dynamic("saveId") {
                execute<CommandSender> { sender, context, _ ->
                    val player = sender as? Player
                    if (player == null) {
                        sender.sendComponent("<red>该指令只能由玩家执行")
                        return@execute
                    }
                    moveSaveHere(player, context["saveId"])
                }
                suggestion<CommandSender> { _, _ ->
                    SavePointConfig.getAll().map { it.id }.toList()
                }
            }
        }
        literal("migrateids") {
            execute<CommandSender> { sender, _, _ ->
                val player = sender as? Player
                if (player == null) {
                    sender.sendComponent("<red>该指令只能由玩家执行")
                    return@execute
                }
                migrateAllSaveIds(player)
            }
        }
        literal("revive") {
            player("target") {
                execute<CommandSender> { sender, context, _ ->
                    val target = context.player("target").castSafely<Player>()
                    if (target == null) {
                        sender.sendComponent("<red>目标玩家不在线")
                        return@execute
                    }
                    if (!BleedoutManager.reviveDowned(target.uniqueId)) {
                        sender.sendComponent("<yellow>${target.name} <red>当前没有处于倒地状态")
                        return@execute
                    }
                    sender.sendComponent("<green>已将 <yellow>${target.name} <green>拉起")
                }
                suggestion<CommandSender> { _, _ ->
                    Bukkit.getOnlinePlayers().filter { BleedoutManager.isDowned(it.uniqueId) }.map { it.name }
                }
            }
        }
        literal("rank") {
            dynamic("type") {
                literal("set") {
                    player("player") {
                        dynamic("rankId") {
                            execute<CommandSender> { sender, context, _ ->
                                val target = context.player("player").castSafely<Player>()
                                if (target == null) {
                                    sender.sendComponent("<red>目标玩家不在线")
                                    return@execute
                                }
                                setRank(sender, context["type"], target, context["rankId"])
                            }
                            suggestion<CommandSender> { sender, context ->
                                RankManager.ranks[context["type"]]?.keys?.toList() ?: emptyList()
                            }
                        }
                    }
                }
                literal("clear") {
                    player("player") {
                        execute<CommandSender> { sender, context, _ ->
                            val target = context.player("player").castSafely<Player>()
                            if (target == null) {
                                sender.sendComponent("<red>目标玩家不在线")
                                return@execute
                            }
                            clearRank(sender, context["type"], target)
                        }
                    }
                }
                suggestion<CommandSender> { _, _ ->
                    RankManager.getTypes().toList()
                }
            }
        }
    }

    @CommandBody
    val manage = subCommand {
        literal("setspawn") {
            player("target") {
                dynamic("saveId") {
                    execute<CommandSender> { sender, context, _ ->
                        val target = context.player("target").castSafely<Player>()
                        if (target == null) {
                            sender.sendComponent("<red>目标玩家不在线")
                            return@execute
                        }
                        val saveId = context["saveId"]
                        val sp = SavePointConfig.get(saveId)
                        if (sp == null) {
                            sender.sendComponent("<red>存档点 <yellow>$saveId <red>不存在")
                            return@execute
                        }
                        val data = playerDataMap[target.uniqueId.toString()]
                        if (data == null) {
                            sender.sendComponent("<red>目标玩家数据未加载")
                            return@execute
                        }
                        // 按存档点类型自动绑定:基地存档点 -> baseSave,普通存档点 -> normalSave
                        if (sp.isBase) {
                            data.baseSave = saveId
                        } else {
                            data.normalSave = saveId
                        }
                        val typeName = if (sp.isBase) "基地" else "普通"
                        sender.sendComponent("<green>已将 <yellow>${target.name} <green>的${typeName}存档点设为 <yellow>${sp.name} <gray>($saveId)")
                        target.sendComponent("<green>管理员已将你的${typeName}存档点设为 <yellow>${sp.name}")
                    }
                    suggestion<CommandSender> { _, _ ->
                        SavePointConfig.getAll().map { it.id }.toList()
                    }
                }
            }
        }
    }

    private val rankTypeNameMap = mapOf(
        "prefix" to "前缀",
        "suffix" to "后缀",
        "chatsuffix" to "聊天后缀",
        "chatcolor" to "聊天颜色",
        "namecolor" to "名字颜色",
        "joinmessage" to "进服消息",
        "quitmessage" to "退服消息"
    )

    /** /cttadmin feature rank <type> set <player> <rankId> — 管理直接写入,不校验条件(60秒条件检测仍会撤下不合格称号) */
    private fun setRank(sender: CommandSender, type: String, target: Player, rankId: String) {
        val typeRanks = RankManager.ranks[type]
        if (typeRanks == null) {
            sender.sendComponent("<red>未知称号类型 <yellow>$type <red>(可用: ${RankManager.getTypes().joinToString(", ")})")
            return
        }
        if (!typeRanks.containsKey(rankId)) {
            sender.sendComponent("<red>称号 <yellow>$rankId <red>不存在于 <yellow>$type")
            return
        }
        val shared = ProfileManager.getSharedData(target.uniqueId.toString())
        shared.activeRanks[type] = rankId
        ProfileManager.saveSharedData(target.uniqueId.toString())
        target.playerListName(RankManager.buildPlayerListName(target).toComponent())
        sender.sendComponent("<green>已设置 <yellow>${target.name} <green>的${rankTypeNameMap[type] ?: type}称号为 <yellow>$rankId")
    }

    /** /cttadmin feature rank <type> clear <player> — 清除指定类型的称号 */
    private fun clearRank(sender: CommandSender, type: String, target: Player) {
        if (!RankManager.ranks.containsKey(type)) {
            sender.sendComponent("<red>未知称号类型 <yellow>$type")
            return
        }
        val shared = ProfileManager.getSharedData(target.uniqueId.toString())
        shared.activeRanks.remove(type)
        ProfileManager.saveSharedData(target.uniqueId.toString())
        target.playerListName(RankManager.buildPlayerListName(target).toComponent())
        sender.sendComponent("<green>已清除 <yellow>${target.name} <green>的${rankTypeNameMap[type] ?: type}称号")
    }

    /** /cttadmin feature createspawn <名称> NORMAL/BASE — 以玩家当前位置直接写入 saves.yml */
    private fun createSpawn(player: Player, name: String, typeStr: String) {
        val type = runCatching { SavePointType.valueOf(typeStr.uppercase()) }.getOrNull()
        if (type == null) {
            player.sendComponent("<red>存档点类型必须是 NORMAL 或 BASE")
            return
        }
        val key = name.trim()
        if (key.isEmpty()) {
            player.sendComponent("<red>存档点名称不能为空")
            return
        }
        if (SavePointConfig.get(key) != null) {
            player.sendComponent("<red>存档点 <yellow>$key <red>已存在")
            return
        }

        // 自动探测层级:优先命中玩家所在区域的层级,兜底用当前激活层级
        val regions = playerRegions[player.uniqueId.toString()] ?: emptySet()
        val levelId = regions.firstNotNullOfOrNull { region ->
            levelsList[region]?.id
                ?: levelsList.values.firstOrNull { it.region?.id == region }?.id
        } ?: playerDataMap[player.uniqueId.toString()]?.activeLevelStr
        if (levelId == null) {
            player.sendComponent("<red>无法自动探测当前层级(不在任何层级区域内且无激活层级)")
            return
        }

        val loc = player.location
        val bx = loc.blockX
        val by = loc.blockY
        val bz = loc.blockZ
        val world = loc.world.name

        // 以玩家位置为最低点向上生成 5x3x5 的存档区域,全息图取玩家位置上方 3 格,复活点取玩家所在位置
        val pos = "$world ${bx - 2} $by ${bz - 2} ${bx + 2} ${by + 2} ${bz + 2}"
        val holoPos = "$world $bx ${by + 3} $bz"
        val respawn = "$world $bx $by $bz"

        // BASE 类型:自动关联玩家所在的基地区域
        val baseId = if (type == SavePointType.BASE) {
            regions.firstNotNullOfOrNull { region ->
                if (region.startsWith("base-")) BaseManager.getBaseByRegion(region)?.idef else null
            }
        } else null

        try {
            val file = File(getDataFolder(), "saves.yml")
            if (!file.exists()) file.createNewFile()
            val config = Configuration.loadFromFile(file)
            config.set("$key.level", levelId)
            config.set("$key.pos", pos)
            config.set("$key.holoPos", holoPos)
            config.set("$key.respawn", respawn)
            config.set("$key.name", name)
            config.set("$key.type", type.name)
            baseId?.let { config.set("$key.base", it) }
            config.saveToFile(file)
        } catch (e: Exception) {
            player.sendComponent("<red>写入 saves.yml 失败: ${e.message}")
            return
        }

        SavePointConfig.load()
        SavePointManager.createHolograms()

        val typeName = if (type == SavePointType.BASE) "<aqua>基地存档点" else "<green>普通存档点"
        val baseText = baseId?.let { " <gray>(基地 <yellow>$it<gray>)" } ?: ""
        player.sendComponent("$typeName <yellow>$name <green>创建成功: 层级 <yellow>$levelId <gray>@ $world $bx,$by,$bz$baseText")
    }

    /** 最下层允许的方块:空气 + 光源方块(可站立/房间照明场景不误判为被掩埋) */
    private val freeSaveLayerBlocks = setOf(
        Material.AIR, Material.CAVE_AIR, Material.VOID_AIR,
        Material.LIGHT,
        Material.GLOWSTONE, Material.SEA_LANTERN, Material.SHROOMLIGHT, Material.JACK_O_LANTERN,
        Material.REDSTONE_LAMP, Material.MAGMA_BLOCK, Material.END_ROD,
        Material.TORCH, Material.WALL_TORCH, Material.SOUL_TORCH, Material.SOUL_WALL_TORCH,
        Material.LANTERN, Material.SOUL_LANTERN,
        Material.CAMPFIRE, Material.SOUL_CAMPFIRE,
        Material.GLOW_LICHEN, Material.GLOW_BERRIES,
    )

    /**
     * /cttadmin feature fixsavelocation — 异步检查所有存档点,抬升最低/最高点的 y 坐标,
     * 直到存档范围最下方一层出现空气/光源方块(任一即可,不做整层全空要求),并发送修复细则与结果。
     */
    private fun fixSaveLocations(player: Player) {
        submitAsync {
            val saves = SavePointConfig.getAll().toList()
            if (saves.isEmpty()) {
                submit { player.sendComponent("<red>没有存档点可修复") }
                return@submitAsync
            }

            val lines = mutableListOf("<gold>■ <yellow>存档点位置修复报告:")
            val fixes = mutableListOf<Pair<SavePoint, Int>>()
            var ok = 0
            var failed = 0

            for (sp in saves) {
                val world = Bukkit.getWorld(sp.world)
                if (world == null) {
                    failed++
                    lines += "<gray>- <yellow>${sp.id} <gray>(${sp.name}) <red>世界 ${sp.world} 不存在,跳过"
                    continue
                }

                val minX = minOf(sp.pos1.x, sp.pos2.x).toInt()
                val maxX = maxOf(sp.pos1.x, sp.pos2.x).toInt()
                val minZ = minOf(sp.pos1.z, sp.pos2.z).toInt()
                val maxZ = maxOf(sp.pos1.z, sp.pos2.z).toInt()
                var minY = minOf(sp.pos1.y, sp.pos2.y).toInt()
                var maxY = maxOf(sp.pos1.y, sp.pos2.y).toInt()
                val oldMinY = minY
                val oldMaxY = maxY

                var raised = 0
                var found = false
                while (!found && raised < 256 && maxY < world.maxHeight) {
                    // 单点检查失败(如区块加载失败)按"不可用"处理,继续抬升;最终仍找不到则只报告不写回
                    val free = runCatching { isLayerHasFree(world, minX, maxX, minY, minZ, maxZ) }.getOrElse { false }
                    if (free) {
                        found = true
                    } else {
                        minY++
                        maxY++
                        raised++
                    }
                }

                when {
                    !found -> {
                        failed++
                        lines += "<gray>- <yellow>${sp.id} <gray>(${sp.name}) <red>修复失败:抬升 ${raised} 格后仍未找到可用空间(达到世界高度上限或迭代上限)"
                    }
                    raised == 0 -> {
                        ok++
                        lines += "<gray>- <yellow>${sp.id} <gray>(${sp.name}) <green>无需修复(最下层已有空气/光源方块)"
                    }
                    else -> {
                        fixes += sp to raised
                        lines += "<gray>- <yellow>${sp.id} <gray>(${sp.name}) <green>已修复:最低点 <yellow>$oldMinY→$minY<green>,最高点 <yellow>$oldMaxY→$maxY<green>,抬升 <yellow>$raised<green> 格"
                    }
                }
            }

            lines += "<gold>共 <yellow>${fixes.size}<gold> 个修复,<green>$ok<green> 个无需修复,<red>$failed<red> 个失败"

            // 写回/重载/全息图必须回主线程:SavePointConfig 是普通 Map(主线程每秒在迭代),
            // 与 createSpawn 的 saves.yml 写入也要串行,否则并发写文件会互相覆盖丢键
            submit {
                if (fixes.isNotEmpty()) {
                    for ((sp, deltaY) in fixes) writeBackSavePos(sp, deltaY)
                    SavePointConfig.load()
                    SavePointManager.createHolograms()
                    lines += "<gray>(已重载存档点配置并刷新全息图)"
                }
                player.sendComponent(lines.joinToString("\n"))
            }
        }
    }

    /**
     * 存档范围最下层是否已存在至少一个空气/光源方块。
     * 只要有一个可站立的空隙即视为"未被完全掩埋",停止抬升 —— 避免整层抬升穿过房间/建筑到屋顶。
     */
    private fun isLayerHasFree(world: World, minX: Int, maxX: Int, y: Int, minZ: Int, maxZ: Int): Boolean {
        val chunkCoords = mutableSetOf<Pair<Int, Int>>()
        for (x in minX..maxX) {
            for (z in minZ..maxZ) {
                chunkCoords += (x shr 4) to (z shr 4)
            }
        }
        for ((cx, cz) in chunkCoords) {
            runCatching { world.getChunkAtAsync(cx, cz).join() }.getOrNull() ?: return false
        }
        for (x in minX..maxX) {
            for (z in minZ..maxZ) {
                val type = world.getBlockAt(x, y, z).type
                if (type in freeSaveLayerBlocks) return true
            }
        }
        return false
    }

    /** 把存档点 pos 的最低/最高 y 同步抬升 deltaY 格后写回 saves.yml(仅主线程调用) */
    private fun writeBackSavePos(sp: SavePoint, deltaY: Int) {
        try {
            val file = File(getDataFolder(), "saves.yml")
            if (!file.exists()) return
            val config = Configuration.loadFromFile(file)
            val newPos1 = sp.pos1.clone().add(org.bukkit.util.Vector(0.0, deltaY.toDouble(), 0.0))
            val newPos2 = sp.pos2.clone().add(org.bukkit.util.Vector(0.0, deltaY.toDouble(), 0.0))
            config.set("${sp.id}.pos", "${sp.world} ${fmtCoord(newPos1.x)} ${fmtCoord(newPos1.y)} ${fmtCoord(newPos1.z)} ${fmtCoord(newPos2.x)} ${fmtCoord(newPos2.y)} ${fmtCoord(newPos2.z)}")
            config.saveToFile(file)
        } catch (e: Exception) {
            playerReportError(e.message ?: "未知错误")
        }
    }

    private fun playerReportError(msg: String) {
        // 仅在异步任务中可用的兜底日志,避免直接向玩家发送(玩家可能已下线)
        taboolib.common.platform.function.warning("[CttAdmin] 存档点修复写入失败: $msg")
    }

    /** 整数坐标保持整数形式,避免 yml 里出现 100.0 之类的浮点写法 */
    private fun fmtCoord(d: Double) = if (d % 1.0 == 0.0) d.toInt().toString() else d.toString()

    /** /cttadmin feature tpsave <saveId> — 传送到存档点(有复活点用复活点,否则用全息图位置) */
    private fun teleportToSave(player: Player, saveId: String) {
        val sp = SavePointConfig.get(saveId)
        if (sp == null) {
            player.sendComponent("<red>存档点 <yellow>$saveId <red>不存在")
            return
        }
        if (Bukkit.getWorld(sp.world) == null) {
            player.sendComponent("<red>存档点 <yellow>${sp.name} <red>的世界 <yellow>${sp.world} <red>不存在")
            return
        }
        val loc = sp.respawnPos?.toLocation() ?: sp.effectiveHoloPos
        player.teleportAsync(loc).whenComplete { _, err ->
            if (err != null) player.sendComponent("<red>传送失败: ${err.message}")
        }
        player.sendComponent("<green>已传送到存档点 <yellow>${sp.name} <gray>($saveId)")
    }

    /**
     * /cttadmin feature movesavetohere <saveId> — 以玩家位置(xyz 四舍五入)为最下层中心点重建存档区域,
     * 全息图/复活点按区域中心位移同步平移,并重新探测层级。
     */
    private fun moveSaveHere(player: Player, saveId: String) {
        val sp = SavePointConfig.get(saveId)
        if (sp == null) {
            player.sendComponent("<red>存档点 <yellow>$saveId <red>不存在")
            return
        }

        val loc = player.location
        val bx = Math.round(loc.x).toInt()
        val by = Math.round(loc.y).toInt()
        val bz = Math.round(loc.z).toInt()
        val worldName = loc.world.name

        // 新区域:玩家位置为最下层中心点,x/z 各 ±2,y 向上 +2(与 createspawn 布局一致)
        val newPos = "$worldName ${bx - 2} $by ${bz - 2} ${bx + 2} ${by + 4} ${bz + 2}"

        // 位移量:新区域中心 - 旧区域中心
        val oldCenterX = (sp.pos1.x + sp.pos2.x) / 2
        val oldCenterY = (minOf(sp.pos1.y, sp.pos2.y) + maxOf(sp.pos1.y, sp.pos2.y)) / 2
        val oldCenterZ = (sp.pos1.z + sp.pos2.z) / 2
        val dx = bx - oldCenterX
        val dy = by + 1 - oldCenterY
        val dz = bz - oldCenterZ

        // 层级重探测:以新位置所在层级区域更新 level(探测不到则保留原值)
        val regions = playerRegions[player.uniqueId.toString()] ?: emptySet()
        val levelId = regions.firstNotNullOfOrNull { region ->
            levelsList[region]?.id
                ?: levelsList.values.firstOrNull { it.region?.id == region }?.id
        } ?: playerDataMap[player.uniqueId.toString()]?.activeLevelStr

        try {
            val file = File(getDataFolder(), "saves.yml")
            if (!file.exists()) file.createNewFile()
            val config = Configuration.loadFromFile(file)
            config.set("$saveId.pos", newPos)
            config.getString("$saveId.holoPos")?.let { h -> config.set("$saveId.holoPos", shiftCoordString(h, worldName, dx, dy, dz)) }
            config.getString("$saveId.respawn")?.let { r -> config.set("$saveId.respawn", shiftCoordString(r, worldName, dx, dy, dz)) }
            if (levelId != null) config.set("$saveId.level", levelId)
            config.saveToFile(file)
        } catch (e: Exception) {
            player.sendComponent("<red>写入 saves.yml 失败: ${e.message}")
            return
        }

        SavePointConfig.load()
        SavePointManager.createHolograms()

        player.sendComponent("<green>存档点 <yellow>${sp.name} <green>已移动: 区域 <yellow>$worldName (${bx - 2}..${bx + 2}, $by..${by + 2}, ${bz - 2}..${bz + 2})")
        player.sendComponent("<gray>位移: x <yellow>$dx<gray>, y <yellow>$dy<gray>, z <yellow>$dz<gray>;层级 ${if (levelId != null) "<yellow>$levelId" else "<red>保留原值(未探测到层级区域)"}")
    }

    /** 平移 "world x y z ..." 坐标字符串的前四个坐标(用于 holoPos/respawn 随区域移动,前缀世界名同步替换) */
    private fun shiftCoordString(s: String, worldName: String, dx: Double, dy: Double, dz: Double): String {
        val parts = s.split(" ")
        if (parts.size < 4) return s
        return "$worldName ${fmtCoord(parts[1].toDouble() + dx)} ${fmtCoord(parts[2].toDouble() + dy)} ${fmtCoord(parts[3].toDouble() + dz)}"
    }

    /**
     * /cttadmin feature migrateids — 批量迁移所有档案中失效的存档点 ID(旧中文 ID 按 name 匹配新英文 ID)。
     * 在线玩家 join/切档时已由 loadProfileData 自动转换,此处补齐离线玩家与其余档案。
     */
    private fun migrateAllSaveIds(player: Player) {
        submitAsync {
            val rows = runCatching { ProfileManager.profileMapper.findAll { } }.getOrNull()
            if (rows == null) {
                submit { player.sendComponent("<red>读取档案数据失败") }
                return@submitAsync
            }
            var migrated = 0
            val details = mutableListOf<String>()
            for (row in rows) {
                val data = runCatching { jsonUtils.fromJson(row.data, PlayerData::class.java)?.normalize() }.getOrNull() ?: continue
                val oldBase = data.baseSave
                val oldNormal = data.normalSave
                if (SaveIdMigrator.convert(data)) {
                    row.data = jsonUtils.toJson(data, PlayerData::class.java)
                    runCatching { ProfileManager.profileMapper.update(row) }.onFailure {
                        devLog("[CttAdmin] migrateids 更新失败 ${row.uuid}/${row.profileName}: ${it.message}")
                    }
                    migrated++
                    details += "<gray>- <yellow>${row.uuid} <gray>/ <yellow>${row.profileName}<gray>: 基地 <yellow>${oldBase ?: "无"} → ${data.baseSave ?: "无"}<gray>, 普通 <yellow>${oldNormal ?: "无"} → ${data.normalSave ?: "无"}"
                }
            }
            submit {
                val lines = mutableListOf("<gold>■ <yellow>存档点 ID 迁移报告:")
                if (migrated == 0) {
                    lines += "<gray>没有需要迁移的档案(所有存档点 ID 均已有效)"
                } else {
                    lines += "<green>共迁移 <yellow>$migrated<green> 个档案:"
                    lines += details.take(30)
                    if (details.size > 30) lines += "<gray>... 其余 ${details.size - 30} 个省略"
                }
                player.sendComponent(lines.joinToString("\n"))
            }
        }
    }
}
