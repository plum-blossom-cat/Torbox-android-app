package io.github.plumblossomcat.torbox.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.plumblossomcat.torbox.core.api.IntegrationProvider
import io.github.plumblossomcat.torbox.core.api.SeedPreference
import io.github.plumblossomcat.torbox.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.store: DataStore<Preferences> by preferencesDataStore(name = "torbox")

/** What happens when a file is tapped. */
enum class FileAction(val label: String) {
    ASK("Ask every time"),
    SAVE("Save to device"),
    OPEN("Open in another app"),
    COPY("Copy link"),
}

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val seed: SeedPreference = SeedPreference.AUTO,
    val allowZip: Boolean = true,
    val fileAction: FileAction = FileAction.ASK,
    val refreshSeconds: Int = 5,
    val notifyOnComplete: Boolean = false,
    val recentSearches: List<String> = emptyList(),
)

class SettingsStore(private val context: Context, private val secrets: SecretBox) {
    private object Keys {
        val apiKey = stringPreferencesKey("api_key_sealed")
        val theme = stringPreferencesKey("theme")
        val seed = intPreferencesKey("seed")
        val allowZip = booleanPreferencesKey("allow_zip")
        val fileAction = stringPreferencesKey("file_action")
        val refresh = intPreferencesKey("refresh_seconds")
        val notify = booleanPreferencesKey("notify_complete")
        val searches = stringPreferencesKey("recent_searches")
        val knownDone = stringSetPreferencesKey("known_done")
        val lastSeenNotification = androidx.datastore.preferences.core.longPreferencesKey("last_seen_notification")
        fun integration(p: IntegrationProvider) = stringPreferencesKey("integration_${p.name.lowercase()}")
    }

    val settings: Flow<AppSettings> = context.store.data.map { p ->
        AppSettings(
            theme = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            seed = SeedPreference.from((p[Keys.seed] ?: 1).toLong()),
            allowZip = p[Keys.allowZip] ?: true,
            fileAction = p[Keys.fileAction]?.let { runCatching { FileAction.valueOf(it) }.getOrNull() } ?: FileAction.ASK,
            refreshSeconds = p[Keys.refresh] ?: 5,
            notifyOnComplete = p[Keys.notify] ?: false,
            recentSearches = p[Keys.searches]?.split('\u001F')?.filter { it.isNotBlank() } ?: emptyList(),
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun apiKey(): String? = context.store.data.first()[Keys.apiKey]?.let(secrets::open)

    suspend fun setApiKey(key: String?) {
        context.store.edit { p ->
            if (key == null) p.remove(Keys.apiKey) else p[Keys.apiKey] = secrets.seal(key)
        }
    }

    suspend fun setTheme(mode: ThemeMode) = context.store.edit { it[Keys.theme] = mode.name }
    suspend fun setSeed(seed: SeedPreference) = context.store.edit { it[Keys.seed] = seed.apiValue }
    suspend fun setAllowZip(value: Boolean) = context.store.edit { it[Keys.allowZip] = value }
    suspend fun setFileAction(action: FileAction) = context.store.edit { it[Keys.fileAction] = action.name }
    suspend fun setRefreshSeconds(seconds: Int) = context.store.edit { it[Keys.refresh] = seconds }
    suspend fun setNotifyOnComplete(value: Boolean) = context.store.edit { it[Keys.notify] = value }

    suspend fun rememberSearch(query: String) = context.store.edit { p ->
        val existing = p[Keys.searches]?.split('\u001F')?.filter { it.isNotBlank() } ?: emptyList()
        val updated = (listOf(query.trim()) + existing.filterNot { it.equals(query.trim(), ignoreCase = true) }).take(8)
        p[Keys.searches] = updated.joinToString("\u001F")
    }

    suspend fun clearSearches() = context.store.edit { it.remove(Keys.searches) }

    fun integrationTokens(): Flow<Map<IntegrationProvider, String>> = context.store.data.map { p ->
        IntegrationProvider.entries.mapNotNull { provider ->
            p[Keys.integration(provider)]?.let(secrets::open)?.takeIf { it.isNotBlank() }?.let { provider to it }
        }.toMap()
    }

    suspend fun setIntegrationToken(provider: IntegrationProvider, token: String?) = context.store.edit { p ->
        if (token.isNullOrBlank()) p.remove(Keys.integration(provider)) else p[Keys.integration(provider)] = secrets.seal(token.trim())
    }

    /** Keys of downloads already known to be finished; used to notify only about new completions. */
    suspend fun knownDone(): Set<String>? = context.store.data.first()[Keys.knownDone]

    suspend fun setKnownDone(keys: Set<String>) = context.store.edit { it[Keys.knownDone] = keys }

    suspend fun lastSeenNotification(): Long = context.store.data.first()[Keys.lastSeenNotification] ?: 0L

    suspend fun setLastSeenNotification(id: Long) = context.store.edit { it[Keys.lastSeenNotification] = id }

    /** Signing out forgets everything tied to the account but keeps appearance preferences. */
    suspend fun clearAccount() = context.store.edit { p ->
        p.remove(Keys.apiKey)
        p.remove(Keys.knownDone)
        p.remove(Keys.lastSeenNotification)
        p.remove(Keys.searches)
        IntegrationProvider.entries.forEach { p.remove(Keys.integration(it)) }
    }
}
