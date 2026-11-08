package dev.kosh.financetracker.security

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Tester conveniences that exist only in debuggable builds. Release builds always report the defaults. */
@Singleton
class DebugOptions @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("tinward_debug", Context.MODE_PRIVATE)
    val enabled: Boolean = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private val _fontScale = MutableStateFlow(if (enabled) prefs.getFloat("font_scale", 1f) else 1f)
    /** Extra multiplier on top of the system font size, to test large-text layouts. */
    val fontScale: StateFlow<Float> = _fontScale.asStateFlow()

    fun setFontScale(value: Float) {
        if (!enabled) return
        prefs.edit().putFloat("font_scale", value).apply()
        _fontScale.value = value
    }
}
