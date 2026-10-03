package pro.xiangyu.cashierhelper.images

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class EntryDatesTest {
    private val shanghai = ZoneId.of("Asia/Shanghai")
    private val now = Instant.parse("2026-10-03T20:30:00Z") // already 2026-10-04 in Shanghai

    @Test
    fun `today uses the phone's time zone`() {
        assertEquals("2026-10-04", EntryDates.today(now, shanghai))
        assertEquals("2026-10-03", EntryDates.today(now, ZoneId.of("UTC")))
    }

    @Test
    fun `a recent photo keeps its own date`() {
        val taken = Instant.parse("2026-09-20T03:00:00Z").toEpochMilli()
        assertEquals("2026-09-20", EntryDates.forShared(taken, now, shanghai))
    }

    @Test
    fun `missing, future and very old dates fall back to today`() {
        assertEquals("2026-10-04", EntryDates.forShared(null, now, shanghai))
        assertEquals("2026-10-04", EntryDates.forShared(0, now, shanghai))
        assertEquals("2026-10-04", EntryDates.forShared(now.plusSeconds(3600).toEpochMilli(), now, shanghai))
        assertEquals("2026-10-04", EntryDates.forShared(Instant.parse("2020-01-01T00:00:00Z").toEpochMilli(), now, shanghai))
    }
}

class ExifTimestampTest {
    private val zone = java.time.ZoneId.of("Asia/Shanghai")

    @org.junit.Test
    fun `an exif time is read as local wall clock time`() {
        val millis = EntryDates.parseExifTimestamp("2025:10:08 23:30:00", zone)

        org.junit.Assert.assertEquals(
            "2025-10-08",
            EntryDates.forShared(millis, java.time.Instant.parse("2025-10-09T04:00:00Z"), zone),
        )
        org.junit.Assert.assertEquals(java.time.Instant.parse("2025-10-08T15:30:00Z").toEpochMilli(), millis)
    }

    @org.junit.Test
    fun `garbage or missing exif values give nothing`() {
        org.junit.Assert.assertNull(EntryDates.parseExifTimestamp(null, zone))
        org.junit.Assert.assertNull(EntryDates.parseExifTimestamp("", zone))
        org.junit.Assert.assertNull(EntryDates.parseExifTimestamp("0000:00:00 00:00:00", zone))
        org.junit.Assert.assertNull(EntryDates.parseExifTimestamp("yesterday", zone))
    }
}
