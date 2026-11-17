# Pocket Signal Assistant

Android Forex analysis app built with Kotlin and Jetpack Compose.

## Data sources

- **Live Forex:** Twelve Data REST API. A user supplies their own API key in Settings; the app stores it encrypted on-device. Standard Forex quotes are not Pocket Option OTC quotes.
- **Historical OTC:** The app reads the public `manifest.json` from [PHCStanton/pocket-option-otc-dataset](https://github.com/PHCStanton/pocket-option-otc-dataset), can download its available CSV.GZ samples, and can stream-import CSV or CSV.GZ tick files into a local SQLite database. Duplicate asset/timestamp pairs are ignored; invalid rows, ordering anomalies, and gaps are reported.

The public repository currently exposes a manifest and five sample CSV.GZ files, not the advertised full multi-asset archive. At the time it was checked, the manifest listed 88 assets and 39,511,615 ticks, while the README described different totals. The dataset publisher also describes the OTC streams as synthetic/research data. These historical files are not current OTC quotes and must not be treated as independently verified broker data.

Parquet import is not supported in the Android app; convert Parquet files to the documented CSV.GZ schema before importing. The manifest and sample downloads require internet access.

## Analysis limitations

The app calculates technical indicators, searches historical analogs, and runs a chronological 70/30 research backtest without using future candles to form earlier predictions. Backtest direction hit rate is not binary-option profitability. Until an independently validated and calibrated model and trustworthy current OTC feed are available, the app displays **INSUFFICIENT DATA / NO SIGNAL** rather than inventing UP/DOWN probabilities or trading signals.

## Build

Use JDK 21 with the checked-in Gradle wrapper:

```sh
./gradlew testDebugUnitTest
./gradlew assembleRelease
```
