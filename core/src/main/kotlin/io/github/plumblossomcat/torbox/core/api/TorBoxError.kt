package io.github.plumblossomcat.torbox.core.api

import java.io.IOException

/** Every failure surfaced by [TorBoxClient]. [message] is always safe to show to a person. */
sealed class TorBoxException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** The API answered, but refused the request. [code] is TorBox's machine-readable error, e.g. `BAD_TOKEN`. */
    class Api(
        val code: String?,
        val detail: String?,
        val httpStatus: Int,
    ) : TorBoxException(humanize(code, detail, httpStatus)) {
        val isAuthError: Boolean
            get() = httpStatus == 401 || code in AUTH_CODES
    }

    /** No response at all: offline, DNS failure, timeout. */
    class Network(cause: IOException) : TorBoxException(
        "Can't reach TorBox. Check your connection and try again.",
        cause,
    )

    /** A response arrived but could not be understood. */
    class Malformed(cause: Throwable) : TorBoxException(
        "TorBox sent a response this app doesn't understand yet.",
        cause,
    )

    companion object {
        private val AUTH_CODES = setOf("AUTH_ERROR", "BAD_TOKEN", "NO_AUTH", "INVALID_TOKEN")

        private val FRIENDLY = mapOf(
            "AUTH_ERROR" to "Your API key was rejected. Sign in again with a current key.",
            "BAD_TOKEN" to "Your API key was rejected. Sign in again with a current key.",
            "NO_AUTH" to "You're signed out. Add your API key to continue.",
            "PLAN_RESTRICTED_FEATURE" to "Your plan doesn't include this feature.",
            "ACTIVE_LIMIT" to "You've reached your plan's limit of active downloads. Finish or remove one first.",
            "DOWNLOAD_TOO_LARGE" to "This download is larger than your plan allows.",
            "MONTHLY_LIMIT" to "You've used this month's allowance for this download type.",
            "COOLDOWN_LIMIT" to "You're on a short cooldown. Try again in a few minutes.",
            "DUPLICATE_ITEM" to "This is already in your library.",
            "DOWNLOAD_SERVER_ERROR" to "TorBox's download servers hit a problem. Try again shortly.",
            "DATABASE_ERROR" to "TorBox had a temporary database problem. Try again shortly.",
            "ITEM_NOT_FOUND" to "That item no longer exists on TorBox.",
            "INVALID_OPTION" to "TorBox didn't accept one of the options sent.",
            "BAD_REQUEST" to "TorBox didn't accept that request.",
            "MISSING_REQUIRED_OPTION" to "Something required is missing from the request.",
            "TOO_MUCH_DATA" to "That's more than TorBox accepts in one request.",
            "SERVER_ERROR" to "TorBox hit an internal error. Try again shortly.",
            "RATE_LIMIT" to "Slow down a little — too many requests in a short time.",
        )

        internal fun humanize(code: String?, detail: String?, status: Int): String {
            FRIENDLY[code]?.let { return it }
            val cleanDetail = detail?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", true) }
            if (cleanDetail != null) return cleanDetail
            return when (status) {
                401, 403 -> "Your API key was rejected. Sign in again with a current key."
                404 -> "That item no longer exists on TorBox."
                429 -> "Slow down a little — too many requests in a short time."
                in 500..599 -> "TorBox is having trouble right now (HTTP $status). Try again shortly."
                else -> "Request failed (HTTP $status)."
            }
        }
    }
}
