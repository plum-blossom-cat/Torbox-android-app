package io.github.plumblossomcat.torbox.core.util

import java.net.URLDecoder
import java.security.MessageDigest

object Magnet {
    private val BTIH = Regex("xt=urn:btih:([a-zA-Z0-9]+)", RegexOption.IGNORE_CASE)
    private val BARE_HASH = Regex("^[a-fA-F0-9]{40}$")
    private const val BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun isMagnet(text: String): Boolean = text.trim().startsWith("magnet:?", ignoreCase = true)

    /** Lower-case hex info-hash, from a magnet link or a bare 40-char hash. */
    fun infoHash(text: String): String? {
        val t = text.trim()
        if (BARE_HASH.matches(t)) return t.lowercase()
        val raw = BTIH.find(t)?.groupValues?.get(1) ?: return null
        return when (raw.length) {
            40 -> raw.lowercase()
            32 -> base32ToHex(raw.uppercase())
            else -> null
        }
    }

    fun displayName(text: String): String? {
        val dn = text.trim().substringAfter("?", "").split('&')
            .firstOrNull { it.startsWith("dn=", ignoreCase = true) }
            ?.substring(3) ?: return null
        return runCatching { URLDecoder.decode(dn, "UTF-8") }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    fun fromHash(hash: String, name: String? = null): String =
        buildString {
            append("magnet:?xt=urn:btih:").append(hash)
            if (!name.isNullOrBlank()) append("&dn=").append(java.net.URLEncoder.encode(name, "UTF-8"))
        }

    private fun base32ToHex(input: String): String? {
        var buffer = 0
        var bits = 0
        val out = StringBuilder()
        for (c in input) {
            val v = BASE32.indexOf(c)
            if (v < 0) return null
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out.append(String.format("%02x", (buffer shr bits) and 0xff))
            }
        }
        return out.toString().takeIf { it.length == 40 }
    }

    /** TorBox keys usenet and web downloads by the md5 of their link. */
    fun md5(text: String): String =
        MessageDigest.getInstance("MD5").digest(text.trim().toByteArray()).joinToString("") { "%02x".format(it) }
}
