package com.worksbien.borescopedirect.camera

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class FrameStabilityProbeTest {
    @Test
    fun oneFrameNeverPassesCompatibility() {
        val probe = FrameStabilityProbe(requiredFrames = 3, minimumSpanMillis = 200L)
        assertFalse(probe.offer(640, 480, 1_000L))
    }

    @Test
    fun repeatedFramesOverTimePass() {
        val probe = FrameStabilityProbe(requiredFrames = 3, minimumSpanMillis = 200L)
        assertFalse(probe.offer(640, 480, 1_000L))
        assertFalse(probe.offer(640, 480, 1_100L))
        assertTrue(probe.offer(640, 480, 1_250L))
    }

    @Test
    fun sizeChangeRestartsProbe() {
        val probe = FrameStabilityProbe(requiredFrames = 2, minimumSpanMillis = 100L)
        assertFalse(probe.offer(640, 480, 1_000L))
        assertFalse(probe.offer(1280, 720, 1_200L))
        assertTrue(probe.offer(1280, 720, 1_350L))
    }

    @Test
    fun longGapRestartsProbe() {
        val probe = FrameStabilityProbe(requiredFrames = 2, minimumSpanMillis = 100L, maximumGapMillis = 500L)
        assertFalse(probe.offer(640, 480, 1_000L))
        assertFalse(probe.offer(640, 480, 2_000L))
        assertTrue(probe.offer(640, 480, 2_150L))
    }

    @Test
    fun backwardsClockRestartsProbe() {
        val probe = FrameStabilityProbe(requiredFrames = 2, minimumSpanMillis = 100L)
        assertFalse(probe.offer(640, 480, 1_000L))
        assertFalse(probe.offer(640, 480, 900L))
        assertTrue(probe.offer(640, 480, 1_050L))
    }

    @Test
    fun formatChangeRestartsProbe() {
        val probe = FrameStabilityProbe(requiredFrames = 2, minimumSpanMillis = 100L)
        assertFalse(probe.offer(640, 480, 1_000L, "NV21"))
        assertFalse(probe.offer(640, 480, 1_200L, "RGBA"))
        assertTrue(probe.offer(640, 480, 1_350L, "RGBA"))
    }

    @Test
    fun invalidConfigurationFailsFast() {
        assertThrows(IllegalArgumentException::class.java) { FrameStabilityProbe(requiredFrames = 0) }
        assertThrows(IllegalArgumentException::class.java) { FrameStabilityProbe(minimumSpanMillis = -1L) }
        assertThrows(IllegalArgumentException::class.java) { FrameStabilityProbe(maximumGapMillis = 0L) }
    }
}
