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
        /**
         * 本次减伤实际用到的护甲件（含本部位、耐久 > 0 的那些）。
         *
         * 由 [mitigate] 一并返回，调用方**不要**再自己 `pieces(target)` 一遍：
         * 那等于每发子弹多做一轮装备扫描 + NBT 解析（霰弹一次 8 颗弹丸就是 16 次），
         * 而且两次扫描之间护甲可能被换下，导致「减伤用 A 套、耐久扣 B 套」。
         */
        val pieces: List<ArmorPiece> = emptyList(),
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
        // 与韧性系统共用同一套判定：此前这里用 0.85/0.55/0.35 且只返回 HEAD/BODY/STOMACH/LEGS，
        // 与 BodyHealthManager 的 0.80/0.58/0.40 + 左右侧向判定不一致，导致
        // 「韧性结算头部、护甲结算胸甲」，且手臂/单腿护甲永远匹配不上。
        return io.github.zzzyyylllty.chotentech.function.body.BodyHealthManager.hitPart(target, hitPoint)
    }

    /** 把 `LEGS` / `ARMS` 这类旧别名展开成具体肢，避免配置与判定两侧用词不一致。 */
    internal fun expandPart(part: String): Set<String> = when (val id = part.trim().uppercase()) {
        "LEGS" -> setOf("LLEG", "RLEG")
        "ARMS" -> setOf("LARM", "RARM")
        "" -> emptySet()
        else -> setOf(id)
    }

    /**
     * 槽位在**未显式配置 `part`** 时的默认覆盖范围。
     *
     * 抽成纯函数是为了能单测：`covers` 需要真实 `ItemStack`，单测里构造不出来。
     */
    internal fun slotCovers(slot: String, part: String): Boolean {
        val wanted = expandPart(part)
        if (wanted.isEmpty()) return false
        return when (slot.uppercase()) {
            "HEAD" -> wanted.any { it == "HEAD" }
            "BODY" -> wanted.any { it in setOf("BODY", "STOMACH", "LARM", "RARM") }
            "LEGS" -> wanted.any { it in setOf("LLEG", "RLEG", "STOMACH") }
            "FEET" -> wanted.any { it in setOf("LLEG", "RLEG") }
            else -> false
        }
    }

    fun covers(piece: ArmorPiece, part: String): Boolean {
        val wanted = expandPart(part)
        if (wanted.isEmpty()) return false
        // 现在由 `ArmorData.partConfigured` 明确区分「没写 part」与「显式写了 part: [BODY]」，
        // 不再靠「值恰好等于 [BODY]」来猜：那种猜测会让显式 `part: [BODY]`（想当护胸用）
        // 被静默当成「未配置」而按装备槽判定。
        if (!piece.data.partConfigured) return slotCovers(piece.slot, part)
        val declared = piece.data.part.flatMap { expandPart(it.uppercase()) }.toSet()
        return wanted.any { it in declared }
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
            return Mitigation(raw, true, Double.POSITIVE_INFINITY, 0.0, false, emptyList())
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
        // 把用过的护甲件一并交回去，调用方不必再扫描一遍装备槽
        return Mitigation(damage, pierced, penRatio, loss, true, covered)
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
