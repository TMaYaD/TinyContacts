package dev.loonybin.tempcontacts.expiry

import dev.loonybin.tempcontacts.expiry.ExpiryPolicy.Candidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpiryPolicyTest {

    private val now = 1_000_000L

    // ---- isExpired ---------------------------------------------------------

    @Test
    fun `isExpired is true strictly before now`() {
        assertTrue(ExpiryPolicy.isExpired(now - 1, now))
    }

    @Test
    fun `isExpired is false exactly at now (boundary)`() {
        // Mirrors the SQL `SYNC1 < ?` selection: equal timestamps are NOT expired yet.
        assertFalse(ExpiryPolicy.isExpired(now, now))
    }

    @Test
    fun `isExpired is false in the future`() {
        assertFalse(ExpiryPolicy.isExpired(now + 1, now))
    }

    @Test
    fun `isExpired is false when expiry is null (never expires)`() {
        assertFalse(ExpiryPolicy.isExpired(null, now))
    }

    // ---- selectExpired -----------------------------------------------------

    @Test
    fun `selectExpired returns only past-due ids preserving order`() {
        val candidates = listOf(
            Candidate(rawContactId = 10, expiryEpochMillis = now - 5), // expired
            Candidate(rawContactId = 11, expiryEpochMillis = now + 5), // future
            Candidate(rawContactId = 12, expiryEpochMillis = now),     // boundary -> keep
            Candidate(rawContactId = 13, expiryEpochMillis = now - 1), // expired
            Candidate(rawContactId = 14, expiryEpochMillis = null),    // never
        )

        assertEquals(listOf(10L, 13L), ExpiryPolicy.selectExpired(candidates, now))
    }

    @Test
    fun `selectExpired on empty input is empty`() {
        assertEquals(emptyList<Long>(), ExpiryPolicy.selectExpired(emptyList(), now))
    }

    @Test
    fun `selectExpired selects nothing when all are in the future`() {
        val candidates = listOf(
            Candidate(1, now + 1),
            Candidate(2, now + 100),
        )
        assertTrue(ExpiryPolicy.selectExpired(candidates, now).isEmpty())
    }

    @Test
    fun `selectExpired selects all when all are past-due`() {
        val candidates = listOf(
            Candidate(1, now - 1),
            Candidate(2, 0),
        )
        assertEquals(listOf(1L, 2L), ExpiryPolicy.selectExpired(candidates, now))
    }

    // ---- remainingMillis ---------------------------------------------------

    @Test
    fun `remainingMillis is positive for future expiry`() {
        assertEquals(250L, ExpiryPolicy.remainingMillis(now + 250, now))
    }

    @Test
    fun `remainingMillis clamps already-expired to zero`() {
        assertEquals(0L, ExpiryPolicy.remainingMillis(now - 999, now))
    }

    @Test
    fun `remainingMillis is null for never-expiring contact`() {
        assertNull(ExpiryPolicy.remainingMillis(null, now))
    }
}
