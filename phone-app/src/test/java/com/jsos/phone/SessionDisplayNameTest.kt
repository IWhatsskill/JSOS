package com.jsos.phone

import com.google.gson.JsonObject
import com.jsos.shared.SessionInfo
import com.jsos.shared.sessionDisplaySortKey
import com.jsos.shared.shouldShowInJsosSessionPicker
import com.jsos.shared.stableSessionDisplayName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionDisplayNameTest {
    @Test
    fun mapsKnownWebAndDiscordSessionKeys() {
        assertEquals("JARVIS", stableSessionDisplayName("agent:main:main", "JARVIS"))
        assertEquals("Goku", stableSessionDisplayName("agent:coding-lab:main", "Coding"))
        assertEquals("Chappi", stableSessionDisplayName("agent:codex-lab:main", "-Codex"))
        assertEquals("Shelli", stableSessionDisplayName("agent:codex-cli-lab:main", "CLI"))
        assertEquals("Gideon", stableSessionDisplayName("agent:discord-gpt-5:main", "GPT-5.5"))
        assertEquals("Steel", stableSessionDisplayName("agent:discord-qwen-397b:main", "Qwen"))
        assertEquals("General", stableSessionDisplayName("agent:discord-general:main", "General"))

        assertEquals("JARVIS", stableSessionDisplayName("agent:main:discord:channel:1500568623918616607", "DC-Codi"))
        assertEquals("General", stableSessionDisplayName("agent:discord-general:discord:channel:1500344381067235371", "DC-General"))
        assertEquals("Goku", stableSessionDisplayName("agent:coding-lab:discord:channel:1500344381067235371", "DC-Coding"))
        assertEquals("Gideon", stableSessionDisplayName("agent:discord-gpt-5:discord:channel:1500358984887046344", "DC-GPT-5.5"))
        assertEquals("Steel", stableSessionDisplayName("agent:discord-qwen-397b:discord:channel:1500359031427039323", "DC-Qwen397b"))
        assertEquals("Chappi", stableSessionDisplayName("agent:codex-lab:discord:channel:1500618054072012952", "-Codex"))
        assertEquals("Shelli", stableSessionDisplayName("agent:codex-cli-lab:discord:channel:1501628003766112366", "DC-CLI"))
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
        assertEquals("Chappi", stableSessionDisplayName("agent:codex-lab:discord:channel:anything", "discord:g-123#codex-lab"))
        assertEquals("Goku", stableSessionDisplayName("agent:coding-lab:discord:channel:anything", "discord:g-123#general"))
        assertEquals("General", stableSessionDisplayName("agent:discord-general:discord:channel:anything", "discord:g-123#general"))
        assertEquals("Chappi", stableSessionDisplayName("agent:codex-lab:main", displayName = ""))
    }

    @Test
    fun dynamicallyLabelsNewAgentIds() {
        assertEquals("Research-Lab", stableSessionDisplayName("agent:research-lab:main"))
        assertEquals("Research-Lab", stableSessionDisplayName("agent:research-lab:discord:channel:anything"))
        assertEquals("Claude", stableSessionDisplayName("agent:discord-claude:discord:channel:anything"))
        assertEquals("My-New-Bot", stableSessionDisplayName("agent:my-new-bot:main"))
    }

    @Test
    fun mapsRawDiscordRowsByAgentIdAndOrigin() {
        assertEquals(
            "General",
            stableSessionDisplayName(
                key = "row-1",
                displayName = "discord:g-123#general",
                agentId = "discord-general",
                origin = "discord"
            )
        )
        assertEquals(
            "Chappi",
            stableSessionDisplayName(
                key = "row-2",
                displayName = "discord:g-123#codex-lab",
                agentId = "codex-lab"
            )
        )
        assertEquals(
            "Claude",
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
            "Goku",
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
            "Chappi",
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
        assertEquals("Unknown", stableSessionDisplayName("unknown:key", displayName = "discord:g-123#unknown"))
    }

    @Test
    fun sessionInfoNameUsesStableMapping() {
        val session = SessionInfo(
            key = "agent:discord-gpt-5:discord:channel:1500358984887046344",
            label = "DC-GPT 5.5"
        )

        assertEquals("Gideon", session.name)
    }

    @Test
    fun sessionInfoNameUsesAgentMetadata() {
        val session = SessionInfo(
            key = "opaque-row",
            displayName = "discord:g-123#general",
            agentId = "discord-general",
            origin = "discord"
        )

        assertEquals("General", session.name)
    }

    @Test
    fun sortsSessionsByFamilyAndTransport() {
        val sorted = listOf(
            "Steel",
            "Goku",
            "JARVIS",
            "General",
            "WhatsApp",
            "Gideon",
            "Chappi",
            "Research-Lab",
            "Shelli"
        ).sortedWith(compareBy { sessionDisplaySortKey(it) })

        assertEquals(
            listOf(
                "JARVIS",
                "WhatsApp",
                "Gideon",
                "Chappi",
                "Goku",
                "Steel",
                "Shelli",
                "General",
                "Research-Lab"
            ),
            sorted
        )
    }

    @Test
    fun filtersPickerToMainWhatsappAndSelectedDiscordAgents() {
        assertTrue(shouldShowInJsosSessionPicker(SessionInfo(key = "agent:main:main")))
        assertTrue(shouldShowInJsosSessionPicker(SessionInfo(key = "agent:main:whatsapp:direct:49123456789")))
        assertTrue(shouldShowInJsosSessionPicker(SessionInfo(key = "agent:discord-gpt-5:discord:channel:anything")))
        assertTrue(shouldShowInJsosSessionPicker(SessionInfo(key = "row-general", displayName = "discord:g-123#general", origin = "discord")))
        assertTrue(shouldShowInJsosSessionPicker(SessionInfo(key = "row-goku", displayName = "goku", origin = "discord")))

        assertFalse(shouldShowInJsosSessionPicker(SessionInfo(key = "agent:main:discord:channel:anything")))
        assertFalse(shouldShowInJsosSessionPicker(SessionInfo(key = "agent:codex-lab:main")))
        assertFalse(shouldShowInJsosSessionPicker(SessionInfo(key = "agent:discord-gpt-5:main")))
        assertFalse(shouldShowInJsosSessionPicker(SessionInfo(key = "agent:research-lab:discord:channel:anything")))
    }
}
