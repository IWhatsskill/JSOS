package com.jsos.watch

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.jsos.shared.WatchChatMessage
import com.jsos.shared.WatchCodexSession
import com.jsos.shared.WatchCoreIds
import com.jsos.shared.WatchVoiceOutputRoutes

private val Background = Color(0xFF050608)
private val TextPrimary = Color(0xFFEAF7F0)
private val TextMuted = Color(0xFF8EA99B)
private val OnlineGreen = Color(0xFF72F7B1)
private val WarningYellow = Color(0xFFFFC857)
private val ErrorRed = Color(0xFFFF5F6D)
private val CodexBlue = Color(0xFF13364A)
private val StopRed = Color(0xFF4A151D)
private val TalkGreen = Color(0xFF103524)
private val Neutral = Color(0xFF252B31)
private val SessionPurple = Color(0xFF2C263F)
private val ModelOlive = Color(0xFF2F3422)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val bridge = remember { WatchBridge(applicationContext) }
            var speechState by remember { mutableStateOf(WatchSpeechState()) }
            val speechController = remember {
                WatchSpeechController(
                    context = applicationContext,
                    onState = { speechState = it },
                    onFinalText = { text -> bridge.sendAssistantCommand(text) }
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
                onRequestState = bridge::requestState,
                onPreviousSession = bridge::previousSession,
                onNextSession = bridge::nextSession,
                onPreviousModel = bridge::previousModel,
                onNextModel = bridge::nextModel,
                onResetSession = { bridge.sendAssistantCommand("/reset") },
                onClearSession = { bridge.sendAssistantCommand("/clear") },
                onRequestCodexSessions = bridge::requestCodexSessions,
                onResumeCodexSession = bridge::resumeCodexSession
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
    onRequestState: () -> Unit,
    onPreviousSession: () -> Unit,
    onNextSession: () -> Unit,
    onPreviousModel: () -> Unit,
    onNextModel: () -> Unit,
    onResetSession: () -> Unit,
    onClearSession: () -> Unit,
    onRequestCodexSessions: () -> Unit,
    onResumeCodexSession: (String) -> Unit
) {
    val pages = listOf("CHAT", "CTRL", "SESSION", "CODEX", "VOICE")
    val pagerState = rememberPagerState(pageCount = { pages.size })

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Background)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                WatchHeader(state = state, pageTitle = pages[pagerState.currentPage])
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) { page ->
                    when (page) {
                        0 -> ChatPage(
                            state = state,
                            onToggleLiveTalk = onToggleLiveTalk,
                            onStopSpeaking = onStopSpeaking
                        )
                        1 -> ControlsPage(
                            state = state,
                            enabled = !state.commandPending,
                            onCloseHud = onCloseHud,
                            onRequestState = onRequestState
                        )
                        2 -> SessionModelPage(
                            state = state,
                            enabled = !state.commandPending,
                            onPreviousSession = onPreviousSession,
                            onNextSession = onNextSession,
                            onPreviousModel = onPreviousModel,
                            onNextModel = onNextModel,
                            onResetSession = onResetSession,
                            onClearSession = onClearSession
                        )
                        3 -> CodexPage(
                            sessions = state.codexSessions,
                            enabled = !state.commandPending,
                            onRefresh = onRequestCodexSessions,
                            onResume = onResumeCodexSession
                        )
                        4 -> VoicePage(
                            state = state,
                            speechState = speechState,
                            enabled = !state.commandPending,
                            onToggleTts = onToggleTts,
                            onToggleStt = onToggleStt,
                            onNextVoiceOutput = onNextVoiceOutput,
                            onStartWatchMic = onStartWatchMic,
                            onStopWatchMic = onStopWatchMic
                        )
                    }
                }
                PageDots(pageCount = pages.size, selectedPage = pagerState.currentPage)
                CommandFeedback(state)
            }
        }
    }
}

@Composable
private fun WatchHeader(state: WatchUiState, pageTitle: String) {
    Text(
        text = "JSOS  $pageTitle",
        color = TextPrimary,
        fontSize = 12.sp,
        lineHeight = 13.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        maxLines = 1
    )
    Text(
        text = "SELECTED: ${WatchCoreIds.label(state.selectedCoreId)}",
        color = Color(0xFF72D7FF),
        fontSize = 9.sp,
        lineHeight = 10.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        maxLines = 1
    )
    Text(
        text = "CONNECTED: ${yesNo(state.coreOnline)}",
        color = if (state.coreOnline) OnlineGreen else WarningYellow,
        fontSize = 11.sp,
        lineHeight = 12.sp,
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
        color = TextMuted,
        fontSize = 8.sp,
        lineHeight = 9.sp,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun ChatPage(
    state: WatchUiState,
    onToggleLiveTalk: () -> Unit,
    onStopSpeaking: () -> Unit
) {
    val chatScrollState = rememberScrollState()
    LaunchedEffect(state.chatMessages.size, state.lastAnswer) {
        chatScrollState.animateScrollTo(chatScrollState.maxValue)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        WatchLine(label = "SESSION", value = state.currentSession.ifBlank { "--" })
        WatchLine(label = "MODEL", value = state.currentModel.ifBlank { "--" })
        Spacer(modifier = Modifier.height(5.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(chatScrollState),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val messages = state.chatMessages
            if (messages.isEmpty() && state.lastAnswer.isBlank()) {
                EmptyText("NO CHAT")
            } else if (messages.isEmpty()) {
                ChatBubble(
                    label = "AGENT",
                    text = state.lastAnswer,
                    color = Color(0xFF10252F)
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
        Spacer(modifier = Modifier.height(7.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CommandButton(
                label = if (state.liveTalkState == "IDLE") "TALK" else "TALK OFF",
                color = TalkGreen,
                enabled = !state.commandPending,
                onClick = onToggleLiveTalk
            )
            CommandButton(
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
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        SectionLabel("CORE")
        TinyStatusLine("SELECTED: JSOS CORE")
        TinyStatusLine("CONNECTED: ${yesNo(state.coreOnline)}")
        TinyStatusLine("GATEWAY: ${onlineOffline(state.gatewayOnline)}")
        Spacer(modifier = Modifier.height(10.dp))
        WideCommandButton("CLOSE HUD", Color(0xFF123744), enabled, onCloseHud)
        Spacer(modifier = Modifier.height(10.dp))
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
            .padding(top = 4.dp, bottom = 8.dp),
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
    sessions: List<WatchCodexSession>,
    enabled: Boolean,
    onRefresh: () -> Unit,
    onResume: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        WideCommandButton("REFRESH", CodexBlue, enabled, onRefresh)
        Spacer(modifier = Modifier.height(7.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (sessions.isEmpty()) {
                EmptyText("NO SESSIONS")
            } else {
                sessions.take(6).forEach { session ->
                    WideCommandButton(
                        label = "${if (session.isCurrent) "* " else ""}${session.label}",
                        color = if (session.isCurrent) Color(0xFF1D4A5D) else Color(0xFF183346),
                        enabled = enabled,
                        onClick = { onResume(session.id) }
                    )
                }
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
    onStopWatchMic: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 4.dp)
            .verticalScroll(rememberScrollState()),
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
            color = if (speechState.isListening) TalkGreen else Color(0xFF123744),
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
    }
}

@Composable
private fun PageDots(pageCount: Int, selectedPage: Int) {
    Row(
        modifier = Modifier.padding(top = 3.dp),
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
    if (state.lastCommandLabel.isBlank()) {
        Spacer(modifier = Modifier.height(11.dp))
        return
    }
    Spacer(modifier = Modifier.height(3.dp))
    Text(
        text = state.lastCommandLabel,
        color = when (state.lastCommandOk) {
            true -> OnlineGreen
            false -> ErrorRed
            null -> WarningYellow
        },
        fontSize = 8.sp,
        lineHeight = 9.sp,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun ChatBubble(label: String, text: String, color: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(color)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Text(
            text = label,
            color = Color(0xFF72D7FF),
            fontSize = 8.sp,
            lineHeight = 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Text(
            text = text,
            color = TextPrimary,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            maxLines = 12,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun WatchChatMessage.watchLabel(): String =
    if (role.equals("user", ignoreCase = true)) "YOU" else "AGENT"

private fun WatchChatMessage.watchBubbleColor(): Color =
    if (role.equals("user", ignoreCase = true)) Color(0xFF2C263F) else Color(0xFF10252F)

@Composable
private fun CommandButton(
    label: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .widthIn(min = 78.dp)
            .height(34.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = TextPrimary,
            disabledContainerColor = Color(0xFF1B2024),
            disabledContentColor = TextMuted
        )
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.Bold,
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
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .widthIn(min = 184.dp, max = 220.dp)
            .height(34.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = TextPrimary,
            disabledContainerColor = Color(0xFF1B2024),
            disabledContentColor = TextMuted
        )
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun WatchLine(label: String, value: String) {
    Text(
        text = "$label $value",
        color = TextPrimary,
        fontSize = 8.sp,
        lineHeight = 9.sp,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun SectionLabel(label: String) {
    Text(
        text = label,
        color = Color(0xFF72D7FF),
        fontSize = 9.sp,
        lineHeight = 10.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        maxLines = 1
    )
}

@Composable
private fun TinyStatusLine(value: String) {
    Text(
        text = value,
        color = TextMuted,
        fontSize = 8.sp,
        lineHeight = 9.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun DetailText(value: String) {
    Text(
        text = value,
        color = TextPrimary,
        fontSize = 11.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text = text,
        color = TextMuted,
        fontSize = 10.sp,
        lineHeight = 11.sp,
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
        WatchVoiceOutputRoutes.GLASSES -> Color(0xFF123744)
        WatchVoiceOutputRoutes.PHONE -> Color(0xFF1D4A5D)
        WatchVoiceOutputRoutes.WATCH -> SessionPurple
        WatchVoiceOutputRoutes.OFF -> StopRed
        else -> Neutral
    }
