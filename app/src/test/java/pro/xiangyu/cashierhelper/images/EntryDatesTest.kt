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
