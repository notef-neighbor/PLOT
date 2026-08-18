package com.recall.android

import android.app.Application
import android.content.Intent
import android.content.ComponentName
import android.Manifest
import android.content.pm.PackageManager
import android.provider.Settings
import android.content.pm.ApplicationInfo
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.recall.android.codex.CodexRuntimeService
import com.recall.android.codex.CodexRuntimeState
import com.recall.android.data.HistoryMemory
import com.recall.android.data.HistoryEvidenceFormatter
import com.recall.android.data.ObservationState
import com.recall.android.data.RecentActivity
import com.recall.android.worker.DailyReportScheduler
import com.recall.android.worker.CalendarSyncScheduler
import com.recall.android.capture.RecallNotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class InstalledApplication(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val isProtected: Boolean,
)

data class MainUiState(
    val memories: List<HistoryMemory> = emptyList(),
    val todayEventCount: Int = 0,
    val recentActivities: List<RecentActivity> = emptyList(),
    val settings: ObservationState = ObservationState(),
    val installedApplications: List<InstalledApplication> = emptyList(),
    val query: String = "",
    val answer: String? = null,
    val answerSources: List<HistoryMemory> = emptyList(),
    val conversation: List<HistoryConversationMessage> = emptyList(),
    val isAnswering: Boolean = false,
    val codex: CodexRuntimeState = CodexRuntimeState(),
    val message: String? = null,
    val notificationHistoryEnabled: Boolean = false,
    val calendarSyncEnabled: Boolean = false,
)

data class HistoryConversationMessage(
    val isUser: Boolean,
    val text: String,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as RecallApplication).container
    private val apps = MutableStateFlow<List<InstalledApplication>>(emptyList())
    private val interaction = MutableStateFlow(InteractionState())

    private val historyState = combine(
        container.historyRepository.observeMemories(),
        container.historyRepository.observeTodayEventCount(),
        container.historyRepository.observeRecentActivities(),
    ) { memories, count, recentActivities ->
        HistoryState(memories, count, recentActivities)
    }

    val uiState: StateFlow<MainUiState> = combine(
        historyState,
        container.settings.state,
        apps,
        interaction,
        container.codexRuntime.state,
    ) { history, settings, installedApps, interactionState, codexState ->
        MainUiState(
            memories = history.memories,
            todayEventCount = history.todayEventCount,
            recentActivities = history.recentActivities,
            settings = settings,
            installedApplications = installedApps,
            query = interactionState.query,
            answer = interactionState.answer,
            answerSources = interactionState.sources,
            conversation = interactionState.conversation,
            isAnswering = interactionState.isAnswering,
            codex = codexState,
            message = interactionState.message,
            notificationHistoryEnabled = interactionState.notificationHistoryEnabled,
            calendarSyncEnabled = interactionState.calendarSyncEnabled,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    private data class HistoryState(
        val memories: List<HistoryMemory>,
        val todayEventCount: Int,
        val recentActivities: List<RecentActivity>,
    )

    init {
        loadApplications()
        refreshCapabilities()
        if (java.io.File(application.filesDir, "codex-home/auth.json").isFile) {
            CodexRuntimeService.start(application)
        }
    }

    fun acceptDisclosure() = viewModelScope.launch {
        val installed = apps.value.ifEmpty { discoverApplications() }
        container.settings.setAllowedPackages(installed.filterNot { it.isProtected }.map { it.packageName }.toSet())
        container.settings.acceptDisclosure()
    }

    fun setPaused(paused: Boolean) = viewModelScope.launch {
        container.settings.setPaused(paused)
    }

    fun setNotfBotEnabled(enabled: Boolean) = viewModelScope.launch {
        container.settings.setNotfBotEnabled(enabled)
    }

    fun setNotfBotAutoSendEnabled(enabled: Boolean) = viewModelScope.launch {
        container.settings.setNotfBotAutoSendEnabled(enabled)
    }

    fun setPackageAllowed(packageName: String, allowed: Boolean) = viewModelScope.launch {
        container.settings.setPackageAllowed(packageName, allowed)
    }

    fun setAllPackagesAllowed(allowed: Boolean) = viewModelScope.launch {
        val packageNames = if (allowed) {
            apps.value.filterNot { it.isProtected }.map { it.packageName }.toSet()
        } else {
            emptySet()
        }
        container.settings.setAllowedPackages(packageNames)
        interaction.value = interaction.value.copy(
            message = if (allowed) text(R.string.message_apps_enabled, packageNames.size) else text(R.string.message_apps_disabled),
        )
    }

    fun setQuery(value: String) {
        interaction.value = interaction.value.copy(query = value)
    }

    fun ask() {
        val question = interaction.value.query.trim()
        if (question.isBlank()) return
        viewModelScope.launch {
            val previousConversation = interaction.value.conversation
            val previousContext = interaction.value.contextSources
            interaction.value = interaction.value.copy(isAnswering = true, message = null)
            val settings = container.settings.state.value
            runCatching {
                val freshMatches = container.historyRepository.search(question)
                val matches = if (previousConversation.isEmpty()) {
                    freshMatches.take(30)
                } else {
                    (freshMatches + previousContext.take(8))
                        .distinctBy(HistoryMemory::id)
                        .take(12)
                }
                val modelQuestion = HistoryConversationPrompt.build(previousConversation, question)
                val answer = when {
                    container.codexRuntime.state.value.isReady -> {
                        CodexRuntimeService.start(getApplication())
                        container.codexRuntime.askHistory(modelQuestion, matches)
                    }
                    settings.gatewayUrl.isNotBlank() && settings.deviceToken.isNotBlank() ->
                        container.gatewayClient.ask(settings.gatewayUrl, settings.deviceToken, modelQuestion, matches)
                    else -> localAnswer(matches)
                }
                interaction.value = interaction.value.copy(
                    query = "",
                    isAnswering = false,
                    answer = answer,
                    sources = matches.take(5),
                    contextSources = matches,
                    conversation = previousConversation + listOf(
                        HistoryConversationMessage(isUser = true, text = question),
                        HistoryConversationMessage(isUser = false, text = answer),
                    ),
                )
            }.onFailure { error ->
                interaction.value = interaction.value.copy(
                    isAnswering = false,
                    message = error.message ?: text(R.string.message_ask_failed),
                )
            }
        }
    }

    fun newHistoryConversation() {
        interaction.value = InteractionState(
            notificationHistoryEnabled = interaction.value.notificationHistoryEnabled,
            calendarSyncEnabled = interaction.value.calendarSyncEnabled,
        )
    }

    fun saveGateway(url: String, token: String) = viewModelScope.launch {
        val existing = container.settings.state.value
        val effectiveToken = token.ifBlank { existing.deviceToken }
        if (url.isNotBlank() && !url.startsWith("https://")) {
            interaction.value = interaction.value.copy(message = text(R.string.message_gateway_https))
            return@launch
        }
        container.settings.setGateway(url, effectiveToken)
        interaction.value = interaction.value.copy(message = text(R.string.message_gateway_saved))
    }

    fun startCodex() {
        CodexRuntimeService.start(getApplication())
    }

    fun beginCodexLogin(openAuthentication: (String) -> Unit) = viewModelScope.launch {
        interaction.value = interaction.value.copy(message = text(R.string.message_codex_starting))
        CodexRuntimeService.start(getApplication())
        runCatching { container.codexRuntime.beginBrowserLogin() }
            .onSuccess { login ->
                interaction.value = interaction.value.copy(message = text(R.string.message_finish_chatgpt_auth))
                openAuthentication(login.authUrl)
            }
            .onFailure { interaction.value = interaction.value.copy(message = it.message ?: text(R.string.message_codex_connect_failed)) }
    }

    fun beginCodexDeviceLogin() = viewModelScope.launch {
        interaction.value = interaction.value.copy(message = text(R.string.message_requesting_code))
        CodexRuntimeService.start(getApplication())
        runCatching { container.codexRuntime.beginDeviceLogin() }
            .onSuccess { interaction.value = interaction.value.copy(message = text(R.string.message_finish_code_auth)) }
            .onFailure { interaction.value = interaction.value.copy(message = it.message ?: text(R.string.message_code_failed)) }
    }

    fun refreshCodexAccount() = viewModelScope.launch {
        runCatching { container.codexRuntime.refreshAccount() }
            .onFailure { interaction.value = interaction.value.copy(message = it.message ?: text(R.string.message_auth_check_failed)) }
    }

    fun disconnectCodex() = viewModelScope.launch {
        runCatching { container.codexRuntime.logout() }
            .onSuccess { interaction.value = interaction.value.copy(message = text(R.string.message_chatgpt_disconnected)) }
            .onFailure { interaction.value = interaction.value.copy(message = it.message ?: text(R.string.message_disconnect_failed)) }
    }

    fun setDailyReportEnabled(enabled: Boolean) = viewModelScope.launch {
        container.settings.setDailyReportEnabled(enabled)
        interaction.value = interaction.value.copy(
            message = if (enabled) text(R.string.message_daily_enabled) else text(R.string.message_daily_disabled),
        )
    }

    fun setDailyReportTime(hour: Int, minute: Int) = viewModelScope.launch {
        container.settings.setDailyReportTime(hour, minute)
        interaction.value = interaction.value.copy(message = text(R.string.message_daily_time, hour, minute))
    }

    fun generateDailyReportNow() {
        DailyReportScheduler.generateNow(getApplication())
        interaction.value = interaction.value.copy(message = text(R.string.message_daily_generating))
    }

    fun refreshCapabilities() {
        val application = getApplication<Application>()
        val listeners = Settings.Secure.getString(
            application.contentResolver,
            "enabled_notification_listeners",
        ).orEmpty()
        val notificationComponent = ComponentName(application, RecallNotificationListenerService::class.java)
        val notificationEnabled = listeners.split(':')
            .mapNotNull(ComponentName::unflattenFromString)
            .any { it == notificationComponent }
        val calendarEnabled = ContextCompat.checkSelfPermission(
            application,
            Manifest.permission.READ_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED
        interaction.value = interaction.value.copy(
            notificationHistoryEnabled = notificationEnabled,
            calendarSyncEnabled = calendarEnabled,
        )
        if (calendarEnabled) CalendarSyncScheduler.syncNow(application)
    }

    fun clearHistory() = viewModelScope.launch {
        container.historyRepository.clearAll()
        interaction.value = InteractionState(
            message = text(R.string.message_history_deleted),
            notificationHistoryEnabled = interaction.value.notificationHistoryEnabled,
            calendarSyncEnabled = interaction.value.calendarSyncEnabled,
        )
    }

    fun deleteMemory(id: String) = viewModelScope.launch {
        container.historyRepository.deleteMemory(id)
    }

    fun consumeMessage() {
        interaction.value = interaction.value.copy(message = null)
    }

    private fun loadApplications() {
        viewModelScope.launch {
            apps.value = discoverApplications()
        }
    }

    private fun discoverApplications(): List<InstalledApplication> {
        val packageManager = getApplication<Application>().packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val resolved = packageManager.queryIntentActivities(intent, 0)
        return resolved.mapNotNull { info ->
            val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
            if (packageName == getApplication<Application>().packageName) return@mapNotNull null
            val applicationInfo = info.activityInfo.applicationInfo
            InstalledApplication(
                packageName = packageName,
                label = info.loadLabel(packageManager).toString(),
                isSystem = applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                isProtected = container.privacyGuard.isProtectedPackage(packageName),
            )
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    }

    private fun localAnswer(memories: List<HistoryMemory>): String = when {
        memories.isEmpty() -> text(R.string.local_answer_empty)
        else -> buildString {
            append(text(R.string.local_answer_unformatted, memories.size))
            memories.take(2).forEach { memory ->
                append("・${memory.applications.joinToString(" · ")}: ${HistoryEvidenceFormatter.clean(memory.summary, 280)}\n")
            }
        }.trim()
    }

    private fun text(id: Int, vararg args: Any): String =
        getApplication<Application>().getString(id, *args)

    private data class InteractionState(
        val query: String = "",
        val answer: String? = null,
        val sources: List<HistoryMemory> = emptyList(),
        val contextSources: List<HistoryMemory> = emptyList(),
        val conversation: List<HistoryConversationMessage> = emptyList(),
        val isAnswering: Boolean = false,
        val message: String? = null,
        val notificationHistoryEnabled: Boolean = false,
        val calendarSyncEnabled: Boolean = false,
    )
}
