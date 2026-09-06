package com.worksbien.borescopedirect.access

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewAccessStoreTest {
    @Test
    fun trialConsumesOnlyLiveForegroundTime() {
        assertEquals(90_000L, PreviewAccessStore.remainingAfterConsumption(90_000L, 0L))
        assertEquals(45_000L, PreviewAccessStore.remainingAfterConsumption(90_000L, 45_000L))
        assertEquals(0L, PreviewAccessStore.remainingAfterConsumption(45_000L, 95_000L))
    }

    @Test
    fun invalidTimeInputsCannotAddOrUnderflowTrial() {
        assertEquals(90_000L, PreviewAccessStore.remainingAfterConsumption(Long.MAX_VALUE, -1L))
        assertEquals(0L, PreviewAccessStore.remainingAfterConsumption(1L, Long.MAX_VALUE))
        assertEquals(0L, PreviewAccessStore.remainingAfterConsumption(-1L, 1L))
    }
}
