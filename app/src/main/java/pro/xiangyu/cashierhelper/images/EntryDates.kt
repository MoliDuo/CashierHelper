package pro.xiangyu.cashierhelper.images

import java.time.Instant
import java.time.ZoneId
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** The bill date sent with an upload, always a plain `YYYY-MM-DD` in the phone's time zone. */
object EntryDates {
    private val format = DateTimeFormatter.ISO_LOCAL_DATE

    /** How far back a shared photo's own date is still believed to be the bill date. */
    private const val MAX_AGE_DAYS = 366L

    fun today(now: Instant, zone: ZoneId): String = format.format(now.atZone(zone).toLocalDate())

    /**
     * A shared image may be an old photo, so its capture time is used when it
     * is plausible: not in the future and not older than about a year.
     */
    fun forShared(takenAtMillis: Long?, now: Instant, zone: ZoneId): String {
        if (takenAtMillis == null || takenAtMillis <= 0) return today(now, zone)
        val taken = Instant.ofEpochMilli(takenAtMillis)
        if (taken.isAfter(now)) return today(now, zone)
        val takenDay = taken.atZone(zone).toLocalDate()
        val oldest = now.atZone(zone).toLocalDate().minusDays(MAX_AGE_DAYS)
        return if (takenDay.isBefore(oldest)) today(now, zone) else format.format(takenDay)
    }

    private val exifFormat = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

    /** EXIF stores the local wall-clock time as `yyyy:MM:dd HH:mm:ss`, without a zone. */
    fun parseExifTimestamp(value: String?, zone: ZoneId): Long? {
        if (value.isNullOrBlank()) return null
        return try {
            LocalDateTime.parse(value.trim(), exifFormat).atZone(zone).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
