package com.signalintelligence.pocketsignalassistant

import com.signalintelligence.pocketsignalassistant.data.Candle
import com.signalintelligence.pocketsignalassistant.data.CandleInterval
import com.signalintelligence.pocketsignalassistant.data.TradingPair
import com.signalintelligence.pocketsignalassistant.data.ema
import com.signalintelligence.pocketsignalassistant.data.generateSignal
import com.signalintelligence.pocketsignalassistant.data.rsi
import com.signalintelligence.pocketsignalassistant.data.SignalKind
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalEngineTest {
    @Test
    fun emaShouldFollowUptrend() {
        val values = listOf(100.0, 102.0, 105.0, 109.0, 112.0, 118.0, 121.0)
        val result = ema(values, 3)
        assertTrue(result.isNotEmpty())
        assertTrue(result.last() > result.first())
    }

    @Test
    fun flatSeriesShouldRemainNeutral() {
        val values = listOf(100.0, 100.0, 100.0, 100.0, 100.0)
        val result = rsi(values, 14)
        assertTrue(result in 49.0..51.0)
    }

    @Test
    fun signalEngineShouldGenerateNoSignalOnShortHistory() {
        val candles = listOf(
            Candle(0L, 1.0, 1.02, 0.99, 1.01),
            Candle(1L, 1.01, 1.03, 1.0, 1.02),
            Candle(2L, 1.02, 1.04, 1.01, 1.03),
        )
        val signal = generateSignal(TradingPair.EUR_USD, CandleInterval.M5, candles)
        assertTrue(signal.kind.name == "NO_SIGNAL" || signal.reason.contains("минимум"))
    }

    @Test
    fun unvalidatedIndicatorAgreementMustNotEmitTradeSignal() {
        val candles = (0 until 80).map { index ->
            val close = 1.0 + index * 0.001
            Candle(index * 60_000L, close - 0.0005, close + 0.001, close - 0.001, close)
        }

        val signal = generateSignal(TradingPair.EUR_USD, CandleInterval.M1, candles)

        assertTrue(signal.kind == SignalKind.NO_SIGNAL)
        assertTrue(signal.confidence == 0)
    }
}
