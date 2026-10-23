package io.github.plumblossomcat.torbox.core

import com.google.common.truth.Truth.assertThat
import io.github.plumblossomcat.torbox.core.model.TransferMapper
import io.github.plumblossomcat.torbox.core.model.TransferState
import io.github.plumblossomcat.torbox.core.util.Format
import io.github.plumblossomcat.torbox.core.util.Magnet
import io.github.plumblossomcat.torbox.core.util.Timestamps
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class UtilTest {
    @Test
    fun formatsBytes() {
        assertThat(Format.bytes(0)).isEqualTo("0 B")
        assertThat(Format.bytes(512)).isEqualTo("512 B")
        assertThat(Format.bytes(1536)).isEqualTo("1.50 KB")
        assertThat(Format.bytes(276_445_467)).isEqualTo("264 MB")
        assertThat(Format.bytes(5L * 1024 * 1024 * 1024)).isEqualTo("5.00 GB")
    }

    @Test
    fun formatsEtaAndPercent() {
        assertThat(Format.eta(0)).isEqualTo("—")
        assertThat(Format.eta(45)).isEqualTo("45s")
        assertThat(Format.eta(125)).isEqualTo("2m 05s")
        assertThat(Format.eta(7_500)).isEqualTo("2h 05m")
        assertThat(Format.eta(8_640_000)).isEqualTo("—")
        assertThat(Format.percent(0.004)).isEqualTo("<1%")
        assertThat(Format.percent(0.995)).isEqualTo("99%")
        assertThat(Format.percent(1.0)).isEqualTo("100%")
    }

    @Test
    fun formatsRelativeTime() {
        val now = Instant.parse("2025-01-10T12:00:00Z")
        assertThat(Format.relative(now.minusSeconds(10), now)).isEqualTo("just now")
        assertThat(Format.relative(now.minusSeconds(600), now)).isEqualTo("10m ago")
        assertThat(Format.relative(now.minusSeconds(7_200), now)).isEqualTo("2h ago")
        assertThat(Format.relative(now.plusSeconds(3 * 86_400), now)).isEqualTo("in 3d")
        assertThat(Format.relative(Instant.parse("2024-06-01T00:00:00Z"), now, ZoneOffset.UTC)).isEqualTo("1 Jun 2024")
        assertThat(Format.compact(1_234_567)).isEqualTo("1.2M")
        assertThat(Format.compact(950)).isEqualTo("950")
        assertThat(Format.shortDateTime(Instant.parse("2025-01-09T08:05:00Z"), now, ZoneOffset.UTC)).isEqualTo("9 Jan, 08:05")
        assertThat(Format.shortDateTime(Instant.parse("2024-12-31T23:00:00Z"), now, ZoneOffset.UTC)).isEqualTo("31 Dec 2024, 23:00")
    }

    @Test
    fun parsesTimestamps() {
        assertThat(Timestamps.parse("2025-03-01T10:15:30.123456Z")).isEqualTo(Instant.parse("2025-03-01T10:15:30.123456Z"))
        assertThat(Timestamps.parse("2025-03-01T10:15:30+02:00")).isEqualTo(Instant.parse("2025-03-01T08:15:30Z"))
        assertThat(Timestamps.parse("2025-03-01 10:20:00")).isEqualTo(Instant.parse("2025-03-01T10:20:00Z"))
        assertThat(Timestamps.parse("2025-03-01T10:20:00")).isEqualTo(Instant.parse("2025-03-01T10:20:00Z"))
        assertThat(Timestamps.parse(null)).isNull()
        assertThat(Timestamps.parse("garbage")).isNull()
    }

    @Test
    fun parsesMagnets() {
        val m = "magnet:?xt=urn:btih:DD8255ECDC7CA55FB0BBF81323D87062DB1F6D1C&dn=Big+Buck+Bunny&tr=udp%3A%2F%2Fx"
        assertThat(Magnet.isMagnet(m)).isTrue()
        assertThat(Magnet.infoHash(m)).isEqualTo("dd8255ecdc7ca55fb0bbf81323d87062db1f6d1c")
        assertThat(Magnet.displayName(m)).isEqualTo("Big Buck Bunny")
        // base32 form of the same hash
        assertThat(Magnet.infoHash("magnet:?xt=urn:btih:3WBFL3G4PSSV7MF37AJSHWDQMLNR63I4")).isEqualTo("dd8255ecdc7ca55fb0bbf81323d87062db1f6d1c")
        assertThat(Magnet.infoHash("not a magnet")).isNull()
    }

    @Test
    fun mapsStates() {
        fun s(raw: String, finished: Boolean = false, present: Boolean = false, active: Boolean = true, p: Double = 0.0) =
            TransferMapper.state(raw, finished, present, active, p)
        assertThat(s("downloading")).isEqualTo(TransferState.DOWNLOADING)
        assertThat(s("metaDL")).isEqualTo(TransferState.PREPARING)
        assertThat(s("checkingResumeData")).isEqualTo(TransferState.PREPARING)
        assertThat(s("stalled (no seeds)")).isEqualTo(TransferState.STALLED)
        assertThat(s("stalledUP", true, true)).isEqualTo(TransferState.SEEDING)
        assertThat(s("pausedUP", true, true)).isEqualTo(TransferState.READY)
        assertThat(s("paused")).isEqualTo(TransferState.PAUSED)
        assertThat(s("uploading", active = false)).isEqualTo(TransferState.READY)
        assertThat(s("completed", true, true, false)).isEqualTo(TransferState.READY)
        assertThat(s("cached", true, true, false)).isEqualTo(TransferState.READY)
        assertThat(s("Failed (Missing articles)")).isEqualTo(TransferState.FAILED)
        assertThat(s("error")).isEqualTo(TransferState.FAILED)
        assertThat(s("queued")).isEqualTo(TransferState.QUEUED)
        assertThat(s("Repairing")).isEqualTo(TransferState.PREPARING)
    }
}

class SpeedtestParserTest {
    private fun parse(json: String) =
        io.github.plumblossomcat.torbox.core.api.SpeedtestParser.parse(io.github.plumblossomcat.torbox.core.api.TorBoxJson.parseToJsonElement(json))

    @Test
    fun parsesListWithDomainAndPath() {
        val servers = parse("""[{"region":"eu","name":"Amsterdam","domain":"ams.cdn.example","path":"/speedtest/100mb.bin","closest":false},{"region":"us","name":"Dallas","url":"https://dal.cdn.example/100mb.bin","closest":true}]""")
        assertThat(servers.map { it.name }).containsExactly("Dallas", "Amsterdam").inOrder()
        assertThat(servers[1].url).isEqualTo("https://ams.cdn.example/speedtest/100mb.bin")
    }

    @Test
    fun parsesObjectKeyedByRegion() {
        val servers = parse("""{"europe":[{"server":"fra-1","link":"https://fra.example/f.bin"}],"asia":{"server":"sgp-1","url":"https://sgp.example/f.bin"}}""")
        assertThat(servers.map { it.region }).containsExactly("europe", "asia")
        assertThat(servers.map { it.name }).containsExactly("fra-1", "sgp-1")
    }
}
