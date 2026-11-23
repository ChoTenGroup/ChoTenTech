package io.github.zzzyyylllty.chotentech.feature.gun

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 热量镜像回归测试。
 *
 * 锁定的 bug：冷却 tick 没跨整数时不落盘，而「外部改动」判定原本拿镜像里的冷却进度比 NBT，
 * 于是下一 tick 必然判成外部改动 → 清 `heatLocked` / `forcedStopUntil` 并 `resetTrigger`
 * → AUTO/BURST 打不出第二发、热量永不归零。
 */
class HeatMathTest {

    @Test
    fun `unpersisted sub-integer cooling is not an external change`() {
        // 开火后 NBT=镜像=5.00；tick1 冷却到 4.94 并落盘；tick2 冷却到 4.88 但不落盘。
        // 此时 NBT 仍是 4.94，而已落盘值也是 4.94 —— 不该判成外部改动。
        assertFalse(heatChangedExternally(4.94, 4.94))
        // 旧实现比较的是镜像里的 4.88，于是这里会是 true（这正是死循环的起点）。
        assertTrue(heatChangedExternally(4.94, 4.88))
    }

    @Test
    fun `epsilon tolerance absorbs float round-trip noise`() {
        assertFalse(heatChangedExternally(5.0, 5.0 + 1e-9))
        assertTrue(heatChangedExternally(5.0, 5.0 + 1e-3))
    }

    @Test
    fun `real external edit is still detected`() {
        // 别的插件/管理员把热量改成 80，必须仍然认成外部改动。
        assertTrue(heatChangedExternally(80.0, 4.94))
        assertTrue(heatChangedExternally(0.0, 4.94))
    }

    @Test
    fun `persist only when an integer or tier boundary is crossed`() {
        // 4.94 → 4.88：既没跨整数也没跨档位，只更新镜像。
        assertFalse(heatNeedsPersist(4.94, 4.88, 0, 0))
        // 1.02 → 0.96：跨过整数 1，需要落盘。
        assertTrue(heatNeedsPersist(1.02, 0.96, 0, 0))
        // 跨档位即使整数不变也要落盘（档位提示依赖它）。
        assertTrue(heatNeedsPersist(0.20, 0.14, 1, 0))
        // 1.02 → 0.94 跨过整数 1。
        assertTrue(heatNeedsPersist(1.02, 0.94, 0, 0))
        // 0.05 → 0.0 不跨 floor 边界（两者都是 0），归零由 tickHeat 的 current<=0 分支单独写盘，
        // 所以这里不需要落盘 —— 保持 false 是有意为之。
        assertFalse(heatNeedsPersist(0.05, 0.0, 0, 0))
    }
}