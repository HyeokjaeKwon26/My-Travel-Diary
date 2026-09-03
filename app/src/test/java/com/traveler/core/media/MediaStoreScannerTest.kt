package com.traveler.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaStoreScannerTest {

    @Test
    fun normalizeDateTakenMs_handlesMilliseconds() {
        val msTimestamp = 1783153800000L // 2026-07-04 in ms
        val normalized = AndroidMediaStoreScanner.normalizeDateTakenMs(msTimestamp)
        assertEquals(msTimestamp, normalized)
    }

    @Test
    fun normalizeDateTakenMs_convertsSecondsToMilliseconds() {
        val secTimestamp = 1783153800L // 2026-07-04 in seconds
        val normalized = AndroidMediaStoreScanner.normalizeDateTakenMs(secTimestamp)
        assertEquals(1783153800000L, normalized)
    }

    @Test
    fun normalizeDateTakenMs_rejectsZeroOrNegative() {
        assertNull(AndroidMediaStoreScanner.normalizeDateTakenMs(0L))
        assertNull(AndroidMediaStoreScanner.normalizeDateTakenMs(-100L))
    }

    @Test
    fun normalizeDateTakenMs_rejectsPre2000Anomalies() {
        assertNull(AndroidMediaStoreScanner.normalizeDateTakenMs(500L)) // 500 ms = 1970
    }
}
