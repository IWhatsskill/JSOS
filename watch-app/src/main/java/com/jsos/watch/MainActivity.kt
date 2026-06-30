package com.jsos.watch

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.jsos.shared.WatchChatMessage
import com.jsos.shared.WatchCodexSession
import com.jsos.shared.WatchCoreIds
import com.jsos.shared.WatchVoiceOutputRoutes

private val ScreenTop = Color.Black
private val ScreenMid = Color.Black
private val ScreenBottom = Color.Black
private val Background = ScreenBottom
private val TextPrimary = Color(0xFF171A1D)
private val TextMuted = Color(0xFF6F777D)
private val OnlineGreen = Color(0xFF22A35A)
private val WarningYellow = Color(0xFFB88414)
private val ErrorRed = Color(0xFFD84B4B)
private val BrandCyan = Color(0xFF168CAA)
private val BrandBlue = Color(0xFF168CAA)
private val PanelBorder = Color(0x3D168CAA)
private val CodexBlue = Color(0xFFE9F3F5)
private val StopRed = Color(0xFFF3DDDD)
private val TalkGreen = Color(0xFFDDF6E6)
private val Neutral = Color(0xFFFFFFFF)
private val SessionPurple = Color(0xFFE9ECE8)
private val ModelOlive = Color(0xFFF1F3F0)
private val FieldSurface = Color(0xFFFFFFFF)
private val DisabledSurface = Color(0xFFE8ECE8)
private val BackgroundText = Color(0xFFF7F8F6)
private val BackgroundMuted = Color(0xFFC8D0CC)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val bridge = remember { WatchBridge(applicationContext) }
            var speechState by remember { mutableStateOf(WatchSpeechState()) }
            var sendSpeechToCodex by remember { mutableStateOf(false) }
            val speechController = remember {
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
            val micPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) {
                    speechController.start()
                } else {
                    speechState = WatchSpeechState(
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
                val granted = ContextCompat.checkSelfPermission(
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
                onStopSpeaking = bridge::stopSpeaking,
                onCloseHud = bridge::closeHud,
                onToggleLiveTalk = bridge::toggleLiveTalk,
                onToggleTts = bridge::toggleTts,
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
    onStopSpeaking: () -> Unit,
    onCloseHud: () -> Unit,
    onToggleLiveTalk: () -> Unit,
    onToggleTts: () -> Unit,
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
    onSpeechTargetChanged: (Boolean) -> Unit
) {
    val pages = listOf("CHAT", "STATUS", "CTRL", "SESSION", "CODEX", "VOICE")
    val pagerState = rememberPagerState(pageCount = { pages.size })

    LaunchedEffect(pagerState.currentPage) {
        onSpeechTargetChanged(pagerState.currentPage == 4)
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = ScreenBottom
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                WatchMatrixBackground(modifier = Modifier.fillMaxSize())
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) { page ->
                        when (page) {
                            0 -> ChatPage(
                                state = state,
                                speechState = speechState,
                                onStopSpeaking = onStopSpeaking,
                                onRequestMoreChat = onRequestMoreChat,
                                onStartWatchMic = onStartWatchMic,
                                onStopWatchMic = onStopWatchMic,
                                onSendAssistantCommand = onSendAssistantCommand
                            )
                            1 -> StatusPage(state = state)
                            2 -> ControlsPage(
                                state = state,
                                enabled = !state.commandPending,
                                onCloseHud = onCloseHud,
                                onRequestState = onRequestState
                            )
                            3 -> SessionModelPage(
                                state = state,
                                enabled = !state.commandPending,
                                onPreviousSession = onPreviousSession,
                                onNextSession = onNextSession,
                                onPreviousModel = onPreviousModel,
                                onNextModel = onNextModel,
                                onResetSession = onResetSession,
                                onClearSession = onClearSession
                            )
                            4 -> CodexPage(
                                state = state,
                                speechState = speechState,
                                enabled = !state.commandPending,
                                onRefresh = onRequestCodexSessions,
                                onResume = onResumeCodexSession,
                                onStartWatchMic = onStartWatchMic,
                                onStopWatchMic = onStopWatchMic,
                                onSendCodexInput = onSendCodexInput,
                                onStopCodex = onStopCodex,
                                onClearCodex = onClearCodex
                            )
                            5 -> VoicePage(
                                state = state,
                                speechState = speechState,
                                enabled = !state.commandPending,
                                onToggleTts = onToggleTts,
                                onToggleStt = onToggleStt,
                                onNextVoiceOutput = onNextVoiceOutput,
                                onStartWatchMic = onStartWatchMic,
                                onStopWatchMic = onStopWatchMic,
                                onToggleLiveTalk = onToggleLiveTalk,
                                onStopSpeaking = onStopSpeaking
                            )
                        }
                    }
                    PageDots(pageCount = pages.size, selectedPage = pagerState.currentPage)
                }
            }
        }
    }
}

@Composable
private fun WatchMatrixBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(ScreenTop, ScreenMid, ScreenBottom),
                startY = 0f,
                endY = size.height,
            )
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.00f),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.28f, size.height * 0.10f),
                radius = size.width * 0.72f,
            )
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    BrandCyan.copy(alpha = 0.00f),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.70f, size.height * 0.70f),
                radius = size.width * 0.86f,
            )
        )
    }
}
@Composable
private fun StatusPage(state: WatchUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 3.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        SectionLabel("JSOS STATUS")
        StatusPanel {
            StatusPanelLine("CORE", yesNo(state.coreOnline), if (state.coreOnline) OnlineGreen else ErrorRed)
            StatusPanelLine("GATEWAY", onlineOffline(state.gatewayOnline), if (state.gatewayOnline) OnlineGreen else ErrorRed)
            StatusPanelLine("HUD", onOff(state.hudOnline), if (state.hudOnline) OnlineGreen else ErrorRed)
        }
        StatusPanel {
            StatusPanelLine("SESSION", state.currentSession.ifBlank { "--" }, TextPrimary)
            StatusPanelLine("MODEL", state.currentModel.ifBlank { "--" }, TextPrimary)
        }
        StatusPanel {
            StatusPanelLine("LIVE", state.liveTalkState, if (state.liveTalkState == "IDLE") TextPrimary else OnlineGreen)
            StatusPanelLine("OUTPUT", voiceOutputLabel(state.voiceOutputRoute), if (state.voiceOutputRoute == WatchVoiceOutputRoutes.OFF) ErrorRed else TextPrimary)
            StatusPanelLine("AUDIO", state.watchAudioStatus, if (state.watchAudioStatus.contains("READY", ignoreCase = true)) OnlineGreen else ErrorRed)
        }
    }
}

@Composable
private fun StatusPanel(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(FieldSurface)
            .border(1.dp, PanelBorder.copy(alpha = 0.72f), RoundedCornerShape(18.dp))
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        content = content
    )
}

@Composable
private fun StatusPanelLine(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TextPrimary,
            fontSize = 7.sp,
            lineHeight = 8.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            modifier = Modifier
                .padding(start = 6.dp)
                .weight(1f),
            text = value,
            color = valueColor,
            fontSize = 7.sp,
            lineHeight = 8.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun WatchHeader(state: WatchUiState, pageTitle: String) {
    Text(
        text = "JSOS  $pageTitle",
        color = BackgroundText,
        fontSize = 9.sp,
        lineHeight = 10.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        maxLines = 1
    )
    Text(
        text = "SELECTED: ${WatchCoreIds.label(state.selectedCoreId)}",
        color = BrandCyan,
        fontSize = 9.sp,
        lineHeight = 10.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        maxLines = 1
    )
    Text(
        text = "CONNECTED: ${yesNo(state.coreOnline)}",
        color = if (state.coreOnline) OnlineGreen else WarningYellow,
        fontSize = 9.sp,
        lineHeight = 10.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
    Text(
        text = "GATEWAY: ${onlineOffline(state.gatewayOnline)}",
        color = if (state.gatewayOnline) OnlineGreen else ErrorRed,
        fontSize = 9.sp,
        lineHeight = 10.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
    Text(
        text = "HUD ${onOff(state.hudOnline)}  LIVE ${state.liveTalkState}",
        color = BackgroundMuted,
        fontSize = 7.sp,
        lineHeight = 8.sp,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun ChatPage(
    state: WatchUiState,
    speechState: WatchSpeechState,
    onStopSpeaking: () -> Unit,
    onRequestMoreChat: () -> Unit,
    onStartWatchMic: () -> Unit,
    onStopWatchMic: () -> Unit,
    onSendAssistantCommand: (String) -> Unit
) {
    val chatScrollState = rememberScrollState()
    var draft by remember { mutableStateOf("") }

    fun sendDraft() {
        val clean = draft.trim()
        if (clean.isNotBlank()) {
            onSendAssistantCommand(clean)
            draft = ""
        }
    }

    LaunchedEffect(state.chatMessages.size, state.lastAnswer, state.chatPrependedCount) {
        if (state.chatPrependedCount <= 0) {
            chatScrollState.animateScrollTo(chatScrollState.maxValue)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 12.dp, bottom = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChatMiniButton(
                label = if (state.chatLoadingMore) "LOAD" else if (state.chatHasMore) "MORE" else "DONE",
                color = Neutral,
                enabled = !state.commandPending && !state.chatLoadingMore && state.chatHasMore && state.chatMessages.isNotEmpty(),
                onClick = onRequestMoreChat
            )
            TinyStatusLine("${state.chatMessages.size} MSG")
        }
        Spacer(modifier = Modifier.height(2.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(chatScrollState),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val messages = state.chatMessages
            if (messages.isEmpty() && state.lastAnswer.isBlank()) {
                EmptyText("NO CHAT")
            } else if (messages.isEmpty()) {
                ChatBubble(
                    label = "AGENT",
                    text = state.lastAnswer,
                    color = FieldSurface
                )
            } else {
                messages.forEach { message ->
                    ChatBubble(
                        label = message.watchLabel(),
                        text = message.text,
                        color = message.watchBubbleColor()
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        BasicTextField(
            value = draft,
            onValueChange = { draft = it.take(600) },
            enabled = !state.commandPending,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 30.dp, max = 34.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(FieldSurface)
                .padding(horizontal = 9.dp, vertical = 5.dp),
            textStyle = TextStyle(
                color = TextPrimary,
                fontSize = 9.sp,
                lineHeight = 10.sp
            ),
            maxLines = 1,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { sendDraft() }),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (draft.isBlank()) {
                        Text(
                            text = "TYPE",
                            color = TextMuted,
                            fontSize = 8.sp,
                            lineHeight = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    innerTextField()
                }
            }
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChatMiniButton(
                label = if (speechState.isListening) "MIC" else "STT",
                color = if (speechState.isListening) TalkGreen else Neutral,
                enabled = !state.commandPending || speechState.isListening,
                onClick = {
                    if (speechState.isListening) {
                        onStopWatchMic()
                    } else {
                        onStartWatchMic()
                    }
                }
            )
            ChatMiniButton(
                label = "SEND",
                color = TalkGreen,
                enabled = !state.commandPending && draft.trim().isNotBlank(),
                onClick = { sendDraft() }
            )
            ChatMiniButton(
                label = "STOP",
                color = StopRed,
                enabled = !state.commandPending,
                onClick = onStopSpeaking
            )
        }
    }
}
@Composable
private fun ControlsPage(
    state: WatchUiState,
    enabled: Boolean,
    onCloseHud: () -> Unit,
    onRequestState: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 22.dp, bottom = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SectionLabel("CORE")
        TinyStatusLine("SELECTED: ${WatchCoreIds.label(state.selectedCoreId)}")
        TinyStatusLine("CONNECTED: ${yesNo(state.coreOnline)}")
        TinyStatusLine("GATEWAY: ${onlineOffline(state.gatewayOnline)}")
        WideCommandButton("CLOSE HUD", Neutral, enabled, onCloseHud)
        WideCommandButton("STATE", Neutral, enabled, onRequestState)
    }
}
@Composable
private fun SessionModelPage(
    state: WatchUiState,
    enabled: Boolean,
    onPreviousSession: () -> Unit,
    onNextSession: () -> Unit,
    onPreviousModel: () -> Unit,
    onNextModel: () -> Unit,
    onResetSession: () -> Unit,
    onClearSession: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 20.dp, bottom = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        SectionLabel("SESSION")
        DetailText(state.currentSession.ifBlank { "--" })
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CommandButton("SES -", SessionPurple, enabled, onPreviousSession)
            CommandButton("SES +", SessionPurple, enabled, onNextSession)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CommandButton("RESET", StopRed, enabled, onResetSession)
            CommandButton("CLEAR", Neutral, enabled, onClearSession)
        }
        Spacer(modifier = Modifier.height(8.dp))
        SectionLabel("MODEL")
        DetailText(state.currentModel.ifBlank { "--" })
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CommandButton("MOD -", ModelOlive, enabled, onPreviousModel)
            CommandButton("MOD +", ModelOlive, enabled, onNextModel)
        }
    }
}

@Composable
private fun CodexPage(
    state: WatchUiState,
    speechState: WatchSpeechState,
    enabled: Boolean,
    onRefresh: () -> Unit,
    onResume: (String) -> Unit,
    onStartWatchMic: () -> Unit,
    onStopWatchMic: () -> Unit,
    onSendCodexInput: (String) -> Unit,
    onStopCodex: () -> Unit,
    onClearCodex: () -> Unit
) {
    val codexScrollState = rememberScrollState()
    val sessionScrollState = rememberScrollState()
    var draft by remember { mutableStateOf("") }
    var showSessionPicker by remember { mutableStateOf(false) }
    val sessions = state.codexSessions

    fun sendDraft() {
        val clean = draft.trim()
        if (clean.isNotBlank()) {
            onSendCodexInput(clean)
            draft = ""
        }
    }

    LaunchedEffect(state.codexMessages.size, state.codexStatus) {
        if (!showSessionPicker) {
            codexScrollState.animateScrollTo(codexScrollState.maxValue)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 12.dp, bottom = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (showSessionPicker) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChatMiniButton("REFRESH", CodexBlue, enabled, onRefresh)
                ChatMiniButton("CLOSE", Neutral, enabled = true, onClick = { showSessionPicker = false })
            }
            TinyStatusLine("CODEX RESUME")
            TinyStatusLine(
                state.codexCurrentSessionLabel
                    .ifBlank { state.codexDetail }
                    .ifBlank { "SELECT SESSION" }
            )
            Spacer(modifier = Modifier.height(4.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(sessionScrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (sessions.isEmpty()) {
                    EmptyText("NO SESSIONS")
                } else {
                    sessions.forEach { session ->
                        WideCommandButton(
                            label = "${if (session.isCurrent) "* " else ""}RESUME ${session.label}",
                            color = if (session.isCurrent) TalkGreen else Neutral,
                            enabled = enabled,
                            onClick = {
                                onResume(session.id)
                                showSessionPicker = false
                            }
                        )
                    }
                }
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChatMiniButton(
                    label = "SESSION",
                    color = CodexBlue,
                    enabled = enabled,
                    onClick = {
                        showSessionPicker = true
                        onRefresh()
                    }
                )
                ChatMiniButton("STOP", StopRed, enabled, onStopCodex)
                ChatMiniButton("CLR", Neutral, enabled, onClearCodex)
            }
            TinyStatusLine("CODEX ${state.codexStatus}")
            TinyStatusLine(
                state.codexCurrentSessionLabel
                    .ifBlank { state.codexDetail }
                    .ifBlank { "ADMIN" }
            )
            Spacer(modifier = Modifier.height(2.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(codexScrollState),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (state.codexMessages.isEmpty()) {
                    EmptyText("CODEX READY")
                } else {
                    state.codexMessages.forEach { message ->
                        ChatBubble(
                            label = message.codexWatchLabel(),
                            text = message.text,
                            color = message.codexWatchBubbleColor()
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            BasicTextField(
                value = draft,
                onValueChange = { draft = it.take(1200) },
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 30.dp, max = 34.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(FieldSurface)
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                textStyle = TextStyle(
                    color = TextPrimary,
                    fontSize = 9.sp,
                    lineHeight = 10.sp
                ),
                maxLines = 1,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { sendDraft() }),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (draft.isBlank()) {
                            Text(
                                text = "ASK CODEX",
                                color = TextMuted,
                                fontSize = 8.sp,
                                lineHeight = 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                }
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChatMiniButton(
                    label = if (speechState.isListening) "MIC" else "STT",
                    color = if (speechState.isListening) TalkGreen else Neutral,
                    enabled = enabled || speechState.isListening,
                    onClick = {
                        if (speechState.isListening) {
                            onStopWatchMic()
                        } else {
                            onStartWatchMic()
                        }
                    }
                )
                ChatMiniButton(
                    label = "SEND",
                    color = TalkGreen,
                    enabled = enabled && draft.trim().isNotBlank(),
                    onClick = { sendDraft() }
                )
            }
        }
    }
}
@Composable
private fun VoicePage(
    state: WatchUiState,
    speechState: WatchSpeechState,
    enabled: Boolean,
    onToggleTts: () -> Unit,
    onToggleStt: () -> Unit,
    onNextVoiceOutput: () -> Unit,
    onStartWatchMic: () -> Unit,
    onStopWatchMic: () -> Unit,
    onToggleLiveTalk: () -> Unit,
    onStopSpeaking: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 20.dp, bottom = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        SectionLabel("VOICE")
        TinyStatusLine("TTS: ${onOff(state.ttsEnabled)}  STT: ${speechState.status}")
        TinyStatusLine("OUTPUT: ${voiceOutputLabel(state.voiceOutputRoute)}")
        TinyStatusLine("AUDIO: ${state.watchAudioStatus}")
        if (speechState.lastText.isNotBlank()) {
            TinyStatusLine("SAID: ${speechState.lastText}")
        }
        WideCommandButton(
            label = if (speechState.isListening) "STT STOP" else "STT",
            color = if (speechState.isListening) TalkGreen else Neutral,
            enabled = enabled || speechState.isListening,
            onClick = {
                if (speechState.isListening) {
                    onStopWatchMic()
                } else {
                    onStartWatchMic()
                }
            }
        )
        WideCommandButton(
            label = "TTS ${if (state.ttsEnabled) "OFF" else "ON"}",
            color = if (state.ttsEnabled) TalkGreen else Neutral,
            enabled = enabled,
            onClick = onToggleTts
        )
        WideCommandButton(
            label = "OUTPUT ${voiceOutputLabel(state.voiceOutputRoute)}",
            color = voiceOutputColor(state.voiceOutputRoute),
            enabled = enabled,
            onClick = onNextVoiceOutput
        )
        WideCommandButton(
            label = if (state.liveTalkState == "IDLE") "LIVE TALK" else "LIVE OFF",
            color = TalkGreen,
            enabled = enabled,
            onClick = onToggleLiveTalk
        )
        WideCommandButton(
            label = "STOP SPEAKING",
            color = StopRed,
            enabled = enabled,
            onClick = onStopSpeaking
        )
    }
}
@Composable
private fun PageDots(pageCount: Int, selectedPage: Int) {
    Row(
        modifier = Modifier.padding(top = 6.dp, bottom = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            Box(
                modifier = Modifier
                    .size(if (index == selectedPage) 7.dp else 5.dp)
                    .clip(CircleShape)
                    .background(if (index == selectedPage) OnlineGreen else Color(0xFF314039))
            )
        }
    }
}

@Composable
private fun CommandFeedback(state: WatchUiState) {
    val errorText = state.lastError?.trim().orEmpty()
    val feedbackText = errorText.ifBlank { state.lastCommandLabel }.trim()
    if (feedbackText.isBlank()) {
        Spacer(modifier = Modifier.height(11.dp))
        return
    }
    Spacer(modifier = Modifier.height(3.dp))
    Text(
        text = feedbackText,
        color = when {
            errorText.isNotBlank() -> ErrorRed
            state.lastCommandOk == true -> OnlineGreen
            state.lastCommandOk == false -> ErrorRed
            else -> WarningYellow
        },
        fontSize = 8.sp,
        lineHeight = 9.sp,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun ChatBubble(label: String, text: String, color: Color) {
    val role = label.uppercase()
    val isUser = role == "YOU"
    val isSystem = role == "SYSTEM"
    val shape = RoundedCornerShape(16.dp)
    val alignment = when {
        isUser -> Alignment.CenterEnd
        isSystem -> Alignment.Center
        else -> Alignment.CenterStart
    }
    val widthFraction = when {
        isUser -> 0.78f
        isSystem -> 0.80f
        else -> 0.88f
    }
    val textAlign = if (isUser) TextAlign.End else TextAlign.Start

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(widthFraction)
                .clip(shape)
                .background(color)
                .border(1.dp, PanelBorder.copy(alpha = 0.55f), shape)
                .padding(horizontal = 9.dp, vertical = 6.dp)
        ) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = label,
                color = TextPrimary,
                fontSize = 7.sp,
                lineHeight = 8.sp,
                fontWeight = FontWeight.Bold,
                textAlign = textAlign,
                maxLines = 1
            )
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = text,
                color = TextPrimary,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                textAlign = textAlign
            )
        }
    }
}

private fun WatchChatMessage.watchLabel(): String =
    if (role.equals("user", ignoreCase = true)) "YOU" else "AGENT"

private fun WatchChatMessage.watchBubbleColor(): Color =
    FieldSurface

private fun WatchChatMessage.codexWatchLabel(): String =
    when {
        role.equals("user", ignoreCase = true) -> "YOU"
        role.equals("error", ignoreCase = true) -> "ERROR"
        role.equals("system", ignoreCase = true) -> "SYSTEM"
        else -> "CODEX"
    }

private fun WatchChatMessage.codexWatchBubbleColor(): Color =
    when {
        role.equals("user", ignoreCase = true) -> FieldSurface
        role.equals("error", ignoreCase = true) -> StopRed
        role.equals("system", ignoreCase = true) -> FieldSurface
        else -> FieldSurface
    }

private fun commandFillColor(color: Color, enabled: Boolean): Color =
    when {
        !enabled -> DisabledSurface
        color == TalkGreen || color == OnlineGreen -> TalkGreen
        color == StopRed || color == ErrorRed -> StopRed
        else -> FieldSurface
    }

@Composable
private fun ChatMiniButton(
    label: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(999.dp)
    val fill = commandFillColor(color, enabled)
    Box(
        modifier = Modifier
            .widthIn(min = 38.dp, max = 56.dp)
            .height(24.dp)
            .clip(shape)
            .background(fill)
            .border(1.dp, if (enabled) PanelBorder.copy(alpha = 0.72f) else PanelBorder.copy(alpha = 0.16f), shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 7.sp,
            lineHeight = 8.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SmallCommandButton(
    label: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(999.dp)
    val fill = commandFillColor(color, enabled)
    Box(
        modifier = Modifier
            .widthIn(min = 46.dp, max = 66.dp)
            .height(27.dp)
            .clip(shape)
            .background(fill)
            .border(1.dp, if (enabled) PanelBorder.copy(alpha = 0.72f) else PanelBorder.copy(alpha = 0.16f), shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (enabled) TextPrimary else TextMuted,
            fontSize = 7.sp,
            lineHeight = 8.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
@Composable
private fun CommandButton(
    label: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(999.dp)
    val fill = commandFillColor(color, enabled)
    Box(
        modifier = Modifier
            .widthIn(min = 56.dp, max = 66.dp)
            .height(28.dp)
            .clip(shape)
            .background(fill)
            .border(1.dp, if (enabled) PanelBorder.copy(alpha = 0.72f) else PanelBorder.copy(alpha = 0.16f), shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (enabled) TextPrimary else TextMuted,
            fontSize = 7.sp,
            lineHeight = 8.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
@Composable
private fun WideCommandButton(
    label: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(999.dp)
    val fill = commandFillColor(color, enabled)
    Box(
        modifier = Modifier
            .widthIn(min = 112.dp, max = 138.dp)
            .height(29.dp)
            .clip(shape)
            .background(fill)
            .border(1.dp, if (enabled) PanelBorder.copy(alpha = 0.72f) else PanelBorder.copy(alpha = 0.16f), shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (enabled) TextPrimary else TextMuted,
            fontSize = 7.sp,
            lineHeight = 8.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
@Composable
private fun WatchLine(label: String, value: String) {
    Text(
        modifier = Modifier.padding(horizontal = 8.dp),
        text = "$label $value",
        color = BackgroundText,
        fontSize = 7.sp,
        lineHeight = 8.sp,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}
@Composable
private fun SectionLabel(label: String) {
    Text(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(FieldSurface)
            .border(1.dp, PanelBorder.copy(alpha = 0.70f), RoundedCornerShape(999.dp))
            .padding(horizontal = 9.dp, vertical = 3.dp),
        text = label,
        color = TextPrimary,
        fontSize = 9.sp,
        lineHeight = 10.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun TinyStatusLine(value: String) {
    Text(
        modifier = Modifier.padding(horizontal = 8.dp),
        text = value,
        color = BackgroundMuted,
        fontSize = 7.sp,
        lineHeight = 8.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}
@Composable
private fun DetailText(value: String) {
    Text(
        modifier = Modifier.padding(horizontal = 12.dp),
        text = value,
        color = BackgroundText,
        fontSize = 9.sp,
        lineHeight = 10.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis
    )
}
@Composable
private fun EmptyText(text: String) {
    Text(
        text = text,
        color = BackgroundMuted,
        fontSize = 9.sp,
        lineHeight = 10.sp,
        textAlign = TextAlign.Center,
        maxLines = 1
    )
}

private fun onOff(value: Boolean): String = if (value) "ON" else "OFF"

private fun yesNo(value: Boolean): String = if (value) "YES" else "NO"

private fun onlineOffline(value: Boolean): String = if (value) "ONLINE" else "OFFLINE"

private fun voiceOutputLabel(route: String): String =
    when (route) {
        WatchVoiceOutputRoutes.GLASSES -> "GLASSES"
        WatchVoiceOutputRoutes.PHONE -> "PHONE"
        WatchVoiceOutputRoutes.WATCH -> "WATCH"
        WatchVoiceOutputRoutes.OFF -> "OFF"
        else -> "GLASSES"
    }

private fun voiceOutputColor(route: String): Color =
    when (route) {
        WatchVoiceOutputRoutes.GLASSES -> Neutral
        WatchVoiceOutputRoutes.PHONE -> TalkGreen
        WatchVoiceOutputRoutes.WATCH -> SessionPurple
        WatchVoiceOutputRoutes.OFF -> StopRed
        else -> Neutral
    }
