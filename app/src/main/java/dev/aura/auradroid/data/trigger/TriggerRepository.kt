package dev.aura.auradroid.data.trigger

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.triggerStore by preferencesDataStore(name = "aura_triggers")

/**
 * The rule table behind Aura's own initiative: everything she does without
 * being asked in the moment. JSON in DataStore rather than a Room entity —
 * the schema evolves without a database migration, and the list is tiny.
 */
@Singleton
class TriggerRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson,
) {

    val rules: Flow<List<TriggerRule>> = context.triggerStore.data.map { prefs ->
        decode(prefs[KEY_RULES])
    }

    suspend fun rulesNow(): List<TriggerRule> = rules.first()

    suspend fun ruleById(id: String): TriggerRule? = rulesNow().firstOrNull { it.id == id }

    suspend fun add(rule: TriggerRule) {
        write(rulesNow() + rule)
    }

    suspend fun update(rule: TriggerRule) {
        write(rulesNow().map { if (it.id == rule.id) rule else it })
    }

    suspend fun remove(ruleId: String) {
        write(rulesNow().filterNot { it.id == ruleId })
    }

    private suspend fun write(all: List<TriggerRule>) {
        context.triggerStore.edit { it[KEY_RULES] = gson.toJson(all) }
    }

    private fun decode(raw: String?): List<TriggerRule> =
        raw?.takeIf { it.isNotBlank() }
            ?.let { runCatching { gson.fromJson(it, Array<TriggerRule>::class.java).toList() }.getOrDefault(emptyList()) }
            ?: emptyList()

    private companion object {
        val KEY_RULES = stringPreferencesKey("rules_json")
    }
}
