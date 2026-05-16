package com.jsos.phone

import com.google.gson.JsonObject
import com.jsos.shared.SessionInfo
import com.jsos.shared.sessionDisplaySortKey
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
    fun mapsSessionKeyPatternsWithoutHardcodedChannelIds() {
        assertEquals("DC-Codex-Lab", stableSessionDisplayName("agent:codex-lab:discord:channel:anything", "discord:g-123#codex-lab"))
        assertEquals("DC-Coding-Lab", stableSessionDisplayName("agent:coding-lab:discord:channel:anything", "discord:g-123#general"))
        assertEquals("DC-General", stableSessionDisplayName("agent:discord-general:discord:channel:anything", "discord:g-123#general"))
        assertEquals("Web-Codex-Lab", stableSessionDisplayName("agent:codex-lab:main", displayName = ""))
    }

    @Test
    fun dynamicallyLabelsNewAgentIds() {
        assertEquals("Web-Research-Lab", stableSessionDisplayName("agent:research-lab:main"))
        assertEquals("DC-Research-Lab", stableSessionDisplayName("agent:research-lab:discord:channel:anything"))
        assertEquals("DC-Claude", stableSessionDisplayName("agent:discord-claude:discord:channel:anything"))
        assertEquals("Web-My-New-Bot", stableSessionDisplayName("agent:my-new-bot:main"))
    }

    @Test
    fun mapsRawDiscordRowsByAgentIdAndOrigin() {
        assertEquals(
            "DC-General",
            stableSessionDisplayName(
                key = "row-1",
                displayName = "discord:g-123#general",
                agentId = "discord-general",
                origin = "discord"
            )
        )
        assertEquals(
            "DC-Codex-Lab",
            stableSessionDisplayName(
                key = "row-2",
                displayName = "discord:g-123#codex-lab",
                agentId = "codex-lab"
            )
        )
        assertEquals(
            "DC-Claude",
            stableSessionDisplayName(
                key = "row-5",
                displayName = "discord:g-123#claude",
                agentId = "discord-claude",
                origin = "discord"
            )
        )
    }

    @Test
    fun mapsRowsByAgentIdAndDeliveryContext() {
        val deliveryContext = JsonObject().apply {
            addProperty("channel", "general")
        }

        assertEquals(
            "DC-Coding-Lab",
            stableSessionDisplayName(
                key = "row-3",
                displayName = "",
                agentId = "coding-lab",
                deliveryContext = deliveryContext
            )
        )
    }

    @Test
    fun mapsBlankWebRowsByAgentId() {
        assertEquals(
            "Web-Codex-Lab",
            stableSessionDisplayName(
                key = "row-4",
                displayName = "",
                agentId = "codex-lab"
            )
        )
    }

    @Test
    fun fallsBackToGatewayLabelsForUnknownKeys() {
        assertEquals("Label", stableSessionDisplayName("unknown:key", label = "Label", displayName = "Display", derivedTitle = "Derived"))
        assertEquals("Display", stableSessionDisplayName("unknown:key", displayName = "Display", derivedTitle = "Derived"))
        assertEquals("Derived", stableSessionDisplayName("unknown:key", derivedTitle = "Derived"))
        assertEquals("unknown:key", stableSessionDisplayName("unknown:key"))
        assertEquals("discord:g-123#unknown", stableSessionDisplayName("unknown:key", displayName = "discord:g-123#unknown"))
    }

    @Test
    fun sessionInfoNameUsesStableMapping() {
        val session = SessionInfo(
            key = "agent:discord-gpt-5:discord:channel:1500358984887046344",
            label = "DC-GPT 5.5"
        )

        assertEquals("DC-GPT-5", session.name)
    }

    @Test
    fun sessionInfoNameUsesAgentMetadata() {
        val session = SessionInfo(
            key = "opaque-row",
            displayName = "discord:g-123#general",
            agentId = "discord-general",
            origin = "discord"
        )

        assertEquals("DC-General", session.name)
    }

    @Test
    fun sortsSessionsByFamilyAndTransport() {
        val sorted = listOf(
            "Web-Qwen397b",
            "DC-Coding-Lab",
            "Web-JARVIS",
            "DC-General",
            "WhatsApp",
            "DC-JARVIS",
            "Web-Coding-Lab",
            "DC-Research-Lab",
            "Web-Research-Lab"
        ).sortedWith(compareBy { sessionDisplaySortKey(it) })

        assertEquals(
            listOf(
                "WhatsApp",
                "DC-JARVIS",
                "Web-JARVIS",
                "DC-Coding-Lab",
                "Web-Coding-Lab",
                "Web-Qwen397b",
                "DC-General",
                "DC-Research-Lab",
                "Web-Research-Lab"
            ),
            sorted
        )
    }
}
