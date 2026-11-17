package com.signalintelligence.pocketsignalassistant.data

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

data class TechnicalSnapshot(
    val ema9: Double,
    val ema21: Double,
    val ema50: Double,
    val rsi14: Double,
    val macd: MacdComponents,
    val atr14: Double,
    val adx14: Double,
    val bollinger: BollingerBands,
    val momentum: Double,
    val volatility: Double,
    val priceAction: String,
)

data class HistoricalPatternSummary(
    val analogCount: Int,
    val observedUp: Int,
    val observedDown: Int,
    val note: String,
)

data class BacktestResult(
    val totalSignals: Int,
    val wins: Int,
    val losses: Int,
    val ties: Int,
    val maxLosingStreak: Int,
    val directionalAccuracy: Double?,
    val asset: String,
    val expirationMinutes: Int,
    val note: String,
)

fun calculateTechnicalSnapshot(candles: List<Candle>): TechnicalSnapshot? {
    if (candles.size < 50) return null
    val closes = candles.map { it.close }
    val current = closes.last()
    val previous = closes[closes.lastIndex - 5]
    val returns = closes.takeLast(20).zipWithNext { first, second ->
        (second - first) / first.coerceAtLeast(1e-12)
    }
    val meanReturn = returns.average()
    val volatility = sqrt(returns.map { (it - meanReturn).pow(2) }.average().coerceAtLeast(0.0))
    val bands = bollingerBands(closes, 20)
    val context = candles.takeLast(21).dropLast(1)
    val recentHigh = context.maxOf { it.high }
    val recentLow = context.minOf { it.low }
    val last = candles.last()
    val prior = candles[candles.lastIndex - 1]
    val body = abs(last.close - last.open)
    val lowerWick = minOf(last.open, last.close) - last.low
    val upperWick = last.high - maxOf(last.open, last.close)
    val priorRangeHigh = candles.takeLast(22).dropLast(2).maxOf { it.high }
    val priorRangeLow = candles.takeLast(22).dropLast(2).minOf { it.low }
    val recentCloseRange = closes.takeLast(10).let { it.maxOrNull()!! - it.minOrNull()!! }
    val averagePriorRange = candles.takeLast(21).dropLast(1).map { it.high - it.low }.average()
    val priorImpulse = abs(candles[candles.lastIndex - 1].close - candles[candles.lastIndex - 6].close)
    val pushAndSnap = priorImpulse > averagePriorRange * 4.0 &&
        (last.close - last.open) * (prior.close - candles[candles.lastIndex - 6].close) < 0.0
    val currentRange = last.high - last.low
    val action = when {
        prior.high > priorRangeHigh && last.close < priorRangeHigh -> "FALSE_BREAKOUT_DOWN"
        prior.low < priorRangeLow && last.close > priorRangeLow -> "FALSE_BREAKOUT_UP"
        pushAndSnap -> "PUSH_AND_SNAP_CANDIDATE"
        last.close > recentHigh -> "BREAKOUT_UP"
        last.close < recentLow -> "BREAKOUT_DOWN"
        last.close > last.open && prior.close < prior.open && last.close > prior.open && last.open < prior.close -> "BULLISH_ENGULFING"
        last.close < last.open && prior.close > prior.open && last.close < prior.open && last.open > prior.close -> "BEARISH_ENGULFING"
        lowerWick > body * 2.0 && last.close > last.open -> "BULLISH_REVERSAL_CANDIDATE"
        upperWick > body * 2.0 && last.close < last.open -> "BEARISH_REVERSAL_CANDIDATE"
        averagePriorRange > 0.0 && currentRange > averagePriorRange * 2.0 -> "VOLATILITY_EXPANSION"
        averagePriorRange > 0.0 && currentRange < averagePriorRange * 0.4 -> "VOLATILITY_CONTRACTION"
        abs((current - previous) / previous.coerceAtLeast(1e-12)) > volatility * 2.0 -> if (current > previous) "MOMENTUM_UP" else "MOMENTUM_DOWN"
        recentCloseRange / current.coerceAtLeast(1e-12) < volatility * 3.0 -> "PINNING_OR_CONSOLIDATION_CANDIDATE"
        bands.upper > bands.lower && (bands.upper - bands.lower) / bands.middle.coerceAtLeast(1e-12) < volatility * 8.0 -> "CONSOLIDATION"
        else -> "MIXED_PRICE_ACTION"
    }
    return TechnicalSnapshot(
        ema9 = ema(closes, 9).last(),
        ema21 = ema(closes, 21).last(),
        ema50 = ema(closes, 50).last(),
        rsi14 = rsi(closes, 14),
        macd = macd(closes),
        atr14 = atr(candles, 14),
        adx14 = adx(candles, 14),
        bollinger = bands,
        momentum = current - previous,
        volatility = volatility,
        priceAction = action,
    )
}

fun findHistoricalPatterns(
    candles: List<Candle>,
    expirationMinutes: Int,
    minimumMatches: Int = 30,
): HistoricalPatternSummary {
    if (expirationMinutes !in setOf(1, 2, 3, 5, 10, 15) || candles.size < 100 + expirationMinutes) {
        return HistoricalPatternSummary(0, 0, 0, "INSUFFICIENT DATA")
    }
    val currentIndex = candles.lastIndex
    val features = arrayOfNulls<PatternFeatures>(candles.size)
    for (index in 49 until candles.size) features[index] = patternFeatures(candles, index)
    val currentFeatures = features[currentIndex] ?: return HistoricalPatternSummary(0, 0, 0, "INSUFFICIENT DATA")
    var up = 0
    var down = 0
    for (index in 50 until currentIndex - expirationMinutes) {
        if (!hasContinuousHorizon(candles, index, expirationMinutes)) continue
        if (candles[index].time + expirationMinutes * 60_000L > candles[currentIndex].time) continue
        val historicalFeatures = features[index] ?: continue
        if (patternDistance(currentFeatures, historicalFeatures) > 0.30) continue
        val outcome = candles[index + expirationMinutes].close.compareTo(candles[index].close)
        if (outcome > 0) up++ else if (outcome < 0) down++
    }
    val matches = up + down
    return HistoricalPatternSummary(
        analogCount = matches,
        observedUp = up,
        observedDown = down,
        note = if (matches < minimumMatches) {
            "INSUFFICIENT DATA: fewer than $minimumMatches comparable historical patterns."
        } else {
            "Historical analog outcomes only. This source is marked synthetic/research-only by its publisher and is not a validated live forecast."
        },
    )
}

fun runHistoricalBacktest(candles: List<Candle>, asset: String, expirationMinutes: Int): BacktestResult {
    if (expirationMinutes !in setOf(1, 2, 3, 5, 10, 15)) {
        return BacktestResult(0, 0, 0, 0, 0, null, asset, expirationMinutes, "Unsupported expiration.")
    }
    if (candles.size < 100 + expirationMinutes) {
        return BacktestResult(
            0, 0, 0, 0, 0, null, asset, expirationMinutes,
            "At least 100 one-minute candles plus the expiration horizon are required.",
        )
    }
    val split = (candles.size * 0.7).toInt().coerceAtLeast(60)
    val features = arrayOfNulls<PatternFeatures>(candles.size)
    for (index in 49 until candles.size) features[index] = patternFeatures(candles, index)
    var tested = 0
    var wins = 0
    var losses = 0
    var ties = 0
    var losingStreak = 0
    var maximumLosingStreak = 0
    var correctDirections = 0
    for (index in split until candles.size - expirationMinutes) {
        if (!hasContinuousHorizon(candles, index, expirationMinutes)) continue
        val current = features[index] ?: continue
        var up = 0
        var down = 0
        for (candidate in 50 until index - expirationMinutes) {
            if (!hasContinuousHorizon(candles, candidate, expirationMinutes)) continue
            if (candles[candidate].time + expirationMinutes * 60_000L > candles[index].time) continue
            val historical = features[candidate] ?: continue
            if (patternDistance(current, historical) > 0.30) continue
            val outcome = candles[candidate + expirationMinutes].close.compareTo(candles[candidate].close)
            if (outcome > 0) up++ else if (outcome < 0) down++
        }
        val analogs = up + down
        if (analogs < 30 || abs(up.toDouble() / analogs - 0.5) < 0.10) continue

        val actualDirection = candles[index + expirationMinutes].close.compareTo(candles[index].close)
        if (actualDirection == 0) {
            tested++
            ties++
            continue
        }
        val actualUp = actualDirection > 0
        val predictedUp = up > down
        tested++
        if (actualUp == predictedUp) {
            wins++
            correctDirections++
            losingStreak = 0
        } else {
            losses++
            losingStreak++
            maximumLosingStreak = maxOf(maximumLosingStreak, losingStreak)
        }
    }
    return BacktestResult(
        totalSignals = tested,
        wins = wins,
        losses = losses,
        ties = ties,
        maxLosingStreak = maximumLosingStreak,
        directionalAccuracy = if (wins + losses > 0) correctDirections.toDouble() / (wins + losses) else null,
        asset = asset,
        expirationMinutes = expirationMinutes,
        note = "Chronological 70/30 split; each test prediction uses only earlier analogs. The public GitHub source is described by its publisher as synthetic/research-only; other imported file provenance is unverified. Directional hit rate is not binary-option profitability.",
    )
}

private fun hasContinuousHorizon(candles: List<Candle>, startIndex: Int, horizon: Int): Boolean {
    val endIndex = startIndex + horizon
    if (startIndex < 0 || endIndex >= candles.size) return false
    for (index in startIndex + 1..endIndex) {
        if (candles[index].time - candles[index - 1].time > 60_000L) return false
    }
    return candles[endIndex].time - candles[startIndex].time == horizon * 60_000L
}

private data class PatternFeatures(val emaGap: Double, val rsi: Double, val momentum: Double, val volatility: Double)

private fun patternFeatures(candles: List<Candle>, index: Int): PatternFeatures? {
    if (index < 49 || index >= candles.size) return null
    val closes = candles.subList(index - 49, index + 1).map { it.close }
    val fast = ema(closes, 9).last()
    val slow = ema(closes, 21).last()
    val average = closes.average().coerceAtLeast(1e-12)
    val returns = closes.zipWithNext { first, second ->
        (second - first) / first.coerceAtLeast(1e-12)
    }
    val mean = returns.average()
    val volatility = sqrt(returns.map { (it - mean).pow(2) }.average().coerceAtLeast(0.0))
    return PatternFeatures(
        emaGap = (fast - slow) / average,
        rsi = rsi(closes, 14) / 100.0,
        momentum = (closes.last() - closes[closes.size - 6]) / average,
        volatility = volatility,
    )
}

private fun patternDistance(first: PatternFeatures, second: PatternFeatures): Double = sqrt(
    (first.emaGap - second.emaGap).pow(2) * 100.0 +
        (first.rsi - second.rsi).pow(2) * 4.0 +
        (first.momentum - second.momentum).pow(2) * 100.0 +
        (first.volatility - second.volatility).pow(2) * 100.0,
).coerceAtMost(1.0)
