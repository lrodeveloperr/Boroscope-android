package com.worksbien.borescopedirect.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RetryPolicyTest {
    @Test
    fun triesHighQualityThenSafeFallbacks() {
        assertEquals("1280×720 MJPEG", RetryPolicy.profileAt(0)?.label)
        assertEquals("640×480 MJPEG", RetryPolicy.profileAt(1)?.label)
        assertEquals("640×480 YUYV", RetryPolicy.profileAt(2)?.label)
        assertNull(RetryPolicy.profileAt(3))
    }
}
