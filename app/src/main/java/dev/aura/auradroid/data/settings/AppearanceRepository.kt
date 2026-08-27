package dev.aura.auradroid.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appearanceStore by preferencesDataStore(name = "aura_appearance")

/**
 * Appearance choices that live across launches: theme mode and dynamic colour.
 *
 * Unlike [TokenVault], which reads one-shot with `.data.first()`, the theme is
 * consumed reactively by the one place that draws the whole app, so the value
 * is exposed as a hot flow. Default is system-following, the safest choice for
 * a phone that may already be in its owner's preferred mode.
 */
@Singleton
class AppearanceRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    val appearance: Flow<Appearance> = context.appearanceStore.data.map { prefs ->
        Appearance(
            themeMode = ThemeMode.fromName(prefs[KEY_THEME_MODE]),
            dynamicColor = prefs[KEY_DYNAMIC_COLOR] ?: false,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.appearanceStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.appearanceStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    }
}

data class Appearance(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
)

enum class ThemeMode {
    SYSTEM, DARK, LIGHT;

    companion object {
        fun fromName(name: String?): ThemeMode =
            runCatching { valueOf(name!!.uppercase()) }.getOrDefault(SYSTEM)
    }
}
