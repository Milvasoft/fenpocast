package com.fenpo.cast.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.SecureRandom

/** Picture size Fenpo Cast asks the iPhone for. */
enum class VideoQuality {
    /** 4K with H.265 on a 4K TV that can decode it, otherwise 1080p. */
    Auto,
    P1080,
    P720,
}

/** Görünüm: follow the system, or force light or dark. */
enum class ThemeMode {
    System,
    Light,
    Dark,
}

/**
 * User settings.
 *
 * @property deviceName Name shown on the iPhone; null uses the TV's own name.
 * @property requireCode Every connection needs the four-digit code shown on the TV.
 * @property quality Picture size asked of the iPhone.
 * @property directVideo Videos from apps such as YouTube play on the TV by themselves.
 * @property keepRunning Stays ready in the background and after the TV starts.
 * @property theme Görünüm setting.
 */
data class CastSettings(
    val deviceName: String? = null,
    val requireCode: Boolean = false,
    val quality: VideoQuality = VideoQuality.Auto,
    val directVideo: Boolean = true,
    val keepRunning: Boolean = true,
    val theme: ThemeMode = ThemeMode.System,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Reads and writes [CastSettings] and the receiver's identity. */
class SettingsRepository(
    private val context: Context,
) {
    /** Current settings, updated on every change. */
    val settings: Flow<CastSettings> =
        context.dataStore.data.map { preferences ->
            CastSettings(
                deviceName = preferences[DEVICE_NAME]?.takeIf { it.isNotBlank() },
                requireCode = preferences[REQUIRE_CODE] ?: false,
                quality = enumOrNull<VideoQuality>(preferences[QUALITY]) ?: VideoQuality.Auto,
                directVideo = preferences[DIRECT_VIDEO] ?: true,
                keepRunning = preferences[KEEP_RUNNING] ?: true,
                theme = enumOrNull<ThemeMode>(preferences[THEME]) ?: ThemeMode.System,
            )
        }

    /** Sets the name shown on the iPhone; blank goes back to the TV's own name. */
    suspend fun setDeviceName(name: String) = edit { it[DEVICE_NAME] = name.trim() }

    suspend fun setRequireCode(enabled: Boolean) = edit { it[REQUIRE_CODE] = enabled }

    suspend fun setQuality(quality: VideoQuality) = edit { it[QUALITY] = quality.name }

    suspend fun setDirectVideo(enabled: Boolean) = edit { it[DIRECT_VIDEO] = enabled }

    suspend fun setKeepRunning(enabled: Boolean) = edit { it[KEEP_RUNNING] = enabled }

    suspend fun setTheme(theme: ThemeMode) = edit { it[THEME] = theme.name }

    /**
     * The receiver's identity as a MAC-style address. Android does not expose the real one, so
     * Fenpo Cast makes up a locally administered address once and keeps it: the iPhone uses it
     * to recognise the TV.
     */
    suspend fun deviceId(): String {
        context.dataStore.data
            .first()[DEVICE_ID]
            ?.let { return it }
        val generated = DeviceIdentity.generate(SecureRandom())
        context.dataStore.edit { preferences ->
            if (preferences[DEVICE_ID] == null) preferences[DEVICE_ID] = generated
        }
        return context.dataStore.data.first()[DEVICE_ID] ?: generated
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
        enumValues<T>().firstOrNull { it.name == name }

    private companion object {
        val DEVICE_NAME = stringPreferencesKey("device_name")
        val REQUIRE_CODE = booleanPreferencesKey("require_code")
        val QUALITY = stringPreferencesKey("quality")
        val DIRECT_VIDEO = booleanPreferencesKey("direct_video")
        val KEEP_RUNNING = booleanPreferencesKey("keep_running")
        val THEME = stringPreferencesKey("theme")
        val DEVICE_ID = stringPreferencesKey("device_id")
    }
}
