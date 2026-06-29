package com.jsos.watch

import android.graphics.Typeface

import android.graphics.Paint

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.roundToInt
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.jsos.shared.WatchChatMessage
import com.jsos.shared.WatchCodexSession
import com.jsos.shared.WatchCoreIds
import com.jsos.shared.WatchVoiceOutputRoutes

private val ScreenTop = Color(0xFF1D282C)
private val ScreenMid = Color(0xFF141D20)
private val ScreenBottom = Color(0xFF101719)
private val Background = ScreenBottom
private val TextPrimary = Color(0xFFE6F6EA)
private val TextMuted = Color(0xFF9EB7A8)
private val OnlineGreen = Color(0xFF63F45C)
private val WarningYellow = Color(0xFFFFD166)
private val ErrorRed = Color(0xFFFF5F56)
private val CodexBlue = Color(0xFF123A42)
private val StopRed = Color(0xFF4A1714)
private val TalkGreen = Color(0xFF1D5C2A)
private val Neutral = Color(0xFF123A42)
private val SessionPurple = Color(0xFF0E3A46)
private val ModelOlive = Color(0xFF123A42)
private val FieldSurface = Color(0xCC142225)
private val DisabledSurface = Color(0xD10A1214)
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
    val transition = rememberInfiniteTransition(label = "watch_matrix")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "watch_matrix_phase",
    )
    val glyphs = remember { "0123456789JSOS<>[]" }

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
                    Color(0xFF245257).copy(alpha = 0.42f),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.24f, size.height * 0.04f),
                radius = size.width * 0.72f,
            )
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF1E452B).copy(alpha = 0.28f),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.60f, size.height * 0.52f),
                radius = size.width * 0.82f,
            )
        )

        val columnStep = 18.dp.toPx()
        val rowStep = 16.dp.toPx()
        val textSize = 11.sp.toPx()
        val columns = (size.width / columnStep).roundToInt() + 3
        val travel = size.height + rowStep * 18f

        drawIntoCanvas { canvas ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.MONOSPACE
                this.textSize = textSize
                textAlign = Paint.Align.CENTER
            }
            for (col in 0 until columns) {
                val x = (col - 1) * columnStep + if (col % 2 == 0) 0f else columnStep * 0.38f
                val length = 10 + (col * 5 % 9)
                val speed = 0.52f + (col % 4) * 0.07f
                val headY = ((phase * travel * speed) + col * rowStep * 2.9f) % travel - rowStep * 8f
                for (i in 0 until length) {
                    val y = headY - i * rowStep
                    if (y < -rowStep || y > size.height + rowStep) continue

                    val fade = 1f - (i.toFloat() / length.toFloat())
                    val head = i == 0
                    val alpha = if (head) 0.68f else 0.24f * fade
                    val colorAlpha = (alpha * 255f).roundToInt().coerceIn(0, 255)
                    paint.color = if (head) {
                        android.graphics.Color.argb(colorAlpha, 230, 246, 234)
                    } else {
                        android.graphics.Color.argb(colorAlpha, 99, 244, 92)
                    }
                    paint.setShadowLayer(
                        if (head) 12f else 5f,
                        0f,
                        0f,
                        android.graphics.Color.argb((colorAlpha * 0.70f).roundToInt(), 34, 211, 238)
                    )
                    val glyphIndex = ((phase * glyphs.length * 2f).roundToInt() + col * 11 + i * 5) % glyphs.length
                    canvas.nativeCanvas.drawText(glyphs[glyphIndex].toString(), x, y, paint)
                }
            }
            paint.clearShadowLayer()
        }

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF0A1214).copy(alpha = 0.04f),
                    Color.Transparent,
                    Color(0xFF040708).copy(alpha = 0.08f),
                )
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
            .padding(top = 22.dp, bottom = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        WatchHeader(state = state, pageTitle = "STATUS")
        Spacer(modifier = Modifier.height(8.dp))
        WatchLine(label = "SESSION", value = state.currentSession.ifBlank { "--" })
        WatchLine(label = "MODEL", value = state.currentModel.ifBlank { "--" })
        Spacer(modifier = Modifier.height(8.dp))
        TinyStatusLine("OUTPUT: ${voiceOutputLabel(state.voiceOutputRoute)}")
        TinyStatusLine("AUDIO: ${state.watchAudioStatus}")
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
        color = Color(0xFF22D3EE),
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(color)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = Color(0xFF22D3EE),
            fontSize = 7.sp,
            lineHeight = 8.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Text(
            text = text,
            color = TextPrimary,
            fontSize = 12.sp,
            lineHeight = 14.sp
        )
    }
}

private fun WatchChatMessage.watchLabel(): String =
    if (role.equals("user", ignoreCase = true)) "YOU" else "AGENT"

private fun WatchChatMessage.watchBubbleColor(): Color =
    if (role.equals("user", ignoreCase = true)) SessionPurple else FieldSurface

private fun WatchChatMessage.codexWatchLabel(): String =
    when {
        role.equals("user", ignoreCase = true) -> "YOU"
        role.equals("error", ignoreCase = true) -> "ERROR"
        role.equals("system", ignoreCase = true) -> "SYSTEM"
        else -> "CODEX"
    }

private fun WatchChatMessage.codexWatchBubbleColor(): Color =
    when {
        role.equals("user", ignoreCase = true) -> SessionPurple
        role.equals("error", ignoreCase = true) -> StopRed
        role.equals("system", ignoreCase = true) -> Neutral
        else -> FieldSurface
    }

@Composable
private fun ChatMiniButton(
    label: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .widthIn(min = 42.dp)
            .height(26.dp),
        shape = RoundedCornerShape(13.dp),
        contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = TextPrimary,
            disabledContainerColor = DisabledSurface,
            disabledContentColor = TextMuted
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            disabledElevation = 0.dp
        )
    ) {
        Text(
            text = label,
            fontSize = 7.sp,
            lineHeight = 8.sp,
            fontWeight = FontWeight.Bold,
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
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .widthIn(min = 50.dp)
            .height(32.dp),
        shape = RoundedCornerShape(17.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = TextPrimary,
            disabledContainerColor = DisabledSurface,
            disabledContentColor = TextMuted
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            disabledElevation = 0.dp
        )
    ) {
        Text(
            text = label,
            fontSize = 8.sp,
            lineHeight = 9.sp,
            fontWeight = FontWeight.Bold,
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
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .widthIn(min = 68.dp)
            .height(34.dp),
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = TextPrimary,
            disabledContainerColor = DisabledSurface,
            disabledContentColor = TextMuted
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            disabledElevation = 0.dp
        )
    ) {
        Text(
            text = label,
            fontSize = 8.sp,
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
            .widthIn(min = 138.dp, max = 164.dp)
            .height(36.dp),
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = TextPrimary,
            disabledContainerColor = DisabledSurface,
            disabledContentColor = TextMuted
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            disabledElevation = 0.dp
        )
    ) {
        Text(
            text = label,
            fontSize = 8.sp,
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
        modifier = Modifier.padding(horizontal = 8.dp),
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
        color = Color(0xFF22D3EE),
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
        modifier = Modifier.padding(horizontal = 8.dp),
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
        modifier = Modifier.padding(horizontal = 12.dp),
        text = value,
        color = TextPrimary,
        fontSize = 10.sp,
        lineHeight = 12.sp,
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
        WatchVoiceOutputRoutes.GLASSES -> Neutral
        WatchVoiceOutputRoutes.PHONE -> TalkGreen
        WatchVoiceOutputRoutes.WATCH -> SessionPurple
        WatchVoiceOutputRoutes.OFF -> StopRed
        else -> Neutral
    }
