package com.jsos.phone

import com.jsos.shared.SessionInfo
import com.jsos.shared.stableSessionDisplayName
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionDisplayNameTest {
    @Test
    fun mapsKnownWebAndDiscordSessionKeys() {
        assertEquals("Web-JARVIS", stableSessionDisplayName("agent:main:main", "JARVIS"))
        assertEquals("Web-Coding-Lab", stableSessionDisplayName("agent:coding-lab:main", "Coding"))
        assertEquals("Web-Codex-Lab", stableSessionDisplayName("agent:codex-lab:main", "-Codex"))
        assertEquals("Web-CLI-Lab", stableSessionDisplayName("agent:codex-cli-lab:main", "CLI"))
        assertEquals("Web-GPT-5", stableSessionDisplayName("agent:discord-gpt-5:main", "GPT-5.5"))
        assertEquals("Web-Qwen397b", stableSessionDisplayName("agent:discord-qwen-397b:main", "Qwen"))
        assertEquals("Web-General", stableSessionDisplayName("agent:discord-general:main", "General"))

        assertEquals("DC-JARVIS", stableSessionDisplayName("agent:main:discord:channel:1500568623918616607", "DC-Codi"))
        assertEquals("DC-General", stableSessionDisplayName("agent:discord-general:discord:channel:1500344381067235371", "DC-General"))
        assertEquals("DC-Coding-Lab", stableSessionDisplayName("agent:coding-lab:discord:channel:1500344381067235371", "DC-Coding"))
        assertEquals("DC-GPT-5", stableSessionDisplayName("agent:discord-gpt-5:discord:channel:1500358984887046344", "DC-GPT-5.5"))
        assertEquals("DC-Qwen397b", stableSessionDisplayName("agent:discord-qwen-397b:discord:channel:1500359031427039323", "DC-Qwen397b"))
        assertEquals("DC-Codex-Lab", stableSessionDisplayName("agent:codex-lab:discord:channel:1500618054072012952", "-Codex"))
        assertEquals("DC-CLI-Lab", stableSessionDisplayName("agent:codex-cli-lab:discord:channel:1501628003766112366", "DC-CLI"))
    }

    @Test
    fun mapsWhatsappDirectSessionsToStableLabel() {
        assertEquals(
            "WhatsApp",
            stableSessionDisplayName("agent:main:whatsapp:direct:49123456789", "private title")
        )
    }

    @Test
    fun fallsBackToGatewayLabelsForUnknownKeys() {
        assertEquals("Label", stableSessionDisplayName("unknown:key", label = "Label", displayName = "Display", derivedTitle = "Derived"))
        assertEquals("Display", stableSessionDisplayName("unknown:key", displayName = "Display", derivedTitle = "Derived"))
        assertEquals("Derived", stableSessionDisplayName("unknown:key", derivedTitle = "Derived"))
        assertEquals("unknown:key", stableSessionDisplayName("unknown:key"))
    }

    @Test
    fun sessionInfoNameUsesStableMapping() {
        val session = SessionInfo(
            key = "agent:discord-gpt-5:discord:channel:1500358984887046344",
            label = "DC-GPT 5.5"
        )

        assertEquals("DC-GPT-5", session.name)
    }
}
