package com.seasons.app

import com.seasons.app.ui.charts.amountAxisTicks
import com.seasons.app.ui.charts.niceAmountMax
import org.junit.Assert.assertEquals
import org.junit.Test

class ChartAxisTest {
    @Test fun amountAxis_roundMaximumAtOrAboveTheData() {
        assertEquals(0.8, niceAmountMax(0.7), 1e-9)
        assertEquals(20.0, niceAmountMax(17.4), 1e-9)
        assertEquals(10.0, niceAmountMax(10.0), 1e-9)
        assertEquals(20.0, niceAmountMax(12.0), 1e-9)
        assertEquals(40.0, niceAmountMax(33.0), 1e-9)
        assertEquals(50.0, niceAmountMax(41.0), 1e-9)
        assertEquals(1000.0, niceAmountMax(1000.0), 1e-9)
        assertEquals(1.0, niceAmountMax(0.0), 1e-9)
        assertEquals(0.4, niceAmountMax(0.31), 1e-9)
    }

    @Test fun amountAxis_neverBelowTheData() {
        var v = 0.01
        while (v < 100_000.0) {
            assertEquals(true, niceAmountMax(v) >= v)
            v *= 1.07
        }
    }

    @Test fun amountTicks_zeroHalfMax() {
        assertEquals(listOf(0.0, 10.0, 20.0), amountAxisTicks(20.0))
        assertEquals(listOf(0.0, 2.5, 5.0), amountAxisTicks(5.0))
    }
}
