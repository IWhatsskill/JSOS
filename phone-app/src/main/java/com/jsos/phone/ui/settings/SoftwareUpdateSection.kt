package com.jsos.phone.ui.settings

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jsos.phone.deployment.HiRokidHudDeploymentManager
import com.jsos.phone.ui.theme.JsosPalette

@Composable
fun SoftwareUpdateSection(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val deploymentManager = remember(context) { HiRokidHudDeploymentManager(context) }
    var selectedApkUri by remember { mutableStateOf<Uri?>(null) }
    var selectedApkName by remember { mutableStateOf("No HUD APK selected") }
    var statusText by remember {
        mutableStateOf("Ready. Hi Rokid must be installed and connected to the glasses.")
    }
    var busy by remember { mutableStateOf(false) }
    var hiRokidInstalled by remember { mutableStateOf(deploymentManager.isHiRokidInstalled()) }
    var wifiEnabled by remember { mutableStateOf(deploymentManager.isWifiEnabled()) }
    var authorized by remember { mutableStateOf(deploymentManager.hasAuthorization) }

    fun refreshEnvironment() {
        hiRokidInstalled = deploymentManager.isHiRokidInstalled()
        wifiEnabled = deploymentManager.isWifiEnabled()
        authorized = deploymentManager.hasAuthorization
    }

    val apkPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            selectedApkUri = uri
            selectedApkName = context.resolveDisplayName(uri)
            statusText = "Selected HUD APK: $selectedApkName"
        }
    }

    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        statusText = deploymentManager.handleAuthorizationResult(result.resultCode, result.data)
        refreshEnvironment()
    }

    DisposableEffect(deploymentManager) {
        onDispose { deploymentManager.cleanup() }
    }

    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Text(
            "HUD Installation",
            color = JsosPalette.Cyan,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
        Text(
            "Deploy the separate JSOS HUD APK through Hi Rokid / CXR-L.",
            color = JsosPalette.Muted,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
        )

        Spacer(Modifier.height(12.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = JsosPalette.CardDark.copy(alpha = 0.72f),
            border = BorderStroke(1.dp, JsosPalette.Cyan.copy(alpha = 0.34f)),
            shape = RoundedCornerShape(8.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Icon(
                    Icons.Default.InstallMobile,
                    contentDescription = null,
                    tint = JsosPalette.Cyan,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Hi Rokid assistant",
                    color = JsosPalette.Cyan,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                )
                Text(
                    "Hi Rokid must be installed on this phone and already connected to the glasses. JSOS Core selects the HUD APK and hands the install to the CXR-L flow.",
                    color = JsosPalette.Muted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                )

                Spacer(Modifier.height(12.dp))

                DeploymentStatusRow("Hi Rokid", if (hiRokidInstalled) "FOUND" else "MISSING")
                DeploymentStatusRow("Phone Wi-Fi", if (wifiEnabled) "ON" else "OFF")
                DeploymentStatusRow("Authorization", if (authorized) "READY" else "NEEDED")
                DeploymentStatusRow("HUD APK", selectedApkName)

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = !busy,
                        border = BorderStroke(1.dp, JsosPalette.Cyan.copy(alpha = 0.62f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = JsosPalette.Cyan,
                        ),
                        onClick = {
                            apkPicker.launch("application/vnd.android.package-archive")
                        },
                    ) {
                        Text("Select APK", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }

                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = !busy,
                        border = BorderStroke(1.dp, JsosPalette.Cyan.copy(alpha = 0.62f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = JsosPalette.Cyan,
                        ),
                        onClick = {
                            refreshEnvironment()
                            val intent = deploymentManager.createAuthorizationIntent()
                            if (intent == null) {
                                statusText = "Hi Rokid app not found on this phone."
                            } else {
                                runCatching { authLauncher.launch(intent) }
                                    .onFailure { statusText = "Could not open Hi Rokid authorization." }
                            }
                        },
                    ) {
                        Text("Authorize", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JsosPalette.Cyan,
                        contentColor = JsosPalette.ScreenTop,
                        disabledContainerColor = JsosPalette.Disabled.copy(alpha = 0.22f),
                        disabledContentColor = JsosPalette.Muted,
                    ),
                    onClick = {
                        refreshEnvironment()
                        val apkUri = selectedApkUri
                        if (apkUri == null) {
                            statusText = "Select the JSOS HUD APK first."
                            return@Button
                        }
                        deploymentManager.installHudApk(
                            apkUri = apkUri,
                            scope = scope,
                            onStatus = { status ->
                                statusText = status
                                refreshEnvironment()
                            },
                            onBusyChanged = { busy = it },
                        )
                    },
                ) {
                    Text(
                        if (busy) "Deploying..." else "Install via Hi Rokid",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    statusText,
                    color = JsosPalette.Muted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun DeploymentStatusRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            color = JsosPalette.Muted,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
        )
        Text(
            value,
            color = JsosPalette.Cyan,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
        )
    }
}

private fun Context.resolveDisplayName(uri: Uri): String {
    val cursor = contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )
    cursor.use {
        if (it != null && it.moveToFirst()) {
            val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) {
                return it.getString(index)
            }
        }
    }
    return uri.lastPathSegment ?: "Selected APK"
}
