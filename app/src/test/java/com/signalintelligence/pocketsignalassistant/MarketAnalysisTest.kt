package com.signalintelligence.pocketsignalassistant

import com.signalintelligence.pocketsignalassistant.data.Candle
import com.signalintelligence.pocketsignalassistant.data.calculateTechnicalSnapshot
import com.signalintelligence.pocketsignalassistant.data.findHistoricalPatterns
import com.signalintelligence.pocketsignalassistant.data.runHistoricalBacktest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketAnalysisTest {
    @Test
    fun technicalAnalysisRequiresEnoughRealCandles() {
        assertNull(calculateTechnicalSnapshot(monotonicCandles(49)))
        assertNotNull(calculateTechnicalSnapshot(monotonicCandles(50)))
    }

    @Test
    fun flatHistoricalOutcomesDoNotBecomeDownPredictions() {
        val candles = monotonicCandles(300, step = 0.0)
        val backtest = runHistoricalBacktest(candles, "EURUSD_otc", 1)

        assertEquals(0, backtest.totalSignals)
        assertNull(backtest.directionalAccuracy)
        assertTrue(findHistoricalPatterns(candles, 1).note.startsWith("INSUFFICIENT DATA"))
    }

    @Test
    fun backtestReportsOnlyChronologicalOutcomesAndKeepsCountsConsistent() {
        val backtest = runHistoricalBacktest(monotonicCandles(300), "EURUSD_otc", 2)

        assertTrue(backtest.totalSignals > 0)
        assertEquals(backtest.totalSignals, backtest.wins + backtest.losses + backtest.ties)
        assertTrue(backtest.directionalAccuracy != null)
        assertTrue(backtest.note.contains("not binary-option profitability"))
    }

    private fun monotonicCandles(size: Int, step: Double = 0.01): List<Candle> =
        List(size) { index ->
            val close = 1.0 + index * step
            Candle(
                time = index * 60_000L,
                open = close - step / 2.0,
                high = close + 0.005,
                low = close - 0.005,
                close = close,
            )
        }
}
