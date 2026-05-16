package com.jsos.phone.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class OpenAIRealtimeLanguageTest {
    @Test
    fun derivesLanguageFromBcp47Tag() {
        assertEquals("en", realtimeTranscriptionLanguage("en-US"))
        assertEquals("nl", realtimeTranscriptionLanguage("nl-NL"))
        assertEquals("de", realtimeTranscriptionLanguage("de-DE"))
    }

    @Test
    fun fallsBackToGermanForMissingOrInvalidTags() {
        assertEquals("de", realtimeTranscriptionLanguage(null))
        assertEquals("de", realtimeTranscriptionLanguage(""))
        assertEquals("de", realtimeTranscriptionLanguage("123"))
    }
}
