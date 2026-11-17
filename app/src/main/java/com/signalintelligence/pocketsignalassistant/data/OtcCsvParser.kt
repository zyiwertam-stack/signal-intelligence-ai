package com.signalintelligence.pocketsignalassistant.data

import java.util.Locale

internal data class OtcCsvTick(
    val asset: String,
    val timeMs: Long,
    val price: Double,
    val sessionId: Long?,
)

internal class OtcCsvHeader private constructor(
    private val timestampIndex: Int,
    private val priceIndex: Int,
    private val assetIndex: Int?,
    private val sessionIndex: Int?,
) {
    fun parse(line: String, selectedAsset: String? = null): OtcCsvTick? {
        val cells = line.split(',')
        val lastRequiredColumn = maxOf(timestampIndex, priceIndex, assetIndex ?: 0, sessionIndex ?: 0)
        if (cells.size <= lastRequiredColumn) return null
        val timestampValue = cells[timestampIndex].trim().toDoubleOrNull() ?: return null
        val priceValue = cells[priceIndex].trim().toDoubleOrNull() ?: return null
        if (!timestampValue.isFinite() || !priceValue.isFinite() || timestampValue <= 0.0 || priceValue <= 0.0) return null
        val timestampMs = if (timestampValue >= 1_000_000_000_000.0) {
            timestampValue.toLong()
        } else {
            (timestampValue * 1000.0).toLong()
        }
        if (timestampMs !in MIN_TIMESTAMP_MS..MAX_TIMESTAMP_MS) return null
        val asset = selectedAsset?.takeIf { it.isNotBlank() }
            ?: assetIndex?.let { cells[it].trim() }?.takeIf { it.isNotBlank() }
            ?: return null
        val sessionId = sessionIndex?.let { cells[it].trim().toLongOrNull() }
        return OtcCsvTick(asset, timestampMs, priceValue, sessionId)
    }

    companion object {
        fun fromHeader(header: String): OtcCsvHeader? {
            val names = header.removePrefix("\uFEFF").split(',').map { it.trim().lowercase(Locale.ROOT) }
            val timestamp = names.indexOf("timestamp")
            val price = names.indexOf("price")
            val asset = names.indexOf("asset").takeIf { it >= 0 }
            val session = names.indexOf("session_id").takeIf { it >= 0 }
            if (timestamp < 0 || price < 0) return null
            return OtcCsvHeader(timestamp, price, asset, session)
        }
    }
}

private const val MIN_TIMESTAMP_MS = 946_684_800_000L
private const val MAX_TIMESTAMP_MS = 4_102_444_800_000L
