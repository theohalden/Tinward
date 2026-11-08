package dev.kosh.financetracker.feature

import dev.kosh.financetracker.feature.plus.EntitlementPolicy
import dev.kosh.financetracker.feature.plus.EntitlementRecord
import dev.kosh.financetracker.feature.plus.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EntitlementPolicyTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_000 * day

    @Test fun `no record means free`() = assertEquals(Tier.FREE, EntitlementPolicy.tier(null, now))

    @Test fun `play says not subscribed means free`() = assertEquals(Tier.FREE, EntitlementPolicy.tier(EntitlementRecord(false, now), now))

    @Test fun `verified recently means plus`() = assertEquals(Tier.PLUS, EntitlementPolicy.tier(EntitlementRecord(true, now - 2 * day), now))

    @Test fun `offline grace holds for seven days then lapses`() {
        assertEquals(Tier.PLUS, EntitlementPolicy.tier(EntitlementRecord(true, now - 7 * day), now))
        assertEquals(Tier.FREE, EntitlementPolicy.tier(EntitlementRecord(true, now - 8 * day), now))
    }

    @Test fun `grace days left`() {
        assertEquals(5L, EntitlementPolicy.graceDaysLeft(EntitlementRecord(true, now - 2 * day), now))
        assertNull(EntitlementPolicy.graceDaysLeft(EntitlementRecord(true, now - 9 * day), now))
        assertNull(EntitlementPolicy.graceDaysLeft(EntitlementRecord(false, now), now))
    }

    @Test fun `a later check that finds no subscription removes plus immediately`() {
        // cancellation or refund: Play reports nothing, so the record is rewritten as not subscribed
        assertEquals(Tier.FREE, EntitlementPolicy.tier(EntitlementRecord(false, now), now))
    }
}
