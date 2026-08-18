package com.recall.android.data

import android.content.Context
import com.recall.android.crypto.VaultCipher
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val Context.recallDataStore by preferencesDataStore("recall_settings")

data class ObservationState(
    val disclosureAccepted: Boolean = false,
    val collectionPaused: Boolean = false,
    val allowedPackages: Set<String> = emptySet(),
    val gatewayUrl: String = "",
    val deviceToken: String = "",
    val dailyReportEnabled: Boolean = true,
    val dailyReportHour: Int = 21,
    val dailyReportMinute: Int = 0,
    val notfBotEnabled: Boolean = true,
    val notfBotAutoSendEnabled: Boolean = false,
)

class ObservationSettings(
    private val context: Context,
    scope: CoroutineScope,
    private val cipher: VaultCipher,
) {
    val state: StateFlow<ObservationState> = context.recallDataStore.data.map { preferences ->
        ObservationState(
            disclosureAccepted = preferences[DISCLOSURE] ?: false,
            collectionPaused = preferences[PAUSED] ?: false,
            allowedPackages = preferences[ALLOWED_PACKAGES] ?: emptySet(),
            gatewayUrl = preferences[GATEWAY_URL].orEmpty(),
            deviceToken = preferences[DEVICE_TOKEN]
                ?.let { runCatching { cipher.decrypt(it) }.getOrNull() }
                .orEmpty(),
            dailyReportEnabled = preferences[DAILY_REPORT_ENABLED] ?: true,
            dailyReportHour = preferences[DAILY_REPORT_HOUR] ?: 21,
            dailyReportMinute = preferences[DAILY_REPORT_MINUTE] ?: 0,
            notfBotEnabled = preferences[NOTF_BOT_ENABLED] ?: true,
            notfBotAutoSendEnabled = preferences[NOTF_BOT_AUTO_SEND_ENABLED] ?: false,
        )
    }.stateIn(scope, SharingStarted.Eagerly, ObservationState())

    suspend fun acceptDisclosure() = context.recallDataStore.edit { it[DISCLOSURE] = true }

    suspend fun setPaused(paused: Boolean) = context.recallDataStore.edit { it[PAUSED] = paused }

    suspend fun setPackageAllowed(packageName: String, allowed: Boolean) {
        context.recallDataStore.edit { preferences ->
            val current = preferences[ALLOWED_PACKAGES].orEmpty().toMutableSet()
            if (allowed) current += packageName else current -= packageName
            preferences[ALLOWED_PACKAGES] = current
        }
    }

    suspend fun setAllowedPackages(packageNames: Set<String>) {
        context.recallDataStore.edit { preferences ->
            preferences[ALLOWED_PACKAGES] = packageNames
        }
    }

    suspend fun setGateway(url: String, token: String) = context.recallDataStore.edit {
        it[GATEWAY_URL] = url.trim().trimEnd('/')
        if (token.isBlank()) it.remove(DEVICE_TOKEN) else it[DEVICE_TOKEN] = cipher.encrypt(token.trim())
    }

    suspend fun setDailyReportEnabled(enabled: Boolean) = context.recallDataStore.edit {
        it[DAILY_REPORT_ENABLED] = enabled
    }

    suspend fun setDailyReportTime(hour: Int, minute: Int) = context.recallDataStore.edit {
        it[DAILY_REPORT_HOUR] = hour.coerceIn(0, 23)
        it[DAILY_REPORT_MINUTE] = minute.coerceIn(0, 59)
    }

    suspend fun setNotfBotEnabled(enabled: Boolean) = context.recallDataStore.edit {
        it[NOTF_BOT_ENABLED] = enabled
    }

    suspend fun setNotfBotAutoSendEnabled(enabled: Boolean) = context.recallDataStore.edit {
        it[NOTF_BOT_AUTO_SEND_ENABLED] = enabled
    }

    companion object {
        private val DISCLOSURE = booleanPreferencesKey("disclosure_accepted")
        private val PAUSED = booleanPreferencesKey("collection_paused")
        private val ALLOWED_PACKAGES = stringSetPreferencesKey("allowed_packages")
        private val GATEWAY_URL = stringPreferencesKey("gateway_url")
        private val DEVICE_TOKEN = stringPreferencesKey("device_token")
        private val DAILY_REPORT_ENABLED = booleanPreferencesKey("daily_report_enabled")
        private val DAILY_REPORT_HOUR = intPreferencesKey("daily_report_hour")
        private val DAILY_REPORT_MINUTE = intPreferencesKey("daily_report_minute")
        private val NOTF_BOT_ENABLED = booleanPreferencesKey("notf_bot_enabled")
        private val NOTF_BOT_AUTO_SEND_ENABLED = booleanPreferencesKey("notf_bot_auto_send_enabled")
    }
}
