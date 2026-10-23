package io.github.plumblossomcat.torbox.core.util

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

/** Human formatting for sizes, rates and times. Uses binary units, labelled the way TorBox labels them. */
object Format {
    private val UNITS = arrayOf("B", "KB", "MB", "GB", "TB", "PB")

    fun bytes(value: Long): String {
        if (value <= 0) return "0 B"
        val exp = (ln(value.toDouble()) / ln(1024.0)).toInt().coerceAtMost(UNITS.lastIndex)
        if (exp == 0) return "$value B"
        val scaled = value / 1024.0.pow(exp)
        val pattern = when {
            scaled >= 100 -> "%.0f %s"
            scaled >= 10 -> "%.1f %s"
            else -> "%.2f %s"
        }
        return String.format(Locale.US, pattern, scaled, UNITS[exp])
    }

    fun speed(bytesPerSecond: Long): String = if (bytesPerSecond <= 0) "—" else "${bytes(bytesPerSecond)}/s"

    fun percent(fraction: Double): String {
        val p = (fraction * 100).coerceIn(0.0, 100.0)
        return if (p > 0 && p < 1) "<1%" else if (p < 100 && p > 99) "99%" else "${p.toInt()}%"
    }

    fun ratio(value: Double): String = String.format(Locale.US, "%.2f", value)

    /** "2h 05m", "45s", "3d 4h". Returns "—" when unknown. */
    fun eta(seconds: Long): String {
        if (seconds <= 0 || seconds >= 8_640_000) return "—"
        val d = seconds / 86_400
        val h = (seconds % 86_400) / 3_600
        val m = (seconds % 3_600) / 60
        val s = seconds % 60
        return when {
            d > 0 -> "${d}d ${h}h"
            h > 0 -> String.format(Locale.US, "%dh %02dm", h, m)
            m > 0 -> String.format(Locale.US, "%dm %02ds", m, s)
            else -> "${s}s"
        }
    }

    /** "just now", "4m ago", "3h ago", "2d ago", then a short date. Future instants read "in 3d". */
    fun relative(instant: Instant?, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String {
        if (instant == null) return "—"
        val delta = Duration.between(instant, now)
        val future = delta.isNegative
        val secs = abs(delta.seconds)
        val core = when {
            secs < 45 -> return if (future) "in a moment" else "just now"
            secs < 3_600 -> "${(secs / 60).coerceAtLeast(1)}m"
            secs < 86_400 -> "${secs / 3_600}h"
            secs < 86_400 * 30 -> "${secs / 86_400}d"
            else -> return date(instant, zone)
        }
        return if (future) "in $core" else "$core ago"
    }

    fun date(instant: Instant?, zone: ZoneId = ZoneId.systemDefault()): String =
        instant?.let { DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US).withZone(zone).format(it) } ?: "—"

    fun dateTime(instant: Instant?, zone: ZoneId = ZoneId.systemDefault()): String =
        instant?.let { DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.US).withZone(zone).format(it) } ?: "—"

    /** "23 Oct, 19:39" this year, "23 Oct 2024, 19:39" otherwise. */
    fun shortDateTime(instant: Instant?, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String {
        instant ?: return "—"
        val sameYear = instant.atZone(zone).year == now.atZone(zone).year
        val pattern = if (sameYear) "d MMM, HH:mm" else "d MMM yyyy, HH:mm"
        return DateTimeFormatter.ofPattern(pattern, Locale.US).withZone(zone).format(instant)
    }

    /** Whole days until [instant], never negative. */
    fun daysUntil(instant: Instant?, now: Instant = Instant.now()): Long? =
        instant?.let { Duration.between(now, it).toDays().coerceAtLeast(0) }

    fun count(value: Long): String = String.format(Locale.US, "%,d", value)

    /** 1.2K, 34M — for headline statistics. */
    fun compact(value: Long): String {
        val v = value.toDouble()
        return when {
            v >= 1e12 -> String.format(Locale.US, "%.1fT", v / 1e12)
            v >= 1e9 -> String.format(Locale.US, "%.1fB", v / 1e9)
            v >= 1e6 -> String.format(Locale.US, "%.1fM", v / 1e6)
            v >= 1e4 -> String.format(Locale.US, "%.1fK", v / 1e3)
            else -> count(value)
        }.replace(".0", "")
    }
}
