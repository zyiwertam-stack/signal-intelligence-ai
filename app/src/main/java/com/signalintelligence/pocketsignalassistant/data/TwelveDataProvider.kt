package com.signalintelligence.pocketsignalassistant.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class TwelveDataConnectionResult(
    val success: Boolean,
    val message: String,
    val connected: Boolean = success,
)

class TwelveDataMarketDataProvider(
    private val context: Context,
) : MarketDataProvider {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "twelve_data_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun getApiKey(): String? = prefs.getString("api_key", null)?.trim()

    fun hasApiKey(): Boolean = !getApiKey().isNullOrBlank()

    fun isConnected(): Boolean = prefs.getBoolean("connected", false)

    fun verifyConnection(): TwelveDataConnectionResult {
        val apiKey = getApiKey()
            ?: return TwelveDataConnectionResult(false, "No saved Twelve Data API key.", false)
        val result = validateApiKey(apiKey)
        prefs.edit().putBoolean("connected", result.connected).apply()
        return result
    }

    fun connect(apiKey: String): TwelveDataConnectionResult {
        val value = apiKey.trim()
        if (value.isBlank()) {
            return TwelveDataConnectionResult(false, "Введите ваш Twelve Data API Key.")
        }

        val validation = validateApiKey(value)
        if (!validation.success) {
            prefs.edit().putBoolean("connected", false).apply()
            return validation
        }

        prefs.edit().putString("api_key", value).putBoolean("connected", true).apply()
        return TwelveDataConnectionResult(true, "API ПОДКЛЮЧЁН", true)
    }

    fun disconnect(): TwelveDataConnectionResult {
        prefs.edit().remove("api_key").putBoolean("connected", false).apply()
        return TwelveDataConnectionResult(false, "API ОТКЛЮЧЁН", false)
    }

    private fun validateApiKey(apiKey: String): TwelveDataConnectionResult {
        val url = buildUrl("EUR/USD", CandleInterval.M1, 1, apiKey)
        return try {
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val json = body.takeIf { it.isNotBlank() }?.let(::JSONObject)
                val apiError = json?.optString("message").orEmpty()
                when {
                    response.code == 401 || response.code == 403 ->
                        TwelveDataConnectionResult(false, "Неверный API Key Twelve Data.")
                    response.code == 429 || json?.optInt("code") == 429 ->
                        TwelveDataConnectionResult(false, "Превышен лимит запросов к Twelve Data.")
                    !response.isSuccessful ->
                        TwelveDataConnectionResult(false, "Ошибка Twelve Data HTTP ${response.code}: ${apiError.ifBlank { "request failed" }}")
                    json == null || json.optJSONArray("values") == null ->
                        TwelveDataConnectionResult(false, "Twelve Data не вернул котировки: ${apiError.ifBlank { "unexpected response" }}")
                    else -> TwelveDataConnectionResult(true, "Twelve Data подключён, ключ проверен.", true)
                }
            }
        } catch (error: Exception) {
            TwelveDataConnectionResult(false, "Не удалось подключиться к Twelve Data: ${error.localizedMessage ?: "network error"}")
        }
    }

    private fun buildUrl(symbol: String, interval: CandleInterval, limit: Int, key: String? = getApiKey()): HttpUrl {
        val apiKey = key ?: throw IllegalStateException("API key missing")
        val intervalValue = when (interval) {
            CandleInterval.M1 -> "1min"
            CandleInterval.M2 -> "2min"
            CandleInterval.M3 -> "3min"
            CandleInterval.M5 -> "5min"
            CandleInterval.M10 -> "10min"
            CandleInterval.M15 -> "15min"
            CandleInterval.M30 -> "30min"
            CandleInterval.H1 -> "1h"
        }
        return "https://api.twelvedata.com/time_series".toHttpUrl()
            .newBuilder()
            .addQueryParameter("symbol", symbol)
            .addQueryParameter("interval", intervalValue)
            .addQueryParameter("timezone", "UTC")
            .addQueryParameter("outputsize", limit.toString())
            .addQueryParameter("apikey", apiKey)
            .build()
    }

    override fun fetchMarketSnapshot(pair: TradingPair, interval: CandleInterval, limit: Int): MarketSnapshot {
        if (!hasApiKey() || !isConnected()) {
            return MarketSnapshot(
                pair, 0.0, emptyList(), DataStatus.DATA_UNAVAILABLE, System.currentTimeMillis(),
                "A Twelve Data API key is required for live Forex quotes.",
            )
        }

        return try {
            val request = Request.Builder().url(buildUrl(pair.displayName, interval, limit)).get().build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val json = body.takeIf { it.isNotBlank() }?.let(::JSONObject)
                val apiMessage = json?.optString("message").orEmpty()
                val values = json?.optJSONArray("values")
                if (!response.isSuccessful || values == null) {
                    return MarketSnapshot(
                        pair,
                        0.0,
                        emptyList(),
                        if (response.code == 429 || json?.optInt("code") == 429) DataStatus.DELAYED else DataStatus.DATA_UNAVAILABLE,
                        System.currentTimeMillis(),
                        apiMessage.ifBlank { "Twelve Data returned HTTP ${response.code} without candle data." },
                    )
                }

                val candles = mutableListOf<Candle>()
                for (index in 0 until values.length()) {
                    val item = values.optJSONObject(index) ?: continue
                    val open = item.optDouble("open", Double.NaN)
                    val high = item.optDouble("high", Double.NaN)
                    val low = item.optDouble("low", Double.NaN)
                    val close = item.optDouble("close", Double.NaN)
                    val timestamp = parseDateToMillis(item.optString("datetime", ""))
                    if (
                        timestamp > 0L && open.isFinite() && high.isFinite() && low.isFinite() && close.isFinite() &&
                        low <= minOf(open, close) && high >= maxOf(open, close)
                    ) {
                        candles.add(Candle(timestamp, open, high, low, close, item.optDouble("volume", 0.0)))
                    }
                }

                val ordered = candles.distinctBy { it.time }.sortedBy { it.time }.takeLast(limit)
                val latestTime = ordered.lastOrNull()?.time ?: 0L
                val now = System.currentTimeMillis()
                val freshnessLimit = interval.minutes * 2L * 60_000L
                val status = when {
                    ordered.isEmpty() -> DataStatus.DATA_UNAVAILABLE
                    now - latestTime > freshnessLimit -> DataStatus.DELAYED
                    else -> DataStatus.ONLINE
                }
                MarketSnapshot(
                    pair = pair,
                    currentPrice = ordered.lastOrNull()?.close ?: 0.0,
                    candles = ordered,
                    status = status,
                    updatedAt = latestTime,
                    message = when (status) {
                        DataStatus.ONLINE -> "Live candles retrieved from Twelve Data."
                        DataStatus.DELAYED -> "Latest candle is older than the freshness limit."
                        else -> "No valid candles were returned by Twelve Data."
                    },
                )
            }
        } catch (error: Exception) {
            MarketSnapshot(
                pair, 0.0, emptyList(), DataStatus.DATA_UNAVAILABLE, System.currentTimeMillis(),
                "Could not retrieve valid Twelve Data candles: ${error.localizedMessage ?: "network or response error"}",
            )
        }
    }

    private fun parseDateToMillis(dateString: String): Long {
        return try {
            val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
                isLenient = false
            }
            formatter.parse(dateString)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }
}
