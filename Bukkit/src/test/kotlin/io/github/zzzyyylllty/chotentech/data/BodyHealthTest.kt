package io.github.zzzyyylllty.chotentech.data

import io.github.zzzyyylllty.chotentech.function.body.BodyHealthManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BodyHealthTest {
    @Test fun `specified stomach hits use prehit percentage and destroy at zero`() {
        val body = PlayerBodyHealth()
        assertEquals(360.0, body.maxToughness)
        assertEquals(5.0, body.damage("STOMACH", 20.0), 1e-9)
        assertEquals(20.0, body.parts.getValue("STOMACH").toughness)
        assertEquals(10.0, body.damage("STOMACH", 20.0), 1e-9)
        assertEquals("DESTROYED", body.parts.getValue("STOMACH").state)
        assertEquals(0.0, body.parts.getValue("STOMACH").heal(30.0))
    }

    @Test fun `mitigation caps at 75 percent`() {
        val body = PlayerBodyHealth()
        body.parts.getValue("BODY").toughness = 60.0
        assertEquals(5.0, body.damage("BODY", 20.0), 1e-9)
    }

    @Test fun `first hit overflow immediately spreads with source multiplier`() {
        val body = PlayerBodyHealth()
        body.parts.getValue("HEAD").toughness = 5.0
        body.damageToughness("HEAD", 11.0)
        assertEquals(0.0, body.parts.getValue("HEAD").toughness)
        for (part in body.parts.values.filter { it.id != "HEAD" }) {
            assertEquals(part.maxToughness - 2.0, part.toughness, 1e-9)
        }
    }

    @Test fun `destroyed legs spread damage at 0 point 6 without healing themselves`() {
        val body = PlayerBodyHealth()
        body.parts.getValue("LLEG").toughness = 0.0
        assertEquals(10.0, body.damage("LLEG", 10.0), 1e-9)
        assertEquals(0.0, body.parts.getValue("LLEG").toughness)
        assertEquals(44.0, body.parts.getValue("HEAD").toughness, 1e-9)
    }

    @Test fun `overflow waves redistribute after recipients are destroyed`() {
        val body = PlayerBodyHealth()
        body.parts.values.forEach { it.toughness = 0.0 }
        body.parts.getValue("BODY").toughness = 1.0
        body.parts.getValue("RARM").toughness = 50.0
        body.damageToughness("HEAD", 10.0)
        // Head expands 10 to 20: body gets 10, arm gets 10; body's 9 excess goes to arm.
        assertEquals(31.0, body.parts.getValue("RARM").toughness, 1e-9)
    }

    @Test fun `huge damage terminates with all parts at zero`() {
        val body = PlayerBodyHealth()
        body.damageToughness("HEAD", 100000.0)
        assertTrue(body.parts.values.all { it.destroyed && it.toughness == 0.0 })
        body.damageToughness("HEAD", 100000.0)
        assertEquals(0.0, body.toughnessPercentage)
    }

    @Test fun `max changes preserve percentage including destroyed limbs`() {
        val body = PlayerBodyHealth()
        val arm = body.parts.getValue("LARM")
        arm.toughness = 25.0
        arm.resize(100.0)
        assertEquals(50.0, arm.toughness)
        arm.toughness = 0.0
        arm.resize(200.0)
        assertTrue(arm.destroyed)
        assertEquals(body.toughness / body.maxToughness * 100, body.toughnessPercentage, 1e-9)
    }

    @Test fun `effects are unique and anatomically restricted`() {
        val body = PlayerBodyHealth()
        assertFalse(body.applyEffect("HEAD", FRACTURE))
        assertFalse(body.applyEffect("LARM", PENETRATION))
        assertFalse(body.applyEffect("BODY", SHOCK_SEQUELA))
        assertTrue(body.applyEffect("HEAD", PENETRATION, durationMs = 5000))
        assertTrue(body.applyEffect("HEAD", PENETRATION, durationMs = 10000))
        assertEquals(1, body.parts.getValue("HEAD").effects.size)
        assertEquals(10000, body.parts.getValue("HEAD").effects.getValue(PENETRATION).remainingDuration)
    }

    @Test fun `bleeding and wounds replace one another per part`() {
        val body = PlayerBodyHealth()
        body.applyEffect("BODY", MAJOR_BLEED)
        assertFalse(body.applyEffect("BODY", MINOR_BLEED))
        body.parts.getValue("BODY").effects.remove(MAJOR_BLEED)
        body.applyEffect("BODY", WOUND, durationMs = 240000)
        body.applyEffect("BODY", MINOR_BLEED)
        assertFalse(body.hasEffect(WOUND))
        assertTrue(body.hasEffect(MINOR_BLEED))
    }

    @Test fun `bandaging major bleeding creates a four minute wound and rejects destroyed parts`() {
        val body = PlayerBodyHealth()
        body.applyEffect("BODY", MAJOR_BLEED)
        assertTrue(body.bandage("BODY"))
        assertFalse(body.hasEffect(MAJOR_BLEED))
        assertEquals(240000L, body.parts.getValue("BODY").effects.getValue(WOUND).remainingDuration)
        body.applyEffect("LARM", MAJOR_BLEED)
        body.parts.getValue("LARM").toughness = 0.0
        assertFalse(body.bandage("LARM"))
        assertTrue(body.parts.getValue("LARM").effects.containsKey(MAJOR_BLEED))
    }

    @Test fun `quick treatment weights missing percentage by priority`() {
        val body = PlayerBodyHealth()
        body.parts.getValue("HEAD").toughness *= .6
        body.parts.getValue("LARM").toughness *= .3
        assertEquals("HEAD", body.quickTreatmentPart()?.id)
        body.parts.getValue("HEAD").toughness = 0.0
        assertEquals("LARM", body.quickTreatmentPart()?.id)
    }

    @Test fun `both arms stack and right arm alone reduces melee`() {
        val body = PlayerBodyHealth()
        body.applyEffect("LARM", FRACTURE)
        body.applyEffect("RARM", FRACTURE)
        body.parts.getValue("RARM").toughness = 0.0
        val penalties = body.armPenalties()
        assertEquals(.35, penalties.aim, 1e-9)
        assertEquals(.35, penalties.accuracy, 1e-9)
        assertEquals(.65, penalties.search, 1e-9)
        assertEquals(.25, penalties.melee, 1e-9)
        body.applyEffect("HEAD", PAIN_RELIEF)
        assertEquals(.20, body.armPenalties().aim, 1e-9)
        assertEquals(.20, body.armPenalties().melee, 1e-9)
    }

    @Test fun `head penalty caps after 100 seconds and returns 15 percent immediately`() {
        val head = PlayerBodyHealth().parts.getValue("HEAD")
        head.toughness = 0.0
        head.tickDestructionPenalty(0)
        assertEquals(.10, head.destructionPenalty, 1e-9)
        head.tickDestructionPenalty(2000)
        assertEquals(.30, head.destructionPenalty, 1e-9)
        head.toughness = 1.0 // Explicit restoration API; ordinary treatment cannot do this.
        head.tickDestructionPenalty(0)
        assertEquals(.15, head.destructionPenalty, 1e-9)
        head.tickDestructionPenalty(80)
        assertEquals(.14, head.destructionPenalty, 1e-9)
    }

    @Test fun `body penalty reaches cap after 400 seconds and recovers each ten seconds`() {
        val part = PlayerBodyHealth().parts.getValue("BODY")
        part.toughness = 0.0
        part.tickDestructionPenalty(8000)
        assertEquals(.30, part.destructionPenalty, 1e-9)
        part.toughness = 1.0
        part.tickDestructionPenalty(0)
        assertEquals(.20, part.destructionPenalty, 1e-9)
        part.tickDestructionPenalty(200)
        assertEquals(.19, part.destructionPenalty, 1e-9)
    }

    @Test fun `penalty preview sums destruction penetration and shock without advancing timers`() {
        val body = PlayerBodyHealth()
        body.parts.getValue("HEAD").toughness = 0.0
        body.parts.getValue("HEAD").tickDestructionPenalty(0)
        val ticksBefore = body.parts.getValue("HEAD").penaltyTicks
        body.applyEffect("HEAD", PENETRATION)
        body.applyEffect("BODY", PENETRATION)
        body.applyEffect("HEAD", SHOCK_SEQUELA)
        val (health, speed) = BodyHealthManager.computePenalties(body)
        // head 10% + two penetrations 12% (total, not stacked per part) + sequela 20%
        assertEquals(.42, health, 1e-9)
        assertEquals(0.0, speed, 1e-9)
        // A read-only preview must not move the destruction timer.
        assertEquals(ticksBefore, body.parts.getValue("HEAD").penaltyTicks)
    }

    @Test fun `penalty preview stacks leg destruction and fracture at the spec rates`() {
        val body = PlayerBodyHealth()
        body.applyEffect("LLEG", FRACTURE)
        body.parts.getValue("RLEG").toughness = 0.0
        val (_, speed) = BodyHealthManager.computePenalties(body)
        assertEquals(.35, speed, 1e-9)
        body.applyEffect("HEAD", PAIN_RELIEF)
        // Pain relief cancels the fracture penalty; destruction still slows movement.
        assertEquals(.20, BodyHealthManager.computePenalties(body).second, 1e-9)
    }

    @Test fun `invalid damage cannot corrupt toughness`() {
        val body = PlayerBodyHealth()
        for (damage in listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0)) body.damage("HEAD", damage)
        assertEquals(360.0, body.toughness)
    }

    @Test fun `destroyed part stays at zero when its maximum toughness changes`() {
        val body = PlayerBodyHealth()
        val arm = body.parts.getValue("LARM")
        arm.toughness = 0.0
        arm.maxToughness = 120.0
        arm.toughness = 0.0
        assertEquals(0.0, arm.toughness, 1e-9)
        assertEquals("DESTROYED", arm.state)
        // A damaged part still rescales with its percentage.
        val leg = body.parts.getValue("LLEG")
        leg.resize(100.0)
        assertEquals(100.0, leg.toughness, 1e-9)
    }

    @Test fun `legend wounded leg costs match the specified table`() {
        val body = PlayerBodyHealth()
        body.applyEffect("LLEG", FRACTURE)
        body.damageToughness("LLEG", .5)
        assertEquals(49.5, body.parts.getValue("LLEG").toughness, 1e-9)
        // Destroyed + fractured leg cannot absorb, so the 1.0 cost spreads at 0.6.
        val other = PlayerBodyHealth()
        other.parts.getValue("LLEG").toughness = 0.0
        other.applyEffect("LLEG", FRACTURE)
        val before = other.parts.getValue("RARM").toughness
        other.damageToughness("LLEG", 1.0)
        assertEquals(0.0, other.parts.getValue("LLEG").toughness)
        assertTrue(other.parts.getValue("RARM").toughness < before)
    }

    @Test fun `bleed out only drains the part that carries it`() {
        val body = PlayerBodyHealth()
        body.applyEffect("HEAD", MINOR_BLEED)
        val legsBefore = listOf("LLEG", "RLEG").associateWith { body.parts.getValue(it).toughness }
        // 20 ticks x 0.6 = 12 drained; head has 45, so it never overflows into other parts.
        repeat(20) { body.damageToughness("HEAD", .6) }
        assertEquals(33.0, body.parts.getValue("HEAD").toughness, 1e-9)
        for (part in body.parts.values.filter { it.id != "HEAD" }) {
            assertEquals(part.maxToughness, part.toughness, 1e-9)
        }
        for ((id, before) in legsBefore) assertEquals(before, body.parts.getValue(id).toughness, 1e-9)
    }
}
