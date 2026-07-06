@file:OptIn(ExperimentalMaterial3Api::class)

package com.jsos.phone.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jsos.phone.tts.ElevenLabsClient
import com.jsos.phone.tts.TtsSettingsManager
import com.jsos.phone.ui.theme.JsosPalette
import com.jsos.phone.tts.Voice
import com.jsos.phone.voice.VoiceRecognitionManager

@Composable
fun TtsSection(
    ttsSettingsManager: TtsSettingsManager,
    elevenLabsClient: ElevenLabsClient,
    voiceRecognitionManager: VoiceRecognitionManager? = null,
    modifier: Modifier = Modifier,
) {
    val apiKey by ttsSettingsManager.apiKey.collectAsState()
    val provider by ttsSettingsManager.provider.collectAsState()
    val selectedVoiceId by ttsSettingsManager.selectedVoiceId.collectAsState()
    val selectedVoiceName by ttsSettingsManager.selectedVoiceName.collectAsState()
    val openAiModel by ttsSettingsManager.openAiModel.collectAsState()
    val openAiVoice by ttsSettingsManager.openAiVoice.collectAsState()
    val isEnabled by ttsSettingsManager.isEnabled.collectAsState()
    val speed by ttsSettingsManager.speed.collectAsState()

    var localApiKey by remember(apiKey) { mutableStateOf(apiKey) }
    var showApiKey by remember { mutableStateOf(false) }
    var showVoiceSheet by remember { mutableStateOf(false) }
    var showOpenAiModelSheet by remember { mutableStateOf(false) }
    var showOpenAiVoiceSheet by remember { mutableStateOf(false) }

    var voices by remember { mutableStateOf<List<Voice>>(emptyList()) }
    var isLoadingVoices by remember { mutableStateOf(false) }
    var voicesError by remember { mutableStateOf<String?>(null) }

    val isOpenAiProvider = provider == TtsSettingsManager.PROVIDER_OPENAI
    val hasApiKey = localApiKey.isNotBlank()
    val hasVoice = selectedVoiceId != null
    val hasOpenAiApiKey = voiceRecognitionManager?.getOpenAIApiKey().orEmpty().isNotBlank()
    val openAiVoices = ttsSettingsManager.openAiVoicesForModel(openAiModel)
    val providerConfigured = if (isOpenAiProvider) hasOpenAiApiKey else hasApiKey && hasVoice
    val isConfigured = providerConfigured && isEnabled
    val activeVoiceName = if (isOpenAiProvider) openAiVoice else selectedVoiceName

    // Fetch voices when API key changes and is valid
    LaunchedEffect(apiKey, provider) {
        if (!isOpenAiProvider && apiKey.isNotBlank()) {
            isLoadingVoices = true
            voicesError = null
            elevenLabsClient.getVoices(apiKey)
                .onSuccess { fetchedVoices ->
                    voices = fetchedVoices
                    isLoadingVoices = false
                }
                .onFailure { error ->
                    voicesError = error.message
                    isLoadingVoices = false
                }
        } else {
            voices = emptyList()
            isLoadingVoices = false
            voicesError = null
        }
    }

    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = JsosPalette.CardDark.copy(alpha = 0.72f),
            border = jsosPanelBorder(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                // Header row with icon and enable switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.RecordVoiceOver,
                        contentDescription = null,
                        tint = JsosPalette.Cyan,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Voice Responses",
                            style = MaterialTheme.typography.bodyLarge,
                            color = JsosPalette.Text,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        )
                        if (isConfigured) {
                            Text(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(color = JsosPalette.Green)) {
                                        append("Active")
                                    }
                                    withStyle(SpanStyle(color = JsosPalette.Cyan)) {
                                        append(" - ${ttsSettingsManager.providerLabel(provider)}")
                                    }
                                    activeVoiceName?.let { voice ->
                                        withStyle(SpanStyle(color = JsosPalette.Cyan)) {
                                            append(" / $voice")
                                        }
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        } else {
                            Text(
                                when {
                                    isOpenAiProvider && !hasOpenAiApiKey -> "OpenAI key required"
                                    !isOpenAiProvider && !hasApiKey -> "ElevenLabs key required"
                                    !isOpenAiProvider && !hasVoice -> "Select a voice"
                                    else -> "Disabled"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = JsosPalette.Muted,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { enabled ->
                            ttsSettingsManager.setEnabled(enabled)
                        },
                        enabled = providerConfigured,
                        colors = jsosSwitchColors(),
                    )
                }

                Spacer(Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    ProviderOption(
                        label = "ElevenLabs",
                        selected = !isOpenAiProvider,
                        onClick = { ttsSettingsManager.setProvider(TtsSettingsManager.PROVIDER_ELEVENLABS) },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    ProviderOption(
                        label = "OpenAI",
                        selected = isOpenAiProvider,
                        onClick = { ttsSettingsManager.setProvider(TtsSettingsManager.PROVIDER_OPENAI) },
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(16.dp))

                if (isOpenAiProvider) {
                    SettingRow(
                        title = "OpenAI API Key",
                        value = if (hasOpenAiApiKey) "Saved in Voice settings" else "Add key in Voice settings",
                        valueColor = if (hasOpenAiApiKey) JsosPalette.Green else JsosPalette.Muted,
                    )

                    Spacer(Modifier.height(12.dp))

                    SettingRow(
                        title = "Model",
                        value = openAiModel,
                        onClick = { showOpenAiModelSheet = true },
                    )

                    Spacer(Modifier.height(12.dp))

                    SettingRow(
                        title = "Voice",
                        value = openAiVoice,
                        onClick = { showOpenAiVoiceSheet = true },
                    )
                } else {
                    // API Key input field
                    OutlinedTextField(
                        value = localApiKey,
                        onValueChange = { newKey ->
                            localApiKey = newKey
                            ttsSettingsManager.setApiKey(newKey)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("ElevenLabs API Key") },
                        placeholder = { Text("xi-...") },
                        singleLine = true,
                        colors = jsosTextFieldColors(),
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            Row {
                                IconButton(onClick = { showApiKey = !showApiKey }) {
                                    Icon(
                                        if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (showApiKey) "Hide API key" else "Show API key",
                                        tint = JsosPalette.Cyan,
                                    )
                                }
                                if (localApiKey.isNotEmpty()) {
                                    IconButton(onClick = {
                                        localApiKey = ""
                                        ttsSettingsManager.setApiKey("")
                                    }) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Clear API key",
                                            tint = JsosPalette.Cyan,
                                        )
                                    }
                                }
                            }
                        },
                        supportingText = {
                            when {
                                voicesError != null -> Text(
                                    "Invalid API key",
                                    color = JsosPalette.Red,
                                )
                                hasApiKey -> Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = JsosPalette.Green,
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "API key saved",
                                        color = JsosPalette.Cyan,
                                        fontSize = 11.sp,
                                    )
                                }
                                else -> Text(
                                    "Required for ElevenLabs voice synthesis",
                                    color = JsosPalette.Muted,
                                )
                            }
                        },
                    )

                    // Voice selector (only show when API key is set)
                    if (hasApiKey) {
                        Spacer(Modifier.height(12.dp))

                        SettingRow(
                            title = "Voice",
                            value = when {
                                isLoadingVoices -> "Loading voices..."
                                voicesError != null -> "Error loading voices"
                                selectedVoiceName != null -> selectedVoiceName!!
                                voices.isNotEmpty() -> "Select a voice"
                                else -> "No voices available"
                            },
                            enabled = !isLoadingVoices && voices.isNotEmpty(),
                            loading = isLoadingVoices,
                            onClick = { showVoiceSheet = true },
                        )
                    }
                }

                // Speed slider
                if (providerConfigured) {
                    Spacer(Modifier.height(12.dp))

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Speed",
                                style = MaterialTheme.typography.bodyMedium,
                                color = JsosPalette.Cyan,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "%.2f\u00D7".format(speed),
                                style = MaterialTheme.typography.bodySmall,
                                color = JsosPalette.Cyan,
                            )
                        }
                        Slider(
                            value = speed,
                            onValueChange = { ttsSettingsManager.setSpeed(it) },
                            valueRange = TtsSettingsManager.MIN_SPEED..TtsSettingsManager.MAX_SPEED,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = JsosPalette.Cyan,
                                activeTrackColor = JsosPalette.Cyan,
                                inactiveTrackColor = JsosPalette.Cyan.copy(alpha = 0.24f),
                                activeTickColor = JsosPalette.Cyan,
                                inactiveTickColor = JsosPalette.Muted.copy(alpha = 0.35f),
                            ),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "Slower",
                                style = MaterialTheme.typography.labelSmall,
                                color = JsosPalette.Muted,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "Faster",
                                style = MaterialTheme.typography.labelSmall,
                                color = JsosPalette.Muted,
                            )
                        }
                    }
                }
            }
        }
    }

    // Voice selection bottom sheet
    if (showVoiceSheet && voices.isNotEmpty()) {
        VoiceBottomSheet(
            voices = voices,
            selectedVoiceId = selectedVoiceId,
            onSelect = { voice ->
                ttsSettingsManager.setSelectedVoice(voice.voiceId, voice.name)
                showVoiceSheet = false
            },
            onDismiss = { showVoiceSheet = false },
        )
    }

    if (showOpenAiModelSheet) {
        TextOptionBottomSheet(
            title = "Select OpenAI TTS Model",
            options = TtsSettingsManager.OPENAI_MODELS,
            selected = openAiModel,
            onSelect = { model ->
                ttsSettingsManager.setOpenAiModel(model)
                showOpenAiModelSheet = false
            },
            onDismiss = { showOpenAiModelSheet = false },
        )
    }

    if (showOpenAiVoiceSheet) {
        TextOptionBottomSheet(
            title = "Select OpenAI Voice",
            options = openAiVoices,
            selected = openAiVoice,
            onSelect = { voice ->
                ttsSettingsManager.setOpenAiVoice(voice)
                showOpenAiVoiceSheet = false
            },
            onDismiss = { showOpenAiVoiceSheet = false },
        )
    }
}

@Composable
private fun ProviderOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) JsosPalette.Cyan.copy(alpha = 0.18f) else JsosPalette.CardDark.copy(alpha = 0.58f),
        border = jsosPanelBorder(alpha = if (selected) 0.58f else 0.26f),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (selected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (selected) JsosPalette.Green else JsosPalette.Muted,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) JsosPalette.Green else JsosPalette.Text,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = JsosPalette.Text,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = JsosPalette.CardDark.copy(alpha = 0.58f),
        border = jsosPanelBorder(alpha = 0.26f),
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && onClick != null) {
                onClick?.invoke()
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = JsosPalette.Cyan,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    value,
                    style = MaterialTheme.typography.bodySmall,
                    color = valueColor,
                    fontFamily = FontFamily.Monospace,
                )
            }
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = JsosPalette.Cyan,
                )
            } else if (onClick != null) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = JsosPalette.Cyan.copy(alpha = 0.72f),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun TextOptionBottomSheet(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = JsosPalette.ScreenMid,
        contentColor = JsosPalette.Text,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = JsosPalette.Cyan,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            HorizontalDivider(thickness = 0.5.dp, color = JsosPalette.BorderSubtle)
            Spacer(Modifier.height(8.dp))
        }

        LazyColumn(
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            items(options) { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(option) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (option == selected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (option == selected) JsosPalette.Green else JsosPalette.Muted,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        option,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        color = if (option == selected) JsosPalette.Green else JsosPalette.Text,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun VoiceBottomSheet(
    voices: List<Voice>,
    selectedVoiceId: String?,
    onSelect: (Voice) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Group voices by category
    val premade = voices.filter { it.category == "premade" || it.category == null }
    val cloned = voices.filter { it.category == "cloned" }
    val generated = voices.filter { it.category == "generated" }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = JsosPalette.ScreenMid,
        contentColor = JsosPalette.Text,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Text(
                "Select Voice",
                style = MaterialTheme.typography.titleMedium,
                color = JsosPalette.Cyan,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            HorizontalDivider(thickness = 0.5.dp, color = JsosPalette.BorderSubtle)
            Spacer(Modifier.height(8.dp))
        }

        LazyColumn(
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            if (premade.isNotEmpty()) {
                item {
                    Text(
                        "Premade Voices",
                        style = MaterialTheme.typography.labelMedium,
                        color = JsosPalette.Cyan,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                items(premade) { voice ->
                    VoiceRow(voice, voice.voiceId == selectedVoiceId, onSelect)
                }
            }

            if (cloned.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Cloned Voices",
                        style = MaterialTheme.typography.labelMedium,
                        color = JsosPalette.Cyan,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                items(cloned) { voice ->
                    VoiceRow(voice, voice.voiceId == selectedVoiceId, onSelect)
                }
            }

            if (generated.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Generated Voices",
                        style = MaterialTheme.typography.labelMedium,
                        color = JsosPalette.Cyan,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                items(generated) { voice ->
                    VoiceRow(voice, voice.voiceId == selectedVoiceId, onSelect)
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun VoiceRow(
    voice: Voice,
    isSelected: Boolean,
    onSelect: (Voice) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(voice) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (isSelected) JsosPalette.Green else JsosPalette.Muted,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            voice.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            color = if (isSelected) JsosPalette.Green else JsosPalette.Text,
            fontFamily = FontFamily.Monospace,
        )
    }
}
