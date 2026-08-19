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
    val macHistoryEnabled: Boolean = false,
    val macHistoryUrl: String = "",
    val macHistoryToken: String = "",
    val macHistoryTlsPin: String = "",
    val macHistoryDeviceId: String = "",
    val macHistoryDeviceName: String = "",
    val macHistoryCursor: String = "",
    val macHistoryLastSyncAt: Long = 0L,
    val macHistoryLastError: String = "",
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
            macHistoryEnabled = preferences[MAC_HISTORY_ENABLED] ?: false,
            macHistoryUrl = preferences[MAC_HISTORY_URL].orEmpty(),
            macHistoryToken = preferences[MAC_HISTORY_TOKEN]
                ?.let { runCatching { cipher.decrypt(it) }.getOrNull() }
                .orEmpty(),
            macHistoryTlsPin = preferences[MAC_HISTORY_TLS_PIN].orEmpty(),
            macHistoryDeviceId = preferences[MAC_HISTORY_DEVICE_ID].orEmpty(),
            macHistoryDeviceName = preferences[MAC_HISTORY_DEVICE_NAME].orEmpty(),
            macHistoryCursor = preferences[MAC_HISTORY_CURSOR].orEmpty(),
            macHistoryLastSyncAt = preferences[MAC_HISTORY_LAST_SYNC_AT]?.toLongOrNull() ?: 0L,
            macHistoryLastError = preferences[MAC_HISTORY_LAST_ERROR].orEmpty(),
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

    suspend fun setMacHistoryPairing(pairing: MacHistoryPairing) = context.recallDataStore.edit {
        it[MAC_HISTORY_ENABLED] = true
        it[MAC_HISTORY_URL] = pairing.url
        it[MAC_HISTORY_TOKEN] = cipher.encrypt(pairing.token)
        it[MAC_HISTORY_TLS_PIN] = pairing.tlsPin
        it[MAC_HISTORY_DEVICE_ID] = pairing.deviceId
        it[MAC_HISTORY_DEVICE_NAME] = pairing.deviceName
        it[MAC_HISTORY_CURSOR] = ""
        it[MAC_HISTORY_LAST_SYNC_AT] = "0"
        it.remove(MAC_HISTORY_LAST_ERROR)
    }

    suspend fun setMacHistorySyncState(cursor: String, lastSyncAt: Long, error: String?) =
        context.recallDataStore.edit {
            it[MAC_HISTORY_CURSOR] = cursor
            it[MAC_HISTORY_LAST_SYNC_AT] = lastSyncAt.toString()
            if (error.isNullOrBlank()) it.remove(MAC_HISTORY_LAST_ERROR)
            else it[MAC_HISTORY_LAST_ERROR] = error.take(240)
        }

    suspend fun clearMacHistory() = context.recallDataStore.edit {
        it.remove(MAC_HISTORY_ENABLED)
        it.remove(MAC_HISTORY_URL)
        it.remove(MAC_HISTORY_TOKEN)
        it.remove(MAC_HISTORY_TLS_PIN)
        it.remove(MAC_HISTORY_DEVICE_ID)
        it.remove(MAC_HISTORY_DEVICE_NAME)
        it.remove(MAC_HISTORY_CURSOR)
        it.remove(MAC_HISTORY_LAST_SYNC_AT)
        it.remove(MAC_HISTORY_LAST_ERROR)
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
        private val MAC_HISTORY_ENABLED = booleanPreferencesKey("mac_history_enabled")
        private val MAC_HISTORY_URL = stringPreferencesKey("mac_history_url")
        private val MAC_HISTORY_TOKEN = stringPreferencesKey("mac_history_token")
        private val MAC_HISTORY_TLS_PIN = stringPreferencesKey("mac_history_tls_pin")
        private val MAC_HISTORY_DEVICE_ID = stringPreferencesKey("mac_history_device_id")
        private val MAC_HISTORY_DEVICE_NAME = stringPreferencesKey("mac_history_device_name")
        private val MAC_HISTORY_CURSOR = stringPreferencesKey("mac_history_cursor")
        private val MAC_HISTORY_LAST_SYNC_AT = stringPreferencesKey("mac_history_last_sync_at")
        private val MAC_HISTORY_LAST_ERROR = stringPreferencesKey("mac_history_last_error")
    }
}
