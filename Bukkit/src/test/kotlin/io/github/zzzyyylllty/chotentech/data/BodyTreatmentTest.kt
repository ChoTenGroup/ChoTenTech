package io.github.zzzyyylllty.chotentech.data

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BodyTreatmentTest {
    @Test fun `removal precedes repair and HP with separate durability and shared heal budget`() {
        val body = PlayerBodyHealth()
        body.parts.getValue("STOMACH").toughness = 20.0
        body.applyEffect("STOMACH", MAJOR_BLEED)
        val result = BodyTreatment.apply(body, "STOMACH", true, 15.0,
            200.0, 0.0, 25.0, 25.0, 1.0, 85.0, mapOf(MAJOR_BLEED to 180.0))
        assertEquals(20.0, result.treated, 1e-9)
        assertEquals(0.0, result.hpHealed)
        assertEquals(0.0, result.durability)
        assertTrue(body.hasEffect(WOUND))
        assertFalse(body.hasEffect(MAJOR_BLEED))
        assertEquals(40.0, body.parts.getValue("STOMACH").toughness)
    }

    @Test fun `one use shares maxheal between toughness and health`() {
        val body = PlayerBodyHealth()
        body.parts.getValue("STOMACH").toughness = 20.0
        val result = BodyTreatment.apply(body, "STOMACH", true, 15.0,
            100.0, 0.0, 25.0, 25.0, 2.0, 85.0, emptyMap())
        assertEquals(25.0, result.treated)
        assertEquals(5.0, result.hpHealed)
        assertEquals(50.0, result.durability)
        val next = BodyTreatment.apply(body, "STOMACH", true, 10.0,
            result.durability, result.treated, 25.0, 25.0, 2.0, 85.0, emptyMap())
        assertEquals(0.0, next.treated)
    }

    @Test fun `HP gate is strict and minus one disables HP at full toughness`() {
        val body = PlayerBodyHealth()
        body.parts.getValue("BODY").toughness -= 54.0 // 306/360 = 85% exactly.
        val gated = BodyTreatment.apply(body, null, true, 10.0, 100.0, 0.0, 20.0, 20.0, 1.0, 85.0, emptyMap())
        assertEquals(0.0, gated.hpHealed)
        body.parts.getValue("BODY").toughness = 75.0
        val disabled = BodyTreatment.apply(body, null, true, 10.0, 100.0, 0.0, 20.0, 20.0, 1.0, -1.0, emptyMap())
        assertEquals(0.0, disabled.hpHealed)
    }

    @Test fun `HP gate uses toughness after treatment`() {
        val body = PlayerBodyHealth()
        body.parts.getValue("BODY").toughness = 10.0 // 81.94%, below gate.
        val result = BodyTreatment.apply(body, "BODY", true, 10.0,
            100.0, 0.0, 75.0, 75.0, 1.0, 85.0, emptyMap())
        assertEquals(10.0, result.hpHealed)
        assertEquals(75.0, result.treated)
    }

    @Test fun `destroyed part refuses both repair and debuff removal`() {
        val body = PlayerBodyHealth()
        body.parts.getValue("LARM").toughness = 0.0
        body.applyEffect("LARM", FRACTURE)
        val result = BodyTreatment.apply(body, "LARM", true, 10.0,
            100.0, 0.0, 25.0, 25.0, 1.0, 85.0, mapOf(FRACTURE to 50.0))
        assertTrue(result.blocked)
        assertEquals(100.0, result.durability)
        assertTrue(body.hasEffect(FRACTURE))
    }

    @Test fun `unaffordable removal cannot be bypassed to repair instead`() {
        val body = PlayerBodyHealth()
        body.parts.getValue("LARM").toughness = 20.0
        body.applyEffect("LARM", FRACTURE)
        val result = BodyTreatment.apply(body, "LARM", true, 10.0,
            49.0, 0.0, 25.0, 25.0, 1.0, 85.0, mapOf(FRACTURE to 50.0))
        assertTrue(result.blocked)
        assertEquals(0.0, result.treated)
        assertEquals(20.0, body.parts.getValue("LARM").toughness)
    }

    @Test fun `fractional durability is not rounded away`() {
        val body = PlayerBodyHealth()
        body.parts.getValue("LARM").toughness = 49.0
        val result = BodyTreatment.apply(body, "LARM", false, 10.0,
            .5, 0.0, 10.0, 10.0, .25, -1.0, emptyMap())
        assertEquals(1.0, result.treated)
        assertEquals(.25, result.durability, 1e-9)
        assertEquals(0.0, result.hpHealed)
    }

    @Test fun `treated already includes health so maxheal is a combined cap`() {
        val body = PlayerBodyHealth()
        body.parts.getValue("STOMACH").toughness = 20.0
        val result = BodyTreatment.apply(body, "STOMACH", true, 15.0,
            100.0, 0.0, 25.0, 25.0, 2.0, 85.0, emptyMap())
        // 20 韧性 + 5 血量 = 25，`treated` 是合计值，血量不能重复累加。
        assertEquals(25.0, result.treated, 1e-9)
        assertEquals(5.0, result.hpHealed, 1e-9)
        assertEquals(20.0, result.toughnessHealed, 1e-9)
        assertEquals(result.treated, result.toughnessHealed + result.hpHealed, 1e-9)
    }

    @Test fun `health sharing the round budget cannot exceed maxheal across rounds`() {
        val body = PlayerBodyHealth()
        body.parts.getValue("STOMACH").toughness = 34.0 // 354/360 = 98.3%, above the 85% gate
        var durability = 200.0
        var used = 0.0
        var hpTotal = 0.0
        repeat(10) {
            val result = BodyTreatment.apply(body, "STOMACH", true, 100.0,
                durability, used, 30.0, 30.0, 1.0, 85.0, emptyMap())
            durability = result.durability
            used += result.treated
            hpTotal += result.hpHealed
        }
        // 6 韧性 + 24 血量 = 30 的 maxheal 上限，之后必须停下来。
        assertEquals(30.0, used, 1e-9)
        assertEquals(24.0, hpTotal, 1e-9)
        assertEquals(40.0, body.parts.getValue("STOMACH").toughness, 1e-9)
    }

    @Test fun `health is deferred until the selected part is full`() {
        val body = PlayerBodyHealth()
        val arm = body.parts.getValue("LARM") // max 50
        arm.toughness = 30.0
        var durability = 200.0
        var used = 0.0
        fun round(): BodyTreatment.Result {
            val result = BodyTreatment.apply(body, "LARM", true, 40.0,
                durability, used, 100.0, 10.0, 1.0, 10.0, emptyMap())
            durability = result.durability
            used += result.treated
            return result
        }

        // 缺口 20：前两轮额度全给韧性。
        val first = round()
        assertEquals(10.0, first.toughnessHealed, 1e-9)
        assertEquals(0.0, first.hpHealed, 1e-9)
        assertEquals(40.0, arm.toughness, 1e-9)
        val second = round()
        assertEquals(10.0, second.toughnessHealed, 1e-9)
        assertEquals(0.0, second.hpHealed, 1e-9)
        assertEquals(50.0, arm.toughness, 1e-9)
        // 打满之后才轮到血量。
        val third = round()
        assertEquals(0.0, third.toughnessHealed, 1e-9)
        assertEquals(10.0, third.hpHealed, 1e-9)
    }
}
