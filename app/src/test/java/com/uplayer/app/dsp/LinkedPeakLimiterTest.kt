package com.uplayer.app.dsp

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LinkedPeakLimiterTest {
 @Test
 fun limitsPeakAndPreservesStereoRatio() {
  val limiter = LinkedPeakLimiter().apply { configure(48_000) }
  val frame = doubleArrayOf(2.0, -1.0)

  limiter.process(frame)

  assertTrue(frame.maxOf(::abs) <= 0.960_001)
  assertEquals(-2.0, frame[0] / frame[1], 0.000_001)
 }

 @Test
 fun releasesGraduallyAfterTransient() {
  val limiter = LinkedPeakLimiter().apply { configure(48_000) }
  limiter.process(doubleArrayOf(2.0, 2.0))
  val quietFrame = doubleArrayOf(0.5, 0.5)

  limiter.process(quietFrame)

  assertTrue(quietFrame[0] < 0.5)
  assertEquals(quietFrame[0], quietFrame[1], 0.0)
 }
}
