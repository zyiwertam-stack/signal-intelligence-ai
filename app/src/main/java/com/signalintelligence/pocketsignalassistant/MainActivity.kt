package com.signalintelligence.pocketsignalassistant

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.signalintelligence.pocketsignalassistant.data.BacktestResult
import com.signalintelligence.pocketsignalassistant.data.Candle
import com.signalintelligence.pocketsignalassistant.data.CandleInterval
import com.signalintelligence.pocketsignalassistant.data.DataStatus
import com.signalintelligence.pocketsignalassistant.data.HistoricalPatternSummary
import com.signalintelligence.pocketsignalassistant.data.MarketSource
import com.signalintelligence.pocketsignalassistant.data.MarketSnapshot
import com.signalintelligence.pocketsignalassistant.data.OtcAsset
import com.signalintelligence.pocketsignalassistant.data.OtcCatalog
import com.signalintelligence.pocketsignalassistant.data.OtcDatabaseSummary
import com.signalintelligence.pocketsignalassistant.data.OtcHistoryDatabase
import com.signalintelligence.pocketsignalassistant.data.SignalHistoryEntry
import com.signalintelligence.pocketsignalassistant.data.SignalHistoryStore
import com.signalintelligence.pocketsignalassistant.data.SignalKind
import com.signalintelligence.pocketsignalassistant.data.TechnicalSnapshot
import com.signalintelligence.pocketsignalassistant.data.TickImportResult
import com.signalintelligence.pocketsignalassistant.data.TradingPair
import com.signalintelligence.pocketsignalassistant.data.TwelveDataMarketDataProvider
import com.signalintelligence.pocketsignalassistant.data.calculateTechnicalSnapshot
import com.signalintelligence.pocketsignalassistant.data.findHistoricalPatterns
import com.signalintelligence.pocketsignalassistant.ui.theme.PocketColors
import com.signalintelligence.pocketsignalassistant.ui.theme.PocketSignalAssistantTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val analysisStages = listOf(
    "Collecting market data...",
    "Analyzing price action...",
    "Checking historical OTC patterns...",
    "Calculating technical indicators...",
    "Checking UP/DOWN model calibration...",
    "Validating trading signal...",
)

data class ApiConnectionState(
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val message: String = "Connect a Twelve Data key to request live Forex prices.",
)

data class AnalysisReport(
    val signal: SignalKind,
    val source: MarketSource,
    val asset: String,
    val expirationMinutes: Int,
    val price: Double?,
    val priceTimestamp: Long?,
    val candles: List<Candle>,
    val technical: TechnicalSnapshot?,
    val analogCount: Int,
    val patternSummary: HistoricalPatternSummary?,
    val backtest: BacktestResult?,
    val reason: String,
    val generatedAt: Long,
)

data class AppUiState(
    val selectedSource: MarketSource = MarketSource.LIVE_FOREX,
    val selectedPair: TradingPair = TradingPair.EUR_USD,
    val selectedOtcAsset: String = "EURUSD_otc",
    val selectedExpirationMinutes: Int = 1,
    val dataStatus: DataStatus = DataStatus.DATA_UNAVAILABLE,
    val currentPrice: Double? = null,
    val lastUpdated: Long? = null,
    val candles: List<Candle> = emptyList(),
    val history: List<SignalHistoryEntry> = emptyList(),
    val storedApiKey: String = "",
    val apiConnection: ApiConnectionState = ApiConnectionState(),
    val catalog: OtcCatalog? = null,
    val catalogLoading: Boolean = false,
    val catalogMessage: String = "OTC manifest has not been loaded.",
    val databaseSummary: OtcDatabaseSummary = OtcDatabaseSummary(0L, emptyMap(), null),
    val databaseMessage: String = "No local OTC ticks imported.",
    val analysisRunning: Boolean = false,
    val analysisStage: Int = -1,
    val analysis: AnalysisReport? = null,
    val analysisError: String? = null,
)

enum class BottomTab { HOME, HISTORY, BACKTEST, SETTINGS }

class PocketSignalViewModel(
    context: Context,
    private val otcDatabase: OtcHistoryDatabase = OtcHistoryDatabase(context.applicationContext),
    private val twelveDataProvider: TwelveDataMarketDataProvider =
        TwelveDataMarketDataProvider(context.applicationContext),
    private val historyStore: SignalHistoryStore = SignalHistoryStore(context.applicationContext),
) : ViewModel() {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(
        AppUiState(
            storedApiKey = twelveDataProvider.getApiKey().orEmpty(),
            history = historyStore.load(),
        ),
    )
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    init {
        refreshCatalog()
        refreshDatabaseSummary()
        verifySavedApiKey()
    }

    fun selectSource(source: MarketSource) {
        _state.value = _state.value.copy(
            selectedSource = source,
            currentPrice = null,
            lastUpdated = null,
            candles = emptyList(),
            analysis = null,
            analysisError = null,
            dataStatus = DataStatus.DATA_UNAVAILABLE,
        )
    }

    fun selectPair(pair: TradingPair) {
        _state.value = _state.value.copy(
            selectedPair = pair,
            currentPrice = null,
            lastUpdated = null,
            candles = emptyList(),
            analysis = null,
            analysisError = null,
        )
    }

    fun selectOtcAsset(asset: String) {
        _state.value = _state.value.copy(
            selectedOtcAsset = asset,
            currentPrice = null,
            lastUpdated = null,
            candles = emptyList(),
            analysis = null,
            analysisError = null,
        )
    }

    fun selectExpiration(minutes: Int) {
        if (minutes !in supportedExpirations) return
        _state.value = _state.value.copy(
            selectedExpirationMinutes = minutes,
            analysis = null,
            analysisError = null,
        )
    }

    fun connectApi(apiKey: String) {
        if (_state.value.apiConnection.connecting) return
        _state.value = _state.value.copy(
            apiConnection = _state.value.apiConnection.copy(connecting = true, message = "Checking Twelve Data key..."),
        )
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { twelveDataProvider.connect(apiKey) }
            _state.value = _state.value.copy(
                storedApiKey = twelveDataProvider.getApiKey().orEmpty(),
                apiConnection = ApiConnectionState(result.connected, false, result.message),
            )
        }
    }

    fun disconnectApi() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { twelveDataProvider.disconnect() }
            _state.value = _state.value.copy(
                storedApiKey = "",
                apiConnection = ApiConnectionState(false, false, result.message),
                analysis = null,
                analysisError = null,
            )
        }
    }

    private fun verifySavedApiKey() {
        if (!twelveDataProvider.hasApiKey()) return
        _state.value = _state.value.copy(
            apiConnection = ApiConnectionState(false, true, "Verifying saved Twelve Data key..."),
        )
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { twelveDataProvider.verifyConnection() }
            _state.value = _state.value.copy(
                apiConnection = ApiConnectionState(result.connected, false, result.message),
            )
        }
    }

    fun refreshCatalog() {
        if (_state.value.catalogLoading) return
        _state.value = _state.value.copy(catalogLoading = true, catalogMessage = "Loading OTC manifest...")
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { otcDatabase.fetchCatalog() } }
                .onSuccess { catalog ->
                    val defaultAsset = if (catalog.assets.any { it.symbol == _state.value.selectedOtcAsset }) {
                        _state.value.selectedOtcAsset
                    } else {
                        catalog.assets.firstOrNull()?.symbol ?: _state.value.selectedOtcAsset
                    }
                    _state.value = _state.value.copy(
                        catalog = catalog,
                        catalogLoading = false,
                        catalogMessage = "Manifest: ${catalog.assets.size} assets; publisher declares ${catalog.declaredTickCount} ticks. Generated ${catalog.generatedAtUtc}.",
                        selectedOtcAsset = defaultAsset,
                    )
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _state.value = _state.value.copy(
                        catalogLoading = false,
                        catalogMessage = "Could not load OTC manifest: ${error.message ?: "network error"}",
                    )
                }
        }
    }

    fun downloadSelectedSample() {
        val symbol = _state.value.selectedOtcAsset
        val asset = _state.value.catalog?.assets?.firstOrNull { it.symbol == symbol }
        if (asset?.sampleAvailable != true) {
            _state.value = _state.value.copy(databaseMessage = "No public sample file is available for $symbol.")
            return
        }
        _state.value = _state.value.copy(databaseMessage = "Downloading compressed sample for $symbol...")
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { otcDatabase.downloadPublicSample(symbol) } }
                .onSuccess { result ->
                    updateDatabaseSummary(importMessage(result))
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _state.value = _state.value.copy(
                        databaseMessage = "Sample download/import failed: ${error.message ?: "unknown error"}",
                    )
                }
        }
    }

    fun importFile(uri: Uri) {
        val name = appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: "selected-file"
        _state.value = _state.value.copy(databaseMessage = "Importing $name in batches...")
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val input = appContext.contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("Could not open the selected file.")
                    input.use { otcDatabase.importFile(it, name) }
                }
            }.onSuccess { result ->
                updateDatabaseSummary(importMessage(result))
            }.onFailure { error ->
                if (error is CancellationException) throw error
                _state.value = _state.value.copy(
                    databaseMessage = "Import failed: ${error.message ?: "unknown error"}",
                )
            }
        }
    }

    private fun updateDatabaseSummary(message: String) {
        viewModelScope.launch {
            val summary = withContext(Dispatchers.IO) { otcDatabase.summary() }
            _state.value = _state.value.copy(databaseSummary = summary, databaseMessage = message)
        }
    }

    private fun refreshDatabaseSummary() {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { otcDatabase.summary() } }
                .onSuccess { summary ->
                    _state.value = _state.value.copy(
                        databaseSummary = summary,
                        databaseMessage = if (summary.tickCount == 0L) {
                            "No local OTC ticks imported."
                        } else {
                            "${summary.tickCount} local ticks across ${summary.assets.size} assets. Historical only; not live quotes."
                        },
                    )
                }
        }
    }

    fun runAnalysis() {
        val initial = _state.value
        if (initial.analysisRunning) return
        _state.value = initial.copy(
            analysisRunning = true,
            analysisStage = 0,
            analysis = null,
            analysisError = null,
            currentPrice = null,
            lastUpdated = null,
            candles = emptyList(),
            dataStatus = DataStatus.DATA_UNAVAILABLE,
        )
        viewModelScope.launch {
            try {
                var candles = emptyList<Candle>()
                var snapshot: MarketSnapshot? = null
                var backtest: BacktestResult? = null
                var technical: TechnicalSnapshot? = null
                var patternSummary: HistoricalPatternSummary? = null
                analysisStages.forEachIndexed { index, _ ->
                    _state.value = _state.value.copy(analysisStage = index)
                    when (index) {
                        0 -> {
                            if (initial.selectedSource == MarketSource.LIVE_FOREX) {
                                if (!twelveDataProvider.isConnected()) {
                                    throw AnalysisNotAvailableException(
                                        "Connect and verify a Twelve Data API key before requesting live Forex data.",
                                    )
                                }
                                val fetchedSnapshot = withContext(Dispatchers.IO) {
                                    twelveDataProvider.fetchMarketSnapshot(
                                        initial.selectedPair,
                                        CandleInterval.M1,
                                        200,
                                    )
                                }
                                snapshot = fetchedSnapshot
                                if (fetchedSnapshot.status != DataStatus.ONLINE || fetchedSnapshot.currentPrice <= 0.0) {
                                    throw AnalysisNotAvailableException(
                                        fetchedSnapshot.message.ifBlank { "Live Forex quote is unavailable or stale." },
                                    )
                                }
                                candles = fetchedSnapshot.candles
                                backtest = null
                            } else {
                                snapshot = null
                                candles = withContext(Dispatchers.IO) {
                                    otcDatabase.loadMinuteCandles(initial.selectedOtcAsset, 720)
                                }
                                if (candles.isEmpty()) {
                                    throw AnalysisNotAvailableException(
                                        "No historical ticks for ${initial.selectedOtcAsset}. Download a published sample or import a CSV.GZ file.",
                                    )
                                }
                            }
                        }
                        1 -> delay(STAGE_DELAY_MS)
                        2 -> {
                            delay(STAGE_DELAY_MS)
                            if (initial.selectedSource == MarketSource.HISTORICAL_OTC) {
                                patternSummary = withContext(Dispatchers.Default) {
                                    findHistoricalPatterns(candles, initial.selectedExpirationMinutes)
                                }
                                backtest = withContext(Dispatchers.IO) {
                                    otcDatabase.runBacktest(initial.selectedOtcAsset, initial.selectedExpirationMinutes)
                                }
                            }
                        }
                        3 -> {
                            delay(STAGE_DELAY_MS)
                            technical = withContext(Dispatchers.Default) { calculateTechnicalSnapshot(candles) }
                        }
                        4 -> delay(STAGE_DELAY_MS)
                        5 -> delay(STAGE_DELAY_MS)
                    }
                }
                val sourceText = if (initial.selectedSource == MarketSource.HISTORICAL_OTC) {
                    "Historical OTC ticks are not current OTC quotes. The public GitHub source is described as synthetic/research data; imported-file provenance is unverified."
                } else {
                    "Technical indicators were calculated from current Twelve Data candles, but no independently validated, calibrated probability model is available."
                }
                val report = AnalysisReport(
                    signal = SignalKind.NO_SIGNAL,
                    source = initial.selectedSource,
                    asset = if (initial.selectedSource == MarketSource.LIVE_FOREX) {
                        initial.selectedPair.displayName
                    } else {
                        initial.selectedOtcAsset
                    },
                    expirationMinutes = initial.selectedExpirationMinutes,
                    price = snapshot?.currentPrice ?: candles.lastOrNull()?.close,
                    priceTimestamp = snapshot?.updatedAt ?: candles.lastOrNull()?.time,
                    candles = candles,
                    technical = technical,
                    analogCount = patternSummary?.analogCount ?: 0,
                    patternSummary = patternSummary,
                    backtest = backtest,
                    reason = when {
                        technical == null -> "INSUFFICIENT DATA: at least 50 valid one-minute candles are required. $sourceText"
                        snapshot?.status == DataStatus.DELAYED -> "The latest live quote is stale; no signal is issued. $sourceText"
                        else -> "NO SIGNAL: no independently validated probability model is available. $sourceText"
                    },
                    generatedAt = System.currentTimeMillis(),
                )
                val latestCandle = snapshot?.candles?.lastOrNull()
                _state.value = _state.value.copy(
                    analysisRunning = false,
                    analysisStage = analysisStages.lastIndex,
                    analysis = report,
                    dataStatus = snapshot?.status ?: DataStatus.DELAYED,
                    currentPrice = report.price,
                    lastUpdated = report.priceTimestamp,
                    candles = candles,
                    databaseSummary = if (snapshot == null) {
                        withContext(Dispatchers.IO) { otcDatabase.summary() }
                    } else {
                        _state.value.databaseSummary
                    },
                    analysisError = null,
                )
                if (latestCandle != null && latestCandle.time != snapshot?.updatedAt) {
                    _state.value = _state.value.copy(
                        dataStatus = DataStatus.DELAYED,
                        analysis = report.copy(reason = "Latest returned candle is not current. No signal is issued. ${report.reason}"),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    analysisRunning = false,
                    analysisStage = -1,
                    analysis = null,
                    currentPrice = null,
                    lastUpdated = null,
                    candles = emptyList(),
                    dataStatus = DataStatus.DATA_UNAVAILABLE,
                    analysisError = error.message ?: "Analysis failed.",
                )
            }
        }
    }

    private fun importMessage(result: TickImportResult): String =
        "Imported ${result.imported} ticks; ${result.duplicates} duplicates; ${result.invalid} invalid rows; ${result.outOfOrder} out-of-order rows; ${result.gaps} gaps; ${result.assets.size} assets. Research data only."

    companion object {
        private const val STAGE_DELAY_MS = 450L
        val supportedExpirations = listOf(1, 2, 3, 5, 10, 15)
    }
}

private class AnalysisNotAvailableException(message: String) : Exception(message)

class PocketSignalViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(PocketSignalViewModel::class.java)) {
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
        @Suppress("UNCHECKED_CAST")
        return PocketSignalViewModel(context.applicationContext) as T
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PocketSignalAssistantTheme { PocketSignalAssistantApp() } }
    }
}

@Composable
fun PocketSignalAssistantApp(
    viewModel: PocketSignalViewModel = viewModel(factory = PocketSignalViewModelFactory(LocalContext.current)),
) {
    val state by viewModel.state.collectAsState()
    var selectedTab by rememberSaveable { mutableStateOf(BottomTab.HOME) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importFile)
    }

    Scaffold(
        containerColor = PocketColors.DarkBackground,
        bottomBar = { BottomNavigationBar(selectedTab) { selectedTab = it } },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) {
                BottomTab.HOME -> HomeScreen(state, viewModel)
                BottomTab.HISTORY -> HistoryScreen(state.history)
                BottomTab.BACKTEST -> BacktestScreen(state)
                BottomTab.SETTINGS -> SettingsScreen(
                    state = state,
                    onConnect = viewModel::connectApi,
                    onDisconnect = viewModel::disconnectApi,
                    onRefreshCatalog = viewModel::refreshCatalog,
                    onDownloadSample = viewModel::downloadSelectedSample,
                    onImportFile = { filePicker.launch(arrayOf("*/*")) },
                    onSelectSource = viewModel::selectSource,
                )
            }
        }
    }
}

@Composable
private fun BottomNavigationBar(selected: BottomTab, onSelect: (BottomTab) -> Unit) {
    Surface(color = PocketColors.PanelBackground, shadowElevation = 10.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            BottomTabEntry(selected, BottomTab.HOME, "Home", Icons.Filled.Home, onSelect)
            BottomTabEntry(selected, BottomTab.HISTORY, "History", Icons.Filled.History, onSelect)
            BottomTabEntry(selected, BottomTab.BACKTEST, "Backtest", Icons.Filled.Analytics, onSelect)
            BottomTabEntry(selected, BottomTab.SETTINGS, "Settings", Icons.Filled.Settings, onSelect)
        }
    }
}

@Composable
private fun BottomTabEntry(
    selected: BottomTab,
    tab: BottomTab,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onSelect: (BottomTab) -> Unit,
) {
    val active = selected == tab
    Column(
        modifier = Modifier.clickable { onSelect(tab) }.padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = if (active) PocketColors.AccentSoft else PocketColors.TextSecondary)
        Text(label, color = if (active) PocketColors.AccentSoft else PocketColors.TextSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun HomeScreen(state: AppUiState, viewModel: PocketSignalViewModel) {
    Column(
        Modifier.fillMaxSize().background(PocketColors.DarkBackground).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("POCKET SIGNAL", color = PocketColors.AccentSoft, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("ASSISTANT", color = PocketColors.TextPrimary, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
            }
            StatusBadge(state.dataStatus)
        }

        SourceSelector(state.selectedSource, viewModel::selectSource)
        if (state.selectedSource == MarketSource.HISTORICAL_OTC) {
            AssetPicker(
                title = "SELECT ASSET",
                selected = state.selectedOtcAsset,
                assets = state.catalog?.assets.orEmpty(),
                onSelected = viewModel::selectOtcAsset,
            )
        } else {
            PairPicker(state.selectedPair, viewModel::selectPair)
        }
        ExpirationPicker(state.selectedExpirationMinutes, viewModel::selectExpiration)

        Button(
            onClick = viewModel::runAnalysis,
            enabled = !state.analysisRunning,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(17.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PocketColors.Accent, disabledContainerColor = PocketColors.Accent.copy(alpha = 0.55f)),
        ) {
            Text(if (state.analysisRunning) "ANALYZING MARKET..." else "GET SIGNAL", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }

        AnalysisProgressCard(state)
        ProbabilityCard(state.analysis)
        FinalSignalCard(state.analysis, state.analysisError)
        state.analysis?.let { AnalysisDetails(it) }
        if (state.selectedSource == MarketSource.HISTORICAL_OTC && state.analysis == null && !state.analysisRunning) {
            InfoCard(
                title = "Historical database",
                body = state.databaseMessage + "\nThe OTC archive is not a live quote source.",
            )
        }
    }
}

@Composable
private fun SourceSelector(source: MarketSource, onSelect: (MarketSource) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("DATA SOURCE")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SourceButton("LIVE MARKET DATA", source == MarketSource.LIVE_FOREX, Modifier.weight(1f)) {
                onSelect(MarketSource.LIVE_FOREX)
            }
            SourceButton("HISTORICAL OTC", source == MarketSource.HISTORICAL_OTC, Modifier.weight(1f)) {
                onSelect(MarketSource.HISTORICAL_OTC)
            }
        }
    }
}

@Composable
private fun SourceButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(13.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) PocketColors.Accent.copy(alpha = 0.18f) else PocketColors.CardBackground,
            contentColor = if (selected) PocketColors.AccentSoft else PocketColors.TextSecondary,
        ),
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AssetPicker(
    title: String,
    selected: String,
    assets: List<OtcAsset>,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(title)
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(selected.replace("_", " ").uppercase(), color = PocketColors.TextPrimary)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                if (assets.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Manifest is not loaded") },
                        onClick = { expanded = false },
                    )
                }
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    assets.forEach { asset ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(asset.symbol.replace("_", " ").uppercase())
                                    Text(
                                        if (asset.sampleAvailable) "Public sample available" else "Manifest only — no public file",
                                        color = PocketColors.TextSecondary,
                                        fontSize = 11.sp,
                                    )
                                    Text(
                                        "${asset.tickCount} manifest ticks • ${asset.firstTickUtc.take(10)} to ${asset.lastTickUtc.take(10)}",
                                        color = PocketColors.TextSecondary,
                                        fontSize = 10.sp,
                                    )
                                }
                            },
                            onClick = {
                                onSelected(asset.symbol)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PairPicker(selected: TradingPair, onSelected: (TradingPair) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("SELECT ASSET")
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(selected.displayName, color = PocketColors.TextPrimary)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                TradingPair.entries.forEach { pair ->
                    DropdownMenuItem(
                        text = { Text(pair.displayName) },
                        onClick = {
                            onSelected(pair)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpirationPicker(selected: Int, onSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("EXPIRATION TIME")
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("$selected MIN", color = PocketColors.TextPrimary, fontWeight = FontWeight.Bold)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                PocketSignalViewModel.supportedExpirations.forEach { minutes ->
                    DropdownMenuItem(
                        text = { Text("$minutes MIN") },
                        onClick = {
                            onSelected(minutes)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AnalysisProgressCard(state: AppUiState) {
    Card(colors = CardDefaults.cardColors(containerColor = PocketColors.CardBackground), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (state.analysisRunning) "ANALYZING MARKET..." else "ANALYSIS PROCESS", color = PocketColors.TextPrimary, fontWeight = FontWeight.Bold)
                if (state.analysisRunning) {
                    val progress by animateFloatAsState((state.analysisStage + 1) / analysisStages.size.toFloat(), label = "analysis-progress")
                    Text("${((progress * 100).toInt()).coerceIn(0, 100)}%", color = PocketColors.AccentSoft, fontSize = 12.sp)
                } else if (state.analysis != null) {
                    Text("COMPLETE", color = PocketColors.Success, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (state.analysisRunning) {
                val progress by animateFloatAsState((state.analysisStage + 1) / analysisStages.size.toFloat(), label = "progress-bar")
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = PocketColors.AccentSoft,
                    trackColor = PocketColors.PanelBackground,
                )
            }
            analysisStages.forEachIndexed { index, stage ->
                val active = state.analysisRunning && index == state.analysisStage
                val completed = state.analysis != null || state.analysisRunning && index < state.analysisStage
                val color by animateColorAsState(
                    if (completed) PocketColors.Success else if (active) PocketColors.AccentSoft else PocketColors.TextSecondary.copy(alpha = 0.55f),
                    label = "stage-color",
                )
                Text("${if (completed) "✓" else if (active) "●" else "○"}  $stage", color = color, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ProbabilityCard(analysis: AnalysisReport?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = PocketColors.CardBackground),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("PRICE MOVEMENT PROBABILITY", color = PocketColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("UP   —   INSUFFICIENT DATA", color = PocketColors.Success, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("DOWN — INSUFFICIENT DATA", color = PocketColors.Danger, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                analysis?.reason ?: "No calibrated, independently validated probability model is available.",
                color = PocketColors.TextSecondary,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun FinalSignalCard(analysis: AnalysisReport?, error: String?) {
    val reason = error ?: analysis?.reason
    Card(
        colors = CardDefaults.cardColors(containerColor = PocketColors.Neutral.copy(alpha = 0.14f)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(if (analysis != null) "ANALYSIS COMPLETE" else "FINAL SIGNAL", color = PocketColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(
                if (error != null || analysis != null) "NO SIGNAL" else "WAITING FOR ANALYSIS",
                color = PocketColors.Neutral,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            analysis?.let {
                Text(it.asset.uppercase(), color = PocketColors.TextPrimary, fontWeight = FontWeight.Bold)
                Text("EXPIRATION: ${it.expirationMinutes} MIN", color = PocketColors.TextSecondary)
                Text("TIME: ${formatTimestamp(it.generatedAt)}", color = PocketColors.TextSecondary)
                Text("HISTORICAL ANALOGS: ${it.analogCount}", color = PocketColors.TextSecondary)
            }
            if (!reason.isNullOrBlank()) {
                Text(reason, color = if (error != null) PocketColors.Danger else PocketColors.TextSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun AnalysisDetails(analysis: AnalysisReport) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("ANALYSIS DETAILS", color = PocketColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = PocketColors.CardBackground), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Source: ${if (analysis.source == MarketSource.LIVE_FOREX) "Twelve Data live Forex" else "Historical OTC database"}", color = PocketColors.TextPrimary)
                Text(
                    if (analysis.source == MarketSource.HISTORICAL_OTC) "Last stored price (not live)" else "Current price",
                    color = PocketColors.TextSecondary,
                    fontSize = 12.sp,
                )
                Text(analysis.price?.let(::formatPrice) ?: "Unavailable", color = PocketColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Quote timestamp: ${analysis.priceTimestamp?.let(::formatTimestamp) ?: "Unavailable"}", color = PocketColors.TextSecondary, fontSize = 12.sp)
                Text("Candles: ${analysis.candles.size}", color = PocketColors.TextSecondary, fontSize = 12.sp)
                analysis.technical?.let { TechnicalMetrics(it) }
                analysis.patternSummary?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Historical analog outcomes (not probabilities): UP ${it.observedUp} • DOWN ${it.observedDown}",
                        color = PocketColors.TextSecondary,
                        fontSize = 12.sp,
                    )
                    Text(it.note, color = PocketColors.TextSecondary, fontSize = 11.sp)
                }
            }
        }
        CandleChart(analysis.candles)
    }
}

@Composable
private fun TechnicalMetrics(metrics: TechnicalSnapshot) {
    Spacer(Modifier.height(4.dp))
    Text("EMA 9  ${formatPrice(metrics.ema9)}    EMA 21  ${formatPrice(metrics.ema21)}    EMA 50  ${formatPrice(metrics.ema50)}", color = PocketColors.TextSecondary, fontSize = 12.sp)
    Text("RSI 14  ${"%.1f".format(Locale.US, metrics.rsi14)}    MACD  ${"%.5f".format(Locale.US, metrics.macd.macd)}    ADX 14  ${"%.1f".format(Locale.US, metrics.adx14)}", color = PocketColors.TextSecondary, fontSize = 12.sp)
    Text("ATR 14  ${formatPrice(metrics.atr14)}    Momentum  ${"%.5f".format(Locale.US, metrics.momentum)}", color = PocketColors.TextSecondary, fontSize = 12.sp)
    Text("Bollinger  ${formatPrice(metrics.bollinger.lower)} – ${formatPrice(metrics.bollinger.upper)}", color = PocketColors.TextSecondary, fontSize = 12.sp)
    Text("Volatility  ${"%.6f".format(Locale.US, metrics.volatility)}    Price action  ${metrics.priceAction}", color = PocketColors.TextSecondary, fontSize = 12.sp)
}

@Composable
private fun HistoryScreen(history: List<SignalHistoryEntry>) {
    Column(Modifier.fillMaxSize().background(PocketColors.DarkBackground).padding(16.dp)) {
        Text("SIGNAL HISTORY", color = PocketColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        if (history.isEmpty()) {
            InfoCard("No signal history", "Only completed, data-backed signals are saved. Current build blocks signals until a validated probability model is available.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(history) { entry ->
                    InfoCard(
                        "${entry.direction.name}  •  ${entry.pair.displayName}",
                        "${entry.interval.label} • ${formatTimestamp(entry.timestamp)} • ${entry.source}\n${entry.reason}",
                    )
                }
            }
        }
    }
}

@Composable
private fun BacktestScreen(state: AppUiState) {
    Column(
        Modifier.fillMaxSize().background(PocketColors.DarkBackground).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("OTC BACKTEST", color = PocketColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        val result = state.analysis?.backtest
        if (result == null) {
            InfoCard("No backtest run", "Select HISTORICAL OTC and press GET SIGNAL to run a chronological 70/30 research backtest.")
        } else {
            InfoCard(
                "${result.asset} • ${result.expirationMinutes} MIN",
                "Total signals: ${result.totalSignals}\nWinning directions: ${result.wins}\nLosing directions: ${result.losses}\nUnchanged outcomes: ${result.ties}\nDirectional accuracy: ${result.directionalAccuracy?.let { "%.1f%%".format(Locale.US, it * 100.0) } ?: "INSUFFICIENT DATA"}\nMaximum losing streak: ${result.maxLosingStreak}\n\n${result.note}",
            )
        }
        InfoCard("Database inventory", "${state.databaseSummary.tickCount} ticks across ${state.databaseSummary.assets.size} assets\n${state.databaseMessage}")
    }
}

@Composable
private fun SettingsScreen(
    state: AppUiState,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onRefreshCatalog: () -> Unit,
    onDownloadSample: () -> Unit,
    onImportFile: () -> Unit,
    onSelectSource: (MarketSource) -> Unit,
) {
    var apiKey by rememberSaveable { mutableStateOf(state.storedApiKey) }
    var showKey by rememberSaveable { mutableStateOf(false) }
    val selectedSampleAvailable = state.catalog?.assets
        ?.firstOrNull { it.symbol == state.selectedOtcAsset }
        ?.sampleAvailable == true
    Column(
        Modifier.fillMaxSize().background(PocketColors.DarkBackground).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("SETTINGS", color = PocketColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        InfoCard(
            "DATA SOURCE",
            "Selected: ${if (state.selectedSource == MarketSource.LIVE_FOREX) "LIVE MARKET DATA" else "HISTORICAL OTC DATABASE"}",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SourceButton("LIVE FOREX", state.selectedSource == MarketSource.LIVE_FOREX, Modifier.weight(1f)) { onSelectSource(MarketSource.LIVE_FOREX) }
            SourceButton("OTC HISTORY", state.selectedSource == MarketSource.HISTORICAL_OTC, Modifier.weight(1f)) { onSelectSource(MarketSource.HISTORICAL_OTC) }
        }

        Card(colors = CardDefaults.cardColors(containerColor = PocketColors.CardBackground), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("TWELVE DATA — LIVE FOREX", color = PocketColors.TextPrimary, fontWeight = FontWeight.Bold)
                Text(
                    if (state.apiConnection.connecting) "VERIFYING" else if (state.apiConnection.connected) "CONNECTED" else "NOT CONNECTED",
                    color = if (state.apiConnection.connected) PocketColors.Success else PocketColors.TextSecondary,
                    fontWeight = FontWeight.Bold,
                )
                Text(state.apiConnection.message, color = PocketColors.TextSecondary, fontSize = 12.sp)
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Personal Twelve Data API key") },
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Text(
                            if (showKey) "HIDE" else "SHOW",
                            color = PocketColors.AccentSoft,
                            modifier = Modifier.clickable { showKey = !showKey }.padding(8.dp),
                            fontSize = 11.sp,
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onConnect(apiKey) },
                        enabled = !state.apiConnection.connecting,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = PocketColors.Accent),
                    ) { Text("CONNECT API") }
                    OutlinedButton(onClick = onDisconnect, modifier = Modifier.weight(1f)) { Text("DISCONNECT") }
                }
                Text("Key is stored encrypted on-device. Live data are standard Forex quotes, not Pocket Option OTC.", color = PocketColors.TextSecondary, fontSize = 11.sp)
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = PocketColors.CardBackground), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("OTC HISTORICAL DATABASE", color = PocketColors.TextPrimary, fontWeight = FontWeight.Bold)
                Text(state.catalogMessage, color = PocketColors.TextSecondary, fontSize = 12.sp)
                Text(state.catalog?.sourceNotice.orEmpty(), color = PocketColors.Danger, fontSize = 12.sp)
                Text(state.databaseMessage, color = PocketColors.TextSecondary, fontSize = 12.sp)
                Text(
                    "Public repository includes only five sample CSV.GZ files and a manifest, not the full archive. Publisher labels the stream synthetic/research-only. Historical data is not a current OTC quote.",
                    color = PocketColors.TextSecondary,
                    fontSize = 11.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onRefreshCatalog, enabled = !state.catalogLoading, modifier = Modifier.weight(1f)) {
                        Text(if (state.catalogLoading) "LOADING..." else "UPDATE MANIFEST")
                    }
                    OutlinedButton(
                        onClick = onDownloadSample,
                        enabled = selectedSampleAvailable,
                        modifier = Modifier.weight(1f),
                    ) { Text("GET SAMPLE") }
                }
                OutlinedButton(onClick = onImportFile, modifier = Modifier.fillMaxWidth()) {
                    Text("IMPORT CSV / CSV.GZ")
                }
                Text("Parquet import is not available in this Android build. Export Parquet to CSV.GZ first.", color = PocketColors.TextSecondary, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun StatusBadge(status: DataStatus) {
    val color = when (status) {
        DataStatus.ONLINE -> PocketColors.Success
        DataStatus.DELAYED -> Color(0xFFFFC857)
        DataStatus.OFFLINE, DataStatus.DATA_UNAVAILABLE -> PocketColors.Neutral
    }
    Box(
        Modifier.background(color.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
            .border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            when (status) {
                DataStatus.ONLINE -> "ONLINE"
                DataStatus.DELAYED -> "HISTORICAL / DELAYED"
                DataStatus.OFFLINE -> "OFFLINE"
                DataStatus.DATA_UNAVAILABLE -> "DATA UNAVAILABLE"
            },
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun CandleChart(candles: List<Candle>) {
    if (candles.isEmpty()) {
        InfoCard("PRICE CHART", "No candles available.")
        return
    }
    Card(colors = CardDefaults.cardColors(containerColor = PocketColors.CardBackground), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            val visibleCandles = candles.takeLast(120)
            Text("PRICE HISTORY • ${visibleCandles.size} bars", color = PocketColors.TextPrimary, fontWeight = FontWeight.Bold)
            Canvas(Modifier.fillMaxWidth().height(180.dp).padding(top = 8.dp)) {
                val min = visibleCandles.minOf { it.low }
                val max = visibleCandles.maxOf { it.high }
                val range = (max - min).coerceAtLeast(1e-12)
                val step = size.width / visibleCandles.size
                visibleCandles.forEachIndexed { index, candle ->
                    val x = step * (index + 0.5f)
                    fun y(price: Double) = size.height * (1.0 - (price - min) / range).toFloat()
                    val color = if (candle.close >= candle.open) PocketColors.Success else PocketColors.Danger
                    drawLine(color, Offset(x, y(candle.high)), Offset(x, y(candle.low)), 1.2f)
                    drawLine(color, Offset(x, y(candle.open)), Offset(x, y(candle.close)), maxOf(2f, step * 0.55f))
                }
                val closes = visibleCandles.map { it.close }
                val movingAverage = com.signalintelligence.pocketsignalassistant.data.ema(closes, 9)
                val path = Path()
                movingAverage.forEachIndexed { index, price ->
                    val x = step * (index + 0.5f)
                    val y = size.height * (1.0 - (price - min) / range).toFloat()
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, PocketColors.AccentSoft, style = Stroke(width = 2f))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = PocketColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(colors = CardDefaults.cardColors(containerColor = PocketColors.CardBackground), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, color = PocketColors.TextPrimary, fontWeight = FontWeight.Bold)
            Text(body, color = PocketColors.TextSecondary, fontSize = 12.sp)
        }
    }
}

private fun formatTimestamp(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(timestamp))

private fun formatPrice(price: Double): String =
    if (!price.isFinite() || price == 0.0) "—" else "%.5f".format(Locale.US, price)
