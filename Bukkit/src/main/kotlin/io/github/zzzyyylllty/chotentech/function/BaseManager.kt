package io.github.zzzyyylllty.chotentech.function

import io.github.zzzyyylllty.chotentech.ChoTenTech
import io.github.zzzyyylllty.chotentech.data.Base
import io.github.zzzyyylllty.chotentech.data.BaseSpawn
import io.github.zzzyyylllty.chotentech.data.PlayerData
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.function.player.sendComponent
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import taboolib.common.platform.function.warning
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
                    region = sec.getString("region") ?: "base-${idef.lowercase()}",
                    spawn = spawn,
                    savePointRegion = sec.getString("save-point.region"),
                )
                baseMap[idef] = base
            } catch (e: Exception) {
                warning("加载基地 $idef 失败: ${e.message}")
            }
        }
        ChoTenTech.consoleSender.sendComponent("<green>已加载 ${baseMap.size} 个基地")
    }

    fun getBaseByRegion(regionId: String): Base? {
        return baseMap.values.firstOrNull { it.region.equals(regionId, ignoreCase = true) }
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
        player.sendComponent("<gold>■ <green>发现新基地: <yellow>${base.name}")
        player.sendComponent("<gray>${base.lore.joinToString("") ?: ""}")
        return true
    }

    fun joinBase(player: Player, idef: String): Boolean {
        val base = baseMap[idef] ?: run {
            player.sendComponent("<red>基地 $idef 不存在！")
            return false
        }
        if (!base.enable) {
            player.sendComponent("<red>该基地尚不可用！")
            return false
        }
        val data = player.getData() ?: return false

        data.mainTag = base.faction
        data.subTag = base.idef

        player.sendComponent("<green>你已加入 <gold>${base.name}")
        player.sendComponent("<gray>阵营: ${base.faction} | 基地: ${base.name}")

        val loc = base.spawn
        val world = Bukkit.getWorld(loc.world)
        if (world != null) {
            player.teleportAsync(Location(world, loc.x, loc.y, loc.z, loc.yaw, loc.pitch))
        }

        return true
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
            color = "<#ffdd00>",
            description = "我们是一线希望。我们承诺尽可能保护所有人，\n探索从未有人见过的非凡地方，我们会保证你的安全。\n这是他们所承诺的。随着冷战愈演愈烈，\n在战争被波及到的流浪者们纷纷涌向 M.E.G.。\n然而，M.E.G. 必须继续维护其名号的高标准，\n否则，阈限世界将损失一个伟大的组织......",
            iconModel = "kangelitem:itm/bookmark/meg",
        ),
        FactionInfo(
            tag = "BNTG",
            displayName = "不结盟贸易集团",
            color = "<#33ffaa>",
            description = "金钱归我，荣耀归你。\nB.N.T.G.，亦称不结盟贸易集团。\n一个通过电汇、钞票和股票证书联系在一起的组织。\n只要你能带来收益，你就是B.N.T.G.的不错人选。\n我们等待着，渴望一个未来，一个可能属于金钱和荣耀的秩序。",
            iconModel = "kangelitem:itm/bookmark/bntg",
        ),
        FactionInfo(
            tag = "UEC",
            displayName = "无羁探险者联盟",
            color = "<#ff3d00>",
            description = "U.E.C.创立的宗旨是\"人们的安全高于一切\"。至少他们是这么说的。\n无羁探险者联盟通过创立傀儡组织，扶持代理人来攫取利益。\n一个臭名昭著的例子是\"无尽下水道\"组织，许多U.E.C.的居民都定居于此。\n无羁探险者联盟是一个结合了经济联盟、军事联盟和一体化项目的组织。",
            iconModel = "kangelitem:itm/bookmark/uec",
        ),
    )
}
