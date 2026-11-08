package dev.kosh.financetracker.feature.plus

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class Tier { FREE, PLUS }
enum class EntitlementSource { NONE, PLAY, DEBUG }

/** What was last learned about the user's subscription. Kept on the phone; no Tinward account exists. */
data class EntitlementRecord(val plusAccordingToPlay: Boolean, val lastVerifiedAtMillis: Long, val productId: String? = null)

/**
 * Pure rules for turning the cached record into "Plus or Free right now". Play can only be asked
 * while online, so a Plus user who is offline keeps Plus for a short grace period; after that it
 * is not assumed. Nothing here can lock anyone out of their own data: Free is always fully usable.
 */
object EntitlementPolicy {
    const val GRACE_DAYS = 7L
    private const val DAY = 24L * 60 * 60 * 1000

    fun tier(record: EntitlementRecord?, nowMillis: Long): Tier = when {
        record == null || !record.plusAccordingToPlay -> Tier.FREE
        nowMillis - record.lastVerifiedAtMillis <= GRACE_DAYS * DAY -> Tier.PLUS
        else -> Tier.FREE
    }

    /** Days of offline grace left, or null when not applicable. */
    fun graceDaysLeft(record: EntitlementRecord?, nowMillis: Long): Long? {
        if (record == null || !record.plusAccordingToPlay) return null
        val left = GRACE_DAYS - (nowMillis - record.lastVerifiedAtMillis) / DAY
        return left.takeIf { it >= 0 }
    }
}

/**
 * The single source of truth for "is Plus on". Every gated feature reads [isPlus]. In a debuggable
 * build only, a switch lets testers turn Plus on without a purchase; release builds ignore it.
 */
@Singleton
class Entitlements @Inject constructor(@ApplicationContext private val context: Context) {
    private val prefs = context.getSharedPreferences("tinward_plus", Context.MODE_PRIVATE)
    private val debuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private val _tier = MutableStateFlow(compute())
    val tier: StateFlow<Tier> = _tier.asStateFlow()
    val isPlus: Boolean get() = _tier.value == Tier.PLUS
    val canUseDebugSwitch: Boolean get() = debuggable

    private fun record(): EntitlementRecord? =
        if (!prefs.contains("verified_at")) null
        else EntitlementRecord(prefs.getBoolean("plus", false), prefs.getLong("verified_at", 0L), prefs.getString("product", null))

    val source: EntitlementSource get() = when {
        debuggable && prefs.getBoolean("debug_plus", false) -> EntitlementSource.DEBUG
        EntitlementPolicy.tier(record(), System.currentTimeMillis()) == Tier.PLUS -> EntitlementSource.PLAY
        else -> EntitlementSource.NONE
    }

    fun graceDaysLeft(): Long? = EntitlementPolicy.graceDaysLeft(record(), System.currentTimeMillis())

    private fun compute(): Tier =
        if (debuggable && prefs.getBoolean("debug_plus", false)) Tier.PLUS else EntitlementPolicy.tier(record(), System.currentTimeMillis())

    /** Called with what Google Play reported just now (online). */
    fun onPlayResult(hasActiveSubscription: Boolean, productId: String?) {
        prefs.edit().putBoolean("plus", hasActiveSubscription).putLong("verified_at", System.currentTimeMillis()).putString("product", productId).apply()
        _tier.value = compute()
    }

    fun setDebugPlus(on: Boolean) {
        if (!debuggable) return
        prefs.edit().putBoolean("debug_plus", on).apply()
        _tier.value = compute()
    }

    fun refresh() { _tier.value = compute() }
}
