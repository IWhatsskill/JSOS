package com.jsos.phone.tts

import android.content.Context
import com.jsos.phone.security.SecurePrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages TTS settings persistence and reactive state.
 */
class TtsSettingsManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val securePrefs = SecurePrefs(context).also {
        it.migrateString(prefs, KEY_API_KEY)
    }

    private val _apiKey = MutableStateFlow(securePrefs.getString(KEY_API_KEY, "") ?: "")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _provider = MutableStateFlow(
        sanitizeProvider(prefs.getString(KEY_PROVIDER, PROVIDER_ELEVENLABS))
    )
    val provider: StateFlow<String> = _provider.asStateFlow()

    private val _selectedVoiceId = MutableStateFlow(prefs.getString(KEY_VOICE_ID, null))
    val selectedVoiceId: StateFlow<String?> = _selectedVoiceId.asStateFlow()

    private val _selectedVoiceName = MutableStateFlow(prefs.getString(KEY_VOICE_NAME, null))
    val selectedVoiceName: StateFlow<String?> = _selectedVoiceName.asStateFlow()

    private val _openAiModel = MutableStateFlow(
        sanitizeOpenAiModel(prefs.getString(KEY_OPENAI_MODEL, DEFAULT_OPENAI_MODEL))
    )
    val openAiModel: StateFlow<String> = _openAiModel.asStateFlow()

    private val _openAiVoice = MutableStateFlow(
        sanitizeOpenAiVoice(
            prefs.getString(KEY_OPENAI_VOICE, defaultOpenAiVoice(_openAiModel.value)),
            _openAiModel.value
        )
    )
    val openAiVoice: StateFlow<String> = _openAiVoice.asStateFlow()

    private val _isEnabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _speed = MutableStateFlow(prefs.getFloat(KEY_SPEED, DEFAULT_SPEED))
    val speed: StateFlow<Float> = _speed.asStateFlow()

    fun setApiKey(key: String) {
        _apiKey.value = key
        securePrefs.putString(KEY_API_KEY, key)
    }

    fun setSelectedVoice(id: String, name: String) {
        _selectedVoiceId.value = id
        _selectedVoiceName.value = name
        prefs.edit()
            .putString(KEY_VOICE_ID, id)
            .putString(KEY_VOICE_NAME, name)
            .apply()
    }

    fun setProvider(provider: String) {
        val safeProvider = sanitizeProvider(provider)
        _provider.value = safeProvider
        prefs.edit().putString(KEY_PROVIDER, safeProvider).apply()
    }

    fun setOpenAiModel(model: String) {
        val safeModel = sanitizeOpenAiModel(model)
        _openAiModel.value = safeModel
        val safeVoice = sanitizeOpenAiVoice(_openAiVoice.value, safeModel)
        _openAiVoice.value = safeVoice
        prefs.edit()
            .putString(KEY_OPENAI_MODEL, safeModel)
            .putString(KEY_OPENAI_VOICE, safeVoice)
            .apply()
    }

    fun setOpenAiVoice(voice: String) {
        val safeVoice = sanitizeOpenAiVoice(voice, _openAiModel.value)
        _openAiVoice.value = safeVoice
        prefs.edit().putString(KEY_OPENAI_VOICE, safeVoice).apply()
    }

    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun setSpeed(speed: Float) {
        _speed.value = speed.coerceIn(MIN_SPEED, MAX_SPEED)
        prefs.edit().putFloat(KEY_SPEED, _speed.value).apply()
    }

    /**
     * Check if TTS is properly configured (has API key and voice selected).
     */
    fun isConfigured(): Boolean {
        return isElevenLabsConfigured()
    }

    fun isElevenLabsConfigured(): Boolean {
        return _apiKey.value.isNotBlank() && _selectedVoiceId.value != null
    }

    fun isOpenAiConfigured(openAiApiKey: String): Boolean {
        return openAiApiKey.isNotBlank() && _openAiModel.value.isNotBlank() && _openAiVoice.value.isNotBlank()
    }

    fun isConfigured(openAiApiKey: String): Boolean {
        return when (_provider.value) {
            PROVIDER_OPENAI -> isOpenAiConfigured(openAiApiKey)
            else -> isElevenLabsConfigured()
        }
    }

    fun providerLabel(provider: String = _provider.value): String {
        return when (provider) {
            PROVIDER_OPENAI -> "OpenAI"
            else -> "ElevenLabs"
        }
    }

    fun openAiVoicesForModel(model: String = _openAiModel.value): List<String> {
        return if (model == MODEL_GPT_4O_MINI_TTS) OPENAI_VOICES else OPENAI_LEGACY_VOICES
    }

    companion object {
        private const val PREFS_NAME = "jsos"
        private const val KEY_API_KEY = "tts_api_key"
        private const val KEY_PROVIDER = "tts_provider"
        private const val KEY_VOICE_ID = "tts_voice_id"
        private const val KEY_VOICE_NAME = "tts_voice_name"
        private const val KEY_OPENAI_MODEL = "openai_tts_model"
        private const val KEY_OPENAI_VOICE = "openai_tts_voice"
        private const val KEY_ENABLED = "tts_enabled"
        private const val KEY_SPEED = "tts_speed"
        const val PROVIDER_ELEVENLABS = "elevenlabs"
        const val PROVIDER_OPENAI = "openai"
        private const val MODEL_GPT_4O_MINI_TTS = "gpt-4o-mini-tts"
        const val DEFAULT_OPENAI_MODEL = MODEL_GPT_4O_MINI_TTS
        val OPENAI_MODELS = listOf(
            MODEL_GPT_4O_MINI_TTS,
            "tts-1-hd",
            "tts-1"
        )
        private val OPENAI_LEGACY_VOICES = listOf(
            "alloy",
            "ash",
            "coral",
            "echo",
            "fable",
            "onyx",
            "nova",
            "sage",
            "shimmer"
        )
        val OPENAI_VOICES = listOf(
            "alloy",
            "ash",
            "ballad",
            "coral",
            "echo",
            "fable",
            "onyx",
            "nova",
            "sage",
            "shimmer",
            "verse",
            "marin",
            "cedar"
        )
        const val DEFAULT_SPEED = 1.0f
        const val MIN_SPEED = 0.7f
        const val MAX_SPEED = 1.2f

        private fun sanitizeProvider(provider: String?): String {
            return when (provider) {
                PROVIDER_OPENAI -> PROVIDER_OPENAI
                else -> PROVIDER_ELEVENLABS
            }
        }

        private fun sanitizeOpenAiModel(model: String?): String {
            return model?.takeIf { it in OPENAI_MODELS } ?: DEFAULT_OPENAI_MODEL
        }

        private fun sanitizeOpenAiVoice(voice: String?, model: String): String {
            val allowedVoices = if (model == MODEL_GPT_4O_MINI_TTS) OPENAI_VOICES else OPENAI_LEGACY_VOICES
            return voice?.takeIf { it in allowedVoices } ?: defaultOpenAiVoice(model)
        }

        private fun defaultOpenAiVoice(model: String): String {
            return if (model == MODEL_GPT_4O_MINI_TTS) "cedar" else "coral"
        }
    }
}
