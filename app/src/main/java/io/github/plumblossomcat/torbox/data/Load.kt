package io.github.plumblossomcat.torbox.data

/** Minimal async state for screens that load one thing. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>

    val valueOrNull: T? get() = (this as? Ready<T>)?.value
}

fun Throwable.friendly(): String = message?.takeIf { it.isNotBlank() } ?: "Something went wrong. Try again."
