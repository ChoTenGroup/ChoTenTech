package io.github.zzzyyylllty.chotentech.function

import io.github.zzzyyylllty.chotentech.ChoTenTech.config
import io.github.zzzyyylllty.chotentech.data.DataManager.saveData
import io.github.zzzyyylllty.chotentech.data.DeathDropData
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.util.ItemSerializer
import io.github.zzzyyylllty.chotentech.util.minimessage.mmUtil
import org.bukkit.Bukkit
import org.bukkit.GameRule
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.persistence.PersistentDataType
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit
import taboolib.common.platform.service.PlatformExecutor
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object DeathDropManager {

    private val DEATH_DROP_KEY = NamespacedKey("chotentech", "deathdrop")

    val deathDrops = ConcurrentHashMap<String, DeathDropData>()
    private val dropTasks = ConcurrentHashMap<String, PlatformExecutor.PlatformTask>()

    private val timeoutSec by lazy { config.getInt("death-drop.timeout-seconds", 1200) }
    private val dropTitleStr by lazy { config.getString("death-drop.drop-title", "死亡掉落") ?: "死亡掉落" }

    fun handlePlayerDeath(player: Player) {
        val data = player.getData() ?: return

        // 检测是否死亡不掉落 — 如果开启 keepInventory 则不处理
        if (player.world.getGameRuleValue(GameRule.KEEP_INVENTORY) == true) {
            return
        }

        InsuranceManager.cleanExpired(player)

        val chance = InsuranceManager.getDropChance(player)

        val drops = linkedMapOf<Int, String>()
        val returns = mutableListOf<String>()
        val keeps = mutableListOf<String>()

        for (slot in 0 until 41) {
            val item = player.inventory.getItem(slot) ?: continue
            if (item.type.isAir) continue

            // 已投保物品：取出保险，返还给玩家
            val owner = InsuranceManager.getInsuredOwner(item)
            if (owner != null && owner == player.name) {
                val cleaned = InsuranceManager.untagItem(item.clone())
                ItemSerializer.serializeNbt(cleaned)?.let { returns.add(it) }
                // 保险由物品 NBT 标签承担，不再使用 data.insuredItems 副本。
                // 清除遗留的 insuredItems 记录，避免旧版本存储的副本被还原造成刷物品。
                data.insuredItems.clear()
                player.inventory.setItem(slot, ItemStack(Material.AIR))
                continue
            }

            if (Math.random() * 100 < chance) {
                // 掉落
                ItemSerializer.serializeNbt(item)?.let { drops[slot] = it }
                player.inventory.setItem(slot, ItemStack(Material.AIR))
            } else {
                // 保留在身上
                ItemSerializer.serializeNbt(item)?.let { keeps.add(it) }
                player.inventory.setItem(slot, ItemStack(Material.AIR))
            }
        }

        // 保存投保返还
        if (returns.isNotEmpty()) {
            data.returnedInsuranceItems.addAll(returns)
            player.sendMessage(
                mmUtil.deserialize(
                    "<#DFBD7C>📦 <#F2F5FA>你的 <#EFDF8F>${returns.size}</#EFDF8F> 个投保物品已安全返还，使用 <#EFDF8F>/cttdrop cover</#EFDF8F> 取回。"
                )
            )
        }

        // 保存保留物品（重生物品时归还）
        if (keeps.isNotEmpty()) {
            data.deathKeepItems = keeps
        }

        saveData(player)

        if (drops.isEmpty()) return

        val loc = player.location
        val entityUuid = UUID.randomUUID().toString()

        deathDrops[entityUuid] = DeathDropData(
            entityUuid = entityUuid,
            ownerUuid = player.uniqueId.toString(),
            world = loc.world.name,
            x = loc.x,
            y = loc.y,
            z = loc.z,
            items = drops
        )

        spawnDropStand(loc, entityUuid, player)
        scheduleScatter(entityUuid)
    }

    @SubscribeEvent
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        val data = player.getData() ?: return

        val keepItems = data.deathKeepItems.toList()
        if (keepItems.isEmpty()) return

        // 先清空数据并保存，避免崩服后重复归还
        data.deathKeepItems.clear()
        saveData(player)

        for (serialized in keepItems) {
            val item = ItemSerializer.deserializeAny(serialized) ?: continue
            player.inventory.addItem(item).values.forEach { leftover ->
                player.world.dropItemNaturally(player.location, leftover)
            }
        }
    }

    private fun spawnDropStand(loc: Location, entityUuid: String, player: Player) {
        val world = loc.world ?: return
        world.spawn(loc, ArmorStand::class.java) { stand ->
            stand.isVisible = false
            stand.isSmall = true
            stand.setGravity(false)
            stand.setCanPickupItems(false)
            stand.setCustomNameVisible(true)
            stand.customName(mmUtil.deserialize("<#E08A98>☠ <#F2F5FA>${player.name} <#A9B4C3>的遗物"))
            stand.isInvulnerable = true

            val head = ItemStack(Material.PLAYER_HEAD).apply {
                itemMeta = (itemMeta as? SkullMeta)?.apply { setOwningPlayer(player) }
            }
            stand.setHelmet(head)

            stand.persistentDataContainer.set(DEATH_DROP_KEY, PersistentDataType.STRING, entityUuid)
        }
    }

    private fun scheduleScatter(entityUuid: String) {
        dropTasks.remove(entityUuid)?.cancel()
        val task = submit(delay = timeoutSec * 20L) { scatterItems(entityUuid) }
        dropTasks[entityUuid] = task
    }

    fun scatterItems(entityUuid: String) {
        val data = deathDrops[entityUuid] ?: return
        val world = Bukkit.getWorld(data.world) ?: return

        data.items.forEach { (_, serialized) ->
            ItemSerializer.deserializeAny(serialized)?.let { item ->
                world.dropItemNaturally(Location(world, data.x, data.y, data.z), item)
            }
        }
        cleanup(entityUuid)
    }

    fun cleanup(entityUuid: String) {
        dropTasks.remove(entityUuid)?.cancel()
        deathDrops.remove(entityUuid)

        Bukkit.getWorlds().forEach { world ->
            world.getEntitiesByClass(ArmorStand::class.java).forEach { stand ->
                if (stand.persistentDataContainer.get(DEATH_DROP_KEY, PersistentDataType.STRING) == entityUuid) {
                    stand.remove()
                }
            }
        }
    }

    fun resetTimeout(entityUuid: String) {
        scheduleScatter(entityUuid)
    }

    fun getDropData(stand: ArmorStand): DeathDropData? {
        val id = stand.persistentDataContainer.get(DEATH_DROP_KEY, PersistentDataType.STRING) ?: return null
        return deathDrops[id]
    }

    fun hasDeathDropKey(stand: ArmorStand): Boolean {
        return stand.persistentDataContainer.has(DEATH_DROP_KEY, PersistentDataType.STRING)
    }

    fun removeStand(stand: ArmorStand) {
        val id = stand.persistentDataContainer.get(DEATH_DROP_KEY, PersistentDataType.STRING)
        if (id != null) {
            dropTasks.remove(id)?.cancel()
            deathDrops.remove(id)
        }
        stand.remove()
    }

    fun removeAll() {
        dropTasks.values.forEach { it.cancel() }
        dropTasks.clear()
        deathDrops.clear()
        Bukkit.getWorlds().forEach { world ->
            world.getEntitiesByClass(ArmorStand::class.java).forEach { stand ->
                if (stand.persistentDataContainer.has(DEATH_DROP_KEY, PersistentDataType.STRING)) {
                    stand.remove()
                }
            }
        }
    }

    fun getDropTitle(): String = dropTitleStr
}
