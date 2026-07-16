package com.jsos.watch

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme as WearMaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import com.jsos.shared.WatchChatMessage
import com.jsos.shared.WatchCodexSession
import com.jsos.shared.WatchVoiceOutputRoutes

private enum class WatchThemeMode {
    DARK,
    LIGHT
}

private class WatchThemePreferences(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences("jsos.watch.appearance", Context.MODE_PRIVATE)

    fun read(): WatchThemeMode =
        runCatching {
            WatchThemeMode.valueOf(preferences.getString("themeMode", null).orEmpty())
        }.getOrDefault(WatchThemeMode.DARK)

    fun write(mode: WatchThemeMode) {
        preferences.edit { putString("themeMode", mode.name) }
    }
}

@Immutable
private data class WatchPalette(
    val background: Color,
    val card: Color,
    val cardRaised: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val accentDim: Color,
    val accentContainer: Color,
    val onAccent: Color,
    val onAccentContainer: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val errorContainer: Color,
    val outline: Color,
    val disabled: Color,
    val userBubble: Color,
    val agentBubble: Color,
    val systemBubble: Color
)

private val DarkWatchPalette =
    WatchPalette(
        background = Color(0xFF0B1013),
        card = Color(0xFF202529),
        cardRaised = Color(0xFF292F34),
        text = Color(0xFFE1E2E8),
        muted = Color(0xFFC3C6CF),
        accent = Color(0xFF58D6FF),
        accentDim = Color(0xFF24B8E6),
        accentContainer = Color(0xFF064A61),
        onAccent = Color(0xFF003543),
        onAccentContainer = Color(0xFFC9F3FF),
        success = Color(0xFF62D894),
        warning = Color(0xFFFFC857),
        error = Color(0xFFFFB4AB),
        errorContainer = Color(0xFF6A2B2B),
        outline = Color(0xFF8F939C),
        disabled = Color(0xFF171C20),
        userBubble = Color(0xFF064A61),
        agentBubble = Color(0xFF202529),
        systemBubble = Color(0xFF292F34)
    )

private val LightWatchPalette =
    WatchPalette(
        background = Color(0xFFF3F6F8),
        card = Color(0xFFFFFFFF),
        cardRaised = Color(0xFFE7EDF2),
        text = Color(0xFF191C20),
        muted = Color(0xFF555F69),
        accent = Color(0xFF006780),
        accentDim = Color(0xFF168AA5),
        accentContainer = Color(0xFFC5F1FF),
        onAccent = Color.White,
        onAccentContainer = Color(0xFF003643),
        success = Color(0xFF167346),
        warning = Color(0xFF8A6118),
        error = Color(0xFFB3261E),
        errorContainer = Color(0xFFFFDAD6),
        outline = Color(0xFF74777F),
        disabled = Color(0xFFDDE3E8),
        userBubble = Color(0xFFC5F1FF),
        agentBubble = Color(0xFFFFFFFF),
        systemBubble = Color(0xFFE7EDF2)
    )

private val LocalWatchPalette = staticCompositionLocalOf { DarkWatchPalette }

private fun WatchPalette.panelBorderColor(): Color =
    accentDim.copy(alpha = if (this == LightWatchPalette) 0.78f else 0.32f)

@Composable
private fun JsosWearTheme(
    themeMode: WatchThemeMode,
    content: @Composable () -> Unit
) {
    val palette = if (themeMode == WatchThemeMode.DARK) DarkWatchPalette else LightWatchPalette
    val wearColors =
        WearMaterialTheme.colorScheme.copy(
            primary = palette.accent,
            primaryDim = palette.accentDim,
            primaryContainer = palette.accentContainer,
            onPrimary = palette.onAccent,
            onPrimaryContainer = palette.onAccentContainer,
            secondary = palette.success,
            secondaryDim = palette.success,
            secondaryContainer = palette.cardRaised,
            onSecondary = palette.onAccent,
            onSecondaryContainer = palette.text,
            tertiary = palette.warning,
            tertiaryDim = palette.warning,
            tertiaryContainer = palette.cardRaised,
            onTertiary = palette.text,
            onTertiaryContainer = palette.text,
            surfaceContainerLow = palette.card,
            surfaceContainer = palette.card,
            surfaceContainerHigh = palette.cardRaised,
            onSurface = palette.text,
            onSurfaceVariant = palette.muted,
            outline = palette.outline,
            outlineVariant = palette.outline.copy(alpha = 0.45f),
            background = palette.background,
            onBackground = palette.text,
            error = palette.error,
            errorDim = palette.error,
            errorContainer = palette.errorContainer,
            onError = Color.White,
            onErrorContainer = palette.text
        )

    CompositionLocalProvider(LocalWatchPalette provides palette) {
        WearMaterialTheme(
            colorScheme = wearColors,
            content = content
        )
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val bridge = remember { WatchBridge(applicationContext) }
            val themePreferences = remember { WatchThemePreferences(applicationContext) }
            var themeMode by remember { mutableStateOf(themePreferences.read()) }
            var speechState by remember { mutableStateOf(WatchSpeechState()) }
            var sendSpeechToCodex by remember { mutableStateOf(false) }
            val speechController =
                remember {
                    WatchSpeechController(
                        context = applicationContext,
                        onState = { speechState = it },
                        onFinalText = { text ->
                            if (sendSpeechToCodex) {
                                bridge.sendCodexInput(text)
                            } else {
                                bridge.sendAssistantCommand(text)
                            }
                        }
                    )
                }
            val micPermissionLauncher =
                rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        speechController.start()
                    } else {
                        speechState =
                            WatchSpeechState(
                                status = "MIC DENIED",
                                error = "Permission denied"
                            )
                    }
                }

            DisposableEffect(bridge) {
                bridge.start()
                onDispose { bridge.stop() }
            }
            DisposableEffect(speechController) {
                onDispose { speechController.destroy() }
            }

            val state by bridge.state.collectAsState()
            val startWatchMic = {
                val granted =
                    ContextCompat.checkSelfPermission(
                        applicationContext,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    speechController.start()
                } else {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }

            WatchApp(
                state = state,
                speechState = speechState,
                themeMode = themeMode,
                onThemeModeChange = { selectedMode ->
                    themeMode = selectedMode
                    themePreferences.write(selectedMode)
                },
                onStopSpeaking = bridge::stopSpeaking,
                onCloseHud = bridge::closeHud,
                onToggleLiveTalk = bridge::toggleLiveTalk,
                onToggleTts = bridge::toggleTts,
                onNextTtsProvider = bridge::nextTtsProvider,
                onToggleStt = bridge::toggleStt,
                onNextVoiceOutput = bridge::nextVoiceOutput,
                onStartWatchMic = startWatchMic,
                onStopWatchMic = speechController::stop,
                onSendAssistantCommand = bridge::sendAssistantCommand,
                onRequestState = bridge::requestState,
                onRequestMoreChat = bridge::requestMoreChat,
                onPreviousSession = bridge::previousSession,
                onNextSession = bridge::nextSession,
                onPreviousModel = bridge::previousModel,
                onNextModel = bridge::nextModel,
                onResetSession = { bridge.sendAssistantCommand("/reset") },
                onClearSession = { bridge.sendAssistantCommand("/clear") },
                onRequestCodexSessions = bridge::requestCodexSessions,
                onResumeCodexSession = bridge::resumeCodexSession,
                onSendCodexInput = bridge::sendCodexInput,
                onStopCodex = bridge::stopCodex,
                onClearCodex = bridge::clearCodex,
                onNewCodexSession = bridge::newCodexSession,
                onDeleteCodexSession = bridge::deleteCodexSession,
                onSpeechTargetChanged = { sendSpeechToCodex = it }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WatchApp(
    state: WatchUiState,
    speechState: WatchSpeechState,
    themeMode: WatchThemeMode,
    onThemeModeChange: (WatchThemeMode) -> Unit,
    onStopSpeaking: () -> Unit,
    onCloseHud: () -> Unit,
    onToggleLiveTalk: () -> Unit,
    onToggleTts: () -> Unit,
    onNextTtsProvider: () -> Unit,
    onToggleStt: () -> Unit,
    onNextVoiceOutput: () -> Unit,
    onStartWatchMic: () -> Unit,
    onStopWatchMic: () -> Unit,
    onSendAssistantCommand: (String) -> Unit,
    onRequestState: () -> Unit,
    onRequestMoreChat: () -> Unit,
    onPreviousSession: () -> Unit,
    onNextSession: () -> Unit,
    onPreviousModel: () -> Unit,
    onNextModel: () -> Unit,
    onResetSession: () -> Unit,
    onClearSession: () -> Unit,
    onRequestCodexSessions: () -> Unit,
    onResumeCodexSession: (String) -> Unit,
    onSendCodexInput: (String) -> Unit,
    onStopCodex: () -> Unit,
    onClearCodex: () -> Unit,
    onNewCodexSession: () -> Unit,
    onDeleteCodexSession: () -> Unit,
    onSpeechTargetChanged: (Boolean) -> Unit
) {
    val pages = listOf("Chat", "Status", "Controls", "Session", "Codex", "Codex control", "Voice")
    val pagerState = rememberPagerState(pageCount = { pages.size })

    LaunchedEffect(pagerState.currentPage) {
        onSpeechTargetChanged(pagerState.currentPage == 4)
    }

    JsosWearTheme(themeMode = themeMode) {
        val palette = LocalWatchPalette.current
        AppScaffold(
            modifier = Modifier.fillMaxSize(),
            timeText = {},
            containerColor = palette.background,
            contentColor = palette.text
        ) {
            HorizontalPager(
                state = pagerState,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(palette.background)
            ) { page ->
                when (page) {
                    0 ->
                        ChatPage(
                            state = state,
                            pageIndex = page,
                            pageCount = pages.size,
                            onRequestMoreChat = onRequestMoreChat,
                            onToggleStt = onToggleStt,
                            onToggleLiveTalk = onToggleLiveTalk,
                            onSendAssistantCommand = onSendAssistantCommand
                        )

                    1 ->
                        StatusPage(
                            state = state,
                            themeMode = themeMode,
                            pageIndex = page,
                            pageCount = pages.size,
                            onThemeModeChange = onThemeModeChange
                        )

                    2 ->
                        ControlsPage(
                            state = state,
                            pageIndex = page,
                            pageCount = pages.size,
                            enabled = !state.commandPending,
                            onCloseHud = onCloseHud,
                            onRequestState = onRequestState
                        )

                    3 ->
                        SessionModelPage(
                            state = state,
                            pageIndex = page,
                            pageCount = pages.size,
                            enabled = !state.commandPending,
                            onPreviousSession = onPreviousSession,
                            onNextSession = onNextSession,
                            onPreviousModel = onPreviousModel,
                            onNextModel = onNextModel,
                            onResetSession = onResetSession,
                            onClearSession = onClearSession
                        )

                    4 ->
                        CodexPage(
                            state = state,
                            speechState = speechState,
                            pageIndex = page,
                            pageCount = pages.size,
                            enabled = !state.commandPending,
                            onStartWatchMic = onStartWatchMic,
                            onStopWatchMic = onStopWatchMic,
                            onSendCodexInput = onSendCodexInput,
                            onStopCodex = onStopCodex
                        )

                    5 ->
                        CodexControlPage(
                            state = state,
                            pageIndex = page,
                            pageCount = pages.size,
                            enabled = !state.commandPending,
                            onRefresh = onRequestCodexSessions,
                            onResume = onResumeCodexSession,
                            onNewCodexSession = onNewCodexSession,
                            onDeleteCodexSession = onDeleteCodexSession,
                            onClearCodex = onClearCodex
                        )

                    6 ->
                        VoicePage(
                            state = state,
                            speechState = speechState,
                            pageIndex = page,
                            pageCount = pages.size,
                            enabled = !state.commandPending,
                            onToggleTts = onToggleTts,
                            onNextTtsProvider = onNextTtsProvider,
                            onToggleStt = onToggleStt,
                            onNextVoiceOutput = onNextVoiceOutput,
                            onStartWatchMic = onStartWatchMic,
                            onStopWatchMic = onStopWatchMic,
                            onToggleLiveTalk = onToggleLiveTalk,
                            onStopSpeaking = onStopSpeaking
                        )
                }
            }
        }
    }
}

@Composable
private fun WearPage(
    title: String,
    pageIndex: Int,
    pageCount: Int,
    listState: ScalingLazyListState,
    content: ScalingLazyListScope.() -> Unit
) {
    ScreenScaffold(
        scrollState = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = 18.dp,
                top = 18.dp,
                end = 18.dp,
                bottom = 22.dp
            ),
        timeText = {}
    ) { contentPadding ->
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            autoCentering = null,
            scalingParams =
                ScalingLazyColumnDefaults.scalingParams(
                    edgeScale = 0.84f,
                    edgeAlpha = 0.62f,
                    minTransitionArea = 0.18f,
                    maxTransitionArea = 0.52f
                )
        ) {
            item(key = "page-header") {
                PageHeader(
                    title = title,
                    pageIndex = pageIndex,
                    pageCount = pageCount
                )
            }
            content()
            item(key = "page-end") {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun PageHeader(
    title: String,
    pageIndex: Int,
    pageCount: Int
) {
    val palette = LocalWatchPalette.current
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(
            text = title,
            color = palette.muted,
            fontSize = 16.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(pageCount) { index ->
                val selected = index == pageIndex
                Box(
                    modifier =
                        Modifier
                            .size(if (selected) 6.dp else 4.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) {
                                    palette.accent
                                } else {
                                    palette.outline.copy(alpha = 0.48f)
                                }
                            )
                )
            }
        }
    }
}

@Composable
private fun DrillDownPage(
    title: String,
    detailTitle: String?,
    pageIndex: Int,
    pageCount: Int,
    listState: ScalingLazyListState,
    onBack: () -> Unit,
    content: ScalingLazyListScope.() -> Unit
) {
    BackHandler(enabled = detailTitle != null, onBack = onBack)
    LaunchedEffect(detailTitle) {
        runCatching { listState.scrollToItem(1) }
    }

    WearPage(
        title = detailTitle ?: title,
        pageIndex = pageIndex,
        pageCount = pageCount,
        listState = listState
    ) {
        if (detailTitle != null) {
            item(key = "detail-back") {
                ActionCard(
                    label = "Back to $title",
                    onClick = onBack
                )
            }
        }
        content()
    }
}

@Composable
private fun MenuPanel(
    label: String,
    summary: String,
    onClick: () -> Unit
) {
    ActionCard(
        label = label,
        value = summary,
        primary = true,
        onClick = onClick
    )
}

@Composable
private fun StatusPage(
    state: WatchUiState,
    themeMode: WatchThemeMode,
    pageIndex: Int,
    pageCount: Int,
    onThemeModeChange: (WatchThemeMode) -> Unit
) {
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 1)
    val palette = LocalWatchPalette.current
    var detail by remember { mutableStateOf<String?>(null) }

    DrillDownPage(
        title = "JSOS status",
        detailTitle = detail,
        pageIndex = pageIndex,
        pageCount = pageCount,
        listState = listState,
        onBack = { detail = null }
    ) {
        when (detail) {
            null -> {
                item(key = "status-menu-system") {
                    MenuPanel(
                        label = "System",
                        summary = "Core, gateway and HUD",
                        onClick = { detail = "System" }
                    )
                }
                item(key = "status-menu-context") {
                    MenuPanel(
                        label = "Context",
                        summary = "Session and model",
                        onClick = { detail = "Context" }
                    )
                }
                item(key = "status-menu-voice") {
                    MenuPanel(
                        label = "Voice",
                        summary = "Talk, TTS and audio",
                        onClick = { detail = "Voice" }
                    )
                }
                item(key = "status-menu-appearance") {
                    MenuPanel(
                        label = "Appearance",
                        summary = "Dark or light mode",
                        onClick = { detail = "Appearance" }
                    )
                }
            }

            "System" -> item(key = "system-panel") {
                GroupPanel(title = "System status") {
                    PanelValueRow(
                        label = "Core",
                        value = onlineOffline(state.coreOnline),
                        valueColor = if (state.coreOnline) palette.success else palette.error
                    )
                    PanelDivider()
                    PanelValueRow(
                        label = "Gateway",
                        value = onlineOffline(state.gatewayOnline),
                        valueColor = if (state.gatewayOnline) palette.success else palette.error
                    )
                    PanelDivider()
                    PanelValueRow(
                        label = "HUD",
                        value = onOff(state.hudOnline),
                        valueColor = if (state.hudOnline) palette.success else palette.error
                    )
                }
            }

            "Context" -> item(key = "context-panel") {
                GroupPanel(title = "Current context") {
                    PanelValueRow(
                        label = "Session",
                        value = state.currentSession.ifBlank { "Not set" }
                    )
                    PanelDivider()
                    PanelValueRow(
                        label = "Model",
                        value = state.currentModel.ifBlank { "Not set" }
                    )
                }
            }

            "Voice" -> item(key = "voice-status-panel") {
                GroupPanel(title = "Voice status") {
                    PanelValueRow(
                        label = "Real-time talk",
                        value = state.liveTalkState,
                        valueColor =
                            if (state.liveTalkState == "IDLE") palette.muted else palette.success
                    )
                    PanelDivider()
                    PanelValueRow(
                        label = "TTS",
                        value = if (state.ttsEnabled) state.ttsProviderLabel else "Off",
                        valueColor = if (state.ttsEnabled) palette.success else palette.muted
                    )
                    PanelDivider()
                    PanelValueRow(
                        label = "Output",
                        value = voiceOutputLabel(state.voiceOutputRoute),
                        valueColor =
                            if (state.voiceOutputRoute == WatchVoiceOutputRoutes.OFF) {
                                palette.error
                            } else {
                                palette.text
                            }
                    )
                    PanelDivider()
                    PanelValueRow(
                        label = "Watch audio",
                        value = state.watchAudioStatus,
                        valueColor =
                            if (state.watchAudioStatus.contains("READY", ignoreCase = true)) {
                                palette.success
                            } else {
                                palette.warning
                            }
                    )
                }
            }

            "Appearance" -> item(key = "appearance-panel") {
                GroupPanel(title = "Appearance") {
                    ToggleCard(
                        label = "Dark mode",
                        checked = themeMode == WatchThemeMode.DARK,
                        onCheckedChange = { onThemeModeChange(WatchThemeMode.DARK) }
                    )
                    ToggleCard(
                        label = "Light mode",
                        checked = themeMode == WatchThemeMode.LIGHT,
                        onCheckedChange = { onThemeModeChange(WatchThemeMode.LIGHT) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlsPage(
    state: WatchUiState,
    pageIndex: Int,
    pageCount: Int,
    enabled: Boolean,
    onCloseHud: () -> Unit,
    onRequestState: () -> Unit
) {
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 1)
    var detail by remember { mutableStateOf<String?>(null) }

    DrillDownPage(
        title = "Controls",
        detailTitle = detail,
        pageIndex = pageIndex,
        pageCount = pageCount,
        listState = listState,
        onBack = { detail = null }
    ) {
        when (detail) {
            null -> {
                item(key = "controls-menu-system") {
                    MenuPanel(
                        label = "System",
                        summary = "Core, gateway and HUD state",
                        onClick = { detail = "System" }
                    )
                }
                item(key = "controls-menu-device") {
                    MenuPanel(
                        label = "Device",
                        summary = "HUD and state controls",
                        onClick = { detail = "Device" }
                    )
                }
            }

            "System" -> item(key = "system-control-panel") {
                GroupPanel(title = "System state") {
                    PanelValueRow(
                        label = "Core",
                        value = onlineOffline(state.coreOnline)
                    )
                    PanelDivider()
                    PanelValueRow(
                        label = "Gateway",
                        value = onlineOffline(state.gatewayOnline)
                    )
                    PanelDivider()
                    PanelValueRow(
                        label = "HUD",
                        value = onOff(state.hudOnline)
                    )
                }
            }

            "Device" -> item(key = "device-control-panel") {
                GroupPanel(title = "Device controls") {
                    ActionCard(
                        label = "Close HUD",
                        value = "Close the glasses overlay",
                        enabled = enabled,
                        onClick = onCloseHud
                    )
                    ActionCard(
                        label = "Refresh state",
                        value = "Request a fresh phone snapshot",
                        enabled = enabled,
                        primary = true,
                        onClick = onRequestState
                    )
                }
            }
        }
        commandFeedbackItem(state)
    }
}

@Composable
private fun SessionModelPage(
    state: WatchUiState,
    pageIndex: Int,
    pageCount: Int,
    enabled: Boolean,
    onPreviousSession: () -> Unit,
    onNextSession: () -> Unit,
    onPreviousModel: () -> Unit,
    onNextModel: () -> Unit,
    onResetSession: () -> Unit,
    onClearSession: () -> Unit
) {
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 1)
    var detail by remember { mutableStateOf<String?>(null) }

    DrillDownPage(
        title = "Session & model",
        detailTitle = detail,
        pageIndex = pageIndex,
        pageCount = pageCount,
        listState = listState,
        onBack = { detail = null }
    ) {
        when (detail) {
            null -> {
                item(key = "session-menu-session") {
                    MenuPanel(
                        label = "Session",
                        summary = state.currentSession.ifBlank { "Not set" },
                        onClick = { detail = "Session" }
                    )
                }
                item(key = "session-menu-model") {
                    MenuPanel(
                        label = "Model",
                        summary = state.currentModel.ifBlank { "Not set" },
                        onClick = { detail = "Model" }
                    )
                }
            }

            "Session" -> item(key = "session-panel") {
                GroupPanel(title = "Session controls") {
                    PanelValueRow(
                        label = "Current",
                        value = state.currentSession.ifBlank { "Not set" }
                    )
                    PanelDivider()
                    ActionCard(
                        label = "Previous session",
                        enabled = enabled,
                        onClick = onPreviousSession
                    )
                    ActionCard(
                        label = "Next session",
                        enabled = enabled,
                        primary = true,
                        onClick = onNextSession
                    )
                    ActionCard(
                        label = "Reset session",
                        value = "Start fresh context",
                        enabled = enabled,
                        destructive = true,
                        onClick = onResetSession
                    )
                    ActionCard(
                        label = "Clear session",
                        value = "Remove the visible conversation",
                        enabled = enabled,
                        onClick = onClearSession
                    )
                }
            }

            "Model" -> item(key = "model-panel") {
                GroupPanel(title = "Model controls") {
                    PanelValueRow(
                        label = "Current",
                        value = state.currentModel.ifBlank { "Not set" }
                    )
                    PanelDivider()
                    ActionCard(
                        label = "Previous model",
                        enabled = enabled,
                        onClick = onPreviousModel
                    )
                    ActionCard(
                        label = "Next model",
                        enabled = enabled,
                        primary = true,
                        onClick = onNextModel
                    )
                }
            }
        }
        commandFeedbackItem(state)
    }
}

@Composable
private fun ChatPage(
    state: WatchUiState,
    pageIndex: Int,
    pageCount: Int,
    onRequestMoreChat: () -> Unit,
    onToggleStt: () -> Unit,
    onToggleLiveTalk: () -> Unit,
    onSendAssistantCommand: (String) -> Unit
) {
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 1)
    var draft by remember { mutableStateOf("") }
    val hasSnapshotMessages = state.chatMessages.isNotEmpty()
    val messageItemCount = if (hasSnapshotMessages) state.chatMessages.size else 1
    var detail by remember { mutableStateOf<String?>(null) }

    fun sendDraft() {
        val clean = draft.trim()
        if (clean.isNotBlank()) {
            onSendAssistantCommand(clean)
            draft = ""
        }
    }

    LaunchedEffect(state.chatMessages.size, state.lastAnswer, state.chatPrependedCount) {
        if (detail == "Conversation" && state.chatPrependedCount <= 0) {
            runCatching { listState.animateScrollToItem(2 + messageItemCount) }
        }
    }

    DrillDownPage(
        title = "Assistant chat",
        detailTitle = detail,
        pageIndex = pageIndex,
        pageCount = pageCount,
        listState = listState,
        onBack = { detail = null }
    ) {
        when (detail) {
            null -> {
                item(key = "chat-menu-conversation") {
                    MenuPanel(
                        label = "Conversation",
                        summary = "${state.chatMessages.size} messages",
                        onClick = { detail = "Conversation" }
                    )
                }
                item(key = "chat-menu-message") {
                    MenuPanel(
                        label = "Message",
                        summary = "Type and send to JSOS",
                        onClick = { detail = "Message" }
                    )
                }
                item(key = "chat-menu-talk") {
                    MenuPanel(
                        label = "Talk",
                        summary = "STT and real-time talk",
                        onClick = { detail = "Talk" }
                    )
                }
            }

            "Conversation" -> {
                item(key = "history-panel") {
                    GroupPanel(title = "History") {
                        ActionCard(
                            label =
                                when {
                                    state.chatLoadingMore -> "Loading messages"
                                    state.chatHasMore -> "Load older messages"
                                    else -> "All messages loaded"
                                },
                            value = "${state.chatMessages.size} messages",
                            enabled =
                                !state.commandPending &&
                                    !state.chatLoadingMore &&
                                    state.chatHasMore &&
                                    state.chatMessages.isNotEmpty(),
                            onClick = onRequestMoreChat
                        )
                    }
                }

                if (hasSnapshotMessages) {
                    itemsIndexed(state.chatMessages) { _, message ->
                        ChatBubble(
                            label = message.watchLabel(),
                            text = message.text,
                            isUser = message.role.equals("user", ignoreCase = true)
                        )
                    }
                } else if (state.lastAnswer.isNotBlank()) {
                    item(key = "last-answer") {
                        ChatBubble(
                            label = "AGENT",
                            text = state.lastAnswer,
                            isUser = false
                        )
                    }
                } else {
                    item(key = "empty-chat") {
                        GroupPanel(title = "Chat") {
                            PanelValueRow(
                                label = "Status",
                                value = "Ready"
                            )
                        }
                    }
                }
            }

            "Message" -> item(key = "message-panel") {
                GroupPanel(title = "Message") {
                    InputCard(
                        value = draft,
                        placeholder = "Message JSOS",
                        enabled = !state.commandPending,
                        maxLength = 600,
                        onValueChange = { draft = it },
                        onSend = { sendDraft() }
                    )
                    ActionCard(
                        label = "Send",
                        enabled = !state.commandPending && draft.trim().isNotBlank(),
                        primary = true,
                        onClick = { sendDraft() }
                    )
                }
            }

            "Talk" -> item(key = "talk-panel") {
                GroupPanel(title = "Talk controls") {
                    ToggleCard(
                        label = "STT",
                        value = "Speech recognition on the phone",
                        checked = state.sttEnabled,
                        enabled = !state.commandPending,
                        onCheckedChange = { onToggleStt() }
                    )
                    ToggleCard(
                        label = "Real-time talk",
                        value = state.liveTalkState,
                        checked = state.liveTalkState != "IDLE",
                        enabled = !state.commandPending,
                        onCheckedChange = { onToggleLiveTalk() }
                    )
                }
            }
        }
    }
}

@Composable
private fun CodexPage(
    state: WatchUiState,
    speechState: WatchSpeechState,
    pageIndex: Int,
    pageCount: Int,
    enabled: Boolean,
    onStartWatchMic: () -> Unit,
    onStopWatchMic: () -> Unit,
    onSendCodexInput: (String) -> Unit,
    onStopCodex: () -> Unit
) {
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 1)
    var draft by remember { mutableStateOf("") }
    val visibleMessages =
        state.codexMessages.filterNot { message ->
            message.role.equals("system", ignoreCase = true) &&
                message.text.contains("Codex ready", ignoreCase = true)
        }
    val messageItemCount = if (visibleMessages.isEmpty()) 1 else visibleMessages.size
    var detail by remember { mutableStateOf<String?>(null) }

    fun sendDraft() {
        val clean = draft.trim()
        if (clean.isNotBlank()) {
            onSendCodexInput(clean)
            draft = ""
        }
    }

    LaunchedEffect(visibleMessages.size, state.codexStatus, detail) {
        if (detail == "Conversation") {
            runCatching { listState.animateScrollToItem(1 + messageItemCount) }
        }
    }

    DrillDownPage(
        title = "Codex",
        detailTitle = detail,
        pageIndex = pageIndex,
        pageCount = pageCount,
        listState = listState,
        onBack = { detail = null }
    ) {
        when (detail) {
            null -> {
                item(key = "codex-menu-connection") {
                    MenuPanel(
                        label = "Connection",
                        summary = state.codexStatus.ifBlank { "Disconnected" },
                        onClick = { detail = "Connection" }
                    )
                }
                item(key = "codex-menu-conversation") {
                    MenuPanel(
                        label = "Conversation",
                        summary = "${visibleMessages.size} messages",
                        onClick = { detail = "Conversation" }
                    )
                }
                item(key = "codex-menu-command") {
                    MenuPanel(
                        label = "Command",
                        summary = "Type, speak, send or stop",
                        onClick = { detail = "Command" }
                    )
                }
            }

            "Connection" -> {
                item(key = "codex-status-panel") {
                    GroupPanel(title = "Connection") {
                        PanelValueRow(
                            label = "Status",
                            value = state.codexStatus.ifBlank { "Disconnected" }
                        )
                        if (state.codexCurrentSessionLabel.isNotBlank()) {
                            PanelDivider()
                            PanelValueRow(
                                label = "Session",
                                value = state.codexCurrentSessionLabel
                            )
                        }
                    }
                }
                if (state.codexDetail.isNotBlank()) {
                    item(key = "codex-detail-panel") {
                        GroupPanel(title = "Detail") {
                            PanelValueRow(
                                label = "Codex",
                                value = state.codexDetail
                            )
                        }
                    }
                }
            }

            "Conversation" -> {
                if (visibleMessages.isEmpty()) {
                    item(key = "codex-empty") {
                        GroupPanel(title = "Codex") {
                            PanelValueRow(
                                label = "Status",
                                value = "Ready"
                            )
                        }
                    }
                } else {
                    itemsIndexed(visibleMessages) { _, message ->
                        ChatBubble(
                            label = message.codexWatchLabel(),
                            text = message.text,
                            isUser = message.role.equals("user", ignoreCase = true),
                            error = message.role.equals("error", ignoreCase = true),
                            system = message.role.equals("system", ignoreCase = true)
                        )
                    }
                }
            }

            "Command" -> item(key = "codex-command-panel") {
                GroupPanel(title = "Command") {
                    InputCard(
                        value = draft,
                        placeholder = "Ask Codex",
                        enabled = enabled,
                        maxLength = 1200,
                        onValueChange = { draft = it },
                        onSend = { sendDraft() }
                    )
                    ToggleCard(
                        label = "Watch microphone",
                        value = "Speech goes to Codex",
                        checked = speechState.isListening,
                        enabled = enabled || speechState.isListening,
                        onCheckedChange = { checked ->
                            if (checked) {
                                onStartWatchMic()
                            } else {
                                onStopWatchMic()
                            }
                        }
                    )
                    ActionCard(
                        label = "Send to Codex",
                        enabled = enabled && draft.trim().isNotBlank(),
                        primary = true,
                        onClick = { sendDraft() }
                    )
                    ActionCard(
                        label = "Stop Codex",
                        value = "Cancel the current run",
                        enabled = enabled,
                        destructive = true,
                        onClick = onStopCodex
                    )
                }
            }
        }
        commandFeedbackItem(state)
    }
}

@Composable
private fun CodexControlPage(
    state: WatchUiState,
    pageIndex: Int,
    pageCount: Int,
    enabled: Boolean,
    onRefresh: () -> Unit,
    onResume: (String) -> Unit,
    onNewCodexSession: () -> Unit,
    onDeleteCodexSession: () -> Unit,
    onClearCodex: () -> Unit
) {
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 1)
    val sessions = state.codexSessions
    val canDelete = enabled && state.codexCurrentSessionId.isNotBlank()
    var detail by remember { mutableStateOf<String?>(null) }

    DrillDownPage(
        title = "Codex control",
        detailTitle = detail,
        pageIndex = pageIndex,
        pageCount = pageCount,
        listState = listState,
        onBack = { detail = null }
    ) {
        when (detail) {
            null -> {
                item(key = "control-menu-actions") {
                    MenuPanel(
                        label = "Session actions",
                        summary = "New, delete, refresh or clear",
                        onClick = { detail = "Session actions" }
                    )
                }
                item(key = "control-menu-sessions") {
                    MenuPanel(
                        label = "Sessions",
                        summary = "${sessions.size} available",
                        onClick = { detail = "Sessions" }
                    )
                }
            }

            "Session actions" -> item(key = "codex-session-actions-panel") {
                GroupPanel(title = "Session actions") {
                    ActionCard(
                        label = "New session",
                        enabled = enabled,
                        primary = true,
                        onClick = onNewCodexSession
                    )
                    ActionCard(
                        label = "Delete current",
                        enabled = canDelete,
                        destructive = true,
                        onClick = onDeleteCodexSession
                    )
                    ActionCard(
                        label = "Refresh sessions",
                        enabled = enabled,
                        onClick = onRefresh
                    )
                    ActionCard(
                        label = "Clear Codex chat",
                        enabled = enabled,
                        onClick = onClearCodex
                    )
                }
            }

            "Sessions" -> item(key = "codex-sessions-panel") {
                GroupPanel(title = "Sessions") {
                    if (sessions.isEmpty()) {
                        PanelValueRow(
                            label = "Status",
                            value = "No sessions"
                        )
                    } else {
                        sessions.forEach { session ->
                            CodexSessionCard(
                                session = session,
                                enabled = enabled,
                                onResume = { onResume(session.id) }
                            )
                        }
                    }
                }
            }
        }
        commandFeedbackItem(state)
    }
}

@Composable
private fun VoicePage(
    state: WatchUiState,
    speechState: WatchSpeechState,
    pageIndex: Int,
    pageCount: Int,
    enabled: Boolean,
    onToggleTts: () -> Unit,
    onNextTtsProvider: () -> Unit,
    onToggleStt: () -> Unit,
    onNextVoiceOutput: () -> Unit,
    onStartWatchMic: () -> Unit,
    onStopWatchMic: () -> Unit,
    onToggleLiveTalk: () -> Unit,
    onStopSpeaking: () -> Unit
) {
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 1)
    val speechError = speechState.error.orEmpty()
    val hasActivity = speechState.lastText.isNotBlank() || speechError.isNotBlank()
    var detail by remember { mutableStateOf<String?>(null) }

    DrillDownPage(
        title = "Voice",
        detailTitle = detail,
        pageIndex = pageIndex,
        pageCount = pageCount,
        listState = listState,
        onBack = { detail = null }
    ) {
        when (detail) {
            null -> {
                item(key = "voice-menu-input") {
                    MenuPanel(
                        label = "Speech input",
                        summary = if (speechState.isListening) "Watch microphone active" else "Watch mic and phone STT",
                        onClick = { detail = "Speech input" }
                    )
                }
                item(key = "voice-menu-output") {
                    MenuPanel(
                        label = "Speech output",
                        summary = "${state.ttsProviderLabel} · ${voiceOutputLabel(state.voiceOutputRoute)}",
                        onClick = { detail = "Speech output" }
                    )
                }
                item(key = "voice-menu-conversation") {
                    MenuPanel(
                        label = "Conversation",
                        summary = "Real-time talk and stop",
                        onClick = { detail = "Conversation" }
                    )
                }
                if (hasActivity) {
                    item(key = "voice-menu-activity") {
                        MenuPanel(
                            label = "Activity",
                            summary = if (speechError.isNotBlank()) "Speech error" else "Last heard text",
                            onClick = { detail = "Activity" }
                        )
                    }
                }
            }

            "Speech input" -> item(key = "speech-input-panel") {
                GroupPanel(title = "Speech input") {
                    ToggleCard(
                        label = "Watch microphone",
                        value = speechState.status.ifBlank { "Local speech input" },
                        checked = speechState.isListening,
                        enabled = enabled || speechState.isListening,
                        onCheckedChange = { checked ->
                            if (checked) {
                                onStartWatchMic()
                            } else {
                                onStopWatchMic()
                            }
                        }
                    )
                    ToggleCard(
                        label = "Phone STT",
                        value = "Speech recognition on the phone",
                        checked = state.sttEnabled,
                        enabled = enabled,
                        onCheckedChange = { onToggleStt() }
                    )
                }
            }

            "Speech output" -> item(key = "speech-output-panel") {
                GroupPanel(title = "Speech output") {
                    ToggleCard(
                        label = "Text to speech",
                        value = state.ttsProviderLabel,
                        checked = state.ttsEnabled,
                        enabled = enabled,
                        onCheckedChange = { onToggleTts() }
                    )
                    ActionCard(
                        label = "TTS source",
                        value = state.ttsProviderLabel,
                        enabled = enabled,
                        onClick = onNextTtsProvider
                    )
                    ActionCard(
                        label = "Voice output",
                        value = voiceOutputLabel(state.voiceOutputRoute),
                        enabled = enabled,
                        primary = state.voiceOutputRoute != WatchVoiceOutputRoutes.OFF,
                        destructive = state.voiceOutputRoute == WatchVoiceOutputRoutes.OFF,
                        onClick = onNextVoiceOutput
                    )
                }
            }

            "Conversation" -> item(key = "conversation-panel") {
                GroupPanel(title = "Conversation") {
                    ToggleCard(
                        label = "Real-time talk",
                        value = state.liveTalkState,
                        checked = state.liveTalkState != "IDLE",
                        enabled = enabled,
                        onCheckedChange = { onToggleLiveTalk() }
                    )
                    ActionCard(
                        label = "Stop speaking",
                        value = "Stop all current voice output",
                        enabled = enabled,
                        destructive = true,
                        onClick = onStopSpeaking
                    )
                }
            }

            "Activity" -> item(key = "voice-activity-panel") {
                GroupPanel(title = "Activity") {
                    if (speechState.lastText.isNotBlank()) {
                        PanelValueRow(
                            label = "Last heard",
                            value = speechState.lastText
                        )
                    }
                    if (speechState.lastText.isNotBlank() && speechError.isNotBlank()) {
                        PanelDivider()
                    }
                    if (speechError.isNotBlank()) {
                        PanelValueRow(
                            label = "Error",
                            value = speechError,
                            valueColor = LocalWatchPalette.current.error
                        )
                    }
                }
            }
        }
        commandFeedbackItem(state)
    }
}

@Composable
private fun CodexSessionCard(
    session: WatchCodexSession,
    enabled: Boolean,
    onResume: () -> Unit
) {
    ActionCard(
        label = if (session.isCurrent) "Current: ${session.label}" else session.label,
        value = if (session.isCurrent) "Active Codex session" else "Resume this session",
        enabled = enabled,
        primary = session.isCurrent,
        onClick = onResume
    )
}

@Composable
private fun GroupPanel(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = LocalWatchPalette.current
    val panelShape = RoundedCornerShape(28.dp)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(panelShape)
                .background(palette.card)
                .border(
                    width = if (palette == LightWatchPalette) 1.5.dp else 1.dp,
                    color = palette.panelBorderColor(),
                    shape = panelShape
                )
                .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            text = title,
            color = palette.accent,
            fontSize = 14.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        content()
    }
}

@Composable
private fun PanelValueRow(
    label: String,
    value: String,
    valueColor: Color = LocalWatchPalette.current.muted
) {
    val palette = LocalWatchPalette.current
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 7.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.weight(0.45f),
            text = label,
            color = palette.text,
            fontSize = 15.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            modifier =
                Modifier
                    .weight(0.55f)
                    .padding(start = 8.dp),
            text = value,
            color = valueColor,
            fontSize = 14.sp,
            lineHeight = 17.sp,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PanelDivider() {
    val palette = LocalWatchPalette.current
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 7.dp)
                .height(1.dp)
                .background(palette.outline.copy(alpha = 0.24f))
    )
}

@Composable
private fun ActionCard(
    label: String,
    value: String = "",
    enabled: Boolean = true,
    primary: Boolean = false,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    val palette = LocalWatchPalette.current
    val containerColor =
        when {
            destructive -> palette.errorContainer
            primary -> palette.accentContainer
            else -> palette.cardRaised
        }
    val contentColor =
        when {
            destructive -> if (palette == DarkWatchPalette) Color.White else palette.error
            primary -> palette.onAccentContainer
            else -> palette.text
        }

    Button(
        onClick = onClick,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .border(
                    width = if (palette == LightWatchPalette) 1.5.dp else 1.dp,
                    color = palette.panelBorderColor(),
                    shape = CircleShape
                ),
        enabled = enabled,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = containerColor,
                contentColor = contentColor
            ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                color = contentColor,
                fontSize = 16.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (value.isNotBlank()) {
                Text(
                    text = value,
                    color = contentColor.copy(alpha = 0.78f),
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ToggleCard(
    label: String,
    value: String = "",
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val palette = LocalWatchPalette.current
    val secondaryLabel: (@Composable RowScope.() -> Unit)? =
        if (value.isBlank()) {
            null
        } else {
            {
                Text(
                    text = value,
                    color = palette.muted,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

    SwitchButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp)
                .border(
                    width = if (palette == LightWatchPalette) 1.5.dp else 1.dp,
                    color = palette.panelBorderColor(),
                    shape = CircleShape
                ),
        enabled = enabled,
        secondaryLabel = secondaryLabel,
        label = {
            Text(
                text = label,
                fontSize = 16.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}

@Composable
private fun InputCard(
    value: String,
    placeholder: String,
    enabled: Boolean,
    maxLength: Int,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit
) {
    val palette = LocalWatchPalette.current
    val inputShape = RoundedCornerShape(24.dp)
    BasicTextField(
        value = value,
        onValueChange = { onValueChange(it.take(maxLength)) },
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp, max = 92.dp)
                .clip(inputShape)
                .background(if (enabled) palette.cardRaised else palette.disabled)
                .border(
                    width = if (palette == LightWatchPalette) 1.5.dp else 1.dp,
                    color = palette.panelBorderColor(),
                    shape = inputShape
                )
                .padding(horizontal = 16.dp, vertical = 13.dp),
        enabled = enabled,
        textStyle =
            TextStyle(
                color = palette.text,
                fontSize = 16.sp,
                lineHeight = 20.sp
            ),
        cursorBrush = SolidColor(palette.accent),
        maxLines = 3,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { onSend() }),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isBlank()) {
                    Text(
                        text = placeholder,
                        color = palette.muted,
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                innerTextField()
            }
        }
    )
}

@Composable
private fun ChatBubble(
    label: String,
    text: String,
    isUser: Boolean,
    error: Boolean = false,
    system: Boolean = false
) {
    val palette = LocalWatchPalette.current
    val background =
        when {
            error -> palette.errorContainer
            isUser -> palette.userBubble
            system -> palette.systemBubble
            else -> palette.agentBubble
        }
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleShape =
        RoundedCornerShape(
            topStart = 24.dp,
            topEnd = 24.dp,
            bottomStart = if (isUser) 24.dp else 7.dp,
            bottomEnd = if (isUser) 7.dp else 24.dp
        )

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth(if (isUser) 0.86f else 0.94f)
                    .clip(bubbleShape)
                    .background(background)
                    .border(
                        width = if (palette == LightWatchPalette) 1.5.dp else 1.dp,
                        color = palette.panelBorderColor(),
                        shape = bubbleShape
                    )
                    .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = label,
                color =
                    when {
                        error -> palette.error
                        isUser -> palette.accent
                        else -> palette.muted
                    },
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = if (isUser) TextAlign.End else TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = text,
                color = palette.text,
                fontSize = 15.sp,
                lineHeight = 19.sp,
                textAlign = if (isUser) TextAlign.End else TextAlign.Start
            )
        }
    }
}

private fun ScalingLazyListScope.commandFeedbackItem(state: WatchUiState) {
    val errorText = state.lastError?.trim().orEmpty()
    val commandFailed = state.lastCommandOk == false
    if (errorText.isBlank() && !commandFailed) {
        return
    }
    val feedbackText = errorText.ifBlank { state.lastCommandLabel.trim().ifBlank { "Command failed" } }

    item(key = "command-feedback") {
        GroupPanel(title = "Error") {
            PanelValueRow(
                label = "Command",
                value = feedbackText,
                valueColor = LocalWatchPalette.current.error
            )
        }
    }
}

private fun WatchChatMessage.watchLabel(): String =
    if (role.equals("user", ignoreCase = true)) "YOU" else "AGENT"

private fun WatchChatMessage.codexWatchLabel(): String =
    when {
        role.equals("user", ignoreCase = true) -> "YOU"
        role.equals("error", ignoreCase = true) -> "ERROR"
        role.equals("system", ignoreCase = true) -> "SYSTEM"
        else -> "CODEX"
    }

private fun onOff(value: Boolean): String = if (value) "On" else "Off"

private fun onlineOffline(value: Boolean): String = if (value) "Online" else "Offline"

private fun voiceOutputLabel(route: String): String =
    when (route) {
        WatchVoiceOutputRoutes.GLASSES -> "Glasses"
        WatchVoiceOutputRoutes.PHONE -> "Phone"
        WatchVoiceOutputRoutes.WATCH -> "Watch"
        WatchVoiceOutputRoutes.OFF -> "Off"
        else -> "Glasses"
    }
