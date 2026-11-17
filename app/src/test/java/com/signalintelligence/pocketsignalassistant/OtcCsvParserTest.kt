package com.signalintelligence.pocketsignalassistant

import com.signalintelligence.pocketsignalassistant.data.OtcCsvHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class OtcCsvParserTest {
    @Test
    fun parsesPublishedSecondPrecisionCsvRows() {
        val header = OtcCsvHeader.fromHeader("timestamp,datetime_utc,asset,price,session_id")

        val tick = header?.parse("1774992443.935,2026-03-31 21:27:23.934,EURUSD_otc,1.1582,1")

        assertNotNull(tick)
        assertEquals("EURUSD_otc", tick?.asset)
        assertEquals(1_158_200L, (tick?.price?.times(1_000_000.0))?.toLong())
        assertEquals(1_774_992_443_935L, tick?.timeMs)
        assertEquals(1L, tick?.sessionId)
    }

    @Test
    fun supportsMillisecondTimestampsAndSelectedAssetForSingleAssetCsv() {
        val header = OtcCsvHeader.fromHeader("timestamp,price")

        val tick = header?.parse("1774992443935,1.1582", selectedAsset = "EURUSD_otc")

        assertEquals(1_774_992_443_935L, tick?.timeMs)
        assertEquals("EURUSD_otc", tick?.asset)
    }

    @Test
    fun rejectsInvalidTimestampsPricesAndHeaders() {
        val header = OtcCsvHeader.fromHeader("timestamp,asset,price")

        assertNull(header?.parse("not-a-time,EURUSD_otc,1.2"))
        assertNull(header?.parse("1774992443.935,EURUSD_otc,NaN"))
        assertNull(header?.parse("1774992443.935,,1.2"))
        assertNull(OtcCsvHeader.fromHeader("datetime_utc,asset,price"))
    }
}
