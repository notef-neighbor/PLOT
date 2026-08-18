package com.recall.android

import android.Manifest
import android.app.TimePickerDialog
import android.content.ComponentName
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.recall.android.capture.RecallAccessibilityService
import com.recall.android.codex.CodexRuntimeState
import com.recall.android.codex.CodexRuntimeStatus
import com.recall.android.data.HistoryMemory
import com.recall.android.data.RecentActivity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )
        setContent {
            RecallTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val displayState = if (BuildConfig.SCREENSHOT_DEMO) screenshotDemoState(state) else state
                if (!displayState.settings.disclosureAccepted) {
                    DisclosureScreen(viewModel::acceptDisclosure)
                } else {
                    RecallApp(displayState, viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshCapabilities()
    }
}

private enum class AppScreen { Timeline, Ask, Privacy }

private val ShinInk = Color(0xFF111111)
private val ShinBackground = Color(0xFFF3F0E7)
private val ShinPaper = Color(0xFFFFFCF4)
private val ShinBlue = Color(0xFF2447FF)
private val ShinLime = Color(0xFFC8FF38)

@Composable
private fun screenshotDemoState(state: MainUiState): MainUiState {
    val now = System.currentTimeMillis()
    return state.copy(
        todayEventCount = 184,
        recentActivities = listOf(
            RecentActivity(
                id = "demo-recent-calendar",
                occurredAt = now - 4 * 60_000,
                kind = "calendar_event",
                applicationLabel = stringResource(R.string.demo_recent_app_1),
                preview = stringResource(R.string.demo_recent_text_1),
            ),
            RecentActivity(
                id = "demo-recent-slack",
                occurredAt = now - 12 * 60_000,
                kind = "notification",
                applicationLabel = stringResource(R.string.demo_recent_app_2),
                preview = stringResource(R.string.demo_recent_text_2),
            ),
        ),
        memories = listOf(
            HistoryMemory(
                id = "demo-report",
                startedAt = now - 20 * 60_000,
                endedAt = now - 20 * 60_000,
                title = stringResource(R.string.demo_report_title),
                summary = stringResource(R.string.demo_report_summary),
                applications = listOf("Chrome", "Slack", "Calendar"),
                keywords = emptyList(),
                eventCount = 184,
                source = "daily_codex",
            ),
            HistoryMemory(
                id = "demo-morning",
                startedAt = now - 3 * 60 * 60_000,
                endedAt = now - 2 * 60 * 60_000,
                title = stringResource(R.string.demo_memory_title_1),
                summary = stringResource(R.string.demo_memory_summary_1),
                applications = listOf("Calendar", "Slack"),
                keywords = emptyList(),
                eventCount = 63,
                source = "local",
            ),
            HistoryMemory(
                id = "demo-research",
                startedAt = now - 5 * 60 * 60_000,
                endedAt = now - 4 * 60 * 60_000,
                title = stringResource(R.string.demo_memory_title_2),
                summary = stringResource(R.string.demo_memory_summary_2),
                applications = listOf("Chrome", "Docs"),
                keywords = emptyList(),
                eventCount = 41,
                source = "local",
            ),
        ),
        settings = state.settings.copy(
            disclosureAccepted = true,
            collectionPaused = false,
            allowedPackages = setOf("com.android.chrome", "com.google.android.calendar", "com.slack"),
            dailyReportEnabled = true,
            notfBotEnabled = true,
        ),
        notificationHistoryEnabled = true,
        calendarSyncEnabled = true,
        codex = CodexRuntimeState(
            status = CodexRuntimeStatus.Authenticated,
            planType = "ChatGPT",
            model = "gpt-5.6-luna",
            modelDisplayName = "GPT-5.6 Luna",
            reasoningEffort = "low",
        ),
    ).let { demo ->
        demo.copy(
            query = "",
            answer = stringResource(R.string.demo_answer),
            answerSources = demo.memories.take(2),
            conversation = listOf(
                HistoryConversationMessage(true, stringResource(R.string.demo_question)),
                HistoryConversationMessage(false, stringResource(R.string.demo_answer)),
            ),
        )
    }
}

@Composable
private fun RecallTheme(content: @Composable () -> Unit) {
    val colors = androidx.compose.material3.lightColorScheme(
        primary = ShinInk,
        onPrimary = Color.White,
        secondary = ShinBlue,
        tertiary = ShinLime,
        background = ShinBackground,
        surface = ShinPaper,
        surfaceVariant = Color(0xFFE8E3D8),
        onSurface = ShinInk,
        outline = ShinInk,
    )
    val typography = androidx.compose.material3.Typography(
        headlineLarge = androidx.compose.ui.text.TextStyle(fontSize = 42.sp, lineHeight = 42.sp, fontWeight = FontWeight.Black),
        headlineMedium = androidx.compose.ui.text.TextStyle(fontSize = 30.sp, lineHeight = 32.sp, fontWeight = FontWeight.Black),
        titleLarge = androidx.compose.ui.text.TextStyle(fontSize = 20.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
        bodyLarge = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
        labelLarge = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
    )
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}

@Composable
private fun DisclosureScreen(onAccept: () -> Unit) {
    var acknowledged by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(72.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.History, null, tint = Color.White, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text(stringResource(R.string.disclosure_title), fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.disclosure_body), fontSize = 17.sp, lineHeight = 26.sp)
        Spacer(Modifier.height(24.dp))
        DisclosurePoint(stringResource(R.string.disclosure_capture_title), stringResource(R.string.disclosure_capture_body))
        DisclosurePoint(stringResource(R.string.disclosure_exclude_title), stringResource(R.string.disclosure_exclude_body))
        DisclosurePoint(stringResource(R.string.disclosure_storage_title), stringResource(R.string.disclosure_storage_body))
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = acknowledged, onCheckedChange = { acknowledged = it })
            Text(stringResource(R.string.disclosure_acknowledge), modifier = Modifier.padding(start = 12.dp))
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = onAccept, enabled = acknowledged, modifier = Modifier.fillMaxWidth().height(54.dp)) {
            Text(stringResource(R.string.disclosure_continue))
        }
    }
}

@Composable
private fun DisclosurePoint(title: String, body: String) {
    Column(Modifier.padding(vertical = 7.dp)) {
        Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(body, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f), lineHeight = 21.sp)
    }
}

@Composable
private fun RecallApp(state: MainUiState, viewModel: MainViewModel) {
    var screen by rememberSaveable { mutableStateOf(AppScreen.Timeline) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!imeVisible) NavigationBar(containerColor = ShinInk) {
                val navColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = ShinInk,
                    selectedTextColor = Color.White,
                    indicatorColor = ShinLime,
                    unselectedIconColor = Color.White.copy(alpha = 0.7f),
                    unselectedTextColor = Color.White.copy(alpha = 0.7f),
                )
                NavigationBarItem(screen == AppScreen.Timeline, { screen = AppScreen.Timeline }, { Icon(Icons.Default.History, null) }, label = { Text(stringResource(R.string.nav_history)) }, colors = navColors)
                NavigationBarItem(screen == AppScreen.Ask, { screen = AppScreen.Ask }, { Icon(Icons.Default.AutoAwesome, null) }, label = { Text(stringResource(R.string.nav_ai_search)) }, colors = navColors)
                NavigationBarItem(screen == AppScreen.Privacy, { screen = AppScreen.Privacy }, { Icon(Icons.Default.Security, null) }, label = { Text(stringResource(R.string.nav_settings)) }, colors = navColors)
            }
        },
    ) { padding ->
        when (screen) {
            AppScreen.Timeline -> TimelineScreen(state, viewModel::deleteMemory, padding)
            AppScreen.Ask -> AskScreen(
                state,
                viewModel::setQuery,
                viewModel::ask,
                viewModel::newHistoryConversation,
                onConnectChatGpt = {
                    viewModel.beginCodexLogin { url -> openChatGptAuthentication(context, url) }
                },
                padding,
            )
            AppScreen.Privacy -> PrivacyScreen(state, viewModel, padding)
        }
    }
}

@Composable
private fun TimelineScreen(state: MainUiState, onDelete: (String) -> Unit, padding: PaddingValues) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            BrandHeader(
                eyebrow = stringResource(R.string.timeline_eyebrow),
                title = stringResource(R.string.timeline_title),
                detail = stringResource(R.string.timeline_today_count, state.todayEventCount),
            )
            Spacer(Modifier.height(12.dp))
            CaptureStatusCard(state)
            Spacer(Modifier.height(12.dp))
        }
        item { RecentActivityCard(state.recentActivities) }
        if (state.memories.isEmpty()) {
            item {
                if (state.recentActivities.isEmpty()) EmptyTimeline() else ProcessingTimeline()
            }
        } else {
            items(state.memories, key = { it.id }) { memory -> MemoryCard(memory, onDelete) }
        }
    }
}

@Composable
private fun RecentActivityCard(activities: List<RecentActivity>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, ShinInk),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(Color(0xFF1F8A70), CircleShape))
                Text(stringResource(R.string.recent_events), modifier = Modifier.padding(start = 10.dp), fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
            Spacer(Modifier.height(8.dp))
            if (activities.isEmpty()) {
                Text(stringResource(R.string.recent_events_empty), fontSize = 13.sp)
            } else {
                activities.take(6).forEachIndexed { index, activity ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(activity.applicationLabel, fontWeight = FontWeight.Medium)
                            Text(formatTime(activity.occurredAt), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                        Text(eventKindLabel(activity.kind), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        activity.preview?.let {
                            Text(it, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun eventKindLabel(kind: String): String = when (kind) {
    "view_clicked" -> stringResource(R.string.event_tap)
    "view_selected" -> stringResource(R.string.event_select)
    "view_focused" -> stringResource(R.string.event_focus)
    "text_changed" -> stringResource(R.string.event_text_change)
    "content_changed" -> stringResource(R.string.event_content_change)
    "notification" -> stringResource(R.string.event_notification)
    "calendar_event" -> stringResource(R.string.event_calendar)
    "window_changed", "window_state_changed", "windows_changed" -> stringResource(R.string.event_screen)
    else -> kind
}

@Composable
private fun CaptureStatusCard(state: MainUiState) {
    val context = LocalContext.current
    val enabled = isAccessibilityEnabled(context)
    val active = enabled && !state.settings.collectionPaused && state.settings.allowedPackages.isNotEmpty()
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, ShinInk),
    ) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).background(if (active) ShinBlue else Color(0xFFFF7A59), CircleShape))
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(if (active) stringResource(R.string.capture_on) else stringResource(R.string.capture_action_required), fontWeight = FontWeight.Black, color = ShinInk)
                Text(
                    when {
                        !enabled -> stringResource(R.string.capture_enable_accessibility)
                        state.settings.allowedPackages.isEmpty() -> stringResource(R.string.capture_select_apps)
                        state.settings.collectionPaused -> stringResource(R.string.capture_paused)
                        else -> stringResource(R.string.capture_allowed_count, state.settings.allowedPackages.size)
                    },
                    fontSize = 13.sp, color = ShinInk,
                )
            }
        }
    }
}

@Composable
private fun EmptyTimeline() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.History, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.timeline_empty_title), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(stringResource(R.string.timeline_empty_body), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun ProcessingTimeline() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.timeline_processing_title), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(stringResource(R.string.timeline_processing_body), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun MemoryCard(memory: HistoryMemory, onDelete: (String) -> Unit) {
    val isDailyReport = memory.source.startsWith("daily_")
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isDailyReport) Color(0xFFF2EBCB) else MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, ShinInk),
    ) {
        Column(Modifier.padding(18.dp)) {
            if (isDailyReport) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(" ${stringResource(R.string.daily_report)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(Modifier.height(6.dp))
            }
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(formatTime(memory.startedAt), color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(memory.title, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                }
                IconButton(onClick = { onDelete(memory.id) }) { Icon(Icons.Default.DeleteOutline, stringResource(R.string.delete)) }
            }
            Spacer(Modifier.height(8.dp))
            Text(memory.summary, lineHeight = 22.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                "${memory.applications.joinToString(" · ")}  •  ${memory.eventCount} events" +
                    if (isDailyReport) "  •  ${if (memory.source == "daily_codex") "Codex" else stringResource(R.string.on_device)}" else "",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AskScreen(
    state: MainUiState,
    onQuery: (String) -> Unit,
    onAsk: () -> Unit,
    onNewConversation: () -> Unit,
    onConnectChatGpt: () -> Unit,
    padding: PaddingValues,
) {
    val aiConnected = state.codex.isReady || state.codex.hasStoredLogin ||
        (state.settings.gatewayUrl.isNotBlank() && state.settings.deviceToken.isNotBlank())
    Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                BrandHeader(
                    eyebrow = stringResource(R.string.ask_eyebrow),
                    title = stringResource(R.string.ask_title),
                    detail = stringResource(R.string.ask_detail),
                )
                if (state.conversation.isNotEmpty()) {
                    TextButton(onClick = onNewConversation) { Text(stringResource(R.string.ask_new_conversation)) }
                }
            }
            if (!aiConnected) {
                item { AiFormattingUnavailableCard(onConnectChatGpt) }
            }
            if (state.conversation.isNotEmpty()) {
                items(state.conversation) { message -> HistoryChatBubble(message) }
            } else {
                state.answer?.let { answer -> item { HistoryChatBubble(HistoryConversationMessage(false, answer)) } }
            }
            if (state.answerSources.isNotEmpty()) {
                item { Text(stringResource(R.string.answer_sources), fontWeight = FontWeight.Bold) }
                items(state.answerSources, key = { it.id }) { memory ->
                    Text("${formatTime(memory.startedAt)}  ${memory.title}", modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
        HistoryQuestionComposer(
            state = state,
            onQuery = onQuery,
            onAsk = onAsk,
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 20.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun AiFormattingUnavailableCard(onConnectChatGpt: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = ShinLime,
            contentColor = ShinInk,
        ),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, ShinInk),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringResource(R.string.ask_ai_not_connected_title), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.ask_ai_not_connected_detail), lineHeight = 20.sp)
            TextButton(onClick = onConnectChatGpt) { Text(stringResource(R.string.codex_connect_chatgpt)) }
        }
    }
}

@Composable
private fun HistoryQuestionComposer(
    state: MainUiState,
    onQuery: (String) -> Unit,
    onAsk: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQuery,
            label = {
                Text(
                    stringResource(
                        if (state.conversation.isEmpty()) R.string.ask_example else R.string.ask_follow_up,
                    ),
                )
            },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (state.isAnswering) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onAsk() }),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onAsk,
            enabled = state.query.isNotBlank() && !state.isAnswering,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(if (state.conversation.isEmpty()) R.string.ask_button else R.string.ask_follow_up_button))
        }
    }
}

@Composable
private fun HistoryChatBubble(message: HistoryConversationMessage) {
    val background = if (message.isUser) ShinBlue else MaterialTheme.colorScheme.surface
    val foreground = if (message.isUser) Color.White else MaterialTheme.colorScheme.onSurface
    Card(
        colors = CardDefaults.cardColors(containerColor = background),
        shape = RoundedCornerShape(if (message.isUser) 18.dp else 4.dp),
        border = if (message.isUser) null else BorderStroke(1.dp, ShinInk),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(
                stringResource(if (message.isUser) R.string.assistant_you else R.string.answer),
                color = foreground,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(message.text, color = foreground, lineHeight = 23.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrivacyScreen(state: MainUiState, viewModel: MainViewModel, padding: PaddingValues) {
    val context = LocalContext.current
    val openAuthentication: (String) -> Unit = { url -> openChatGptAuthentication(context, url) }
    var gatewayUrl by rememberSaveable { mutableStateOf(state.settings.gatewayUrl) }
    var gatewayToken by rememberSaveable { mutableStateOf("") }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showDisconnectDialog by rememberSaveable { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.refreshCapabilities() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            BrandHeader(
                eyebrow = stringResource(R.string.settings_eyebrow),
                title = stringResource(R.string.settings_title),
                detail = stringResource(R.string.settings_detail),
            )
        }
        item {
            SettingsCard(stringResource(R.string.settings_language)) {
                Text(stringResource(R.string.settings_language_detail), fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Settings.ACTION_APP_LOCALE_SETTINGS
                        } else {
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        }
                        context.startActivity(Intent(action, Uri.parse("package:${context.packageName}")))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.settings_language_button)) }
            }
        }
        item {
            SettingsCard(stringResource(R.string.settings_capture_status)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (state.settings.collectionPaused) Icons.Default.PauseCircle else Icons.Default.PlayCircle, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.padding(horizontal = 12.dp).weight(1f)) {
                        Text(if (state.settings.collectionPaused) stringResource(R.string.settings_paused) else stringResource(R.string.settings_ready), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.settings_quick_toggle), fontSize = 12.sp)
                    }
                    Switch(checked = !state.settings.collectionPaused, onCheckedChange = { viewModel.setPaused(!it) })
                }
                if (!isAccessibilityEnabled(context)) {
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.settings_enable_accessibility))
                    }
                }
            }
        }
        item {
            SettingsCard(stringResource(R.string.settings_background_capture)) {
                BackgroundSourceRow(
                    icon = { Icon(Icons.Default.Notifications, null) },
                    title = stringResource(R.string.settings_message_notifications),
                    detail = if (state.notificationHistoryEnabled) {
                        stringResource(R.string.settings_notifications_on)
                    } else {
                        stringResource(R.string.settings_notifications_off)
                    },
                    enabled = state.notificationHistoryEnabled,
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                )
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Color.Black.copy(alpha = 0.2f))
                BackgroundSourceRow(
                    icon = { Icon(Icons.Default.CalendarMonth, null) },
                    title = "Google Calendar",
                    detail = if (state.calendarSyncEnabled) {
                        stringResource(R.string.settings_calendar_on)
                    } else {
                        stringResource(R.string.settings_calendar_off)
                    },
                    enabled = state.calendarSyncEnabled,
                    onClick = {
                        if (state.calendarSyncEnabled) {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}"),
                                ),
                            )
                        } else {
                            calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                        }
                    },
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.settings_encryption_note),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }
        item {
            SettingsCard(stringResource(R.string.codex_server)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SmartToy, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(codexStatusLabel(state), fontWeight = FontWeight.Bold)
                        Text(codexDetailLabel(state), fontSize = 12.sp)
                    }
                }
                state.codex.accountEmail?.let {
                    Spacer(Modifier.height(8.dp))
                    Text("$it · ${state.codex.planType ?: "ChatGPT"}", fontSize = 13.sp)
                }
                state.codex.modelDisplayName?.let { modelName ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(
                            R.string.codex_model_detail,
                            modelName,
                            state.codex.reasoningEffort ?: stringResource(R.string.codex_effort_standard),
                        ),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                state.codex.userCode?.let { code ->
                    Spacer(Modifier.height(14.dp))
                    Text(stringResource(R.string.codex_verification_code), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Text(code, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.codex_clipboard_code), code))
                            Toast.makeText(context, context.getString(R.string.codex_code_copied), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.codex_copy_code)) }
                    Button(
                        onClick = {
                            state.codex.verificationUrl?.let(openAuthentication)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.codex_open_code_page)) }
                    OutlinedButton(onClick = viewModel::refreshCodexAccount, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.codex_check_login))
                    }
                } ?: run {
                    Spacer(Modifier.height(12.dp))
                    when (state.codex.status) {
                        CodexRuntimeStatus.Authenticated -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = viewModel::refreshCodexAccount,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(R.string.codex_check_connection)) }
                            TextButton(
                                onClick = { showDisconnectDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            ) { Text(stringResource(R.string.codex_disconnect)) }
                        }
                        CodexRuntimeStatus.LoginRequired -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    state.codex.verificationUrl?.let(openAuthentication)
                                        ?: viewModel.beginCodexLogin(openAuthentication)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(R.string.codex_reopen_auth)) }
                            TextButton(
                                onClick = viewModel::beginCodexDeviceLogin,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(R.string.codex_use_code_fallback)) }
                        }
                        CodexRuntimeStatus.Stopped,
                        CodexRuntimeStatus.Error,
                        -> Button(
                            onClick = { viewModel.beginCodexLogin(openAuthentication) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.codex_connect_chatgpt)) }
                        CodexRuntimeStatus.Unavailable -> Button(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.codex_not_bundled_button)) }
                        CodexRuntimeStatus.Starting,
                        CodexRuntimeStatus.Connecting,
                        -> Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.codex_connecting_button))
                        }
                    }
                }
            }
        }
        item {
            SettingsCard(stringResource(R.string.assistant_everywhere)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.padding(horizontal = 12.dp).weight(1f)) {
                        Text(stringResource(R.string.assistant_trigger), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.assistant_trigger_detail), fontSize = 12.sp)
                    }
                    Switch(
                        checked = state.settings.notfBotEnabled,
                        onCheckedChange = viewModel::setNotfBotEnabled,
                    )
                }
                if (state.settings.notfBotEnabled) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.assistant_auto_send), fontWeight = FontWeight.Medium)
                            Text(stringResource(R.string.assistant_auto_send_detail), fontSize = 12.sp)
                        }
                        Switch(
                            checked = state.settings.notfBotAutoSendEnabled,
                            onCheckedChange = viewModel::setNotfBotAutoSendEnabled,
                        )
                    }
                }
            }
        }
        item {
            SettingsCard(stringResource(R.string.daily_report)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.padding(horizontal = 12.dp).weight(1f)) {
                        Text(
                            if (state.settings.dailyReportEnabled) {
                                stringResource(
                                    R.string.daily_report_schedule,
                                    state.settings.dailyReportHour,
                                    state.settings.dailyReportMinute,
                                )
                            } else {
                                stringResource(R.string.off)
                            },
                            fontWeight = FontWeight.Bold,
                        )
                        Text(stringResource(R.string.daily_report_detail), fontSize = 12.sp)
                    }
                    Switch(
                        checked = state.settings.dailyReportEnabled,
                        onCheckedChange = viewModel::setDailyReportEnabled,
                    )
                }
                if (state.settings.dailyReportEnabled) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, hour, minute -> viewModel.setDailyReportTime(hour, minute) },
                                state.settings.dailyReportHour,
                                state.settings.dailyReportMinute,
                                true,
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.daily_report_change_time)) }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        OutlinedButton(
                            onClick = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.daily_report_allow_notifications)) }
                    }
                    Button(
                        onClick = viewModel::generateDailyReportNow,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.daily_report_generate_now)) }
                }
            }
        }
        item {
            Text(stringResource(R.string.allowed_apps_title), fontWeight = FontWeight.Bold, fontSize = 19.sp)
            Text(stringResource(R.string.allowed_apps_detail), fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { viewModel.setAllPackagesAllowed(true) },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.all_on)) }
                OutlinedButton(
                    onClick = { viewModel.setAllPackagesAllowed(false) },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.all_off)) }
            }
        }
        items(state.installedApplications, key = { it.packageName }) { app ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(app.label, fontWeight = FontWeight.Medium)
                    Text(
                        if (app.isProtected) stringResource(R.string.always_excluded, app.packageName) else app.packageName,
                        fontSize = 11.sp,
                        color = if (app.isProtected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        maxLines = 1,
                    )
                }
                Switch(
                    checked = !app.isProtected && app.packageName in state.settings.allowedPackages,
                    onCheckedChange = { viewModel.setPackageAllowed(app.packageName, it) },
                    enabled = !app.isProtected,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
        }
        item {
            SettingsCard(stringResource(R.string.gateway_title)) {
                Text(stringResource(R.string.gateway_detail), fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(gatewayUrl, { gatewayUrl = it }, label = { Text("https://gateway.example.com") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(
                    gatewayToken,
                    { gatewayToken = it },
                    label = { Text(if (state.settings.deviceToken.isBlank()) stringResource(R.string.gateway_device_token) else stringResource(R.string.gateway_device_token_change)) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { viewModel.saveGateway(gatewayUrl, gatewayToken); gatewayToken = "" }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.gateway_save)) }
            }
        }
        item {
            OutlinedButton(onClick = { showDeleteDialog = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Icon(Icons.Default.DeleteOutline, null)
                Text(" ${stringResource(R.string.delete_all_history)}")
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_history_title)) },
            text = { Text(stringResource(R.string.delete_history_body)) },
            confirmButton = { TextButton(onClick = { showDeleteDialog = false; viewModel.clearHistory() }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text(stringResource(R.string.disconnect_title)) },
            text = { Text(stringResource(R.string.disconnect_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDisconnectDialog = false
                        viewModel.disconnectCodex()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.disconnect_confirm)) }
            },
            dismissButton = { TextButton(onClick = { showDisconnectDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun openChatGptAuthentication(context: Context, url: String) {
    val uri = Uri.parse(url)
    val chatGptIntent = Intent(Intent.ACTION_VIEW, uri).setPackage("com.openai.chatgpt")
    runCatching { context.startActivity(chatGptIntent) }
        .onFailure { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
}

@Composable
private fun codexStatusLabel(state: MainUiState): String = when (state.codex.status) {
    CodexRuntimeStatus.Unavailable -> stringResource(R.string.codex_status_unavailable)
    CodexRuntimeStatus.Stopped -> stringResource(R.string.codex_status_stopped)
    CodexRuntimeStatus.Starting -> stringResource(R.string.codex_status_starting)
    CodexRuntimeStatus.Connecting -> stringResource(R.string.codex_status_connecting)
    CodexRuntimeStatus.LoginRequired -> stringResource(R.string.codex_status_login_required)
    CodexRuntimeStatus.Authenticated -> stringResource(R.string.codex_status_authenticated)
    CodexRuntimeStatus.Error -> stringResource(R.string.codex_status_error)
}

@Composable
private fun codexDetailLabel(state: MainUiState): String = when (state.codex.status) {
    CodexRuntimeStatus.Unavailable -> stringResource(R.string.runtime_not_bundled)
    CodexRuntimeStatus.Stopped -> ""
    CodexRuntimeStatus.Starting -> stringResource(R.string.runtime_starting)
    CodexRuntimeStatus.Connecting -> stringResource(R.string.codex_status_connecting)
    CodexRuntimeStatus.LoginRequired -> stringResource(R.string.runtime_login_required)
    CodexRuntimeStatus.Authenticated -> stringResource(R.string.runtime_fast_model)
    CodexRuntimeStatus.Error -> state.codex.message.orEmpty()
}

@Composable
private fun BrandHeader(eyebrow: String, title: String, detail: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            eyebrow.uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.8.sp,
            color = ShinBlue,
        )
        Text(title, style = MaterialTheme.typography.headlineLarge)
        Text(
            detail,
            fontWeight = FontWeight.Bold,
            color = ShinInk,
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, ShinInk),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().background(ShinInk).padding(horizontal = 15.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).background(ShinLime))
                Text(
                    title.uppercase(),
                    modifier = Modifier.padding(start = 9.dp),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    letterSpacing = 1.1.sp,
                )
            }
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
            content()
            }
        }
    }
}

@Composable
private fun BackgroundSourceRow(
    icon: @Composable () -> Unit,
    title: String,
    detail: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .background(if (enabled) ShinLime else Color(0xFFE4E0D7), RoundedCornerShape(2.dp)),
            contentAlignment = Alignment.Center,
        ) { icon() }
        Column(Modifier.padding(horizontal = 12.dp).weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(detail, fontSize = 12.sp, lineHeight = 16.sp)
        }
        OutlinedButton(onClick = onClick, shape = RoundedCornerShape(2.dp), contentPadding = PaddingValues(horizontal = 10.dp)) {
            Text(if (enabled) stringResource(R.string.settings_configure) else stringResource(R.string.settings_allow))
        }
    }
}

private fun formatTime(value: Long): String = DateTimeFormatter.ofPattern("M/d HH:mm")
    .withZone(ZoneId.systemDefault())
    .format(Instant.ofEpochMilli(value))

private fun isAccessibilityEnabled(context: Context): Boolean {
    if (BuildConfig.SCREENSHOT_DEMO) return true
    val expected = ComponentName(context, RecallAccessibilityService::class.java)
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
    return enabled
        ?.split(':')
        ?.mapNotNull(ComponentName::unflattenFromString)
        ?.any { it == expected } == true
}
