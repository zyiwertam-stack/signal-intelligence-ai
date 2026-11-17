package com.signalintelligence.pocketsignalassistant.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteStatement
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import kotlin.math.floor

data class OtcAsset(
    val symbol: String,
    val tickCount: Long,
    val firstTickUtc: String,
    val lastTickUtc: String,
    val sampleAvailable: Boolean,
)

data class OtcCatalog(
    val assets: List<OtcAsset>,
    val declaredTickCount: Long,
    val generatedAtUtc: String,
    val sourceNotice: String = "Dataset publisher describes these OTC streams as synthetic/research data.",
)

data class TickImportResult(
    val imported: Long,
    val duplicates: Long,
    val invalid: Long,
    val outOfOrder: Long,
    val gaps: Long,
    val assets: Set<String>,
)

data class OtcDatabaseSummary(
    val tickCount: Long,
    val assets: Map<String, Long>,
    val newestTimestamp: Long?,
)

class OtcHistoryDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE ticks (
                asset TEXT NOT NULL,
                time_ms INTEGER NOT NULL,
                price REAL NOT NULL,
                session_id INTEGER,
                PRIMARY KEY (asset, time_ms)
            ) WITHOUT ROWID
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX ticks_time_idx ON ticks(time_ms)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun fetchCatalog(): OtcCatalog {
        val request = Request.Builder().url(MANIFEST_URL).get().build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Manifest request failed (${response.code}).")
            val body = response.body?.string() ?: throw IOException("Manifest response was empty.")
            val root = JSONObject(body)
            val assetsNode = root.optJSONObject("assets") ?: throw IOException("Manifest has no assets object.")
            val sampleAssets = PUBLIC_SAMPLES.keys
            val assets = buildList {
                val keys = assetsNode.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = assetsNode.optJSONObject(key) ?: continue
                    add(
                        OtcAsset(
                            symbol = value.optString("asset", key),
                            tickCount = value.optLong("total_ticks", 0L),
                            firstTickUtc = value.optString("first_tick_utc", ""),
                            lastTickUtc = value.optString("last_tick_utc", ""),
                            sampleAvailable = key in sampleAssets,
                        ),
                    )
                }
            }.sortedBy { it.symbol.lowercase(Locale.ROOT) }
            if (assets.isEmpty()) throw IOException("Manifest contains no usable OTC assets.")
            return OtcCatalog(
                assets = assets,
                declaredTickCount = root.optLong("total_dataset_ticks", 0L),
                generatedAtUtc = root.optString("generated_at_utc", ""),
            )
        }
    }
    fun downloadPublicSample(symbol: String): TickImportResult {
        val file = PUBLIC_SAMPLES[symbol] ?: throw IOException("No public sample is available for $symbol.")
        val request = Request.Builder().url("$SAMPLES_BASE/$file").get().build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Sample download failed (${response.code}).")
            val input = response.body?.byteStream() ?: throw IOException("Sample response was empty.")
            return importCsv(input, fileName = file, selectedAsset = symbol)
        }
    }
    fun importFile(input: InputStream, fileName: String, selectedAsset: String? = null): TickImportResult {
        val lowerName = fileName.lowercase(Locale.ROOT)
        if (lowerName.endsWith(".parquet")) {
            throw UnsupportedOperationException(
                "Parquet import is not supported by this Android build. Export the file to CSV.GZ and import it again.",
            )
        }
        if (!lowerName.endsWith(".csv") && !lowerName.endsWith(".csv.gz") && !lowerName.endsWith(".gz")) {
            throw IOException("Choose a CSV or CSV.GZ tick file.")
        }
        return importCsv(input, fileName, selectedAsset)
    }

    private fun importCsv(input: InputStream, fileName: String, selectedAsset: String?): TickImportResult {
        val decoded = if (fileName.lowercase(Locale.ROOT).endsWith(".gz")) GZIPInputStream(input) else input
        val reader = BufferedReader(InputStreamReader(decoded, Charsets.UTF_8), 64 * 1024)
        val db = writableDatabase
        var imported = 0L
        var duplicates = 0L
        var invalid = 0L
        var outOfOrder = 0L
        var gaps = 0L
        var records = 0L
        val lastTimestampByAsset = mutableMapOf<String, Long>()
        val importedAssets = linkedSetOf<String>()
        val insertStatement = db.compileStatement(
            "INSERT OR IGNORE INTO ticks(asset, time_ms, price, session_id) VALUES(?, ?, ?, ?)",
        )

        try {
            reader.use { buffered ->
                val header = buffered.readLine() ?: throw IOException("CSV file is empty.")
                val indexes = OtcCsvHeader.fromHeader(header)
                    ?: throw IOException("CSV requires timestamp, price, and asset columns.")
                db.beginTransaction()
                try {
                    buffered.forEachLine { row ->
                        if (row.isBlank()) return@forEachLine
                        records++
                        val tick = indexes.parse(row, selectedAsset)
                        if (tick == null) {
                            invalid++
                            return@forEachLine
                        }
                        importedAssets.add(tick.asset)
                        val previousTimestamp = lastTimestampByAsset[tick.asset]
                        if (previousTimestamp != null && tick.timeMs < previousTimestamp) {
                            outOfOrder++
                        } else {
                            if (previousTimestamp != null && tick.timeMs - previousTimestamp > GAP_LIMIT_MS) gaps++
                            lastTimestampByAsset[tick.asset] = tick.timeMs
                        }
                        val insertResult = insertTick(insertStatement, tick.asset, tick.timeMs, tick.price, tick.sessionId)
                        if (insertResult == -1L) duplicates++ else imported++

                        if (records % BATCH_SIZE == 0L) {
                            db.setTransactionSuccessful()
                            db.endTransaction()
                            db.beginTransaction()
                        }
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
        } finally {
            insertStatement.close()
        }
        if (records == 0L || importedAssets.isEmpty()) {
            throw IOException("No valid tick rows were found in $fileName.")
        }
        return TickImportResult(imported, duplicates, invalid, outOfOrder, gaps, importedAssets)
    }

    private fun insertTick(
        statement: SQLiteStatement,
        asset: String,
        timestamp: Long,
        price: Double,
        sessionId: Long?,
    ): Long {
        statement.clearBindings()
        statement.bindString(1, asset)
        statement.bindLong(2, timestamp)
        statement.bindDouble(3, price)
        if (sessionId == null) statement.bindNull(4) else statement.bindLong(4, sessionId)
        return statement.executeInsert()
    }

    fun loadMinuteCandles(asset: String, limit: Int = 720): List<Candle> {
        if (limit <= 0) return emptyList()
        val maxTime = readableDatabase.rawQuery(
            "SELECT MAX(time_ms) FROM ticks WHERE asset = ?",
            arrayOf(asset),
        ).use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else return emptyList()
        }
        val startTime = maxTime - limit * MINUTE_MS
        val candles = ArrayList<Candle>(limit)
        readableDatabase.rawQuery(
            "SELECT time_ms, price FROM ticks WHERE asset = ? AND time_ms >= ? ORDER BY time_ms ASC",
            arrayOf(asset, startTime.toString()),
        ).use { cursor ->
            var bucketStart = Long.MIN_VALUE
            var open = 0.0
            var high = 0.0
            var low = 0.0
            var close = 0.0
            var tickCount = 0.0

            fun flush() {
                if (bucketStart != Long.MIN_VALUE) {
                    candles.add(Candle(bucketStart, open, high, low, close, tickCount))
                }
            }

            while (cursor.moveToNext()) {
                val timestamp = cursor.getLong(0)
                val price = cursor.getDouble(1)
                val bucket = floor(timestamp.toDouble() / MINUTE_MS).toLong() * MINUTE_MS
                if (bucket != bucketStart) {
                    flush()
                    bucketStart = bucket
                    open = price
                    high = price
                    low = price
                    tickCount = 0.0
                }
                high = maxOf(high, price)
                low = minOf(low, price)
                close = price
                tickCount++
            }
            flush()
        }
        return candles
    }

    fun summary(): OtcDatabaseSummary {
        val counts = linkedMapOf<String, Long>()
        readableDatabase.rawQuery("SELECT asset, COUNT(*) FROM ticks GROUP BY asset ORDER BY asset", null).use { cursor ->
            while (cursor.moveToNext()) counts[cursor.getString(0)] = cursor.getLong(1)
        }
        val newest = readableDatabase.rawQuery("SELECT MAX(time_ms) FROM ticks", null).use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }
        return OtcDatabaseSummary(counts.values.sum(), counts, newest)
    }

    fun runBacktest(asset: String, expirationMinutes: Int): BacktestResult {
        val candles = loadMinuteCandles(asset, BACKTEST_CANDLE_LIMIT)
        return runHistoricalBacktest(candles, asset, expirationMinutes)
    }

    companion object {
        private const val DATABASE_NAME = "otc_ticks.db"
        private const val DATABASE_VERSION = 1
        private const val BATCH_SIZE = 10_000L
        private const val GAP_LIMIT_MS = 60_000L
        private const val MINUTE_MS = 60_000L
        private const val BACKTEST_CANDLE_LIMIT = 1_800
        private const val MANIFEST_URL =
            "https://raw.githubusercontent.com/PHCStanton/pocket-option-otc-dataset/main/data/manifest.json"
        private const val SAMPLES_BASE =
            "https://raw.githubusercontent.com/PHCStanton/pocket-option-otc-dataset/main/data/samples"
        private val PUBLIC_SAMPLES = mapOf(
            "AUDCAD_otc" to "AUDCAD_otc_sample.csv.gz",
            "BTCUSD_otc" to "BTCUSD_otc_sample.csv.gz",
            "EURUSD_otc" to "EURUSD_otc_sample.csv.gz",
            "GBPUSD_otc" to "GBPUSD_otc_sample.csv.gz",
            "USDJPY_otc" to "USDJPY_otc_sample.csv.gz",
        )
    }
}
