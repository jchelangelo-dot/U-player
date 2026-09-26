package com.uplayer.app.focus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class FocusCachePolicyTest {
 @Test
 fun evictsOldestSessionsUntilCountAndSizeAreWithinLimits() {
  val entries = (1L..10L).map { id -> SessionCacheEntry(id.toString(), 100L, id) }

  val evictions = sessionsToEvict(entries, currentId = "10", maxCount = 8, maxBytes = 750L)

  assertEquals(setOf("1", "2", "3"), evictions)
  assertFalse("10" in evictions)
 }

 @Test
 fun neverEvictsCurrentSessionEvenWhenItAloneExceedsBudget() {
  val entries = listOf(
   SessionCacheEntry("old", 100L, 1L),
   SessionCacheEntry("current", 2_000L, 2L)
  )

  val evictions = sessionsToEvict(entries, currentId = "current", maxCount = 8, maxBytes = 1_000L)

  assertEquals(setOf("old"), evictions)
  assertFalse("current" in evictions)
 }
}
