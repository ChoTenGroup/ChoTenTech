package io.github.zzzyyylllty.chotentech.data

import io.github.zzzyyylllty.chotentech.kether.evalKether
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import java.util.UUID

data class Power(
    val name: String,
    val leverPos: Pos,
    val openDelaySeconds: Int,
    val activeDurationSeconds: Int,
    val obstacles: List<ObstacleConfig> = emptyList(),
    val openingAnimation: PowerAnimation = PowerAnimation(),
    val closingAnimation: PowerAnimation = PowerAnimation(),
    val agents: HashMap<String, List<String>> = hashMapOf(),
    val activatorUUID: UUID? = null,
    val activator: String? = null,
) {
    fun runAgent(string: String, player: Player? = activatorUUID?.let { Bukkit.getPlayer(it) }) {
        agents[string]?.evalKether(player, linkedMapOf("power" to this, "powername" to name, "opendelay" to openDelaySeconds, "active" to activeDurationSeconds, "activator" to activator, "activatoruuid" to activatorUUID))
    }
}

data class PowerAnimation(
    val enabled: Boolean = true,
    val particle: Particle = Particle.END_ROD,
    val particleCount: Int = 20,
    val sound: Sound? = null,
    val volume: Float = 1.0f,
    val pitch: Float = 1.0f,
    /** 动画持续 tick */
    val durationTicks: Long = 20L,
    /** 粒子动画间隔 tick */
    val intervalTicks: Long = 2L,
)

data class ObstacleConfig(
    val id: String,

    /** 方块列表 */
    val blocks: List<Pos> = emptyList(),

    /** 或者用区域定义 */
    val cuboid: Cuboid? = null,

    val mode: ObstacleMode = ObstacleMode.RESTORE_BLOCKS,

    /** 缓慢开门动画配置 */
    val sequentialConfig: ObstacleSequentialConfig = ObstacleSequentialConfig(),

    /** 电梯门配置（仅 ELEVATOR_DOORS 模式） */
    val elevatorConfig: ElevatorConfig? = null,
) {
    fun allBlocks(): List<Pos> {
        return cuboid?.toPositions() ?: blocks
    }
}

/** 电梯门配置 */
data class ElevatorConfig(
    /** 外按钮位置（呼叫开门） */
    val externalButton: Pos,
    /** 内按钮位置（关门切出 / 紧急开门） */
    val internalButton: Pos,
    /** 切出点名称 */
    val noclipPointName: String,
    /** 切出点范围检查用（两个对角线Pos） */
    val noclipArea1: Pos,
    val noclipArea2: Pos,
    /** 无电源时内部按钮开门持续时间（秒） */
    val doorOpenDurationSeconds: Int = 5,
    /** 开门音效 */
    val doorOpenSound: Sound? = Sound.BLOCK_IRON_TRAPDOOR_OPEN,
    /** 关门音效 */
    val doorCloseSound: Sound? = Sound.BLOCK_IRON_TRAPDOOR_CLOSE,
    /** 开门动画粒子 */
    val doorOpenParticle: Particle? = Particle.SMOKE,
    /** 关门动画粒子 */
    val doorCloseParticle: Particle? = Particle.SMOKE,
    /** 音量和音高 */
    val soundVolume: Float = 1.0f,
    val soundPitch: Float = 1.0f,
)

/** 电梯运行时状态 */
enum class ElevatorState {
    /** 门关闭，等待中 */
    IDLE,
    /** 门正在打开 */
    DOORS_OPENING,
    /** 门已打开，等待玩家进入 */
    DOORS_OPEN,
    /** 门正在关闭 */
    DOORS_CLOSING,
    /** 正在切出中 */
    NOCLIPPING
}

/** 电梯运行时的临时数据 */
data class ElevatorRuntime(
    val config: ElevatorConfig,
    var state: ElevatorState = ElevatorState.IDLE,
    var doorTask: Any? = null,
    var noclipPlayers: MutableSet<String> = mutableSetOf(),
)

data class ObstacleSequentialConfig(
    val enabled: Boolean = true,

    /** 方块打开顺序 */
    val openDirection: OpenDirection = OpenDirection.LEFT_TO_RIGHT,

    /** 关闭时是否反向恢复 */
    val reverseOnClose: Boolean = true,

    /** 每次打开几个方块 */
    val blocksPerStep: Int = 1,

    /** 每一步之间间隔多少 tick */
    val stepDelayTicks: Long = 2L,

    /** 打开过程粒子 */
    val openEffect: ProgressEffect = ProgressEffect(),

    /** 关闭过程粒子 */
    val closeEffect: ProgressEffect = ProgressEffect(
        particle = Particle.WHITE_SMOKE,
        count = 6
    )
)
enum class ObstacleMode {
    /** 拉闸后：临时移除方块，结束后恢复原状 */
    RESTORE_BLOCKS,

    /** 拉闸后：适用于门类方块，直接打开 */
    OPEN_DOOR,

    /** 电梯门：电源激活后外按钮开门，内按钮关门切出，无电源时内按钮短暂开门 */
    ELEVATOR_DOORS
}
fun sortPositionsByDirection(
    positions: List<Pos>,
    direction: OpenDirection
): List<Pos> {
    return when (direction) {
        OpenDirection.LEFT_TO_RIGHT ->
            positions.sortedWith(compareBy<Pos> { it.x }.thenBy { it.y }.thenBy { it.z })

        OpenDirection.RIGHT_TO_LEFT ->
            positions.sortedWith(compareByDescending<Pos> { it.x }.thenBy { it.y }.thenBy { it.z })

        OpenDirection.BOTTOM_TO_TOP ->
            positions.sortedWith(compareBy<Pos> { it.y }.thenBy { it.x }.thenBy { it.z })

        OpenDirection.TOP_TO_BOTTOM ->
            positions.sortedWith(compareByDescending<Pos> { it.y }.thenBy { it.x }.thenBy { it.z })

        OpenDirection.FRONT_TO_BACK ->
            positions.sortedWith(compareBy<Pos> { it.z }.thenBy { it.x }.thenBy { it.y })

        OpenDirection.BACK_TO_FRONT ->
            positions.sortedWith(compareByDescending<Pos> { it.z }.thenBy { it.x }.thenBy { it.y })
    }
}

data class ProgressEffect(
    val enabled: Boolean = true,
    val particle: Particle = Particle.WHITE_SMOKE,
    val count: Int = 8,
    val offsetX: Double = 0.15,
    val offsetY: Double = 0.15,
    val offsetZ: Double = 0.15,
    val speed: Double = 0.01,
    val sound: Sound? = null,
    val volume: Float = 1.0f,
    val pitch: Float = 1.0f
)
enum class OpenDirection {
    LEFT_TO_RIGHT,
    RIGHT_TO_LEFT,
    BOTTOM_TO_TOP,
    TOP_TO_BOTTOM,
    FRONT_TO_BACK,
    BACK_TO_FRONT
}

data class Pos(
    val world: String,
    val x: Int,
    val y: Int,
    val z: Int,
) {
    fun toLocation(): Location {
        val w: World = Bukkit.getWorld(world)
            ?: error("World $world not found")
        return Location(w, x.toDouble(), y.toDouble(), z.toDouble())
    }

    companion object {
        fun fromLocation(loc: Location): Pos {
            return Pos(
                world = loc.world?.name ?: error("Location world is null"),
                x = loc.blockX,
                y = loc.blockY,
                z = loc.blockZ
            )
        }
    }
}

data class Cuboid(
    val pos1: Pos,
    val pos2: Pos
) {
    fun toPositions(): List<Pos> {
        val minX = minOf(pos1.x, pos2.x)
        val maxX = maxOf(pos1.x, pos2.x)
        val minY = minOf(pos1.y, pos2.y)
        val maxY = maxOf(pos1.y, pos2.y)
        val minZ = minOf(pos1.z, pos2.z)
        val maxZ = maxOf(pos1.z, pos2.z)

        val result = mutableListOf<Pos>()
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    result.add(Pos(pos1.world, x, y, z))
                }
            }
        }
        return result
    }
}