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
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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
    PHOTO("CAM", "Cam"),
    SESSION("SESS", "Sess"),
    VOICE_SEND("MIC", "Ask"),
    AR_TOOLS("AR", "AR"),
    SIZE("SIZE", "Size"),
    MORE("...", "More"),
    SLASH("/", "Cmd"),
    CODEX_CLI("CODX", "Codex"),
}

/**
 * Items available in the MORE menu
 */
enum class MoreMenuItem(val icon: String, val label: String) {
    VOICE_SEND("SEND", "Send Mode"),
    AR_TOOLS("AR", "AR Tools"),
    DISPLAY("DISP", "Display"),
    CODEX_CLI("CODX", "Codex CLI"),
    CODEX_CLEAR("CLR", "Codex Clear"),
    VOICE("\uD83D\uDD0A", "TTS"),  // speaker icon - label is dynamic
}

enum class VoiceSendMode {
    ASK,
    AUTO
}

enum class CliActionItem(val label: String) {
    CAM("Cam"),
    LINK("Link"),
    SEND("Send"),
    STOP("Stop"),
    MODE("Mode"),
    CLEAR("Clear")
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
 * Chat HUD state - replaces the old TerminalState
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
    val menuBarPage: Int = 0,
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
    val showMoreSubMenu: Boolean = false,
    val selectedMoreSubIndex: Int = 0,
    val moreSubMenuType: MoreSubMenuType? = null,
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
    val ttsEnabled: Boolean = false,
    // Experimental Codex CLI terminal overlay
    val showCliTerminal: Boolean = false,
    val cliStatus: String = "OFFLINE",
    val cliDetail: String = "",
    val cliLines: List<String> = emptyList(),
    val cliScrollPosition: Int = 0,
    val cliScrollTrigger: Int = 0,
    val cliScrollCommand: Int = 0,
    val cliScrollDirection: Int = 0,
    val cliIsScrolledToEnd: Boolean = false,
    val cliActionIndex: Int = CliActionItem.SEND.ordinal
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

enum class MoreSubMenuType(val title: String) {
    AR("AR TOOLS"),
    DISPLAY("DISPLAY")
}

data class MoreSubMenuOption(
    val label: String,
    val description: String,
    val displaySize: HudDisplaySize? = null,
    val arAction: String? = null
)

/**
 * Available slash commands from the OpenClaw Gateway.
 * Keep this list aligned with the OpenClaw slash command reference.
 */
val SLASH_COMMANDS = listOf(
    SlashCommandItem("/status", "Show status"),
    SlashCommandItem("/model", "Switch model"),
    SlashCommandItem("/think", "Thinking level"),
    SlashCommandItem("/reset", "New session"),
    SlashCommandItem("/stop", "Stop generation"),
    SlashCommandItem("/help", "Show help"),
    SlashCommandItem("/commands", "List commands"),
    SlashCommandItem("/compact", "Compact context"),
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

val AR_TOOL_OPTIONS = listOf(
    MoreSubMenuOption("AR PIC", "Take AR picture", arAction = "picture"),
    MoreSubMenuOption("AR REC", "Start AR recording", arAction = "record_start"),
    MoreSubMenuOption("AR STOP", "Stop AR recording", arAction = "record_stop")
)

val DISPLAY_OPTIONS = listOf(
    MoreSubMenuOption("COMPACT", "Smallest text", displaySize = HudDisplaySize.COMPACT),
    MoreSubMenuOption("NORMAL", "Balanced text", displaySize = HudDisplaySize.NORMAL),
    MoreSubMenuOption("COMFORT", "Larger text", displaySize = HudDisplaySize.COMFORTABLE),
    MoreSubMenuOption("LARGE", "Largest text", displaySize = HudDisplaySize.LARGE)
)

fun moreSubMenuOptions(type: MoreSubMenuType): List<MoreSubMenuOption> = when (type) {
    MoreSubMenuType.AR -> AR_TOOL_OPTIONS
    MoreSubMenuType.DISPLAY -> DISPLAY_OPTIONS
}

// ============================================================================
// MAIN HUD SCREEN
// ============================================================================

/**
 * Chat-oriented HUD display for Rokid Glasses with OpenClaw backend.
 *
 * Layout:
 * [TopBar]
 * Assistant and session content
 * [Input / staged photos]
 * [Bottom menu]
 */
@Composable
fun HudScreen(
    state: ChatHudState,
    onTap: () -> Unit = {},
    onDoubleTap: () -> Unit = {},
    onLongPress: () -> Unit = {},
    onScrolledToEndChanged: (Boolean) -> Unit = {},
    onCliScrolledToEndChanged: (Boolean) -> Unit = {}
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
                // During streaming, use instant scroll - animated scroll gets
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
        // Calculate font size to fit content width - varies with displaySize
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

                // CONTENT AREA - chat messages
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
                            menuBarPage = state.menuBarPage,
                            isFocused = menuFocused,
                            hudPosition = state.hudPosition,
                            batteryLevel = null,
                            batteryCharging = false,
                            currentTime = "",
                            fontFamily = monoFontFamily,
                            voiceSendMode = state.voiceSendMode,
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

        // More submenu overlay
        AnimatedVisibility(
            visible = state.showMoreSubMenu,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            MoreSubMenuOverlay(
                menuType = state.moreSubMenuType,
                selectedIndex = state.selectedMoreSubIndex,
                currentDisplaySize = state.displaySize,
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

        // Experimental Codex CLI terminal overlay
        AnimatedVisibility(
            visible = state.showCliTerminal,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            CliTerminalOverlay(
                lines = state.cliLines,
                status = state.cliStatus,
                detail = state.cliDetail,
                scrollPosition = state.cliScrollPosition,
                scrollTrigger = state.cliScrollTrigger,
                scrollCommand = state.cliScrollCommand,
                scrollDirection = state.cliScrollDirection,
                selectedActionIndex = state.cliActionIndex,
                focusedArea = state.focusedArea,
                inputActionIndex = state.inputActionIndex,
                stagingText = state.stagingText,
                voiceText = state.voiceText,
                showInputStaging = state.showInputStaging,
                photos = state.photoThumbnails,
                voiceState = state.voiceState,
                voiceSendMode = state.voiceSendMode,
                displaySize = state.displaySize,
                fontFamily = monoFontFamily,
                onScrolledToEndChanged = onCliScrolledToEndChanged
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
                color = if (batteryLevel != null && batteryLevel <= 15) HudColors.error else HudColors.primaryText.copy(alpha = 0.86f),
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
 *   [Photo1] [Photo2] ... [Photo4]  spacer ->  [Clear] [Send]
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
            // Photo thumbnails - left-aligned
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
    menuBarPage: Int,
    isFocused: Boolean,
    hudPosition: HudPosition,
    batteryLevel: Int?,
    batteryCharging: Boolean,
    currentTime: String,
    fontFamily: FontFamily,
    voiceSendMode: VoiceSendMode,
    alpha: Float,
    modifier: Modifier = Modifier
) {
    val commandFontSize = 8.sp  // Fixed size - FONT only affects content
    val pageItems = menuBarItemsForPage(menuBarPage.coerceIn(0, 1))
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
            pageItems.forEachIndexed { index, item ->
                val isSelected = index == selectedIndex && isFocused


                val displayLabel = when (item) {
                    MenuBarItem.PHOTO -> "CAM"
                    MenuBarItem.SESSION -> "SESSION"
                    MenuBarItem.VOICE_SEND -> if (voiceSendMode == VoiceSendMode.AUTO) "AUTO" else "ASK"
                    MenuBarItem.AR_TOOLS -> "AR"
                    MenuBarItem.SIZE -> "SIZE"
                    MenuBarItem.MORE -> "MORE"
                    MenuBarItem.SLASH -> "CMD"
                    MenuBarItem.CODEX_CLI -> "CODEX"
                }

                Box(
                    modifier = Modifier
                        .width(56.dp)
                        .height(30.dp)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) HudColors.green else HudColors.green.copy(alpha = 0.38f),
                            shape = RoundedCornerShape(2.dp)
                        )
                        .padding(horizontal = 2.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        HudMenuIcon(
                            item = item,
                            color = if (isSelected) HudColors.green else HudColors.primaryText.copy(alpha = 0.92f),
                            voiceSendMode = voiceSendMode,
                            modifier = Modifier
                                .width(30.dp)
                                .height(15.dp)
                        )
                        Text(
                            text = displayLabel.uppercase(),
                            color = if (isSelected) HudColors.green else HudColors.primaryText.copy(alpha = 0.92f),
                            fontSize = 6.sp,
                            fontFamily = fontFamily,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Current time (HH:MM, 24-hour format)
        if (currentTime.isNotEmpty()) {
            Text(
                text = currentTime,
                color = HudColors.primaryText.copy(alpha = 0.86f),
                fontSize = commandFontSize,
                fontFamily = fontFamily,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        // Battery indicator (bottom-right, only shown when available)
        if (batteryLevel != null) {
            Text(
                text = "${if (batteryCharging) "\u26A1" else "\uD83D\uDD0B"}${batteryLevel}%",  // charging or battery
                color = if (batteryLevel <= 15) HudColors.error else HudColors.primaryText.copy(alpha = 0.86f),
                fontSize = commandFontSize,
                fontFamily = fontFamily,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

fun menuBarItemsForPage(page: Int): List<MenuBarItem> = if (page == 0) {
    listOf(MenuBarItem.PHOTO, MenuBarItem.SESSION, MenuBarItem.VOICE_SEND, MenuBarItem.AR_TOOLS)
} else {
    listOf(MenuBarItem.SIZE, MenuBarItem.MORE, MenuBarItem.SLASH, MenuBarItem.CODEX_CLI)
}

private fun pageForMenuBarItem(item: MenuBarItem): Int = if (item in menuBarItemsForPage(0)) 0 else 1

private fun indexForMenuBarItem(item: MenuBarItem): Int = menuBarItemsForPage(pageForMenuBarItem(item)).indexOf(item).coerceAtLeast(0)

@Composable
private fun HudMenuIcon(
    item: MenuBarItem,
    color: Color,
    voiceSendMode: VoiceSendMode,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(
            width = 1.9.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        val thin = Stroke(
            width = 1.25.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        when (item) {
            MenuBarItem.VOICE_SEND -> {
                if (voiceSendMode == VoiceSendMode.AUTO) {
                    drawLine(color, Offset(w * 0.32f, h * 0.24f), Offset(w * 0.32f, h * 0.76f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    drawLine(color, Offset(w * 0.50f, h * 0.14f), Offset(w * 0.50f, h * 0.86f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    drawLine(color, Offset(w * 0.68f, h * 0.24f), Offset(w * 0.68f, h * 0.76f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    drawCircle(color = color, radius = 1.6.dp.toPx(), center = Offset(w * 0.50f, h * 0.50f))
                } else {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(w * 0.34f, h * 0.10f),
                        size = Size(w * 0.32f, h * 0.54f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.16f, w * 0.16f),
                        style = stroke
                    )
                    drawLine(color, Offset(w * 0.50f, h * 0.64f), Offset(w * 0.50f, h * 0.84f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    drawLine(color, Offset(w * 0.34f, h * 0.84f), Offset(w * 0.66f, h * 0.84f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    drawLine(color, Offset(w * 0.20f, h * 0.42f), Offset(w * 0.20f, h * 0.58f), strokeWidth = thin.width, cap = StrokeCap.Round)
                    drawLine(color, Offset(w * 0.80f, h * 0.42f), Offset(w * 0.80f, h * 0.58f), strokeWidth = thin.width, cap = StrokeCap.Round)
                }
            }
            MenuBarItem.PHOTO -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.15f, h * 0.34f),
                    size = Size(w * 0.70f, h * 0.50f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.10f, w * 0.10f),
                    style = stroke
                )
                drawLine(color, Offset(w * 0.30f, h * 0.34f), Offset(w * 0.40f, h * 0.16f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.40f, h * 0.16f), Offset(w * 0.62f, h * 0.16f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.62f, h * 0.16f), Offset(w * 0.72f, h * 0.34f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawCircle(color = color, radius = h * 0.16f, center = Offset(w * 0.50f, h * 0.60f), style = stroke)
                drawCircle(color = color, radius = h * 0.04f, center = Offset(w * 0.72f, h * 0.46f))
            }
            MenuBarItem.SESSION -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.13f, h * 0.18f),
                    size = Size(w * 0.74f, h * 0.48f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.10f, w * 0.10f),
                    style = stroke
                )
                drawLine(color, Offset(w * 0.38f, h * 0.66f), Offset(w * 0.28f, h * 0.88f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.28f, h * 0.88f), Offset(w * 0.50f, h * 0.66f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.27f, h * 0.39f), Offset(w * 0.72f, h * 0.39f), strokeWidth = thin.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.27f, h * 0.52f), Offset(w * 0.58f, h * 0.52f), strokeWidth = thin.width, cap = StrokeCap.Round)
            }
            MenuBarItem.SIZE -> {
                drawCircle(color = color, radius = h * 0.045f, center = Offset(w * 0.20f, h * 0.25f))
                drawCircle(color = color, radius = h * 0.045f, center = Offset(w * 0.20f, h * 0.50f))
                drawCircle(color = color, radius = h * 0.045f, center = Offset(w * 0.20f, h * 0.75f))
                drawLine(color, Offset(w * 0.34f, h * 0.25f), Offset(w * 0.86f, h * 0.25f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.34f, h * 0.50f), Offset(w * 0.74f, h * 0.50f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.34f, h * 0.75f), Offset(w * 0.62f, h * 0.75f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            MenuBarItem.SLASH -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.12f, h * 0.16f),
                    size = Size(w * 0.76f, h * 0.68f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.10f, w * 0.10f),
                    style = thin
                )
                drawLine(color, Offset(w * 0.32f, h * 0.72f), Offset(w * 0.68f, h * 0.28f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            MenuBarItem.AR_TOOLS -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.08f, h * 0.34f),
                    size = Size(w * 0.38f, h * 0.38f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.12f, w * 0.12f),
                    style = stroke
                )
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.54f, h * 0.34f),
                    size = Size(w * 0.38f, h * 0.38f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.12f, w * 0.12f),
                    style = stroke
                )
                drawLine(color, Offset(w * 0.46f, h * 0.50f), Offset(w * 0.54f, h * 0.50f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.22f, h * 0.18f), Offset(w * 0.16f, h * 0.08f), strokeWidth = thin.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.50f, h * 0.16f), Offset(w * 0.50f, h * 0.04f), strokeWidth = thin.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.78f, h * 0.18f), Offset(w * 0.84f, h * 0.08f), strokeWidth = thin.width, cap = StrokeCap.Round)
            }
            MenuBarItem.CODEX_CLI -> {
                drawLine(color, Offset(w * 0.38f, h * 0.18f), Offset(w * 0.14f, h * 0.50f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.14f, h * 0.50f), Offset(w * 0.38f, h * 0.82f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.62f, h * 0.18f), Offset(w * 0.86f, h * 0.50f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.86f, h * 0.50f), Offset(w * 0.62f, h * 0.82f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.52f, h * 0.18f), Offset(w * 0.48f, h * 0.82f), strokeWidth = thin.width, cap = StrokeCap.Round)
            }
            MenuBarItem.MORE -> {
                drawCircle(color = color, radius = h * 0.11f, center = Offset(w * 0.28f, h * 0.50f))
                drawCircle(color = color, radius = h * 0.11f, center = Offset(w * 0.50f, h * 0.50f))
                drawCircle(color = color, radius = h * 0.11f, center = Offset(w * 0.72f, h * 0.50f))
            }
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
                color = HudColors.primaryText.copy(alpha = 0.72f),
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
                                color = if (isSelected) HudColors.primaryText else HudColors.primaryText.copy(alpha = 0.86f),
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
                        else -> false
                    }

                    // Dynamic labels for toggle items
                    val displayLabel = when (item) {
                        MoreMenuItem.VOICE_SEND -> if (voiceSendMode == VoiceSendMode.AUTO) "SEND AUTO" else "SEND ASK"
                        MoreMenuItem.AR_TOOLS -> "AR TOOLS"
                        MoreMenuItem.DISPLAY -> "DISPLAY"
                        MoreMenuItem.CODEX_CLI -> "CODEX CLI"
                        MoreMenuItem.CODEX_CLEAR -> "CODEX CLEAR"
                        MoreMenuItem.VOICE -> if (ttsEnabled) "TTS ON" else "TTS OFF"
                    }
                    val activeMark = if (isActive) "*" else " "
                    val leftMark = if (isSelected) ">" else " "
                    val rightMark = if (isSelected) "<" else " "
                    val itemFontSize = 13.sp

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
// MORE SUBMENU OVERLAY
// ============================================================================

@Composable
private fun MoreSubMenuOverlay(
    menuType: MoreSubMenuType?,
    selectedIndex: Int,
    currentDisplaySize: HudDisplaySize,
    fontFamily: FontFamily,
    modifier: Modifier = Modifier
) {
    val type = menuType ?: MoreSubMenuType.DISPLAY
    val options = moreSubMenuOptions(type)
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
        footerText = "SWIPE SELECT  TAP OK  2XTAP BACK"
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
                val isActive = item.displaySize == currentDisplaySize
                val activeMark = if (isActive) "*" else " "

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
                        text = activeMark,
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
                        fontWeight = if (isSelected || isActive) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(92.dp)
                    )
                    Text(
                        text = item.description,
                        color = HudColors.primaryText.copy(alpha = 0.86f),
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
                            text = " ",
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
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(92.dp)
                        )
                        Text(
                            text = item.description,
                            color = if (isSelected) HudColors.primaryText else HudColors.primaryText.copy(alpha = 0.86f),
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
                        text = " ",
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
                        modifier = Modifier.width(92.dp)
                    )
                    Text(
                        text = item.description,
                        color = if (isSelected) HudColors.primaryText else HudColors.primaryText.copy(alpha = 0.86f),
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
// CODEX CLI TERMINAL OVERLAY
// ============================================================================

private enum class CliBlockKind {
    INPUT,
    RESPONSE,
    SYSTEM,
    ERROR
}

private data class CliLineBlock(
    val kind: CliBlockKind,
    val text: String
)

private const val CLI_RESPONSE_SEGMENT_CHARS = 280
private const val CLI_RESPONSE_SEGMENT_LINES = 5

private fun splitCliResponseText(text: String): List<String> {
    val segments = mutableListOf<String>()
    val currentLines = mutableListOf<String>()
    var currentLength = 0

    fun flush() {
        val segment = currentLines.joinToString("\n").trim()
        if (segment.isNotEmpty()) {
            segments += segment
        }
        currentLines.clear()
        currentLength = 0
    }

    fun splitLongLine(line: String): List<String> {
        val chunks = mutableListOf<String>()
        var remaining = line.trim()
        while (remaining.length > CLI_RESPONSE_SEGMENT_CHARS) {
            val window = remaining.take(CLI_RESPONSE_SEGMENT_CHARS + 1)
            val whitespaceIndex = window.lastIndexOf(' ').takeIf { it >= CLI_RESPONSE_SEGMENT_CHARS / 2 }
            val splitAt = whitespaceIndex ?: CLI_RESPONSE_SEGMENT_CHARS
            val chunk = remaining.take(splitAt).trim()
            if (chunk.isNotEmpty()) chunks += chunk
            remaining = remaining.drop(splitAt).trimStart()
        }
        if (remaining.isNotEmpty()) chunks += remaining
        return chunks
    }

    text.lines().forEach { rawLine ->
        val line = rawLine.trimEnd()
        if (line.isBlank()) {
            flush()
            return@forEach
        }

        splitLongLine(line).forEach { chunk ->
            val wouldOverflow = currentLines.size >= CLI_RESPONSE_SEGMENT_LINES ||
                    currentLength + chunk.length > CLI_RESPONSE_SEGMENT_CHARS
            if (wouldOverflow) flush()
            currentLines += chunk
            currentLength += chunk.length
        }
    }

    flush()
    return segments.ifEmpty { listOf(text.trim()) }
}

internal fun cliVisibleBlockCount(lines: List<String>): Int = buildCliLineBlocks(lines).size

private fun buildCliLineBlocks(lines: List<String>): List<CliLineBlock> {
    if (lines.isEmpty()) {
        return listOf(CliLineBlock(CliBlockKind.SYSTEM, "Admin Codex ready."))
    }

    val blocks = mutableListOf<CliLineBlock>()
    val responseBuffer = mutableListOf<String>()

    fun flushResponse() {
        val text = responseBuffer.joinToString("\n").trim()
        if (text.isNotEmpty()) {
            splitCliResponseText(text).forEach { segment ->
                blocks += CliLineBlock(CliBlockKind.RESPONSE, segment)
            }
        }
        responseBuffer.clear()
    }

    lines.forEach { rawLine ->
        val line = rawLine.trimEnd()
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return@forEach

        when {
            trimmed.startsWith("[status]", ignoreCase = true) -> {
                // Status is already shown in the header; keep the transcript clean.
            }
            trimmed.startsWith("[error]", ignoreCase = true) -> {
                flushResponse()
                blocks += CliLineBlock(
                    CliBlockKind.ERROR,
                    trimmed.removePrefix("[error]").trim().ifEmpty { "Codex bridge error" }
                )
            }
            trimmed.startsWith(">") -> {
                flushResponse()
                blocks += CliLineBlock(CliBlockKind.INPUT, trimmed.removePrefix(">").trim())
            }
            trimmed.startsWith("[link]", ignoreCase = true) -> {
                flushResponse()
            }
            trimmed.contains("screen cleared", ignoreCase = true) ||
                    trimmed.contains("output cleared", ignoreCase = true) ||
                    trimmed.contains("bridge ready", ignoreCase = true) ||
                    trimmed.contains("Admin Codex ready", ignoreCase = true) -> {
                flushResponse()
                blocks += CliLineBlock(CliBlockKind.SYSTEM, trimmed)
            }
            else -> responseBuffer += line
        }
    }

    flushResponse()
    return blocks.ifEmpty { listOf(CliLineBlock(CliBlockKind.SYSTEM, "Admin Codex ready.")) }
}
@Composable
private fun CliTerminalOverlay(
    lines: List<String>,
    status: String,
    detail: String,
    scrollPosition: Int,
    scrollTrigger: Int,
    scrollCommand: Int,
    scrollDirection: Int,
    selectedActionIndex: Int,
    focusedArea: ChatFocusArea,
    inputActionIndex: Int,
    stagingText: String,
    voiceText: String,
    showInputStaging: Boolean,
    photos: List<Bitmap>,
    voiceState: VoiceInputState,
    voiceSendMode: VoiceSendMode,
    displaySize: HudDisplaySize,
    fontFamily: FontFamily,
    onScrolledToEndChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val visibleBlocks = remember(lines) { buildCliLineBlocks(lines) }
    val lastBlockText = visibleBlocks.lastOrNull()?.text.orEmpty()
    val lastResponseIndex = visibleBlocks.indexOfLast { it.kind == CliBlockKind.RESPONSE }
    val bodyFontSize = displaySize.fontSizeSp.sp
    val smallFontSize = (displaySize.fontSizeSp - 2).coerceAtLeast(8).sp
    val selectedAction = selectedActionIndex.coerceIn(CliActionItem.entries.indices)
    val statusColor = when (status.uppercase()) {
        "CONNECTED" -> HudColors.green
        "CONNECTING" -> HudColors.yellow
        "ERROR" -> HudColors.error
        else -> HudColors.primaryText
    }
    val statusLabel = when (status.uppercase()) {
        "CONNECTED" -> "READY"
        "CONNECTING" -> "LINK"
        "ERROR" -> "ERROR"
        else -> status.ifBlank { "OFFLINE" }
    }

    val canScrollForward = listState.canScrollForward
    LaunchedEffect(canScrollForward) {
        onScrolledToEndChanged(!canScrollForward)
    }

    LaunchedEffect(scrollCommand) {
        if (scrollCommand != 0 && scrollDirection != 0) {
            val viewportHeight = listState.layoutInfo.viewportSize.height
            val fallbackHeight = 420
            val distance = (if (viewportHeight > 0) viewportHeight else fallbackHeight) * 0.72f
            listState.animateScrollBy(scrollDirection * distance)
        }
    }

    LaunchedEffect(scrollPosition, scrollTrigger, visibleBlocks.size, lastBlockText) {
        if (visibleBlocks.isNotEmpty()) {
            val totalItems = visibleBlocks.size
            val targetIndex = scrollPosition.coerceIn(0, totalItems - 1)
            val currentIndex = listState.firstVisibleItemIndex
            if (targetIndex < currentIndex) {
                val viewportHeight = listState.layoutInfo.viewportSize.height
                val itemsToScroll = currentIndex - targetIndex
                val visibleItems = listState.layoutInfo.visibleItemsInfo
                val avgItemHeight = if (visibleItems.isNotEmpty()) {
                    visibleItems.sumOf { it.size } / visibleItems.size.toFloat()
                } else {
                    viewportHeight / 5f
                }
                listState.animateScrollBy(-(itemsToScroll * avgItemHeight))
            } else if (targetIndex == totalItems - 1) {
                listState.animateScrollToItem(targetIndex, Int.MAX_VALUE)
            } else {
                listState.animateScrollToItem(targetIndex)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.TopStart
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = TopGhostSafeZone)
                .padding(horizontal = 2.dp, vertical = 6.dp)
                .background(Color.Black.copy(alpha = 0.96f), RoundedCornerShape(4.dp))
                .padding(horizontal = 2.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = statusLabel,
                    color = statusColor,
                    fontSize = smallFontSize,
                    fontFamily = fontFamily,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "CODEX CLI",
                    color = HudColors.green,
                    fontSize = smallFontSize,
                    fontFamily = fontFamily,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = detail.takeIf { it.isNotBlank() }?.take(24) ?: "Admin",
                    color = HudColors.primaryText.copy(alpha = 0.86f),
                    fontSize = smallFontSize,
                    fontFamily = fontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End
                )
            }
            HudDivider(alpha = 0.18f)

            Spacer(modifier = Modifier.height(6.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clipToBounds(),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                contentPadding = PaddingValues(bottom = 4.dp)
            ) {
                itemsIndexed(
                    visibleBlocks,
                    key = { index, block -> "$index-${block.kind}-${block.text.take(12)}" }
                ) { index, block ->
                    CliTerminalBlockItem(
                        block = block,
                        isCurrent = block.kind == CliBlockKind.RESPONSE && index == lastResponseIndex,
                        fontSize = bodyFontSize,
                        fontFamily = fontFamily
                    )
                }
                itemsIndexed(listOf(Unit), key = { _, _ -> "codex-cli-end" }) { _, _ ->
                    Spacer(modifier = Modifier.height(1.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            CodexInputPreview(
                text = stagingText,
                voiceText = voiceText,
                showText = showInputStaging,
                photos = photos,
                isFocused = focusedArea == ChatFocusArea.INPUT,
                isProcessing = voiceState is VoiceInputState.Processing,
                fontFamily = fontFamily,
                fontSize = bodyFontSize
            )
            Spacer(modifier = Modifier.height(4.dp))
            CliBottomMenu(
                selectedActionIndex = selectedAction,
                selectedFocused = focusedArea == ChatFocusArea.MENU,
                connected = status.uppercase() == "CONNECTED",
                voiceSendMode = voiceSendMode,
                fontFamily = fontFamily
            )
        }
    }
}

@Composable
private fun CodexInputPreview(
    text: String,
    voiceText: String,
    showText: Boolean,
    photos: List<Bitmap>,
    isFocused: Boolean,
    isProcessing: Boolean,
    fontFamily: FontFamily,
    fontSize: androidx.compose.ui.unit.TextUnit
) {
    val previewText = text.ifBlank { voiceText }
    val hasText = previewText.isNotBlank()
    if (!isProcessing && photos.isEmpty() && !hasText) return

    val cursorVisible = if (isProcessing) {
        val infiniteTransition = rememberInfiniteTransition(label = "codexInputCursor")
        val cursorAlpha by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(animation = tween(500)),
            label = "codexInputBlink"
        )
        cursorAlpha > 0.5f
    } else {
        false
    }
    val displayText = if (isProcessing) {
        val cursor = if (cursorVisible) "\u2588" else " "
        if (previewText.isNotEmpty()) "$previewText $cursor" else cursor
    } else {
        previewText
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black, RoundedCornerShape(4.dp))
            .border(
                width = 1.dp,
                color = when {
                    isFocused -> HudColors.cyan.copy(alpha = 0.9f)
                    isProcessing -> HudColors.cyan.copy(alpha = 0.72f)
                    else -> HudColors.green.copy(alpha = 0.7f)
                },
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .heightIn(min = 22.dp, max = 80.dp)
    ) {
        if (photos.isNotEmpty()) {
            PhotoThumbnailRow(
                thumbnails = photos,
                modifier = Modifier.padding(bottom = if (showText || isProcessing || hasText) 3.dp else 0.dp)
            )
        }
        if (showText || isProcessing || hasText) {
            Text(
                text = displayText,
                color = if (previewText.isBlank() && isProcessing) HudColors.cyan else HudColors.primaryText,
                fontSize = fontSize,
                fontFamily = fontFamily,
                lineHeight = (fontSize.value + 2).sp,
                letterSpacing = 0.sp,
                softWrap = true,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CliBottomMenu(
    selectedActionIndex: Int,
    selectedFocused: Boolean,
    connected: Boolean,
    voiceSendMode: VoiceSendMode,
    fontFamily: FontFamily
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CliActionItem.entries.forEachIndexed { index, item ->
            val selected = selectedFocused && index == selectedActionIndex
            val color = if (selected) HudColors.green else HudColors.primaryText.copy(alpha = 0.74f)
            val borderColor = if (selected) HudColors.green.copy(alpha = 0.92f) else HudColors.green.copy(alpha = 0.42f)
            val label = when (item) {
                CliActionItem.CAM -> "CAM"
                CliActionItem.LINK -> if (connected) "DISC" else "LINK"
                CliActionItem.SEND -> "SEND"
                CliActionItem.STOP -> "STOP"
                CliActionItem.MODE -> if (voiceSendMode == VoiceSendMode.AUTO) "AUTO" else "ASK"
                CliActionItem.CLEAR -> "CLEAR"
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .border(
                        width = if (selected) 1.dp else 0.5.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(3.dp)
                    )
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    CliMenuIcon(
                        item = item,
                        color = color,
                        connected = connected,
                        voiceSendMode = voiceSendMode,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(18.dp)
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = label,
                        color = color,
                        fontSize = 5.sp,
                        fontFamily = fontFamily,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        overflow = TextOverflow.Clip
                    )
                }
            }
        }
    }
}

@Composable
private fun CliMenuIcon(
    item: CliActionItem,
    color: Color,
    connected: Boolean,
    voiceSendMode: VoiceSendMode,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val thin = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (item) {
            CliActionItem.CAM -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.25f, h * 0.34f),
                    size = Size(w * 0.5f, h * 0.42f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                    style = stroke
                )
                drawCircle(color = color, radius = h * 0.12f, center = Offset(w * 0.5f, h * 0.55f), style = stroke)
                drawLine(color, Offset(w * 0.36f, h * 0.34f), Offset(w * 0.42f, h * 0.22f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.42f, h * 0.22f), Offset(w * 0.58f, h * 0.22f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.58f, h * 0.22f), Offset(w * 0.64f, h * 0.34f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            CliActionItem.LINK -> {
                val linkColor = if (connected) HudColors.green else color
                drawRoundRect(
                    color = linkColor,
                    topLeft = Offset(w * 0.22f, h * 0.32f),
                    size = Size(w * 0.28f, h * 0.28f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
                    style = stroke
                )
                drawRoundRect(
                    color = linkColor,
                    topLeft = Offset(w * 0.50f, h * 0.42f),
                    size = Size(w * 0.28f, h * 0.28f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
                    style = stroke
                )
                drawLine(linkColor, Offset(w * 0.42f, h * 0.52f), Offset(w * 0.58f, h * 0.48f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            CliActionItem.SEND -> {
                val path = Path().apply {
                    moveTo(w * 0.34f, h * 0.25f)
                    lineTo(w * 0.72f, h * 0.50f)
                    lineTo(w * 0.34f, h * 0.75f)
                    close()
                }
                drawPath(path, color = color, style = stroke)
                drawLine(color, Offset(w * 0.22f, h * 0.50f), Offset(w * 0.56f, h * 0.50f), strokeWidth = thin.width, cap = StrokeCap.Round)
            }
            CliActionItem.STOP -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.34f, h * 0.30f),
                    size = Size(w * 0.32f, h * 0.40f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()),
                    style = stroke
                )
            }
            CliActionItem.MODE -> {
                if (voiceSendMode == VoiceSendMode.AUTO) {
                    drawLine(color, Offset(w * 0.35f, h * 0.25f), Offset(w * 0.35f, h * 0.74f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    drawLine(color, Offset(w * 0.50f, h * 0.18f), Offset(w * 0.50f, h * 0.82f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    drawLine(color, Offset(w * 0.65f, h * 0.25f), Offset(w * 0.65f, h * 0.74f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    drawCircle(color = color, radius = 1.4.dp.toPx(), center = Offset(w * 0.50f, h * 0.50f))
                } else {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(w * 0.34f, h * 0.18f),
                        size = Size(w * 0.32f, h * 0.46f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()),
                        style = stroke
                    )
                    drawLine(color, Offset(w * 0.50f, h * 0.64f), Offset(w * 0.50f, h * 0.82f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    drawLine(color, Offset(w * 0.40f, h * 0.82f), Offset(w * 0.60f, h * 0.82f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                }
            }
            CliActionItem.CLEAR -> {
                drawLine(color, Offset(w * 0.34f, h * 0.30f), Offset(w * 0.66f, h * 0.70f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.66f, h * 0.30f), Offset(w * 0.34f, h * 0.70f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawRoundRect(
                    color = color.copy(alpha = 0.65f),
                    topLeft = Offset(w * 0.25f, h * 0.18f),
                    size = Size(w * 0.50f, h * 0.64f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                    style = thin
                )
            }
        }
    }
}
@Composable
private fun CliTerminalBlockItem(
    block: CliLineBlock,
    isCurrent: Boolean,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontFamily: FontFamily
) {
    val shape = RoundedCornerShape(4.dp)
    val prefix = when (block.kind) {
        CliBlockKind.INPUT -> ">"
        CliBlockKind.RESPONSE -> "CODEX>"
        CliBlockKind.ERROR -> "ERR>"
        CliBlockKind.SYSTEM -> ""
    }
    val textColor = when (block.kind) {
        CliBlockKind.INPUT -> HudColors.cyan
        CliBlockKind.RESPONSE -> HudColors.green
        CliBlockKind.ERROR -> HudColors.error
        CliBlockKind.SYSTEM -> HudColors.primaryText.copy(alpha = 0.7f)
    }
    val decoratedModifier = when {
        block.kind == CliBlockKind.RESPONSE && isCurrent -> Modifier.border(1.dp, HudColors.green, shape)
        block.kind == CliBlockKind.ERROR -> Modifier.border(1.dp, HudColors.error, shape)
        block.kind == CliBlockKind.RESPONSE -> Modifier.drawBehind {
            drawLine(
                color = HudColors.green,
                start = Offset(0f, 0f),
                end = Offset(0f, size.height),
                strokeWidth = 1.dp.toPx()
            )
        }
        else -> Modifier
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(decoratedModifier)
            .padding(horizontal = 4.dp, vertical = if (block.kind == CliBlockKind.SYSTEM) 2.dp else 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (prefix.isNotEmpty()) {
            Text(
                text = prefix,
                color = when (block.kind) {
                    CliBlockKind.INPUT -> HudColors.cyan
                    CliBlockKind.ERROR -> HudColors.error
                    else -> HudColors.green
                },
                fontSize = fontSize,
                fontFamily = fontFamily,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.widthIn(min = 34.dp, max = 76.dp)
            )
        }

        Text(
            text = block.text,
            color = textColor,
            fontSize = if (block.kind == CliBlockKind.SYSTEM) (fontSize.value - 2).coerceAtLeast(8f).sp else fontSize,
            fontFamily = fontFamily,
            lineHeight = (fontSize.value + 2).sp,
            letterSpacing = 0.sp,
            softWrap = true,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
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
