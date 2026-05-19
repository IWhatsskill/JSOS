package com.jsos.phone.voice

import com.jsos.shared.SessionInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgentWakeRouterTest {
    private val sessions = listOf(
        SessionInfo(key = "agent:main:main"),
        SessionInfo(key = "agent:main:whatsapp:direct:49123456789"),
        SessionInfo(key = "agent:codex-lab:discord:channel:anything"),
        SessionInfo(key = "agent:codex-cli-lab:discord:channel:anything"),
        SessionInfo(key = "agent:coding-lab:discord:channel:anything"),
        SessionInfo(key = "agent:discord-general:discord:channel:anything"),
        SessionInfo(key = "agent:discord-gpt-5:discord:channel:anything"),
        SessionInfo(key = "agent:discord-qwen-397b:discord:channel:anything"),
    )

    @Test
    fun routesLeadingAgentNameAndKeepsMessageText() {
        val decision = AgentWakeRouter.route("cli lab was steht heute an", sessions, activeSessionKey = null)

        assertEquals(AgentWakeRouter.Action.SendToAgent, decision.action)
        assertEquals("CLI Lab", decision.session?.name)
        assertEquals("was steht heute an", decision.message)
    }

    @Test
    fun continuesInActiveSessionWithoutRepeatingAgentName() {
        val activeKey = "agent:codex-cli-lab:discord:channel:anything"
        val decision = AgentWakeRouter.route("und was ist um 15 40", sessions, activeSessionKey = activeKey)

        assertEquals(AgentWakeRouter.Action.ContinueActive, decision.action)
        assertEquals("CLI Lab", decision.session?.name)
        assertEquals("und was ist um 15 40", decision.message)
    }

    @Test
    fun doesNotRouteWithoutAgentOrActiveSession() {
        val decision = AgentWakeRouter.route("was steht heute an", sessions, activeSessionKey = null)

        assertEquals(AgentWakeRouter.Action.NoMatch, decision.action)
        assertNull(decision.session)
    }

    @Test
    fun handlesMainAlias() {
        val decision = AgentWakeRouter.route("main starte bitte", sessions, activeSessionKey = null)

        assertEquals(AgentWakeRouter.Action.SendToAgent, decision.action)
        assertEquals("Main", decision.session?.name)
        assertEquals("starte bitte", decision.message)
    }

    @Test
    fun handlesAdditionalMainAliases() {
        for (alias in listOf("assistant", "primary")) {
            val decision = AgentWakeRouter.route("$alias test", sessions, activeSessionKey = null)

            assertEquals(AgentWakeRouter.Action.SendToAgent, decision.action)
            assertEquals("Main", decision.session?.name)
            assertEquals("test", decision.message)
        }
    }

    @Test
    fun switchesAgentsEvenWhenAnotherSessionIsActive() {
        val activeKey = "agent:main:main"
        val decision = AgentWakeRouter.route("cli test", sessions, activeSessionKey = activeKey)

        assertEquals(AgentWakeRouter.Action.SendToAgent, decision.action)
        assertEquals("CLI Lab", decision.session?.name)
        assertEquals("test", decision.message)
    }

    @Test
    fun handlesAdditionalCliLabAliases() {
        for (alias in listOf("cli lab", "cli", "command line")) {
            val decision = AgentWakeRouter.route("$alias test", sessions, activeSessionKey = "agent:main:main")

            assertEquals(AgentWakeRouter.Action.SendToAgent, decision.action)
            assertEquals("CLI Lab", decision.session?.name)
            assertEquals("test", decision.message)
        }
    }

    @Test
    fun handlesAdditionalGptAliases() {
        for (alias in listOf("gpt five", "gpt 5", "gpt")) {
            val decision = AgentWakeRouter.route("$alias test", sessions, activeSessionKey = "agent:main:main")

            assertEquals(AgentWakeRouter.Action.SendToAgent, decision.action)
            assertEquals("GPT-5", decision.session?.name)
            assertEquals("test", decision.message)
        }
    }

    @Test
    fun handlesAdditionalCodexLabAliases() {
        for (alias in listOf("codex lab", "codex")) {
            val decision = AgentWakeRouter.route("$alias test", sessions, activeSessionKey = "agent:main:main")

            assertEquals(AgentWakeRouter.Action.SendToAgent, decision.action)
            assertEquals("Codex Lab", decision.session?.name)
            assertEquals("test", decision.message)
        }
    }

    @Test
    fun handlesAdditionalQwenAliases() {
        for (alias in listOf("qwen", "queen")) {
            val decision = AgentWakeRouter.route("$alias test", sessions, activeSessionKey = "agent:main:main")

            assertEquals(AgentWakeRouter.Action.SendToAgent, decision.action)
            assertEquals("Qwen", decision.session?.name)
            assertEquals("test", decision.message)
        }
    }

    @Test
    fun handlesAdditionalCodingLabAliases() {
        for (alias in listOf("coding lab", "coding")) {
            val decision = AgentWakeRouter.route("$alias test", sessions, activeSessionKey = "agent:main:main")

            assertEquals(AgentWakeRouter.Action.SendToAgent, decision.action)
            assertEquals("Coding Lab", decision.session?.name)
            assertEquals("test", decision.message)
        }
    }

    @Test
    fun handlesAdditionalGeneralAliases() {
        for (alias in listOf("general", "genelal", "generel")) {
            val decision = AgentWakeRouter.route("$alias test", sessions, activeSessionKey = "agent:main:main")

            assertEquals(AgentWakeRouter.Action.SendToAgent, decision.action)
            assertEquals("General", decision.session?.name)
            assertEquals("test", decision.message)
        }
    }

    @Test
    fun handlesAdditionalWhatsappAliases() {
        for (alias in listOf("whatsapp", "whatsepp", "whatsap")) {
            val decision = AgentWakeRouter.route("$alias test", sessions, activeSessionKey = "agent:main:main")

            assertEquals(AgentWakeRouter.Action.SendToAgent, decision.action)
            assertEquals("WhatsApp", decision.session?.name)
            assertEquals("test", decision.message)
        }
    }

    @Test
    fun recognizesStopAndSleepCommands() {
        assertEquals(
            AgentWakeRouter.Action.ClearActive,
            AgentWakeRouter.route("stopp", sessions, activeSessionKey = sessions.first().key).action
        )
        assertEquals(
            AgentWakeRouter.Action.Sleep,
            AgentWakeRouter.route("wake off", sessions, activeSessionKey = sessions.first().key).action
        )
    }
}
