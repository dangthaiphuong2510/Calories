package com.example.calories.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM,
}

enum class UnitSystem {
    METRIC,
    IMPERIAL,
}

enum class AppLanguage {
    ENGLISH,
    VIETNAMESE,
}

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(readThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _unitSystem = MutableStateFlow(readUnitSystem())
    val unitSystem: StateFlow<UnitSystem> = _unitSystem.asStateFlow()

    private val _language = MutableStateFlow(readLanguage())
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
        applyTheme(mode)
    }

    fun setUnitSystem(system: UnitSystem) {
        prefs.edit().putString(KEY_UNIT_SYSTEM, system.name).apply()
        _unitSystem.value = system
    }

    fun setLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.name).apply()
        _language.value = language
        applyLanguage(language)
    }

    fun applyStoredSettings() {
        applyTheme(_themeMode.value)
        applyLanguage(_language.value)
    }

    fun clear() {
        prefs.edit().clear().apply()
        _themeMode.value = ThemeMode.SYSTEM
        _unitSystem.value = UnitSystem.METRIC
        _language.value = AppLanguage.ENGLISH
    }

    private fun applyTheme(mode: ThemeMode) {
        val nightMode = when (mode) {
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    private fun applyLanguage(language: AppLanguage) {
        val tag = when (language) {
            AppLanguage.ENGLISH -> "en"
            AppLanguage.VIETNAMESE -> "vi"
        }
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    private fun readThemeMode(): ThemeMode {
        val raw = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        return runCatching { ThemeMode.valueOf(raw.orEmpty()) }.getOrDefault(ThemeMode.SYSTEM)
    }

    private fun readUnitSystem(): UnitSystem {
        val raw = prefs.getString(KEY_UNIT_SYSTEM, UnitSystem.METRIC.name)
        return runCatching { UnitSystem.valueOf(raw.orEmpty()) }.getOrDefault(UnitSystem.METRIC)
    }

    private fun readLanguage(): AppLanguage {
        val raw = prefs.getString(KEY_LANGUAGE, AppLanguage.ENGLISH.name)
        return runCatching { AppLanguage.valueOf(raw.orEmpty()) }.getOrDefault(AppLanguage.ENGLISH)
    }

    companion object {
        const val PREFS_NAME = "app_preferences"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_UNIT_SYSTEM = "unit_system"
        private const val KEY_LANGUAGE = "language"
    }
}
