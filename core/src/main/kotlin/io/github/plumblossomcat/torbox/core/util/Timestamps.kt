package io.github.plumblossomcat.torbox.core.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object Timestamps {
    private val SPACE_SEPARATED: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSSSSS][.SSS]")

    /** Parses the handful of timestamp shapes TorBox emits. Zone-less values are treated as UTC. */
    fun parse(value: String?): Instant? {
        val v = value?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", true) } ?: return null
        v.toLongOrNull()?.let { epoch -> return if (epoch > 10_000_000_000L) Instant.ofEpochMilli(epoch) else Instant.ofEpochSecond(epoch) }
        return try {
            OffsetDateTime.parse(v).toInstant()
        } catch (_: DateTimeParseException) {
            try {
                Instant.parse(v)
            } catch (_: DateTimeParseException) {
                try {
                    LocalDateTime.parse(v).toInstant(ZoneOffset.UTC)
                } catch (_: DateTimeParseException) {
                    try {
                        LocalDateTime.parse(v, SPACE_SEPARATED).toInstant(ZoneOffset.UTC)
                    } catch (_: DateTimeParseException) {
                        null
                    }
                }
            }
        }
    }
}
