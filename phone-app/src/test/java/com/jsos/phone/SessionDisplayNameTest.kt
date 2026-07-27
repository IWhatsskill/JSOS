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
    fun mapsGenericWebAndDiscordSessionKeys() {
        assertEquals("Main", stableSessionDisplayName("agent:main:main", "Main"))
        assertEquals("Build Helper", stableSessionDisplayName("agent:build-helper:main", "Build Helper"))
        assertEquals("Research Assistant", stableSessionDisplayName("agent:research-assistant:main", "Research Assistant"))

        assertEquals("Main", stableSessionDisplayName("agent:main:discord:channel:sample-main", "DC-Main"))
        assertEquals(
            "Research Assistant",
            stableSessionDisplayName(
                "agent:research-assistant:discord:channel:sample-research",
                "DC-Research Assistant"
            )
        )
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
        assertEquals(
            "Build-Helper",
            stableSessionDisplayName(
                "agent:build-helper:discord:channel:sample-build",
                "discord:sample-server#build-helper"
            )
        )
        assertEquals("Build-Helper", stableSessionDisplayName("agent:build-helper:main", displayName = ""))
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
            "Research-Assistant",
            stableSessionDisplayName(
                key = "row-1",
                displayName = "discord:sample-server#research-assistant",
                agentId = "research-assistant",
                origin = "discord"
            )
        )
        assertEquals(
            "Build-Helper",
            stableSessionDisplayName(
                key = "row-2",
                displayName = "discord:sample-server#build-helper",
                agentId = "build-helper"
            )
        )
        assertEquals(
            "Claude",
            stableSessionDisplayName(
                key = "row-5",
                displayName = "discord:sample-server#claude",
                agentId = "claude",
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
            "Build-Helper",
            stableSessionDisplayName(
                key = "row-3",
                displayName = "",
                agentId = "build-helper",
                deliveryContext = deliveryContext
            )
        )
    }

    @Test
    fun mapsBlankWebRowsByAgentId() {
        assertEquals(
            "Research-Assistant",
            stableSessionDisplayName(
                key = "row-4",
                displayName = "",
                agentId = "research-assistant"
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
            key = "agent:research-assistant:discord:channel:sample-research",
            label = "DC-Research Assistant"
        )

        assertEquals("Research Assistant", session.name)
    }

    @Test
    fun sessionInfoNameUsesAgentMetadata() {
        val session = SessionInfo(
            key = "opaque-row",
            displayName = "discord:sample-server#research-assistant",
            agentId = "research-assistant",
            origin = "discord"
        )

        assertEquals("Research-Assistant", session.name)
    }

    @Test
    fun sortsSessionsByFamilyAndTransport() {
        val sorted = listOf(
            "Main",
            "WhatsApp",
            "Research-Lab",
            "Build-Helper",
            "Telegram"
        ).sortedWith(compareBy { sessionDisplaySortKey(it) })

        assertEquals(
            listOf(
                "Main",
                "WhatsApp",
                "Telegram",
                "Build-Helper",
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
        assertTrue(shouldShowInJsosSessionPicker(SessionInfo(key = "agent:build-helper:main")))
        assertTrue(shouldShowInJsosSessionPicker(SessionInfo(key = "agent:research-assistant:main")))
        assertTrue(shouldShowInJsosSessionPicker(SessionInfo(key = "agent:main:whatsapp:direct:sample-contact")))
        assertTrue(shouldShowInJsosSessionPicker(SessionInfo(key = "agent:custom-helper:whatsapp:direct:sample-contact")))
        assertTrue(shouldShowInJsosSessionPicker(SessionInfo(key = "agent:research-assistant:discord:channel:sample-research")))
        assertTrue(shouldShowInJsosSessionPicker(SessionInfo(key = "agent:custom-helper:discord:channel:sample-helper")))
        assertTrue(shouldShowInJsosSessionPicker(SessionInfo(key = "agent:research-lab:discord:channel:sample-research")))
        assertTrue(shouldShowInJsosSessionPicker(SessionInfo(key = "row-research", displayName = "discord:sample-server#research", origin = "discord")))
        assertTrue(shouldShowInJsosSessionPicker(SessionInfo(key = "row-build", displayName = "build-helper", origin = "discord")))

        assertFalse(shouldShowInJsosSessionPicker(SessionInfo(key = "row-empty", origin = "discord")))
        assertFalse(shouldShowInJsosSessionPicker(SessionInfo(key = "row-phone", displayName = "discord:sample-server#123456", origin = "discord")))
    }
}
