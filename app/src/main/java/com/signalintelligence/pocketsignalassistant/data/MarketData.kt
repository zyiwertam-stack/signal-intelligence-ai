package com.signalintelligence.pocketsignalassistant.data

import kotlin.math.abs
import kotlin.math.roundToInt

enum class TradingPair(val displayName: String) {
    EUR_USD("EUR/USD"),
    GBP_USD("GBP/USD"),
    USD_JPY("USD/JPY"),
    AUD_USD("AUD/USD"),
    USD_CHF("USD/CHF"),
    USD_CAD("USD/CAD"),
    NZD_USD("NZD/USD"),
    EUR_JPY("EUR/JPY"),
    GBP_JPY("GBP/JPY"),
    EUR_GBP("EUR/GBP");

    companion object {
        fun all(): List<TradingPair> = entries
    }
}

enum class CandleInterval(val label: String, val minutes: Int) {
    M1("1m", 1),
    M2("2m", 2),
    M3("3m", 3),
    M5("5m", 5),
    M10("10m", 10),
    M15("15m", 15),
    M30("30m", 30),
    H1("1h", 60);

    companion object {
        fun homeOptions(): List<CandleInterval> = listOf(M1, M5, M15, M30, H1)
    }
}

enum class DataStatus { ONLINE, OFFLINE, DELAYED, DATA_UNAVAILABLE }

enum class SignalKind { BUY, SELL, NO_SIGNAL }

enum class MarketSource { LIVE_FOREX, HISTORICAL_OTC }

data class Candle(
    val time: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double = 0.0,
)

data class MarketSnapshot(
    val pair: TradingPair,
    val currentPrice: Double,
    val candles: List<Candle>,
    val status: DataStatus,
    val updatedAt: Long,
    val message: String = "",
)

data class SignalResult(
    val kind: SignalKind,
    val confidence: Int,
    val trend: String,
    val rsi: Double,
    val macd: Double,
    val adx: Double,
    val atr: Double,
    val agreement: Int,
    val reason: String,
    val pair: TradingPair,
    val interval: CandleInterval,
    val timestamp: Long,
)

interface MarketDataProvider {
    fun fetchMarketSnapshot(pair: TradingPair, interval: CandleInterval, limit: Int = 96): MarketSnapshot
}

class NoKeyForexDataProvider : MarketDataProvider {
    override fun fetchMarketSnapshot(pair: TradingPair, interval: CandleInterval, limit: Int): MarketSnapshot {
        return MarketSnapshot(
            pair = pair,
            currentPrice = 0.0,
            candles = emptyList(),
            status = DataStatus.DATA_UNAVAILABLE,
            updatedAt = System.currentTimeMillis(),
            message = "A Twelve Data API key is required for live Forex quotes.",
        )
    }
}

internal fun Double.formatPercent(): String = ((this * 100.0).roundToInt() / 100.0).toString()

internal fun Collection<Double>.safeAverage(): Double = if (isEmpty()) 0.0 else average()

internal fun List<Double>.lastOrZero(): Double = lastOrNull() ?: 0.0

internal fun Pair<Double, Double>.range(): Double = abs(second - first)
