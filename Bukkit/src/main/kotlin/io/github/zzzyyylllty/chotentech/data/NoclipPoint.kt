package io.github.zzzyyylllty.chotentech.data

import org.bukkit.Location
import org.bukkit.World
import org.bukkit.util.Vector

data class NoclipPoint(
    val noclipPointWorld: String?,
    val noclipPointLocation1: Vector, // 切出点位置1 对角线构成的位置即为切出点范围
    val noclipPointLocation2: Vector, // 切出点位置2 对角线构成的位置即为切出点范围
    val targetLocation: Location, // 传送玩家到某处
    val basicSuccessRate: Double = 25.0, // 基础成功概率
    val rangeDecay: Double = 1.0, // 远离中心点成功率衰减倍数
    val hunger: Double = 1.0, // 切出饥饿惩罚
    val damage: Double = 1.0, // 切出失败血量惩罚
    val cooldown: Double = 2.0, // 执行切出操作后的冷却。
    val noclippingTime: Double = 10.0,
    val name: String?,
    val targetLevel: String?,
    val autoNoclip: Boolean = false, // 是否自动切出（无需蹲下）
){
}
