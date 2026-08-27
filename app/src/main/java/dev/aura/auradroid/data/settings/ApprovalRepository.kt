package dev.aura.auradroid.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.approvalStore by preferencesDataStore(name = "aura_approval")

/**
 * The one switch that decides whether the agent asks before it acts.
 *
 * Lives with the app, not with a conversation: turning "approve all" on in
 * Settings has to still be in force next week, or it is not a setting at all.
 * A per-chat "don't ask again" grant sits on top of this as a looser, shorter
 * override; the effective answer is either.
 */
@Singleton
class ApprovalRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** True means every tool runs without asking, in any mode. Default asks. */
    val autoApproveAll: Flow<Boolean> = context.approvalStore.data.map { prefs ->
        prefs[KEY_AUTO_APPROVE_ALL] ?: false
    }

    suspend fun setAutoApproveAll(enabled: Boolean) {
        context.approvalStore.edit { it[KEY_AUTO_APPROVE_ALL] = enabled }
    }

    private companion object {
        val KEY_AUTO_APPROVE_ALL = booleanPreferencesKey("auto_approve_all")
    }
}
