package com.liquidcode7.hearthcraft.worker

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkerConstantsTest {

    @Test
    fun `ProcessWorker SLOT_ID is process_0`() {
        assertEquals("process_0", ProcessWorker.SLOT_ID)
    }

    @Test
    fun `ProcessWorker NOTIFICATION_ID is 40`() {
        assertEquals(40, ProcessWorker.NOTIFICATION_ID)
    }

    @Test
    fun `durationForType mill is 3 minutes`() {
        assertEquals(3 * 60 * 1000L, ProcessWorker.durationForType("mill"))
    }

    @Test
    fun `durationForType press is 4 minutes`() {
        assertEquals(4 * 60 * 1000L, ProcessWorker.durationForType("press"))
    }

    @Test
    fun `durationForType render is 5 minutes`() {
        assertEquals(5 * 60 * 1000L, ProcessWorker.durationForType("render"))
    }

    @Test
    fun `durationForType churn is 5 minutes`() {
        assertEquals(5 * 60 * 1000L, ProcessWorker.durationForType("churn"))
    }

    @Test
    fun `durationForType smoke is 6 minutes`() {
        assertEquals(6 * 60 * 1000L, ProcessWorker.durationForType("smoke"))
    }

    @Test
    fun `durationForType cure is 8 minutes`() {
        assertEquals(8 * 60 * 1000L, ProcessWorker.durationForType("cure"))
    }

    @Test
    fun `durationForType brew is 10 minutes`() {
        assertEquals(10 * 60 * 1000L, ProcessWorker.durationForType("brew"))
    }

    @Test
    fun `durationForType unknown falls back to 5 minutes`() {
        assertEquals(5 * 60 * 1000L, ProcessWorker.durationForType("unknown_type"))
    }

    @Test
    fun `CoopWorker SLOT_ID is coop_0`() {
        assertEquals("coop_0", CoopWorker.SLOT_ID)
    }

    @Test
    fun `CoopWorker NOTIFICATION_ID is 41`() {
        assertEquals(41, CoopWorker.NOTIFICATION_ID)
    }

    @Test
    fun `DairyWorker SLOT_ID is dairy_0`() {
        assertEquals("dairy_0", DairyWorker.SLOT_ID)
    }

    @Test
    fun `DairyWorker NOTIFICATION_ID is 42`() {
        assertEquals(42, DairyWorker.NOTIFICATION_ID)
    }
}
