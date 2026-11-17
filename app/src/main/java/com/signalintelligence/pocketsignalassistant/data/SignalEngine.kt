package com.signalintelligence.pocketsignalassistant.data

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

data class MacdComponents(
    val macd: Double,
    val signal: Double,
    val histogram: Double,
)

data class BollingerBands(
    val middle: Double,
    val upper: Double,
    val lower: Double,
)

fun ema(values: List<Double>, period: Int): List<Double> {
    if (values.isEmpty()) return emptyList()
    require(period > 0) { "EMA period must be positive." }
    if (period == 1) return values.toList()
    val alpha = 2.0 / (period + 1.0)
    val result = ArrayList<Double>(values.size)
    var previous = values.first()
    values.forEachIndexed { index, value ->
        previous = if (index == 0) value else previous + alpha * (value - previous)
        result.add(previous)
    }
    return result
}

fun rsi(values: List<Double>, period: Int = 14): Double {
    require(period > 0) { "RSI period must be positive." }
    if (values.size < 2) return 50.0
    val gains = ArrayList<Double>()
    val losses = ArrayList<Double>()
    for (index in 1 until values.size) {
        val delta = values[index] - values[index - 1]
        gains.add(max(0.0, delta))
        losses.add(max(0.0, -delta))
    }
    if (gains.size <= period) {
        val avgGain = gains.average()
        val avgLoss = losses.average()
        if (avgGain == 0.0 && avgLoss == 0.0) return 50.0
        val denominator = avgLoss.coerceAtLeast(1e-9)
        return if (avgLoss == 0.0) 100.0 else 100.0 - (100.0 / (1.0 + avgGain / denominator))
    }

    var avgGain = gains.take(period).average()
    var avgLoss = losses.take(period).average()
    if (avgGain == 0.0 && avgLoss == 0.0) return 50.0
    for (index in period until gains.size) {
        avgGain = ((avgGain * (period - 1)) + gains[index]) / period
        avgLoss = ((avgLoss * (period - 1)) + losses[index]) / period
    }
    val denominator = avgLoss.coerceAtLeast(1e-9)
    return if (avgLoss == 0.0) 100.0 else 100.0 - (100.0 / (1.0 + avgGain / denominator))
}

fun macd(values: List<Double>, fastPeriod: Int = 12, slowPeriod: Int = 26, signalPeriod: Int = 9): MacdComponents {
    require(fastPeriod > 0 && slowPeriod > fastPeriod && signalPeriod > 0) { "MACD periods are invalid." }
    if (values.size < slowPeriod + signalPeriod) return MacdComponents(0.0, 0.0, 0.0)
    val fastValues = ema(values, fastPeriod)
    val slowValues = ema(values, slowPeriod)
    val macdSeries = values.indices.map { index -> fastValues[index] - slowValues[index] }
    val macdValue = macdSeries.last()
    val signalValue = ema(macdSeries, signalPeriod).last()
    return MacdComponents(macdValue, signalValue, macdValue - signalValue)
}

fun bollingerBands(values: List<Double>, period: Int = 20): BollingerBands {
    require(period > 0) { "Bollinger period must be positive." }
    if (values.size < period) return BollingerBands(0.0, 0.0, 0.0)
    val window = values.takeLast(period)
    val mean = window.average()
    val variance = window.map { (it - mean).pow(2) }.average()
    val stdDev = sqrt(variance)
    return BollingerBands(mean, mean + (stdDev * 2.0), mean - (stdDev * 2.0))
}

fun atr(candles: List<Candle>, period: Int = 14): Double {
    require(period > 0) { "ATR period must be positive." }
    if (candles.size < period + 1) return 0.0
    val trueRanges = mutableListOf<Double>()
    for (index in 1 until candles.size) {
        val previous = candles[index - 1]
        val current = candles[index]
        val highLow = current.high - current.low
        val highClose = abs(current.high - previous.close)
        val lowClose = abs(current.low - previous.close)
        trueRanges.add(max(highLow, max(highClose, lowClose)))
    }
    val window = trueRanges.takeLast(period)
    return if (window.isEmpty()) 0.0 else window.average()
}

fun adx(candles: List<Candle>, period: Int = 14): Double {
    require(period > 0) { "ADX period must be positive." }
    if (candles.size < period * 2 + 1) return 0.0
    val dxValues = mutableListOf<Double>()
    for (index in 1 until candles.size) {
        val start = (index - period + 1).coerceAtLeast(1)
        var plusSum = 0.0
        var minusSum = 0.0
        var rangeSum = 0.0
        for (windowIndex in start..index) {
            val point = candles[windowIndex]
            val prior = candles[windowIndex - 1]
            val windowUp = point.high - prior.high
            val windowDown = prior.low - point.low
            plusSum += if (windowUp > windowDown && windowUp > 0.0) windowUp else 0.0
            minusSum += if (windowDown > windowUp && windowDown > 0.0) windowDown else 0.0
            rangeSum += max(
                point.high - point.low,
                max(abs(point.high - prior.close), abs(point.low - prior.close)),
            )
        }
        val plusDi = if (rangeSum == 0.0) 0.0 else 100.0 * plusSum / rangeSum
        val minusDi = if (rangeSum == 0.0) 0.0 else 100.0 * minusSum / rangeSum
        val denominator = plusDi + minusDi
        dxValues.add(if (denominator == 0.0) 0.0 else 100.0 * abs(plusDi - minusDi) / denominator)
    }
    return dxValues.takeLast(period).average().coerceIn(0.0, 100.0)
}

fun generateSignal(pair: TradingPair, interval: CandleInterval, candles: List<Candle>): SignalResult {
    if (candles.size < 50) {
        return SignalResult(
            kind = SignalKind.NO_SIGNAL,
            confidence = 0,
            trend = "Недостаточно истории",
            rsi = 50.0,
            macd = 0.0,
            adx = 0.0,
            atr = 0.0,
            agreement = 0,
            reason = "Для анализа требуется минимум 50 свечей.",
            pair = pair,
            interval = interval,
            timestamp = System.currentTimeMillis(),
        )
    }

    val closes = candles.map { it.close }
    val ema9 = ema(closes, 9).last()
    val ema21 = ema(closes, 21).last()
    val ema50 = ema(closes, 50).last()
    val rsiValue = rsi(closes, 14)
    val macdValue = macd(closes)
    val atrValue = atr(candles, 14)
    val adxValue = adx(candles, 14)

    val trendDirection = when {
        ema9 > ema21 && ema21 > ema50 -> "Восходящий тренд"
        ema9 < ema21 && ema21 < ema50 -> "Нисходящий тренд"
        else -> "Слабый или боковой тренд"
    }

    return SignalResult(
        kind = SignalKind.NO_SIGNAL,
        confidence = 0,
        trend = trendDirection,
        rsi = rsiValue,
        macd = macdValue.macd,
        adx = adxValue,
        atr = atrValue,
        agreement = 0,
        reason = "Indicators are descriptive only. No BUY/SELL signal is emitted without an independently validated probability model.",
        pair = pair,
        interval = interval,
        timestamp = System.currentTimeMillis(),
    )
}
