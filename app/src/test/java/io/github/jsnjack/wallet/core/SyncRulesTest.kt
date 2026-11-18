package io.github.jsnjack.wallet.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncRulesTest {
    @Test
    fun fileNameRoundTrips() {
        assertEquals("abc-123", SyncRules.cardId(SyncRules.fileName("abc-123")))
    }

    @Test
    fun ignoresOtherFiles() {
        assertNull(SyncRules.cardId("notes.txt"))
        assertNull(SyncRules.cardId("card-.json"))
        assertNull(SyncRules.cardId(null))
    }

    @Test
    fun downloadsRemoteOnlyAndNewerRemoteCards() {
        val remote = mapOf(
            "new" to RemoteEntry("new", "f1", 5),
            "newer" to RemoteEntry("newer", "f2", 20),
            "older" to RemoteEntry("older", "f3", 5),
        )
        val plan = SyncRules.plan(mapOf("newer" to 10L, "older" to 10L), remote)
        assertEquals(listOf("new", "newer"), plan.download.map { it.cardId })
        assertEquals(listOf("older"), plan.upload)
    }

    @Test
    fun uploadsLocalOnlyCardsAndSkipsEqualTimes() {
        val plan = SyncRules.plan(mapOf("local" to 3L, "same" to 7L), mapOf("same" to RemoteEntry("same", "f", 7)))
        assertEquals(listOf("local"), plan.upload)
        assertTrue(plan.download.isEmpty())
    }

    @Test
    fun keepsNewestCopyWhenTwoFilesExistForOneCard() {
        val entries = listOf(RemoteEntry("a", "old", 1), RemoteEntry("a", "new", 9), RemoteEntry("b", "only", 2))
        val newest = SyncRules.newestPerCard(entries)
        assertEquals("new", newest.getValue("a").fileId)
        assertEquals("only", newest.getValue("b").fileId)
    }

    @Test
    fun isNewerOnlyWhenStrictlyLater() {
        assertTrue(SyncRules.isNewer(2, null))
        assertTrue(SyncRules.isNewer(2, 1))
        assertEquals(false, SyncRules.isNewer(2, 2))
    }
}
