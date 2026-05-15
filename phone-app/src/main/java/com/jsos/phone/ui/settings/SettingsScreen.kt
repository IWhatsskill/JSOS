@file:OptIn(ExperimentalMaterial3Api::class)

package com.jsos.phone.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jsos.phone.glasses.GlassesConnectionManager
import com.jsos.phone.openclaw.OpenClawClient
import com.jsos.phone.tts.ElevenLabsClient
import com.jsos.phone.tts.TtsSettingsManager
import com.jsos.phone.ui.theme.JsosPalette
import com.jsos.phone.util.isEmulator
import com.jsos.phone.voice.VoiceLanguageManager
import com.jsos.phone.voice.VoiceRecognitionManager

enum class SettingsTarget {
    SystemLink,
    HudLink,
    Deployment,
    Voice,
    ResponseVoice,
    Developer,
}

@Composable
fun SettingsScreen(
    // Server
    openClawHost: String,
    openClawPort: String,
    openClawToken: String,
    openClawState: OpenClawClient.ConnectionState,
    onApplyServerSettings: (host: String, port: String, token: String) -> Unit,
    // Glasses
    glassesState: GlassesConnectionManager.ConnectionState,
    discoveredDevices: List<GlassesConnectionManager.DiscoveredDevice>,
    debugModeEnabled: Boolean,
    onStartScanning: () -> Unit,
    onStopScanning: () -> Unit,
    onConnectDevice: (GlassesConnectionManager.DiscoveredDevice) -> Unit,
    onDisconnectGlasses: () -> Unit,
    onClearSn: () -> Unit,
    onCancelReconnect: () -> Unit,
    onRetryReconnect: () -> Unit = {},
    hasCachedSn: Boolean,
    cachedSn: String?,
    cachedDeviceName: String?,
    // Wake on stream
    wakeOnStreamEnabled: Boolean = true,
    onWakeOnStreamChange: (Boolean) -> Unit = {},
    // Voice
    voiceLanguageManager: VoiceLanguageManager,
    voiceRecognitionManager: VoiceRecognitionManager? = null,
    // TTS
    ttsSettingsManager: TtsSettingsManager? = null,
    elevenLabsClient: ElevenLabsClient? = null,
    // Developer
    onDebugModeChange: (Boolean) -> Unit,
    // Navigation
    initialTarget: SettingsTarget = SettingsTarget.SystemLink,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val listState = rememberLazyListState()
    val hasTtsPanel = ttsSettingsManager != null && elevenLabsClient != null
    val hasDeveloperPanel = isEmulator()
    val initialIndex = when (initialTarget) {
        SettingsTarget.SystemLink -> 0
        SettingsTarget.HudLink -> 1
        SettingsTarget.Deployment -> 2
        SettingsTarget.Voice -> 3
        SettingsTarget.ResponseVoice -> if (hasTtsPanel) 4 else 3
        SettingsTarget.Developer -> when {
            hasDeveloperPanel && hasTtsPanel -> 5
            hasDeveloperPanel -> 4
            else -> 0
        }
    }

    LaunchedEffect(initialTarget, hasTtsPanel, hasDeveloperPanel) {
        listState.scrollToItem(initialIndex)
    }

    Scaffold(
        containerColor = JsosPalette.ScreenTop,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = JsosPalette.ScreenTop,
                    titleContentColor = JsosPalette.Cyan,
                    navigationIconContentColor = JsosPalette.Cyan,
                ),
                title = {
                    Column {
                        Text(
                            "CONTROL DECK",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = JsosPalette.Cyan,
                        )
                        Text(
                            "JSOS Spatial Operating System",
                            color = JsosPalette.Muted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            JsosPalette.ScreenTop,
                            JsosPalette.ScreenMid,
                            JsosPalette.ScreenBottom,
                        )
                    )
                )
                .padding(padding),
        ) {
            item {
                ControlDeckPanel(
                    title = "SYSTEM LINK",
                    subtitle = "OpenClaw gateway",
                    status = when (openClawState) {
                        is OpenClawClient.ConnectionState.Connected -> "ONLINE"
                        is OpenClawClient.ConnectionState.Connecting -> "LINKING"
                        is OpenClawClient.ConnectionState.Authenticating -> "AUTH"
                        is OpenClawClient.ConnectionState.PairingRequired -> "PAIRING"
                        is OpenClawClient.ConnectionState.Error -> "ERROR"
                        is OpenClawClient.ConnectionState.Disconnected -> "OFFLINE"
                    },
                    icon = Icons.Default.Cloud,
                    accent = when (openClawState) {
                        is OpenClawClient.ConnectionState.Connected -> JsosPalette.Green
                        is OpenClawClient.ConnectionState.Error -> JsosPalette.Red
                        is OpenClawClient.ConnectionState.PairingRequired -> JsosPalette.Orange
                        is OpenClawClient.ConnectionState.Connecting,
                        is OpenClawClient.ConnectionState.Authenticating -> JsosPalette.Yellow
                        is OpenClawClient.ConnectionState.Disconnected -> JsosPalette.Disabled
                    },
                ) {
                    ServerSection(
                        initialHost = openClawHost,
                        initialPort = openClawPort,
                        initialToken = openClawToken,
                        connectionState = openClawState,
                        onApply = onApplyServerSettings,
                    )
                }
            }

            item {
                ControlDeckPanel(
                    title = "HUD LINK",
                    subtitle = cachedDeviceName ?: "Rokid glasses bridge",
                    status = when (glassesState) {
                        is GlassesConnectionManager.ConnectionState.Connected -> "ONLINE"
                        is GlassesConnectionManager.ConnectionState.Scanning -> "SCAN"
                        is GlassesConnectionManager.ConnectionState.Connecting -> "LINKING"
                        is GlassesConnectionManager.ConnectionState.Reconnecting -> "RETRY"
                        is GlassesConnectionManager.ConnectionState.Error -> "ERROR"
                        is GlassesConnectionManager.ConnectionState.Disconnected -> "OFFLINE"
                    },
                    icon = Icons.Default.Visibility,
                    accent = when (glassesState) {
                        is GlassesConnectionManager.ConnectionState.Connected -> JsosPalette.Green
                        is GlassesConnectionManager.ConnectionState.Error -> JsosPalette.Red
                        is GlassesConnectionManager.ConnectionState.Reconnecting -> JsosPalette.Orange
                        is GlassesConnectionManager.ConnectionState.Scanning,
                        is GlassesConnectionManager.ConnectionState.Connecting -> JsosPalette.Yellow
                        is GlassesConnectionManager.ConnectionState.Disconnected -> JsosPalette.Disabled
                    },
                ) {
                    GlassesSection(
                        state = glassesState,
                        discoveredDevices = discoveredDevices,
                        debugModeEnabled = debugModeEnabled,
                        onStartScanning = onStartScanning,
                        onStopScanning = onStopScanning,
                        onConnectDevice = onConnectDevice,
                        onDisconnectGlasses = onDisconnectGlasses,
                        onClearSn = onClearSn,
                        onCancelReconnect = onCancelReconnect,
                        onRetryReconnect = onRetryReconnect,
                        hasCachedSn = hasCachedSn,
                        cachedSn = cachedSn,
                        cachedDeviceName = cachedDeviceName,
                        wakeOnStreamEnabled = wakeOnStreamEnabled,
                        onWakeOnStreamChange = onWakeOnStreamChange,
                    )
                }
            }

            item {
                ControlDeckPanel(
                    title = "HUD DEPLOYMENT",
                    subtitle = "JSOS HUD APK",
                    status = "HI ROKID",
                    icon = Icons.Default.InstallMobile,
                    accent = JsosPalette.Cyan,
                ) {
                    SoftwareUpdateSection()
                }
            }

            item {
                ControlDeckPanel(
                    title = "VOICE MATRIX",
                    subtitle = "Recognition and language",
                    status = "READY",
                    icon = Icons.Default.GraphicEq,
                    accent = JsosPalette.Cyan,
                ) {
                    VoiceSection(
                        voiceLanguageManager = voiceLanguageManager,
                        voiceRecognitionManager = voiceRecognitionManager,
                    )
                }
            }

            if (ttsSettingsManager != null && elevenLabsClient != null) {
                item {
                    ControlDeckPanel(
                        title = "RESPONSE VOICE",
                        subtitle = "ElevenLabs TTS",
                        status = "OPTIONAL",
                        icon = Icons.Default.RecordVoiceOver,
                        accent = JsosPalette.Yellow,
                    ) {
                        TtsSection(
                            ttsSettingsManager = ttsSettingsManager,
                            elevenLabsClient = elevenLabsClient,
                        )
                    }
                }
            }

            if (isEmulator()) {
                item {
                    ControlDeckPanel(
                        title = "DEV ACCESS",
                        subtitle = "Emulator diagnostics",
                        status = if (debugModeEnabled) "DEBUG" else "LOCKED",
                        icon = Icons.Default.Code,
                        accent = if (debugModeEnabled) JsosPalette.Green else JsosPalette.Disabled,
                    ) {
                        DeveloperSection(
                            debugModeEnabled = debugModeEnabled,
                            onDebugModeChange = onDebugModeChange,
                        )
                    }
                }
            }

            // Bottom spacing
            item {
                androidx.compose.foundation.layout.Spacer(
                    Modifier.padding(bottom = 32.dp)
                )
            }
        }
    }
}

@Composable
private fun ControlDeckPanel(
    title: String,
    subtitle: String,
    status: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        color = JsosPalette.Card,
        border = BorderStroke(1.dp, JsosPalette.Cyan.copy(alpha = 0.46f)),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = null, tint = JsosPalette.Cyan, modifier = Modifier.size(22.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        color = JsosPalette.Cyan,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        subtitle,
                        color = JsosPalette.Cyan.copy(alpha = 0.78f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    "[$status]",
                    color = accent,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = JsosPalette.Cyan.copy(alpha = 0.24f))
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
