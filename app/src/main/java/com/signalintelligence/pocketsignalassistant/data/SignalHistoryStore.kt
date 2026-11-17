package com.signalintelligence.pocketsignalassistant.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SignalHistoryEntry(
    val pair: TradingPair,
    val interval: CandleInterval,
    val direction: SignalKind,
    val confidence: Int,
    val timestamp: Long,
    val price: Double,
    val source: String,
    val reason: String,
)

class SignalHistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("signal_history_store", Context.MODE_PRIVATE)

    fun load(): List<SignalHistoryEntry> {
        val raw = prefs.getString("entries", null) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val node = array.getJSONObject(index)
                    add(
                        SignalHistoryEntry(
                            pair = TradingPair.valueOf(node.getString("pair")),
                            interval = CandleInterval.valueOf(node.getString("interval")),
                            direction = SignalKind.valueOf(node.getString("direction")),
                            confidence = node.optInt("confidence", 0),
                            timestamp = node.optLong("timestamp", System.currentTimeMillis()),
                            price = node.optDouble("price", 0.0),
                            source = node.optString("source", "unknown"),
                            reason = node.optString("reason", ""),
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun add(entry: SignalHistoryEntry) {
        val updated = load().toMutableList()
        updated.add(0, entry)
        replace(updated.take(20))
    }

    fun replace(entries: List<SignalHistoryEntry>) {
        val array = JSONArray()
        entries.forEach { item ->
            val obj = JSONObject()
            obj.put("pair", item.pair.name)
            obj.put("interval", item.interval.name)
            obj.put("direction", item.direction.name)
            obj.put("confidence", item.confidence)
            obj.put("timestamp", item.timestamp)
            obj.put("price", item.price)
            obj.put("source", item.source)
            obj.put("reason", item.reason)
            array.put(obj)
        }
        prefs.edit().putString("entries", array.toString()).apply()
    }
}
