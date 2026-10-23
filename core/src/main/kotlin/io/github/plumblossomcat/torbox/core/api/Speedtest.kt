package io.github.plumblossomcat.torbox.core.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/** One CDN file TorBox offers for measuring download speed. */
data class SpeedServer(
    val name: String,
    val region: String?,
    val url: String,
    val closest: Boolean,
)

/**
 * The speedtest endpoint isn't strictly typed, so this accepts either a list of servers or an
 * object keyed by region, and finds the file URL from `url`, `path` or `domain` + `path`.
 */
object SpeedtestParser {
    fun parse(data: JsonElement?): List<SpeedServer> {
        val out = mutableListOf<SpeedServer>()
        fun visit(element: JsonElement?, regionHint: String?) {
            when (element) {
                is JsonArray -> element.forEach { visit(it, regionHint) }
                is JsonObject -> {
                    val server = server(element, regionHint)
                    if (server != null) {
                        out += server
                    } else {
                        element.forEach { (key, value) -> if (value is JsonObject || value is JsonArray) visit(value, key) }
                    }
                }
                else -> Unit
            }
        }
        visit(data, null)
        return out.distinctBy { it.url }.sortedByDescending { it.closest }
    }

    private fun JsonObject.str(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { (this[it] as? JsonPrimitive)?.contentOrNull?.takeIf { v -> v.isNotBlank() } }

    private fun server(obj: JsonObject, regionHint: String?): SpeedServer? {
        val direct = obj.str("url", "link", "path")?.takeIf { it.startsWith("http") }
        val domain = obj.str("domain", "host", "hostname")
        val path = obj.str("path", "file")
        val url = direct
            ?: if (domain != null && path != null) "https://" + domain.removePrefix("https://").trimEnd('/') + "/" + path.trimStart('/') else null
            ?: obj.values.firstNotNullOfOrNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf { v -> v.startsWith("http") } }
            ?: return null
        val region = obj.str("region", "location", "country") ?: regionHint
        val name = obj.str("name", "server", "server_name") ?: region ?: domain ?: "TorBox CDN"
        val closest = listOf("closest", "user_closest", "is_closest").any { (obj[it] as? JsonPrimitive)?.booleanOrNull == true }
        return SpeedServer(name, region, url, closest)
    }
}
