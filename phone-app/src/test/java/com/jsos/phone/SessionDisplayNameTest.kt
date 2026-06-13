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
        assertEquals("Main", stableSessionDisplayName("agent:main:main", "Main"))
        assertEquals("Coding Lab", stableSessionDisplayName("agent:coding-lab:main", "Coding"))
        assertEquals("Codex Lab", stableSessionDisplayName("agent:codex-lab:main", "-Codex"))
        assertEquals("CLI Lab", stableSessionDisplayName("agent:codex-cli-lab:main", "CLI"))
        assertEquals("GPT-5", stableSessionDisplayName("agent:discord-gpt-5:main", "GPT-5.5"))
        assertEquals("Qwen", stableSessionDisplayName("agent:discord-qwen-397b:main", "Qwen"))
        assertEquals("General", stableSessionDisplayName("agent:discord-general:main", "General"))

        assertEquals("Main", stableSessionDisplayName("agent:main:discord:channel:sample-main", "DC-Main"))
        assertEquals("General", stableSessionDisplayName("agent:discord-general:discord:channel:sample-general", "DC-General"))
        assertEquals("Coding Lab", stableSessionDisplayName("agent:coding-lab:discord:channel:sample-general", "DC-Coding"))
        assertEquals("GPT-5", stableSessionDisplayName("agent:discord-gpt-5:discord:channel:sample-gpt", "DC-GPT-5"))
        assertEquals("Qwen", stableSessionDisplayName("agent:discord-qwen-397b:discord:channel:sample-qwen", "DC-Qwen397b"))
        assertEquals("Codex Lab", stableSessionDisplayName("agent:codex-lab:discord:channel:sample-codex", "-Codex"))
        assertEquals("CLI Lab", stableSessionDisplayName("agent:codex-cli-lab:discord:channel:sample-cli", "DC-CLI"))
    }

    @Test
    fun mapsWhatsappDirectSessionsToStableLabel() {
        assertEquals(
            "WhatsApp",
            stableSessionDisplayName("agent:main:whatsapp:direct:sample-contact", "private title")
        )
    }

    @Test
    fun mapsSessionKeyPatternsWithoutHardcodedChannelIds() {
        assertEquals("Codex Lab", stableSessionDisplayName("agent:codex-lab:discord:channel:anything", "discord:g-123#codex-lab"))
        assertEquals("Coding Lab", stableSessionDisplayName("agent:coding-lab:discord:channel:anything", "discord:g-123#general"))
        assertEquals("General", stableSessionDisplayName("agent:discord-general:discord:channel:anything", "discord:g-123#general"))
        assertEquals("Codex Lab", stableSessionDisplayName("agent:codex-lab:main", displayName = ""))
    }

    @Test
    fun dynamicallyLabelsNewAgentIds() {
        assertEquals("Custom-Helper", stableSessionDisplayName("agent:custom-helper:main"))
        assertEquals("My-Custom-Agent", stableSessionDisplayName("agent:my-custom-agent:main"))
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
            "Codex Lab",
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
            "Coding Lab",
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
            "Codex Lab",
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
            key = "agent:discord-gpt-5:discord:channel:sample-gpt",
            label = "DC-GPT-5"
        )

        assertEquals("GPT-5", session.name)
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
            "Qwen",
            "Coding Lab",
            "Main",
            "General",
            "WhatsApp",
            "GPT-5",
            "Codex Lab",
            "Research-Lab",
            "CLI Lab"
        ).sortedWith(compareBy { sessionDisplaySortKey(it) })

        assertEquals(
            listOf(
                "Main",
                "WhatsApp",
                "GPT-5",
                "Codex Lab",
                "Coding Lab",
                "Qwen",
                "CLI Lab",
                "General",
                "Research-Lab"
            ),
            sorted
        )
    }

    @Test
    fun filtersPickerToMainWhatsappAndSelectedDiscordAgents() {
        assertTrue(shouldShowInJsosSessionPicker(SessionInfo(key = "agent:main:main")))
        assertTrue(shouldShowInJsosSessionPicker(SessionInfo(key = "agent:custom-helper:main")))
        assertTrue(shouldShowInJsosSessionPicker(SessionInfo(key = "agent:my-custom-agent:main")))
        assertTrue(shouldShowInJsosSessionPicker(SessionInfo(key = "agent:codex-lab:main")))
        assertTrue(shouldShowInJsosSessionPicker(SessionInfo(key = "agent:discord-gpt-5:main")))
        assertTrue(shouldShowInJsosSessionPicker(SessionInfo(key = "agent:main:whatsapp:direct:sample-contact")))
        assertTrue(shouldShowInJsosSessionPicker(SessionInfo(key = "agent:custom-helper:whatsapp:direct:sample-contact")))
        assertTrue(shouldShowInJsosSessionPicker(SessionInfo(key = "agent:discord-gpt-5:discord:channel:anything")))
        assertTrue(shouldShowInJsosSessionPicker(SessionInfo(key = "agent:custom-helper:discord:channel:anything")))
        assertTrue(shouldShowInJsosSessionPicker(SessionInfo(key = "agent:research-lab:discord:channel:anything")))
        assertTrue(shouldShowInJsosSessionPicker(SessionInfo(key = "row-general", displayName = "discord:g-123#general", origin = "discord")))
        assertTrue(shouldShowInJsosSessionPicker(SessionInfo(key = "row-coding", displayName = "coding-lab", origin = "discord")))

        assertFalse(shouldShowInJsosSessionPicker(SessionInfo(key = "row-empty", origin = "discord")))
        assertFalse(shouldShowInJsosSessionPicker(SessionInfo(key = "row-phone", displayName = "discord:g-123#123456", origin = "discord")))
    }
}
