package dev.aura.auradroid.data.trigger

import java.util.UUID

/**
 * One "when X, run this prompt" rule.
 *
 * Two kinds, because Aura has to wake for both the clock and the world:
 * TIME fires on a repeating interval; NOTIFICATION fires when an app posts
 * a notification matching a package and/or keyword. Serialised as JSON in
 * DataStore, so adding a field is backward-compatible for rules already
 * stored — defaults fill the gap.
 */
data class TriggerRule(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val prompt: String,
    val enabled: Boolean = true,
    val kind: Kind = Kind.TIME,
    /** TIME: fire every N minutes while enabled. */
    val intervalMinutes: Long = DEFAULT_INTERVAL_MINUTES,
    /** NOTIFICATION: empty matches any app. */
    val sourcePackage: String = "",
    /** NOTIFICATION: empty matches any text; otherwise substring, case-insensitive. */
    val keyword: String = "",
) {
    enum class Kind { TIME, NOTIFICATION }

    val isTime: Boolean get() = kind == Kind.TIME

    fun matches(sourcePackage: String, text: String): Boolean {
        if (kind != Kind.NOTIFICATION) return false
        if (this.sourcePackage.isNotBlank() && this.sourcePackage != sourcePackage) return false
        if (keyword.isNotBlank() && !text.contains(keyword, ignoreCase = true)) return false
        return true
    }

    companion object {
        const val DEFAULT_INTERVAL_MINUTES = 30L

        /** Cooldown between event fires of one rule, so a chatty app cannot loop her. */
        const val EVENT_COOLDOWN_MS = 5 * 60_000L
    }
}
