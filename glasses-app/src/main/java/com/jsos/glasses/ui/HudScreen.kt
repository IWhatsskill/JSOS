package com.jsos.glasses.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.jsos.glasses.R
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import kotlinx.coroutines.delay

/**
 * Display size presets for the 480x640 portrait HUD
 * Each preset optimizes for different character counts vs readability
 */
enum class HudDisplaySize(val fontSizeSp: Int, val label: String) {
    COMPACT(10, "Compact"),
    NORMAL(12, "Normal"),
    COMFORTABLE(14, "Comfortable"),
    LARGE(16, "Large")
}

/**
 * HUD position controls how much of the 480x640 display is used.
 * Smaller positions let the user see more of the outside world.
 */
enum class HudPosition(val label: String) {
    FULL("Full"),
    BOTTOM_HALF("Bottom"),
    TOP_HALF("Mid")
}

private val TopGhostSafeZone = 82.dp
private const val MidSafeHudHeightFraction = 0.66f

/**
 * Focus areas of the chat UI.
 */
enum class ChatFocusArea {
    CONTENT,  // Chat messages (scrollable)
    INPUT,    // Voice input staging area (photos + Send / Clear buttons)
    MENU      // Bottom menu bar
}

/**
 * Action buttons in the input staging area
 */
enum class InputActionItem(val icon: String, val label: String) {
    SEND("\u21B5", "Send"),
    CLEAR("\u2715", "Clear")
}

/** Maximum number of photos that can be attached. */
const val MAX_PHOTOS = 4

/**
 * Agent response states
 */
enum class AgentState {
    IDLE,       // No active request
    THINKING,   // Ack received, waiting for first chunk
    STREAMING   // Receiving streaming chunks
}

/**
 * Menu bar items
 */
enum class MenuBarItem(val icon: String, val label: String) {
    PHOTO("\uD83D\uDCF7", "Photo"),
    SESSION("\u25CE", "Sess"),
    SIZE("\u2588", "Size"),  // Icon overridden dynamically based on next HudPosition
    MORE("\u2026", "More"),
}

/**
 * Items available in the MORE menu
 */
enum class MoreMenuItem(val icon: String, val label: String, val displaySize: HudDisplaySize? = null) {
    VOICE_SEND("SEND", "Send Mode"),
    FONT_COMPACT("Aa", "Compact", HudDisplaySize.COMPACT),
    FONT_NORMAL("Aa", "Normal", HudDisplaySize.NORMAL),
    FONT_COMFORTABLE("Aa", "Comfortable", HudDisplaySize.COMFORTABLE),
    FONT_LARGE("Aa", "Large", HudDisplaySize.LARGE),
    SLASH("/", "Slash Cmds"),
    AR_PICTURE("PIC", "AR Picture"),
    AR_RECORD("REC", "AR Record"),
    AR_STOP("STOP", "AR Stop"),
    VOICE("\uD83D\uDD0A", "Voice"),  // speaker icon - label is dynamic
}

enum class VoiceSendMode {
    ASK,
    AUTO
}

/**
 * A display-ready chat message for the HUD.
 * Stores raw content; wrapping is computed at render time.
 */
data class DisplayMessage(
    val id: String,
    val role: String,  // "user" or "assistant"
    val content: String,
    val isStreaming: Boolean = false,
    val thumbnails: List<Bitmap> = emptyList()
)

/**
 * Recognition mode indicator (OpenAI vs device)
 */
enum class RecognitionMode {
    DEVICE,  // Android's SpeechRecognizer
    OPENAI   // OpenAI Realtime API
}

/**
 * Voice input states for HUD display
 */
sealed class VoiceInputState {
    object Idle : VoiceInputState()
    data class Listening(val mode: RecognitionMode = RecognitionMode.DEVICE) : VoiceInputState()
    data class Recognizing(val mode: RecognitionMode = RecognitionMode.DEVICE) : VoiceInputState()
    data class Processing(val mode: RecognitionMode = RecognitionMode.DEVICE) : VoiceInputState()
    data class Error(val message: String) : VoiceInputState()
}

/**
 * Session info for session picker
 */
data class SessionPickerInfo(
    val key: String,
    val name: String,
    val kind: String? = null,
    val hasUnread: Boolean = false,
    val updatedAt: Long? = null
)

/** Format a millisecond epoch timestamp as a short relative time string. */
private fun formatRelativeTime(timestampMs: Long): String {
    val now = System.currentTimeMillis()
    val diffMs = now - timestampMs
    if (diffMs < 0) return "now"
    val seconds = diffMs / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    return when {
        seconds < 60 -> "now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days == 1L -> "yesterday"
        days < 30 -> "${days}d ago"
        else -> "${days / 30}mo ago"
    }
}

/**
 * Chat HUD state — replaces the old TerminalState
 */
data class ChatHudState(
    val messages: List<DisplayMessage> = emptyList(),
    val scrollPosition: Int = 0,
    val scrollTrigger: Int = 0,
    val isScrolledToEnd: Boolean = false,
    val inputText: String = "",
    val photoThumbnails: List<Bitmap> = emptyList(),
    val isConnected: Boolean = false,
    val agentState: AgentState = AgentState.IDLE,
    val menuBarIndex: Int = 0,
    val hudPosition: HudPosition = HudPosition.FULL,
    val displaySize: HudDisplaySize = HudDisplaySize.NORMAL,
    val focusedArea: ChatFocusArea = ChatFocusArea.CONTENT,
    val voiceState: VoiceInputState = VoiceInputState.Idle,
    val voiceText: String = "",
    // Session picker
    val showSessionPicker: Boolean = false,
    val availableSessions: List<SessionPickerInfo> = emptyList(),
    val currentSessionKey: String? = null,
    val currentSessionName: String? = null,
    val selectedSessionIndex: Int = 0,
    // More menu
    val showMoreMenu: Boolean = false,
    val selectedMoreIndex: Int = 0,
    // Slash command menu
    val showSlashMenu: Boolean = false,
    val selectedSlashIndex: Int = 0,
    val showSlashParamMenu: Boolean = false,
    val selectedSlashParamIndex: Int = 0,
    val slashParamMenuType: SlashParamMenuType? = null,
    // Input staging area (voice text accumulation)
    val stagingText: String = "",
    val showInputStaging: Boolean = false,
    val inputActionIndex: Int = 0,  // Index into combined row: [photo0..N-1, Clear, Send]. Default = Send (last)
    // Exit confirmation dialog
    val showExitConfirm: Boolean = false,
    // Battery level (0-100), null = unavailable / hide indicator
    val batteryLevel: Int? = null,
    val batteryCharging: Boolean = false,
    // Current time (HH:MM, 24-hour format)
    val currentTime: String = "",
    // History loading state
    val isLoadingMoreHistory: Boolean = false,
    val hasMoreHistory: Boolean = true,  // Assume there's more until we're told otherwise
    val newPrependCount: Int = 0,  // Number of newly prepended messages (for fade-in animation)
    // Wake notification (shown briefly when glasses wakes from standby due to new content)
    val showWakeNotification: Boolean = false,
    val wakeReason: String? = null,  // "stream_content", "new_message", "cron_message"
    // Voice input send mode
    val voiceSendMode: VoiceSendMode = VoiceSendMode.ASK,
    // TTS state (voice responses)
    val ttsEnabled: Boolean = false
) {
    /** Total number of messages */
    val totalMessages: Int get() = messages.size
}

/**
 * Slash command with display label.
 * Commands are sent to the OpenClaw Gateway as chat messages.
 */
data class SlashCommandItem(val command: String, val description: String)

enum class SlashParamMenuType(val title: String) {
    MODEL("MODELS"),
    THINK("THINK")
}

data class SlashParamOption(
    val label: String,
    val command: String,
    val description: String
)

/**
 * Available slash commands from the OpenClaw Gateway.
 * Keep this list aligned with the OpenClaw slash command reference.
 */
val SLASH_COMMANDS = listOf(
    SlashCommandItem("/help", "Show help"),
    SlashCommandItem("/commands", "List commands"),
    SlashCommandItem("/status", "Show status"),
    SlashCommandItem("/model", "Switch model"),
    SlashCommandItem("/compact", "Compact context"),
    SlashCommandItem("/reset", "New session"),
    SlashCommandItem("/stop", "Stop generation"),
    SlashCommandItem("/think", "Thinking level"),
    SlashCommandItem("/context", "Show context"),
    SlashCommandItem("/usage", "Usage info"),
    SlashCommandItem("/whoami", "Show identity"),
    SlashCommandItem("/reasoning", "Toggle reasoning"),
    SlashCommandItem("/elevated", "Elevated mode"),
    SlashCommandItem("/verbose", "Verbose output"),
    SlashCommandItem("/exec", "Exec settings"),
    SlashCommandItem("/subagents", "Sub-agents"),
)

val MODEL_PARAM_OPTIONS = listOf(
    SlashParamOption("ALL", "/models", "List all models"),
    SlashParamOption("OPENAI", "/models openai", "List OpenAI models"),
    SlashParamOption("OLLAMA", "/models ollama", "List Ollama models"),
    SlashParamOption("CURRENT", "/model", "Show current model"),
    SlashParamOption("GPT-5.5", "/model openai/gpt-5.5", "Switch to OpenAI GPT-5.5"),
    SlashParamOption("QWEN397B", "/model ollama/qwen3.5:397b-cloud", "Switch to Qwen 397B")
)

val THINK_PARAM_OPTIONS = listOf(
    SlashParamOption("OFF", "/think off", "Disable thinking"),
    SlashParamOption("MIN", "/think minimal", "Minimal thinking"),
    SlashParamOption("LOW", "/think low", "Low thinking"),
    SlashParamOption("MED", "/think medium", "Medium thinking"),
    SlashParamOption("HIGH", "/think high", "High thinking"),
    SlashParamOption("XHIGH", "/think xhigh", "Extra high"),
    SlashParamOption("AUTO", "/think adaptive", "Adaptive thinking"),
    SlashParamOption("MAX", "/think max", "Maximum thinking"),
    SlashParamOption("ON", "/think on", "Provider default")
)

fun slashParamOptions(type: SlashParamMenuType): List<SlashParamOption> = when (type) {
    SlashParamMenuType.MODEL -> MODEL_PARAM_OPTIONS
    SlashParamMenuType.THINK -> THINK_PARAM_OPTIONS
}

// ============================================================================
// MAIN HUD SCREEN
// ============================================================================

/**
 * Chat-oriented HUD display for Rokid Glasses with OpenClaw backend.
 *
 * Layout:
 * ┌─[TopBar]──────────────────────────────────┐
 * │ ● connected                    12/42 lines │
 * ├────────────────────────────────────────────┤
 * │ Assistant message (left-aligned, green)     │
 * │         User message (right, light bg) │
 * │ Assistant streaming...█                     │
 * ├───[Input]──────────────────────────────────┤
 * │ > current input text...                     │
 * ├───[Menu Bar]───────────────────────────────┤
 * │ ↵Enter ⌫Clear ◎Sess ⬚Size AaFont …More    │
 * └────────────────────────────────────────────┘
 */
@Composable
fun HudScreen(
    state: ChatHudState,
    onTap: () -> Unit = {},
    onDoubleTap: () -> Unit = {},
    onLongPress: () -> Unit = {},
    onScrolledToEndChanged: (Boolean) -> Unit = {}
) {
    val listState = rememberLazyListState()
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val monoFontFamily = remember { FontFamily(Font(R.font.jetbrains_mono)) }

    // Track whether the list is scrolled to the very end (pixel-level)
    val canScrollForward = listState.canScrollForward
    LaunchedEffect(canScrollForward) {
        onScrolledToEndChanged(!canScrollForward)
    }

    // Auto-scroll when position or trigger changes. The HUD chat only renders
    // JSOS messages, so map the backing message index to the visible list.
    LaunchedEffect(state.scrollPosition, state.scrollTrigger) {
        val visibleMessages = state.messages.withIndex().filter { it.value.role != "user" }
        val historyOffset = if (!state.hasMoreHistory && visibleMessages.isNotEmpty()) 1 else 0
        val thinkingOffset = if (state.agentState == AgentState.THINKING) 1 else 0
        val totalItems = historyOffset + visibleMessages.size + thinkingOffset
        val visibleMessageIndex = visibleMessages.indexOfLast { it.index <= state.scrollPosition }
        val targetIndex = when {
            state.agentState == AgentState.THINKING && state.scrollPosition >= state.messages.lastIndex ->
                historyOffset + visibleMessages.size
            visibleMessageIndex >= 0 ->
                historyOffset + visibleMessageIndex
            state.agentState == AgentState.THINKING ->
                historyOffset
            else -> null
        }

        if (totalItems > 0 && targetIndex != null && targetIndex < totalItems) {
            val currentIndex = listState.firstVisibleItemIndex
            if (targetIndex < currentIndex) {
                // Scrolling up: use pixel-based animation for smoothness
                // (animateScrollToItem can jump when target items aren't composed yet)
                val viewportHeight = listState.layoutInfo.viewportSize.height
                val itemsToScroll = currentIndex - targetIndex
                // Estimate scroll distance from average visible item height
                val visibleItems = listState.layoutInfo.visibleItemsInfo
                val avgItemHeight = if (visibleItems.isNotEmpty()) {
                    visibleItems.sumOf { it.size } / visibleItems.size.toFloat()
                } else {
                    viewportHeight / 5f
                }
                val scrollDistance = -(itemsToScroll * avgItemHeight)
                listState.animateScrollBy(scrollDistance)
            } else if (targetIndex == totalItems - 1) {
                // Scrolling to last item: use a large offset so the bottom of the
                // item aligns with the viewport bottom (Compose clamps internally).
                // During streaming, use instant scroll — animated scroll gets
                // cancelled and restarted on every chunk, causing visible flicker.
                val isStreaming = visibleMessages.lastOrNull()?.value?.isStreaming == true
                if (isStreaming) {
                    listState.scrollToItem(targetIndex, Int.MAX_VALUE)
                } else {
                    listState.animateScrollToItem(targetIndex, Int.MAX_VALUE)
                }
            } else {
                listState.animateScrollToItem(targetIndex)
            }
        }
    }

    // Focus brightness
    val inputFocused = state.focusedArea == ChatFocusArea.INPUT
    val menuFocused = state.focusedArea == ChatFocusArea.MENU

    // Keep chat fully opaque. Dimming the whole chat layer produces visible
    // dithering/blocking on Rokid displays at very low brightness.
    val contentAlpha = 1f
    val inputAlpha = focusBrightness(inputFocused)
    val menuAlpha = focusBrightness(menuFocused)

    // HUD position offset
    val hudHeight = when (state.hudPosition) {
        HudPosition.FULL -> 1f
        HudPosition.BOTTOM_HALF -> 0.5f
        HudPosition.TOP_HALF -> MidSafeHudHeightFraction
    }
    val hudAlignment = when (state.hudPosition) {
        HudPosition.FULL -> Alignment.TopStart
        HudPosition.BOTTOM_HALF -> Alignment.BottomStart
        HudPosition.TOP_HALF -> Alignment.TopStart
    }
    val topSafePadding = when (state.hudPosition) {
        HudPosition.BOTTOM_HALF -> 0.dp
        HudPosition.FULL, HudPosition.TOP_HALF -> TopGhostSafeZone
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onDoubleTap() },
                    onTap = { onTap() },
                    onLongPress = { onLongPress() }
                )
            }
    ) {
        // Calculate font size to fit content width — varies with displaySize
        val targetColumns = when (state.displaySize) {
            HudDisplaySize.COMPACT -> 70
            HudDisplaySize.NORMAL -> 60
            HudDisplaySize.COMFORTABLE -> 50
            HudDisplaySize.LARGE -> 40
        }
        val referenceText = "M".repeat(targetColumns)
        val referenceFontSize = 12.sp

        val fontSize = remember(maxWidth, monoFontFamily, targetColumns) {
            val referenceStyle = TextStyle(
                fontFamily = monoFontFamily,
                fontSize = referenceFontSize,
                letterSpacing = 0.sp
            )
            val measuredWidth = textMeasurer.measure(referenceText, referenceStyle).size.width
            val availableWidthPx = with(density) { maxWidth.toPx() }
            val scaledSize = referenceFontSize.value * (availableWidthPx / measuredWidth) * 0.99f
            scaledSize.coerceIn(6f, 24f).sp
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = hudAlignment
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(hudHeight)
                    .clipToBounds()
                    .padding(top = topSafePadding)
                    .padding(horizontal = 0.dp, vertical = 6.dp)
                    .background(Color.Black.copy(alpha = 0.96f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 2.dp, vertical = 6.dp)
            ) {
                // TOP BAR
                VoiceStatusStrip(
                    isConnected = state.isConnected,
                    agentState = state.agentState,
                    focusedArea = state.focusedArea,
                    voiceState = state.voiceState,
                    sessionTitle = state.currentSessionName,
                    isLoadingMoreHistory = state.isLoadingMoreHistory,
                    showWakeNotification = state.showWakeNotification,
                    wakeReason = state.wakeReason,
                    batteryLevel = state.batteryLevel,
                    batteryCharging = state.batteryCharging,
                    currentTime = state.currentTime,
                    ttsEnabled = state.ttsEnabled,
                    fontFamily = monoFontFamily,
                    fontSize = fontSize,
                    alpha = 1f
                )

                Spacer(modifier = Modifier.height(4.dp))

                // CONTENT AREA — chat messages
                ChatContentArea(
                    messages = state.messages,
                    agentState = state.agentState,
                    speakerLabel = state.currentSessionName ?: "JSOS",
                    listState = listState,
                    fontSize = fontSize,
                    fontFamily = monoFontFamily,
                    alpha = contentAlpha,
                    hasMoreHistory = state.hasMoreHistory,
                    newPrependCount = state.newPrependCount,
                    modifier = Modifier.weight(1f)
                )

                // INPUT STAGING AREA (with inline photo thumbnails)
                AnimatedVisibility(
                    visible = state.showInputStaging || state.photoThumbnails.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    InputStagingArea(
                        text = state.stagingText,
                        showText = state.showInputStaging,
                        photos = state.photoThumbnails,
                        selectedIndex = state.inputActionIndex,
                        isFocused = inputFocused,
                        isProcessing = state.voiceState is VoiceInputState.Processing,
                        fontFamily = monoFontFamily,
                        fontSize = fontSize,
                        alpha = inputAlpha
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                AnimatedVisibility(
                    visible = menuFocused,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(4.dp))
                        ChatMenuBar(
                            selectedIndex = state.menuBarIndex,
                            isFocused = menuFocused,
                            hudPosition = state.hudPosition,
                            batteryLevel = null,
                            batteryCharging = false,
                            currentTime = "",
                            fontFamily = monoFontFamily,
                            alpha = menuAlpha
                        )
                    }
                }
            }
        }

        // Session picker overlay
        AnimatedVisibility(
            visible = state.showSessionPicker,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            SessionPickerOverlay(
                sessions = state.availableSessions,
                currentSessionKey = state.currentSessionKey,
                selectedIndex = state.selectedSessionIndex,
                fontFamily = monoFontFamily
            )
        }

        // More menu overlay
        AnimatedVisibility(
            visible = state.showMoreMenu,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            MoreMenuOverlay(
                selectedIndex = state.selectedMoreIndex,
                currentDisplaySize = state.displaySize,
                voiceSendMode = state.voiceSendMode,
                ttsEnabled = state.ttsEnabled,
                fontFamily = monoFontFamily
            )
        }

        // Slash command menu overlay
        AnimatedVisibility(
            visible = state.showSlashMenu,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            SlashCommandOverlay(
                selectedIndex = state.selectedSlashIndex,
                fontFamily = monoFontFamily
            )
        }

        // Slash command parameter overlay
        AnimatedVisibility(
            visible = state.showSlashParamMenu,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            SlashParamOverlay(
                menuType = state.slashParamMenuType,
                selectedIndex = state.selectedSlashParamIndex,
                fontFamily = monoFontFamily
            )
        }

        // Exit confirmation overlay
        AnimatedVisibility(
            visible = state.showExitConfirm,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            ExitConfirmOverlay(fontFamily = monoFontFamily)
        }
    }
}

// ============================================================================
// BRIGHTNESS ANIMATION
// ============================================================================

@Composable
fun focusBrightness(isFocused: Boolean): Float {
    val baseAlpha = if (isFocused) 1f else 0.4f
    return animateFloatAsState(
        targetValue = baseAlpha,
        animationSpec = tween(200),
        label = "brightness"
    ).value
}

@Composable
private fun HudDivider(
    alpha: Float,
    modifier: Modifier = Modifier
) {
    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(HudColors.green.copy(alpha = alpha))
    )
}

// ============================================================================
// TOP BAR
// ============================================================================

// ============================================================================
// CHAT CONTENT AREA
// ============================================================================

@Composable
private fun ChatContentArea(
    messages: List<DisplayMessage>,
    agentState: AgentState,
    speakerLabel: String,
    listState: androidx.compose.foundation.lazy.LazyListState,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontFamily: FontFamily,
    alpha: Float,
    hasMoreHistory: Boolean = true,
    newPrependCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val visibleMessages = messages.withIndex().filter { it.value.role != "user" }
    val lastVisibleOriginalIndex = visibleMessages.lastOrNull()?.index ?: -1

    // Auto-scroll to reveal the thinking indicator when it appears.
    // Uses a pixel-based scrollBy after a frame delay so the LazyColumn
    // has laid out the new item before we scroll.
    val isThinking = agentState == AgentState.THINKING
    LaunchedEffect(isThinking) {
        if (isThinking && messages.isNotEmpty()) {
            // Wait for the thinking indicator item to be composed and laid out
            delay(50)
            // Only auto-scroll if the user is near the bottom
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            if (lastVisible >= visibleMessages.size - 2) {
                listState.animateScrollBy(500f)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .alpha(alpha)
    ) {
        if (visibleMessages.isEmpty() && agentState == AgentState.IDLE) {
            Text(
                text = "No messages yet",
                color = HudColors.dimText,
                fontSize = fontSize,
                fontFamily = fontFamily,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // "Beginning of conversation" marker (static, no displacement issues)
                if (!hasMoreHistory && visibleMessages.isNotEmpty()) {
                    item(key = "history_start") {
                        HistoryStartIndicator(
                            fontSize = fontSize,
                            fontFamily = fontFamily
                        )
                    }
                }

                itemsIndexed(visibleMessages, key = { _, item -> item.value.id }) { _, item ->
                    val index = item.index
                    val message = item.value
                    val isCurrentMessage = index == lastVisibleOriginalIndex || message.isStreaming
                    // Fade in newly prepended messages as they scroll into view
                    if (index < newPrependCount) {
                        val fadeAlpha = remember { Animatable(0.15f) }
                        LaunchedEffect(Unit) {
                            fadeAlpha.animateTo(1f, tween(400))
                        }
                        Box(modifier = Modifier.alpha(fadeAlpha.value)) {
                            ChatMessageItem(
                                message = message,
                                speakerLabel = speakerLabel,
                                fontSize = fontSize,
                                fontFamily = fontFamily,
                                isCurrent = isCurrentMessage
                            )
                        }
                    } else {
                        ChatMessageItem(
                            message = message,
                            speakerLabel = speakerLabel,
                            fontSize = fontSize,
                            fontFamily = fontFamily,
                            isCurrent = isCurrentMessage
                        )
                    }
                }

                // Thinking indicator (shown after last message when agent is thinking)
                if (agentState == AgentState.THINKING) {
                    item {
                        ThinkingIndicator(
                            fontSize = fontSize,
                            fontFamily = fontFamily
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatMessageItem(
    message: DisplayMessage,
    speakerLabel: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontFamily: FontFamily,
    isCurrent: Boolean
) {
    val isUser = message.role == "user"
    val isStreaming = message.isStreaming
    val speaker = if (isUser) "YOU>" else "${speakerLabel}>"
    val speakerColor = if (isUser) HudColors.cyan else HudColors.green

    // Blinking cursor for streaming
    val cursorVisible = if (isStreaming) {
        val infiniteTransition = rememberInfiniteTransition(label = "cursor")
        val cursorAlpha by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(animation = tween(500)),
            label = "blink"
        )
        cursorAlpha > 0.5f
    } else {
        false
    }
    val messageShape = RoundedCornerShape(4.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 2.dp)
    ) {
        if (message.thumbnails.isNotEmpty()) {
            PhotoThumbnailRow(
                thumbnails = message.thumbnails,
                modifier = Modifier.padding(start = 44.dp, bottom = 2.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isUser) {
                        Modifier
                    } else if (isCurrent) {
                        Modifier.border(
                            width = 1.dp,
                            color = HudColors.green,
                            shape = messageShape
                        )
                    } else {
                        Modifier.drawBehind {
                            drawLine(
                                color = HudColors.green,
                                start = Offset(0f, 0f),
                                end = Offset(0f, size.height),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                    }
                )
                .padding(horizontal = 4.dp, vertical = 3.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = speaker,
                color = speakerColor,
                fontSize = fontSize,
                fontFamily = fontFamily,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp,
                modifier = Modifier.widthIn(min = 44.dp, max = 92.dp)
            )

            val displayText = if (message.content.isEmpty() && isStreaming) {
                if (cursorVisible) "\u2588" else " "
            } else if (isStreaming && cursorVisible) {
                "${message.content}\u2588"
            } else {
                message.content
            }

            Text(
                text = displayText,
                color = if (isUser) HudColors.primaryText else HudColors.green,
                fontSize = fontSize,
                fontFamily = fontFamily,
                lineHeight = fontSize,
                letterSpacing = 0.sp,
                textAlign = TextAlign.Start,
                softWrap = true,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ThinkingIndicator(
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontFamily: FontFamily
) {
    val infiniteTransition = rememberInfiniteTransition(label = "thinking")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(600)),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .padding(end = 16.dp)
            .graphicsLayer { this.alpha = alpha }
    ) {
        Text(
            text = "...",
            color = HudColors.cyan,
            fontSize = (fontSize.value + 2).sp,
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun HistoryStartIndicator(
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontFamily: FontFamily
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "\u2500\u2500 beginning of conversation \u2500\u2500",
            color = HudColors.dimText,
            fontSize = fontSize,
            fontFamily = fontFamily
        )
    }
}

// ============================================================================
// PHOTO STRIP
// ============================================================================

private val greenColorMatrix = ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
    0f, 0f, 0f, 0f, 0f,
    0.3f, 0.59f, 0.11f, 0f, 0f,
    0f, 0f, 0f, 0f, 0f,
    0f, 0f, 0f, 1f, 0f
)))


@Composable
private fun PhotoThumbnailRow(
    thumbnails: List<Bitmap>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        thumbnails.forEach { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(width = 24.dp, height = 18.dp)
                    .border(1.dp, HudColors.green.copy(alpha = 0.5f), RoundedCornerShape(1.dp)),
                contentScale = ContentScale.Crop,
                colorFilter = greenColorMatrix
            )
        }
    }
}

// ============================================================================
// VOICE STATUS STRIP
// ============================================================================

@Composable
private fun VoiceStatusStrip(
    isConnected: Boolean,
    agentState: AgentState,
    focusedArea: ChatFocusArea,
    voiceState: VoiceInputState,
    sessionTitle: String?,
    isLoadingMoreHistory: Boolean,
    showWakeNotification: Boolean,
    wakeReason: String?,
    batteryLevel: Int?,
    batteryCharging: Boolean,
    currentTime: String,
    ttsEnabled: Boolean,
    fontFamily: FontFamily,
    fontSize: androidx.compose.ui.unit.TextUnit,
    alpha: Float,
    modifier: Modifier = Modifier
) {
    val stripFontSize = (fontSize.value - 4).coerceAtLeast(7f).sp
    val isVoiceActive = voiceState is VoiceInputState.Listening ||
            voiceState is VoiceInputState.Recognizing ||
            voiceState is VoiceInputState.Processing
    val isLive = agentState == AgentState.STREAMING
    val infiniteTransition = rememberInfiniteTransition(label = "voicePulse")
    val animatedAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(700)),
        label = "voicePulseAlpha"
    )
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(650)),
        label = "voiceWavePhase"
    )
    val pulseAlpha = if (isVoiceActive || isLive) animatedAlpha else 0.55f
    val mode = when (voiceState) {
        is VoiceInputState.Listening -> voiceState.mode
        is VoiceInputState.Recognizing -> voiceState.mode
        is VoiceInputState.Processing -> voiceState.mode
        else -> null
    }
    val providerLabel = when (mode) {
        RecognitionMode.OPENAI -> "OpenAI STT"
        RecognitionMode.DEVICE -> "Device STT"
        null -> "Voice"
    }
    val stateLabel = when (voiceState) {
        is VoiceInputState.Listening -> "LISTEN"
        is VoiceInputState.Recognizing -> "HEAR"
        is VoiceInputState.Processing -> "SEND"
        is VoiceInputState.Error -> "ERROR"
        VoiceInputState.Idle -> "READY"
    }
    val topLabel = when {
        isLive -> "LIVE"
        agentState == AgentState.THINKING -> "THINK"
        isLoadingMoreHistory -> "LOAD"
        showWakeNotification -> wakeReason?.replace('_', ' ')?.uppercase() ?: "WAKE"
        else -> stateLabel
    }
    val accentColor = when (voiceState) {
        is VoiceInputState.Error -> HudColors.error
        is VoiceInputState.Processing -> HudColors.cyan
        is VoiceInputState.Recognizing -> HudColors.yellow
        else -> if (isLive) HudColors.cyan else HudColors.green
    }
    val statusTag = when {
        showWakeNotification -> "[WAKE]"
        voiceState is VoiceInputState.Error -> "[ERR]"
        isVoiceActive -> "[MIC]"
        isConnected -> "[OK]"
        else -> "[ERR]"
    }
    val statusColor = when {
        showWakeNotification -> HudColors.yellow
        voiceState is VoiceInputState.Error || !isConnected -> HudColors.error
        isVoiceActive -> HudColors.cyan
        else -> HudColors.green
    }
    val sessionLabel = sessionTitle?.takeIf { it.isNotBlank() } ?: "main"
    val rightLabel = listOf(
        statusTag,
        "|",
        sessionLabel,
        "|",
        currentTime,
        batteryLevel?.let { "${if (batteryCharging) "+" else ""}$it%" }.orEmpty()
    ).filter { it.isNotBlank() }.joinToString(" ")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(alpha)
            .heightIn(min = 22.dp)
            .padding(horizontal = 0.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isLive) {
            VoicePulseDot(
                color = HudColors.cyan,
                pulseAlpha = pulseAlpha,
                rotation = wavePhase,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
        } else if (isVoiceActive) {
            VoicePulseDot(
                color = accentColor,
                pulseAlpha = pulseAlpha,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(7.dp))
        }
        Text(
            text = topLabel,
            color = accentColor,
            fontSize = stripFontSize,
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        if (isVoiceActive) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = providerLabel,
                color = HudColors.primaryText,
                fontSize = stripFontSize,
                fontFamily = fontFamily,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(10.dp))
            MiniWaveform(
                phase = wavePhase,
                color = accentColor,
                modifier = Modifier.width(58.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Spacer(modifier = Modifier.weight(1f))
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
        if (ttsEnabled) {
            Text(
                text = "TTS",
                color = HudColors.cyan,
                fontSize = stripFontSize,
                fontFamily = fontFamily,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        if (rightLabel.isNotBlank()) {
            Text(
                text = rightLabel,
                color = if (batteryLevel != null && batteryLevel <= 15) HudColors.error else HudColors.dimText,
                fontSize = stripFontSize,
                fontFamily = fontFamily,
                maxLines = 1,
                textAlign = TextAlign.End
            )
        }
    }
    HudDivider(alpha = 0.18f)
}

@Composable
private fun VoicePulseDot(
    color: Color,
    pulseAlpha: Float,
    rotation: Float = 0f,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .graphicsLayer { rotationZ = rotation * 360f }
            .border(
                width = 1.dp,
                color = color.copy(alpha = (pulseAlpha * 0.7f).coerceIn(0.35f, 0.8f)),
                shape = RoundedCornerShape(50.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(3.dp)
                .background(color.copy(alpha = pulseAlpha), RoundedCornerShape(50.dp))
        )
        Box(
            modifier = Modifier
                .size(5.dp)
                .background(color.copy(alpha = pulseAlpha), RoundedCornerShape(50.dp))
        )
    }
}

@Composable
private fun MiniWaveform(
    phase: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val baseHeights = listOf(4f, 7f, 13f, 19f, 8f, 15f, 22f, 10f, 6f, 12f, 5f, 9f)
    Row(
        modifier = modifier.height(22.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        baseHeights.forEachIndexed { index, baseHeight ->
            val step = (index + (phase * baseHeights.size).toInt()) % 5
            val delta = when (step) {
                0 -> 3f
                1 -> 1f
                2 -> -1f
                else -> 0f
            }
            val barHeight = (baseHeight + delta).coerceIn(3f, 22f).dp
            val strongBar = index == 3 || index == 6 || step == 0
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(barHeight)
                    .background(
                        color.copy(alpha = if (strongBar) 0.95f else 0.62f),
                        RoundedCornerShape(1.dp)
                    )
            )
        }
    }
}

// ============================================================================
// INPUT STAGING AREA
// ============================================================================

/**
 * Combined input staging area.
 *
 * Layout (single line below text):
 *   [Photo1] [Photo2] ... [Photo4]  ←spacer→  [Clear] [Send]
 *
 * Clear and Send buttons are only shown when there is staged input (text or photos).
 *
 * `selectedIndex` maps into: photos (0..N-1), then CLEAR (N), SEND (N+1).
 */
@Composable
private fun InputStagingArea(
    text: String,
    showText: Boolean,
    photos: List<Bitmap>,
    selectedIndex: Int,
    isFocused: Boolean,
    isProcessing: Boolean,
    fontFamily: FontFamily,
    fontSize: androidx.compose.ui.unit.TextUnit,
    alpha: Float,
    modifier: Modifier = Modifier
) {
    val commandFontSize = 8.sp  // Match menu bar fixed size
    val photoCount = photos.size
    val hasContent = text.isNotEmpty()

    // Blinking cursor for processing state
    val cursorVisible = if (isProcessing) {
        val infiniteTransition = rememberInfiniteTransition(label = "processingCursor")
        val cursorAlpha by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(animation = tween(500)),
            label = "blink"
        )
        cursorAlpha > 0.5f
    } else {
        false
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(alpha)
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        // Staged text display (show when text is present OR when processing)
        if (showText || isProcessing) {
            val borderColor = if (isProcessing) {
                HudColors.cyan.copy(alpha = 0.6f)
            } else if (isFocused) {
                HudColors.yellow.copy(alpha = 0.6f)
            } else {
                HudColors.dimText.copy(alpha = 0.4f)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black, RoundedCornerShape(4.dp))
                    .border(
                        width = 1.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .heightIn(min = 20.dp, max = 60.dp)
            ) {
                val displayText = if (isProcessing) {
                    val cursor = if (cursorVisible) "\u2588" else " "
                    if (text.isNotEmpty()) "$text $cursor" else cursor
                } else {
                    text.ifEmpty { "..." }
                }

                val textColor = if (isProcessing && text.isEmpty()) {
                    HudColors.cyan
                } else if (text.isEmpty()) {
                    HudColors.dimText
                } else {
                    HudColors.primaryText
                }

                Text(
                    text = displayText,
                    color = textColor,
                    fontSize = fontSize,
                    fontFamily = fontFamily,
                    lineHeight = fontSize,
                    letterSpacing = 0.sp,
                    softWrap = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(2.dp))
        }

        // Single-line: photos (left) + buttons (right)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Photo thumbnails — left-aligned
            photos.forEachIndexed { index, bitmap ->
                val isSelected = index == selectedIndex && isFocused
                Box(modifier = Modifier.padding(end = 4.dp)) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Photo ${index + 1}",
                        modifier = Modifier
                            .size(width = 36.dp, height = 27.dp)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) HudColors.green else HudColors.dimText,
                                shape = RoundedCornerShape(2.dp)
                            ),
                        contentScale = ContentScale.Crop,
                        colorFilter = greenColorMatrix
                    )
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(width = 36.dp, height = 27.dp)
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(2.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "\u2715",
                                color = HudColors.green,
                                fontSize = 12.sp,
                                fontFamily = fontFamily,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            if (hasContent) {
                Spacer(modifier = Modifier.weight(1f))

                // Clear button
                val clearIndex = photoCount  // index right after photos
                val clearSelected = selectedIndex == clearIndex && isFocused
                Box(
                    modifier = Modifier
                        .background(
                            if (clearSelected) HudColors.green.copy(alpha = 0.3f) else Color.Transparent,
                            RoundedCornerShape(4.dp)
                        )
                        .border(
                            width = if (clearSelected) 1.dp else 0.dp,
                            color = if (clearSelected) HudColors.green else Color.Transparent,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = InputActionItem.CLEAR.icon,
                            color = if (clearSelected) HudColors.green else HudColors.primaryText,
                            fontSize = (commandFontSize.value + 2).sp,
                            fontFamily = fontFamily
                        )
                        Text(
                            text = InputActionItem.CLEAR.label,
                            color = if (clearSelected) HudColors.green else HudColors.dimText,
                            fontSize = commandFontSize,
                            fontFamily = fontFamily,
                            fontWeight = if (clearSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Send button
                val sendIndex = photoCount + 1  // last item
                val sendSelected = selectedIndex == sendIndex && isFocused
                Box(
                    modifier = Modifier
                        .background(
                            if (sendSelected) HudColors.green.copy(alpha = 0.3f) else Color.Transparent,
                            RoundedCornerShape(4.dp)
                        )
                        .border(
                            width = if (sendSelected) 1.dp else 0.dp,
                            color = if (sendSelected) HudColors.green else Color.Transparent,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = InputActionItem.SEND.icon,
                            color = if (sendSelected) HudColors.green else HudColors.primaryText,
                            fontSize = (commandFontSize.value + 2).sp,
                            fontFamily = fontFamily
                        )
                        Text(
                            text = InputActionItem.SEND.label,
                            color = if (sendSelected) HudColors.green else HudColors.dimText,
                            fontSize = commandFontSize,
                            fontFamily = fontFamily,
                            fontWeight = if (sendSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// MENU BAR
// ============================================================================

@Composable
private fun ChatMenuBar(
    selectedIndex: Int,
    isFocused: Boolean,
    hudPosition: HudPosition,
    batteryLevel: Int?,
    batteryCharging: Boolean,
    currentTime: String,
    fontFamily: FontFamily,
    alpha: Float,
    modifier: Modifier = Modifier
) {
    val commandFontSize = 8.sp  // Fixed size — FONT only affects content
    val items = MenuBarItem.entries
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(alpha),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = index == selectedIndex && isFocused

                val displayLabel = when (item) {
                    MenuBarItem.PHOTO -> "CAM"
                    MenuBarItem.SESSION -> "SESS"
                    MenuBarItem.SIZE -> when (hudPosition) {
                        HudPosition.FULL -> "FULL"
                        HudPosition.BOTTOM_HALF -> "BOT"
                        HudPosition.TOP_HALF -> "MID"
                    }
                    MenuBarItem.MORE -> "MORE"
                }
                val buttonText = if (isSelected) "> $displayLabel <" else displayLabel

                Box(
                    modifier = Modifier
                        .width(54.dp)
                        .height(24.dp)
                        .background(
                            if (isSelected) HudColors.green.copy(alpha = 0.16f) else Color.Transparent,
                            RoundedCornerShape(2.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) HudColors.green else HudColors.green.copy(alpha = 0.38f),
                            shape = RoundedCornerShape(2.dp)
                        )
                        .padding(horizontal = 2.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = buttonText,
                        color = if (isSelected) HudColors.green else HudColors.primaryText.copy(alpha = 0.72f),
                        fontSize = commandFontSize,
                        fontFamily = fontFamily,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }

        // Current time (HH:MM, 24-hour format)
        if (currentTime.isNotEmpty()) {
            Text(
                text = currentTime,
                color = HudColors.dimText,
                fontSize = commandFontSize,
                fontFamily = fontFamily,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        // Battery indicator (bottom-right, only shown when available)
        if (batteryLevel != null) {
            Text(
                text = "${if (batteryCharging) "\u26A1" else "\uD83D\uDD0B"}${batteryLevel}%",  // ⚡ or 🔋
                color = if (batteryLevel <= 15) HudColors.error else HudColors.dimText,
                fontSize = commandFontSize,
                fontFamily = fontFamily,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

// ============================================================================
// HUD OVERLAY PANEL
// ============================================================================

@Composable
private fun HudOverlayPanel(
    title: String,
    fontFamily: FontFamily,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 18.dp,
    verticalPadding: Dp = 18.dp,
    footerText: String = "SWIPE SELECT  TAP OK  2XTAP BACK",
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding)
                .border(1.dp, HudColors.green, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 10.dp)
        ) {
            Text(
                text = title,
                color = HudColors.green,
                fontSize = 14.sp,
                fontFamily = fontFamily,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(HudColors.green)
            )
            Spacer(modifier = Modifier.height(8.dp))

            content()

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = footerText,
                color = HudColors.dimText,
                fontSize = 9.sp,
                fontFamily = fontFamily,
                maxLines = 1
            )
        }
    }
}

// ============================================================================
// SESSION PICKER OVERLAY
// ============================================================================

@Composable
private fun SessionPickerOverlay(
    sessions: List<SessionPickerInfo>,
    currentSessionKey: String?,
    selectedIndex: Int,
    fontFamily: FontFamily,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Keep selected item visible
    LaunchedEffect(selectedIndex) {
        if (sessions.isNotEmpty()) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    HudOverlayPanel(
        title = "SESSIONS",
        fontFamily = fontFamily,
        modifier = modifier
    ) {
        if (sessions.isEmpty()) {
            Text(
                text = "NO SESSIONS",
                color = HudColors.dimText,
                fontSize = 13.sp,
                fontFamily = fontFamily
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(sessions) { index, session ->
                    val isSelected = index == selectedIndex
                    val isCurrent = session.key == currentSessionKey
                    val isNewSession = session.key == "__new_session__"
                    val mark = when {
                        isNewSession -> "+"
                        isCurrent -> "*"
                        session.hasUnread -> "!"
                        else -> " "
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .border(
                                width = if (isSelected) 1.dp else 0.dp,
                                color = if (isSelected) HudColors.green else Color.Transparent,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 7.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isSelected) ">" else " ",
                            color = if (isSelected) HudColors.green else Color.Transparent,
                            fontSize = 12.sp,
                            fontFamily = fontFamily
                        )
                        Text(
                            text = mark,
                            color = HudColors.green,
                            fontSize = 12.sp,
                            fontFamily = fontFamily
                        )
                        Text(
                            text = session.name,
                            color = if (isSelected) HudColors.green else HudColors.primaryText,
                            fontSize = 12.sp,
                            fontFamily = fontFamily,
                            fontWeight = if (isCurrent || isNewSession || isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (session.updatedAt != null) {
                            Text(
                                text = formatRelativeTime(session.updatedAt),
                                color = HudColors.dimText,
                                fontSize = 9.sp,
                                fontFamily = fontFamily,
                                maxLines = 1
                            )
                        }
                        Text(
                            text = if (isSelected) "<" else " ",
                            color = if (isSelected) HudColors.green else Color.Transparent,
                            fontSize = 12.sp,
                            fontFamily = fontFamily
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// MORE MENU OVERLAY
// ============================================================================

@Composable
private fun MoreMenuOverlay(
    selectedIndex: Int,
    currentDisplaySize: HudDisplaySize,
    voiceSendMode: VoiceSendMode,
    ttsEnabled: Boolean,
    fontFamily: FontFamily,
    modifier: Modifier = Modifier
) {
    val items = MoreMenuItem.entries

    HudOverlayPanel(
        title = "OPTIONS",
        fontFamily = fontFamily,
        modifier = modifier,
        horizontalPadding = 22.dp,
        verticalPadding = 24.dp
    ) {
        Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items.forEachIndexed { itemIndex, item ->
                    val isSelected = itemIndex == selectedIndex
                    val isActive = when (item) {
                        MoreMenuItem.VOICE_SEND -> voiceSendMode == VoiceSendMode.AUTO
                        MoreMenuItem.VOICE -> ttsEnabled
                        else -> item.displaySize == currentDisplaySize
                    }

                    // Dynamic labels for toggle items
                    val displayLabel = when (item) {
                        MoreMenuItem.VOICE_SEND -> if (voiceSendMode == VoiceSendMode.AUTO) "SEND AUTO" else "SEND ASK"
                        MoreMenuItem.FONT_COMPACT -> "FONT COMPACT"
                        MoreMenuItem.FONT_NORMAL -> "FONT NORMAL"
                        MoreMenuItem.FONT_COMFORTABLE -> "FONT COMFORT"
                        MoreMenuItem.FONT_LARGE -> "FONT LARGE"
                        MoreMenuItem.SLASH -> "SLASH CMDS"
                        MoreMenuItem.AR_PICTURE -> "AR PIC"
                        MoreMenuItem.AR_RECORD -> "AR REC"
                        MoreMenuItem.AR_STOP -> "AR STOP"
                        MoreMenuItem.VOICE -> if (ttsEnabled) "VOICE ON" else "VOICE OFF"
                    }
                    val activeMark = if (isActive) "*" else " "
                    val leftMark = if (isSelected) ">" else " "
                    val rightMark = if (isSelected) "<" else " "
                    val itemFontSize = if (item.displaySize != null) 12.sp else 13.sp

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .border(
                                width = if (isSelected) 1.dp else 0.dp,
                                color = if (isSelected) HudColors.green else Color.Transparent,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = leftMark,
                            color = if (isSelected) HudColors.green else Color.Transparent,
                            fontSize = itemFontSize,
                            fontFamily = fontFamily
                        )
                        Text(
                            text = activeMark,
                            color = HudColors.green,
                            fontSize = itemFontSize,
                            fontFamily = fontFamily
                        )
                        Text(
                            text = displayLabel,
                            color = if (isSelected) HudColors.green else HudColors.primaryText,
                            fontSize = itemFontSize,
                            fontFamily = fontFamily,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = rightMark,
                            color = if (isSelected) HudColors.green else Color.Transparent,
                            fontSize = itemFontSize,
                            fontFamily = fontFamily
                        )
                    }
                }
        }
    }
}

// ============================================================================
// SLASH COMMAND OVERLAY
// ============================================================================

@Composable
private fun SlashCommandOverlay(
    selectedIndex: Int,
    fontFamily: FontFamily,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Keep selected item visible
    LaunchedEffect(selectedIndex) {
        listState.animateScrollToItem(selectedIndex)
    }

    HudOverlayPanel(
        title = "COMMANDS",
        fontFamily = fontFamily,
        modifier = modifier
    ) {
        LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(SLASH_COMMANDS) { index, item ->
                    val isSelected = index == selectedIndex

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .border(
                                width = if (isSelected) 1.dp else 0.dp,
                                color = if (isSelected) HudColors.green else Color.Transparent,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isSelected) ">" else " ",
                            color = HudColors.green,
                            fontSize = 12.sp,
                            fontFamily = fontFamily
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = item.command,
                            color = if (isSelected) HudColors.green else HudColors.primaryText,
                            fontSize = 12.sp,
                            fontFamily = fontFamily,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.width(82.dp)
                        )
                        Text(
                            text = item.description,
                            color = HudColors.dimText,
                            fontSize = 10.sp,
                            fontFamily = fontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (isSelected) "<" else " ",
                            color = if (isSelected) HudColors.green else Color.Transparent,
                            fontSize = 12.sp,
                            fontFamily = fontFamily
                        )
                    }
                }
        }
    }
}

// ============================================================================
// SLASH PARAMETER OVERLAY
// ============================================================================

@Composable
private fun SlashParamOverlay(
    menuType: SlashParamMenuType?,
    selectedIndex: Int,
    fontFamily: FontFamily,
    modifier: Modifier = Modifier
) {
    val type = menuType ?: SlashParamMenuType.MODEL
    val options = slashParamOptions(type)
    val listState = rememberLazyListState()

    LaunchedEffect(type, selectedIndex) {
        if (options.isNotEmpty()) {
            listState.animateScrollToItem(selectedIndex.coerceIn(options.indices))
        }
    }

    HudOverlayPanel(
        title = type.title,
        fontFamily = fontFamily,
        modifier = modifier,
        footerText = "SWIPE SELECT  TAP SEND  2XTAP BACK"
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(options) { index, item ->
                val isSelected = index == selectedIndex

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .border(
                            width = if (isSelected) 1.dp else 0.dp,
                            color = if (isSelected) HudColors.green else Color.Transparent,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isSelected) ">" else " ",
                        color = HudColors.green,
                        fontSize = 12.sp,
                        fontFamily = fontFamily
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = item.label,
                        color = if (isSelected) HudColors.green else HudColors.primaryText,
                        fontSize = 12.sp,
                        fontFamily = fontFamily,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(82.dp)
                    )
                    Text(
                        text = item.description,
                        color = HudColors.dimText,
                        fontSize = 10.sp,
                        fontFamily = fontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (isSelected) "<" else " ",
                        color = if (isSelected) HudColors.green else Color.Transparent,
                        fontSize = 12.sp,
                        fontFamily = fontFamily
                    )
                }
            }
        }
    }
}

// ============================================================================
// EXIT CONFIRMATION OVERLAY
// ============================================================================

@Composable
private fun ExitConfirmOverlay(
    fontFamily: FontFamily,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "EXIT",
                color = HudColors.error,
                fontSize = 16.sp,
                fontFamily = fontFamily,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "2\u00D7TAP again to exit",
                color = HudColors.primaryText,
                fontSize = 14.sp,
                fontFamily = fontFamily,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Any other input to continue",
                color = HudColors.dimText,
                fontSize = 12.sp,
                fontFamily = fontFamily,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ============================================================================
// HUD COLORS
// ============================================================================

object HudColors {
    val primaryText = Color(0xFF00FF00)    // Bright green
    val cyan = Color(0xFF00FFFF)           // Cyan
    val green = Color(0xFF39FF14)          // Neon green
    val yellow = Color(0xFFFFFF00)         // Yellow
    val dimText = Color(0xFF666666)        // Dimmed
    val error = Color(0xFFFF4444)          // Red
}
