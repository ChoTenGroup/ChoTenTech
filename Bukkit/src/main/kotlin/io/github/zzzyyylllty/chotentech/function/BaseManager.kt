package io.github.zzzyyylllty.chotentech.function

import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldguard.WorldGuard
import io.github.zzzyyylllty.chotentech.ChoTenTech
import io.github.zzzyyylllty.chotentech.data.Base
import io.github.zzzyyylllty.chotentech.data.BaseSpawn
import io.github.zzzyyylllty.chotentech.data.BaseStartItemsConfig
import io.github.zzzyyylllty.chotentech.data.DataManager
import io.github.zzzyyylllty.chotentech.data.PlayerData
import io.github.zzzyyylllty.chotentech.data.WarehouseConfig
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.function.player.sendComponent
import io.github.zzzyyylllty.chotentech.util.ExternalItemHelper
import io.github.zzzyyylllty.chotentech.util.ItemSerializer
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.function.warning
import taboolib.library.xseries.XMaterial
import taboolib.module.configuration.Configuration

object BaseManager {

    val baseMap = linkedMapOf<String, Base>()

    fun loadBases(config: Configuration) {
        baseMap.clear()
        val section = config.getConfigurationSection("bases") ?: return
        for (idef in section.getKeys(false)) {
            try {
                val sec = section.getConfigurationSection(idef) ?: continue
                val spawnSec = sec.getConfigurationSection("spawn")
                val spawn = if (spawnSec != null) {
                    BaseSpawn(
                        world = spawnSec.getString("world") ?: "world",
                        x = spawnSec.getDouble("x", 0.0),
                        y = spawnSec.getDouble("y", 100.0),
                        z = spawnSec.getDouble("z", 0.0),
                        yaw = spawnSec.getDouble("yaw", 0.0).toFloat(),
                        pitch = spawnSec.getDouble("pitch", 0.0).toFloat(),
                    )
                } else {
                    BaseSpawn("world", 0.0, 100.0, 0.0, 0f, 0f)
                }

                val base = Base(
                    idef = idef,
                    name = sec.getString("name") ?: idef,
                    faction = sec.getString("faction") ?: "MEG",
                    model = sec.getString("model") ?: "",
                    enable = sec.getBoolean("enable", false),
                    dev = sec.getString("dev") ?: "",
                    lore = sec.getStringList("lore"),
                    start = sec.getString("start") ?: "",
                    title = sec.getString("title") ?: "",
                    region = sec.getString("region") ?: "base-${idef.lowercase()}",
                    spawn = spawn,
                    savePointRegion = sec.getString("save-point.region"),
                    autoGrant = sec.getBoolean("auto-grant", false),
                )
                baseMap[idef] = base
            } catch (e: Exception) {
                warning("加载基地 $idef 失败: ${e.message}")
            }
        }
        ChoTenTech.consoleSender.sendComponent("<#8FD4A6>已加载 ${baseMap.size} 个基地")
    }

    fun getBaseByRegion(regionId: String): Base? {
        return baseMap.values.firstOrNull { it.region.equals(regionId, ignoreCase = true) }
    }

    /**
     * 副职业经验/升级的安全区判定:world 主世界或任意基地区域内。
     * 基地内可通过岩浆等自伤源无限刷经验(耐力/健康),因此经验与升级都在此拦截。
     */
    fun isInLevelSafeZone(player: Player): Boolean {
        if (player.world.name.equals("world", ignoreCase = true)) return true
        return try {
            val regions = WorldGuard.getInstance().platform.regionContainer.createQuery()
                .getApplicableRegions(BukkitAdapter.adapt(player.location)).regions
            regions.any { rg -> baseMap.values.any { it.region.equals(rg.id, ignoreCase = true) } }
        } catch (_: Exception) {
            false
        }
    }

    fun getBasesByFaction(faction: String): List<Base> {
        return baseMap.values.filter { it.faction.equals(faction, ignoreCase = true) }
    }

    fun getEnabledBasesByFaction(faction: String): List<Base> {
        return baseMap.values.filter { it.faction.equals(faction, ignoreCase = true) && it.enable }
    }

    fun discoverBase(player: Player, idef: String): Boolean {
        val base = baseMap[idef] ?: return false
        val data = player.getData() ?: return false

        if (data.discoveredBases.contains(idef)) return false

        data.discoveredBases.add(idef)
        player.sendComponent("<#DFBD7C>■ <#8FD4A6>发现新基地: <#EFDF8F>${base.name}")
        player.sendComponent("<#A9B4C3>${base.lore.joinToString("") ?: ""}")
        return true
    }

    fun joinBase(player: Player, idef: String): Boolean {
        val base = baseMap[idef] ?: run {
            player.sendComponent("<#E08A98>基地 $idef 不存在！")
            return false
        }
        if (!base.enable) {
            player.sendComponent("<#E08A98>该基地尚不可用！")
            return false
        }
        val data = player.getData() ?: return false

        data.mainTag = base.faction
        data.subTag = base.idef
        grantBaseStartItems(player, data, idef)

        player.sendComponent("<#8FD4A6>你已加入 <#DFBD7C>${base.name}")
        player.sendComponent("<#A9B4C3>阵营: ${base.faction} | 基地: ${base.name}")

        val loc = base.spawn
        val world = Bukkit.getWorld(loc.world)
        if (world != null) {
            player.teleportAsync(Location(world, loc.x, loc.y, loc.z, loc.yaw, loc.pitch))
        }

        return true
    }

    /** 自动发放初始物资的基地(auto-grant: true):玩家即使未加入该基地,进服加载数据时也会自动发放 */
    fun grantAutoStartItems(player: Player, data: PlayerData) {
        if (!player.isOnline) return
        for (base in baseMap.values) {
            if (!base.autoGrant) continue
            grantBaseStartItems(player, data, base.idef)
        }
    }

    /** 重置指定基地仓库到初始状态:清空内容与发放标记;玩家在线则立即重新发放 */
    fun resetBaseWarehouse(player: Player?, data: PlayerData, idef: String): Boolean {
        val base = baseMap[idef] ?: return false
        data.warehouseContents.remove(idef)
        data.grantedStartItems.remove(idef)
        if (player != null && base.enable) {
            grantBaseStartItems(player, data, idef)
        }
        return true
    }

    /** 重置全部仓库到初始状态;玩家在线则立即重新发放 auto-grant 基地的物资 */
    fun resetAllWarehouses(player: Player?, data: PlayerData) {
        data.warehouseContents.clear()
        data.grantedStartItems.clear()
        if (player != null) {
            grantAutoStartItems(player, data)
        }
    }

    /** 发放初始物资:仓库为空且无发放标记才放入(仅首次;搬空仓库后也不会重复发放) */
    private fun grantBaseStartItems(player: Player, data: PlayerData, idef: String) {
        val items = BaseStartItemsConfig.getStartItems(idef)
        if (items.isEmpty()) return
        val contents = data.warehouseContents.getOrPut(idef) { linkedMapOf() }
        // 已有发放标记,或仓库非空(旧档迁移:视为已发放)则跳过并补标记,防止搬空仓库后重复发放
        if (idef in data.grantedStartItems || contents.isNotEmpty()) {
            if (data.grantedStartItems.add(idef)) {
                DataManager.saveData(player)
            }
            return
        }

        val maxSlots = WarehouseConfig.getMaxSlots(player, idef)
        var slot = 1
        for ((itemId, amount) in items) {
            val template = resolveStartItem(player, itemId) ?: continue
            val maxStack = template.maxStackSize
            var remaining = amount
            while (remaining > 0 && slot <= maxSlots) {
                val stackSize = minOf(remaining, maxStack)
                val stack = template.clone().apply { this.amount = stackSize }
                val serialized = ItemSerializer.serializeNbt(stack) ?: break
                contents[slot] = serialized
                slot++
                remaining -= stackSize
            }
            if (slot > maxSlots) break
        }

        if (slot > 1) {
            data.grantedStartItems.add(idef)
            DataManager.saveData(player)
            player.sendComponent("<#DFBD7C>■ <#EFDF8F>基地仓库已放入初始物资,打开仓库查看")
        }
    }

    /** 解析初始物资物品 ID:sertraline:xxx 插件物品 -> 原版 -> XSeries */
    private fun resolveStartItem(player: Player, itemId: String): ItemStack? {
        if (itemId.contains(":")) {
            val parts = itemId.split(":", limit = 2)
            if (parts.size == 2) {
                try {
                    val result = ExternalItemHelper.build(player, parts[0], parts[1])
                    if (result != null && !result.type.isAir) return result
                } catch (_: Exception) { /* fall through */ }
            }
        }
        try {
            val mat = Material.valueOf(itemId.uppercase())
            return ItemStack(mat)
        } catch (_: Exception) { /* fall through */ }
        try {
            val xmat = XMaterial.valueOf(itemId.uppercase())
            return xmat.parseItem()
        } catch (_: Exception) { /* fall through */ }
        return null
    }

    fun getPlayerBase(data: PlayerData): Base? {
        val subtag = data.subTag ?: return null
        return baseMap[subtag]
    }

    data class FactionInfo(
        val tag: String,
        val displayName: String,
        val color: String,
        val description: String,
        val iconModel: String,
    )

    val factions = listOf(
        FactionInfo(
            tag = "MEG",
            displayName = "探险者总署",
            color = "<#cfc26e>",
            description = "我们是一线希望。我们承诺尽可能保护所有人，\n探索从未有人见过的非凡地方，我们会保证你的安全。\n这是他们所承诺的。随着冷战愈演愈烈，\n在战争被波及到的流浪者们纷纷涌向 M.E.G.。\n然而，M.E.G. 必须继续维护其名号的高标准，\n否则，阈限世界将损失一个伟大的组织......",
            iconModel = "kangelitem:itm/bookmark/meg",
        ),
        FactionInfo(
            tag = "BNTG",
            displayName = "不结盟贸易集团",
            color = "<#94dbbe>",
            description = "金钱归我，荣耀归你。\nB.N.T.G.，亦称不结盟贸易集团。\n一个通过电汇、钞票和股票证书联系在一起的组织。\n只要你能带来收益，你就是B.N.T.G.的不错人选。\n我们等待着，渴望一个未来，一个可能属于金钱和荣耀的秩序。",
            iconModel = "kangelitem:itm/bookmark/bntg",
        ),
        FactionInfo(
            tag = "UEC",
            displayName = "无羁探险者联盟",
            color = "<#cf856e>",
            description = "U.E.C.创立的宗旨是\"人们的安全高于一切\"。至少他们是这么说的。\n无羁探险者联盟通过创立傀儡组织，扶持代理人来攫取利益。\n一个臭名昭著的例子是\"无尽下水道\"组织，许多U.E.C.的居民都定居于此。\n无羁探险者联盟是一个结合了经济联盟、军事联盟和一体化项目的组织。",
            iconModel = "kangelitem:itm/bookmark/uec",
        ),
    )
}
