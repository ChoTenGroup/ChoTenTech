package io.github.zzzyyylllty.chotentech.feature.gun

import io.github.zzzyyylllty.chotentech.util.devLog
import io.github.zzzyyylllty.chotentech.util.minimessage.legacyToMiniMessage
import io.github.zzzyyylllty.chotentech.util.minimessage.mmUtil
import net.kyori.adventure.audience.Audience
import org.bukkit.Location
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.module.nms.getItemTag
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.random.Random

/** 护甲数据读取、子弹减伤及耐久结算；近战后续可复用 [mitigate] 作为公共入口。 */
object ArmorManager {
    data class ArmorPiece(
        val item: ItemStack,
        val slot: String,
        val data: ArmorData,
        val durability: Double,
        val maxDurability: Double,
    )

    data class Mitigation(
        val damage: Double,
        val pierced: Boolean,
        val penRatio: Double,
        val armorDurabilityLoss: Double,
        val covered: Boolean,
    )

    data class ArmorMaterial(
        val name: String,
        val durabilityMult: Double,
        val repairMaterial: String,
        val levelBonus: Double,
    )

    private val brokenNoticeAt = ConcurrentHashMap<UUID, Long>()

    /** 当前装备槽里的真实 ItemStack；耐久写回后还会显式 set 回装备槽。 */
    fun pieces(target: LivingEntity): List<ArmorPiece> {
        val equipment = try {
            target.equipment ?: return emptyList()
        } catch (_: Exception) {
            return emptyList()
        }
        val slots = listOf(
            "HEAD" to equipment.helmet,
            "BODY" to equipment.chestplate,
            "LEGS" to equipment.leggings,
            "FEET" to equipment.boots,
        )
        return slots.mapNotNull { (slot, item) ->
            val data = GunRegistry.armorOf(item) ?: return@mapNotNull null
            val actual = item ?: return@mapNotNull null
            ArmorPiece(actual, slot, data, durability(actual, data), data.durability.coerceAtLeast(0.0))
        }
    }

    fun durability(item: ItemStack, data: ArmorData): Double = try {
        item.getItemTag().getDeep("sertraline_data.armor.durability")
            ?.asString()?.toDoubleOrNull()
            ?.takeIf { it.isFinite() }
            ?.coerceIn(0.0, data.durability.coerceAtLeast(0.0))
            ?: data.durability.coerceAtLeast(0.0)
    } catch (_: Exception) {
        data.durability.coerceAtLeast(0.0)
    }

    fun writeDurability(item: ItemStack, value: Double) {
        try {
            val tag = item.getItemTag()
            tag.putDeep("sertraline_data.armor.durability", value.coerceAtLeast(0.0))
            tag.saveTo(item)
        } catch (e: Exception) {
            devLog("[Gun] 写入护甲耐久失败: ${e.message}")
        }
    }

    fun hitPart(target: LivingEntity, hitPoint: Location): String {
        val height = target.height.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
        val h = ((hitPoint.y - target.location.y) / height).coerceIn(0.0, 1.0)
        return when {
            h >= 0.85 -> "HEAD"
            h >= 0.55 -> "BODY"
            h >= 0.35 -> "STOMACH"
            else -> "LEGS"
        }
    }

    fun covers(piece: ArmorPiece, part: String): Boolean {
        val wanted = part.uppercase()
        val declared = piece.data.part.map { it.uppercase() }
        // ArmorData 无法区分“未配置 part”和显式只写 [BODY]；二者均使用槽位默认覆盖。
        if (declared != listOf("BODY")) return wanted in declared
        return when (piece.slot) {
            "HEAD" -> wanted == "HEAD"
            "BODY" -> wanted in setOf("BODY", "STOMACH", "ARMS")
            "LEGS" -> wanted in setOf("LEGS", "STOMACH")
            "FEET" -> wanted == "LEGS"
            else -> false
        }
    }

    fun material(name: String): ArmorMaterial {
        val key = name.trim().uppercase().ifEmpty { "IRON" }
        val configured = GunSettings.armor.materials[key]
        if (configured != null) return configured.copy(name = key)
        val fallback = when (key) {
            "DIAMOND" -> ArmorMaterial(key, 0.6, "DIAMOND", 5.0)
            "IRON" -> ArmorMaterial(key, 1.0, "IRON_INGOT", 0.0)
            "STEEL" -> ArmorMaterial(key, 0.85, "IRON_INGOT", 2.0)
            "LEATHER" -> ArmorMaterial(key, 1.4, "LEATHER", -5.0)
            else -> {
                devLog("[Gun] 未知护甲材质 $name，按 IRON 结算")
                GunSettings.armor.materials["IRON"]
                    ?: ArmorMaterial("IRON", 1.0, "IRON_INGOT", 0.0)
            }
        }
        return fallback
    }

    fun mitigate(raw: Double, ammo: AmmoData, target: LivingEntity, hitPoint: Location, crit: Boolean): Mitigation {
        val part = hitPart(target, hitPoint)
        val covered = pieces(target).filter { covers(it, part) && it.durability > 0.0 }
        if (covered.isEmpty()) {
            return Mitigation(raw, true, Double.POSITIVE_INFINITY, 0.0, false)
        }

        val armorDef = covered.sumOf { it.data.defense }
        val durRatio = covered.map {
            if (it.maxDurability > 0.0) (it.durability / it.maxDurability).coerceIn(0.0, 1.0) else 0.0
        }.average()
        val highestLevel = covered.maxByOrNull { it.data.defenseLevel } ?: covered.first()
        val armorLevel = highestLevel.data.defenseLevel + material(highestLevel.data.material).levelBonus
        val effDef = armorDef * (0.5 + 0.5 * durRatio)
        val effLevel = armorLevel * (0.5 + 0.5 * durRatio)
        val penRatio = ammo.penetration / max(effLevel, 1.0)
        val pierceChance = ((penRatio - 0.35) / 0.9).coerceIn(0.0, 1.0)
        val pierced = penRatio >= 1.25 || Random.nextDouble() < pierceChance
        val durabilityMult = covered.map { material(it.data.material).durabilityMult }.average()
        var loss = ammo.armorDamage * durabilityMult * (1.0 + penRatio * 0.5)

        val damage: Double
        if (pierced) {
            damage = raw * (1.0 - effDef / (effDef + 100.0) * 0.5)
            loss *= 1.2
        } else {
            damage = raw * 0.2
        }
        if (crit) loss *= 1.1
        return Mitigation(damage, pierced, penRatio, loss, true)
    }

    /** 按 defense 占比分摊；零防御护甲均摊，并在跨 0 的那次提示破碎。 */
    fun applyDurabilityLoss(target: LivingEntity, part: String, loss: Double, covered: List<ArmorPiece>) {
        if (loss <= 0.0 || !loss.isFinite() || covered.isEmpty()) return
        val totalDefense = covered.sumOf { it.data.defense.coerceAtLeast(0.0) }
        var broke = false
        for (piece in covered) {
            val share = if (totalDefense > 0.0) piece.data.defense.coerceAtLeast(0.0) / totalDefense
            else 1.0 / covered.size
            val next = (piece.durability - loss * share).coerceIn(0.0, piece.maxDurability)
            writeDurability(piece.item, next)
            writeBack(target, piece)
            if (piece.durability > 0.0 && next <= 0.0) broke = true
        }
        if (broke) notifyBroken(target, part)
    }

    private fun writeBack(target: LivingEntity, piece: ArmorPiece) {
        try {
            val equipment = target.equipment ?: return
            when (piece.slot) {
                "HEAD" -> equipment.setHelmet(piece.item)
                "BODY" -> equipment.setChestplate(piece.item)
                "LEGS" -> equipment.setLeggings(piece.item)
                "FEET" -> equipment.setBoots(piece.item)
            }
        } catch (e: Exception) {
            devLog("[Gun] 护甲耐久写回装备槽失败: ${e.message}")
        }
    }

    private fun notifyBroken(target: LivingEntity, part: String) {
        val player = target as? Player ?: return
        val now = System.currentTimeMillis()
        val previous = brokenNoticeAt[player.uniqueId] ?: 0L
        if (now - previous < 1000L) return
        brokenNoticeAt[player.uniqueId] = now
        GunPrompt.show(player, "<#E08A98>${part}护甲已破碎", force = true)
    }

    /** 清理破碎提示节流缓存（玩家下线 / 关服时调用，避免按 UUID 长期残留）。 */
    fun clearNotice(player: Player) {
        brokenNoticeAt.remove(player.uniqueId)
    }

    fun clearNotices() {
        brokenNoticeAt.clear()
    }

    /** 仅完全破碎的护甲可修；调用方负责材料校验与消耗。 */
    fun repair(item: ItemStack): Boolean {
        val data = GunRegistry.armorOf(item) ?: return false
        if (durability(item, data) > 0.0) return false
        writeDurability(item, data.durability.coerceAtLeast(0.0) * 0.5)
        return true
    }

    fun totalMachineEfficiencyModifier(player: Player): Double = pieces(player).sumOf {
        if (it.durability <= 0.0) return@sumOf 0.0
        val ratio = if (it.maxDurability > 0.0) (it.durability / it.maxDurability).coerceIn(0.0, 1.0) else 0.0
        it.data.machineEfficiency * (0.5 + 0.5 * ratio)
    }

    /** 全部未破碎护甲的移速修正之和；零耐久贡献为 0，其余按 0.5 + 0.5 * ratio 衰减。 */
    fun totalSpeedModifier(player: Player): Double = pieces(player).sumOf {
        if (it.durability <= 0.0) return@sumOf 0.0
        val ratio = if (it.maxDurability > 0.0) (it.durability / it.maxDurability).coerceIn(0.0, 1.0) else 0.0
        it.data.speed * (0.5 + 0.5 * ratio)
    }
}
