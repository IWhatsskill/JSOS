package com.jsos.phone.ui.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.jsos.phone.glasses.GlassesConnectionManager
import com.jsos.phone.glasses.RokidCredentialStore
import com.jsos.phone.glasses.RokidSdkManager
import com.jsos.phone.ui.theme.JsosPalette
import kotlin.math.roundToInt

@Composable
fun GlassesSection(
    state: GlassesConnectionManager.ConnectionState,
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
    wakeOnStreamEnabled: Boolean = true,
    onWakeOnStreamChange: (Boolean) -> Unit = {},
    glassBrightness: Int = 0,
    onGlassBrightnessChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val credentialStore = remember(context) { RokidCredentialStore(context) }
    var credentialsConfigured by remember(credentialStore) {
        mutableStateOf(credentialStore.isConfigured())
    }

    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .animateContentSize(),
    ) {
        if (debugModeEnabled) {
            DebugModeContent(state)
        } else {
            RokidCredentialsPanel(
                configured = credentialsConfigured,
                accessKeySeed = credentialStore.getAccessKey(),
                clientSecretSeed = credentialStore.getClientSecret(),
                onSave = { accessKey, clientSecret ->
                    credentialStore.save(accessKey, clientSecret)
                    RokidSdkManager.refreshRokidCredentials()
                    credentialsConfigured = credentialStore.isConfigured()
                },
                onClear = {
                    credentialStore.clear()
                    RokidSdkManager.refreshRokidCredentials()
                    credentialsConfigured = false
                },
            )

            Spacer(Modifier.height(16.dp))

            when (state) {
                is GlassesConnectionManager.ConnectionState.Disconnected ->
                    DisconnectedContent(onStartScanning)

                is GlassesConnectionManager.ConnectionState.Scanning ->
                    ScanningContent(discoveredDevices, onStopScanning, onConnectDevice)

                is GlassesConnectionManager.ConnectionState.Connecting ->
                    ConnectingContent()

                is GlassesConnectionManager.ConnectionState.Reconnecting ->
                    ReconnectingContent(
                        attempt = state.attempt,
                        nextRetryMs = state.nextRetryMs,
                        onCancel = onCancelReconnect,
                        onRetry = onRetryReconnect,
                    )

                is GlassesConnectionManager.ConnectionState.Connected ->
                    ConnectedContent(
                        deviceName = state.deviceName,
                        hasCachedSn = hasCachedSn,
                        cachedSn = cachedSn,
                        cachedDeviceName = cachedDeviceName,
                        wakeOnStreamEnabled = wakeOnStreamEnabled,
                        onWakeOnStreamChange = onWakeOnStreamChange,
                        glassBrightness = glassBrightness,
                        onGlassBrightnessChange = onGlassBrightnessChange,
                        onDisconnect = onDisconnectGlasses,
                        onClearSn = onClearSn,
                    )

                is GlassesConnectionManager.ConnectionState.Error ->
                    ErrorContent(
                        message = state.message,
                        onRetry = onStartScanning,
                    )
            }
        }
    }
}

@Composable
private fun RokidCredentialsPanel(
    configured: Boolean,
    accessKeySeed: String,
    clientSecretSeed: String,
    onSave: (accessKey: String, clientSecret: String) -> Unit,
    onClear: () -> Unit,
) {
    var expanded by remember(configured) { mutableStateOf(!configured) }
    var accessKey by remember(accessKeySeed, configured) { mutableStateOf(accessKeySeed) }
    var clientSecret by remember(clientSecretSeed, configured) { mutableStateOf(clientSecretSeed) }
    var showSecrets by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = JsosPalette.CardDark.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, JsosPalette.Cyan.copy(alpha = 0.34f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Rokid Credentials",
                        style = MaterialTheme.typography.bodyLarge,
                        color = JsosPalette.Text,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Stored locally for Bluetooth and HUD deployment.",
                        style = MaterialTheme.typography.bodySmall,
                        color = JsosPalette.Muted,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        if (configured) "CONFIGURED" else "MISSING",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (configured) JsosPalette.Green else JsosPalette.Yellow,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    )
                    TextButton(
                        onClick = { expanded = !expanded },
                        colors = jsosTextButtonColors(),
                    ) {
                        Text(if (expanded) "Hide" else "Edit")
                    }
                }
            }

            if (expanded) {
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = accessKey,
                    onValueChange = { accessKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Access key") },
                    singleLine = true,
                    visualTransformation = if (showSecrets) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = jsosTextFieldColors(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = clientSecret,
                    onValueChange = { clientSecret = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Client secret") },
                    singleLine = true,
                    visualTransformation = if (showSecrets) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = jsosTextFieldColors(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                )

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { showSecrets = !showSecrets },
                        colors = jsosTextButtonColors(),
                    ) {
                        Text(if (showSecrets) "Mask" else "Show")
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (configured) {
                            TextButton(
                                onClick = {
                                    onClear()
                                    accessKey = ""
                                    clientSecret = ""
                                    expanded = true
                                },
                                colors = jsosTextButtonColors(contentColor = JsosPalette.Red),
                            ) {
                                Text("Clear")
                            }
                        }
                        Button(
                            onClick = {
                                onSave(accessKey, clientSecret)
                                expanded = false
                            },
                            enabled = accessKey.isNotBlank() && clientSecret.isNotBlank(),
                            colors = jsosPrimaryButtonColors(),
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DebugModeContent(state: GlassesConnectionManager.ConnectionState) {
    StatusRow(
        color = when (state) {
            is GlassesConnectionManager.ConnectionState.Connected -> JsosPalette.Green
            is GlassesConnectionManager.ConnectionState.Connecting -> JsosPalette.Yellow
            else -> JsosPalette.Disabled
        },
        title = when (state) {
            is GlassesConnectionManager.ConnectionState.Connected -> "Connected"
            is GlassesConnectionManager.ConnectionState.Connecting -> "Connecting..."
            else -> "Not connected"
        },
        subtitle = "WebSocket debug mode",
    )
}

@Composable
private fun DisconnectedContent(onScan: () -> Unit) {
    StatusRow(
        color = JsosPalette.Disabled,
        title = "Not connected",
        subtitle = "Tap Scan to find nearby glasses",
    )

    Spacer(Modifier.height(16.dp))

    Button(
        onClick = onScan,
        modifier = Modifier.fillMaxWidth(),
        colors = jsosPrimaryButtonColors(),
    ) {
        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Scan for Glasses")
    }
}

@Composable
private fun ScanningContent(
    devices: List<GlassesConnectionManager.DiscoveredDevice>,
    onStop: () -> Unit,
    onConnect: (GlassesConnectionManager.DiscoveredDevice) -> Unit,
) {
    StatusRow(
        color = JsosPalette.Yellow,
        title = "Scanning...",
        subtitle = "Looking for nearby glasses",
        showProgress = true,
    )

    Spacer(Modifier.height(16.dp))

    OutlinedButton(
        onClick = onStop,
        modifier = Modifier.fillMaxWidth(),
        border = jsosPanelBorder(alpha = 0.55f),
        colors = jsosOutlinedButtonColors(),
    ) {
        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Stop Scanning")
    }

    if (devices.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = JsosPalette.CardDark.copy(alpha = 0.72f),
            border = jsosPanelBorder(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                devices.forEachIndexed { index, device ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                device.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = JsosPalette.Text,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "${signalDescription(device.rssi)} (${device.rssi} dBm)",
                                style = MaterialTheme.typography.bodySmall,
                                color = JsosPalette.Cyan.copy(alpha = 0.78f),
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        TextButton(
                            onClick = { onConnect(device) },
                            colors = jsosTextButtonColors(),
                        ) {
                            Text("Connect")
                        }
                    }
                    if (index < devices.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 16.dp),
                            thickness = 0.5.dp,
                            color = JsosPalette.BorderSubtle,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "${devices.size} device${if (devices.size != 1) "s" else ""} found",
            style = MaterialTheme.typography.bodySmall,
            color = JsosPalette.Muted,
        )
    }
}

@Composable
private fun ConnectingContent(message: String = "Connecting...") {
    StatusRow(
        color = JsosPalette.Yellow,
        title = message,
        subtitle = "Please wait",
        showProgress = true,
    )
}

@Composable
private fun ReconnectingContent(attempt: Int, nextRetryMs: Long, onCancel: () -> Unit, onRetry: () -> Unit) {
    // Live countdown timer
    var secondsLeft by remember(nextRetryMs) { mutableIntStateOf((nextRetryMs / 1000).toInt().coerceAtLeast(0)) }

    LaunchedEffect(nextRetryMs) {
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft = (secondsLeft - 1).coerceAtLeast(0)
        }
    }

    StatusRow(
        color = JsosPalette.Orange,
        title = "Reconnecting...",
        subtitle = "Attempt #$attempt (retry in ${secondsLeft}s)",
        showProgress = true,
    )

    Spacer(Modifier.height(16.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.weight(1f),
            border = jsosPanelBorder(alpha = 0.55f),
            colors = jsosOutlinedButtonColors(),
        ) {
            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Cancel")
        }

        Button(
            onClick = onRetry,
            modifier = Modifier.weight(1f),
            colors = jsosPrimaryButtonColors(),
        ) {
            Text("Retry Now")
        }
    }
}

@Composable
private fun ConnectedContent(
    deviceName: String,
    hasCachedSn: Boolean,
    cachedSn: String?,
    cachedDeviceName: String?,
    wakeOnStreamEnabled: Boolean,
    onWakeOnStreamChange: (Boolean) -> Unit,
    glassBrightness: Int,
    onGlassBrightnessChange: (Int) -> Unit,
    onDisconnect: () -> Unit,
    onClearSn: () -> Unit,
) {
    var showClearConfirmation by remember { mutableStateOf(false) }

    StatusRow(
        color = JsosPalette.Green,
        title = "Connected",
        subtitle = deviceName,
    )

    // Wake on stream toggle
    Spacer(Modifier.height(16.dp))

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = JsosPalette.CardDark.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, JsosPalette.Cyan.copy(alpha = 0.34f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Wake on Stream",
                    style = MaterialTheme.typography.bodyLarge,
                    color = JsosPalette.Text,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Wake glasses when new content arrives during standby",
                    style = MaterialTheme.typography.bodySmall,
                    color = JsosPalette.Muted,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Spacer(Modifier.width(16.dp))
            Switch(
                checked = wakeOnStreamEnabled,
                onCheckedChange = onWakeOnStreamChange,
                colors = jsosSwitchColors(),
            )
        }
    }


    Spacer(Modifier.height(16.dp))

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = JsosPalette.CardDark.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, JsosPalette.Cyan.copy(alpha = 0.34f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val sanitizedBrightness = glassBrightness.coerceIn(
                RokidSdkManager.GLASS_BRIGHTNESS_MIN,
                RokidSdkManager.GLASS_BRIGHTNESS_MAX,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "HUD Brightness",
                    style = MaterialTheme.typography.bodyLarge,
                    color = JsosPalette.Text,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "$sanitizedBrightness / ${RokidSdkManager.GLASS_BRIGHTNESS_MAX}",
                    style = MaterialTheme.typography.bodySmall,
                    color = JsosPalette.Green,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
            }
            Slider(
                value = sanitizedBrightness.toFloat(),
                onValueChange = { onGlassBrightnessChange(it.roundToInt()) },
                valueRange = RokidSdkManager.GLASS_BRIGHTNESS_MIN.toFloat()..RokidSdkManager.GLASS_BRIGHTNESS_MAX.toFloat(),
                steps = (RokidSdkManager.GLASS_BRIGHTNESS_MAX - RokidSdkManager.GLASS_BRIGHTNESS_MIN - 1).coerceAtLeast(0),
                colors = SliderDefaults.colors(
                    thumbColor = JsosPalette.Green,
                    activeTrackColor = JsosPalette.Cyan,
                    inactiveTrackColor = JsosPalette.Cyan.copy(alpha = 0.22f),
                ),
            )
            Text(
                "Default 0. Raise only when the HUD needs more light.",
                style = MaterialTheme.typography.bodySmall,
                color = JsosPalette.Muted,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
    // Paired device card
    if (hasCachedSn) {
        Spacer(Modifier.height(16.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = JsosPalette.CardDark.copy(alpha = 0.72f),
            border = BorderStroke(1.dp, JsosPalette.Cyan.copy(alpha = 0.34f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Paired Device",
                    style = MaterialTheme.typography.bodyMedium,
                    color = JsosPalette.Cyan.copy(alpha = 0.78f),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    cachedDeviceName ?: deviceName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = JsosPalette.Text,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))

                if (showClearConfirmation) {
                    Text(
                        "Clear pairing data?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = JsosPalette.Text,
                    )
                    Text(
                        "You'll need to re-pair next time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = JsosPalette.Muted,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showClearConfirmation = false },
                            border = jsosPanelBorder(alpha = 0.55f),
                            colors = jsosOutlinedButtonColors(),
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                onClearSn()
                                showClearConfirmation = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = JsosPalette.Red,
                                contentColor = Color.Black,
                            ),
                        ) {
                            Text("Clear")
                        }
                    }
                } else {
                    TextButton(
                        onClick = { showClearConfirmation = true },
                        colors = jsosTextButtonColors(contentColor = JsosPalette.Red),
                    ) {
                        Text(
                            "Clear pairing",
                            color = JsosPalette.Red,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    OutlinedButton(
        onClick = onDisconnect,
        modifier = Modifier.fillMaxWidth(),
        border = jsosPanelBorder(alpha = 0.55f),
        colors = jsosOutlinedButtonColors(),
    ) {
        Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Disconnect")
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
) {
    StatusRow(
        color = JsosPalette.Red,
        title = "Connection error",
        subtitle = message,
    )

    Spacer(Modifier.height(16.dp))

    Button(
        onClick = onRetry,
        modifier = Modifier.fillMaxWidth(),
        colors = jsosPrimaryButtonColors(),
    ) {
        Icon(Icons.AutoMirrored.Filled.BluetoothSearching, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Scan for Glasses")
    }
}

@Composable
private fun StatusRow(
    color: Color,
    title: String,
    subtitle: String,
    showProgress: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (showProgress) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 2.dp,
                color = color,
            )
        } else {
            Icon(
                Icons.Default.Circle,
                contentDescription = null,
                tint = color,
                modifier = Modifier
                    .size(12.dp)
                    .padding(top = 2.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = JsosPalette.Text,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = JsosPalette.Cyan.copy(alpha = 0.78f),
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun ConnectionDetail(
    icon: @Composable () -> Unit,
    label: String,
    connected: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        icon()
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.Default.Circle,
            contentDescription = if (connected) "Connected" else "Not connected",
            tint = if (connected) JsosPalette.Green else JsosPalette.Disabled,
            modifier = Modifier.size(8.dp),
        )
    }
}

private fun signalDescription(rssi: Int): String = when {
    rssi >= -50 -> "Strong"
    rssi >= -70 -> "Medium"
    else -> "Weak"
}
