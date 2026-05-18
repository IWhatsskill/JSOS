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
        val decision = AgentWakeRouter.route("Shelli was steht heute an", sessions, activeSessionKey = null)

        assertEquals(AgentWakeRouter.Action.SendToAgent, decision.action)
        assertEquals("Shelli", decision.session?.name)
        assertEquals("was steht heute an", decision.message)
    }

    @Test
    fun continuesInActiveSessionWithoutRepeatingAgentName() {
        val activeKey = "agent:codex-cli-lab:discord:channel:anything"
        val decision = AgentWakeRouter.route("und was ist um 15 40", sessions, activeSessionKey = activeKey)

        assertEquals(AgentWakeRouter.Action.ContinueActive, decision.action)
        assertEquals("Shelli", decision.session?.name)
        assertEquals("und was ist um 15 40", decision.message)
    }

    @Test
    fun doesNotRouteWithoutAgentOrActiveSession() {
        val decision = AgentWakeRouter.route("was steht heute an", sessions, activeSessionKey = null)

        assertEquals(AgentWakeRouter.Action.NoMatch, decision.action)
        assertNull(decision.session)
    }

    @Test
    fun handlesCommonJarvisMisrecognitions() {
        val decision = AgentWakeRouter.route("chiz starte bitte", sessions, activeSessionKey = null)

        assertEquals(AgentWakeRouter.Action.SendToAgent, decision.action)
        assertEquals("JARVIS", decision.session?.name)
        assertEquals("starte bitte", decision.message)
    }

    @Test
    fun handlesAdditionalJarvisAliases() {
        for (alias in listOf("chaivis", "javiz", "javez", "cavis", "cavez")) {
            val decision = AgentWakeRouter.route("$alias test", sessions, activeSessionKey = null)

            assertEquals(AgentWakeRouter.Action.SendToAgent, decision.action)
            assertEquals("JARVIS", decision.session?.name)
            assertEquals("test", decision.message)
        }
    }

    @Test
    fun switchesAgentsEvenWhenAnotherSessionIsActive() {
        val activeKey = "agent:main:main"
        val decision = AgentWakeRouter.route("schalli test", sessions, activeSessionKey = activeKey)

        assertEquals(AgentWakeRouter.Action.SendToAgent, decision.action)
        assertEquals("Shelli", decision.session?.name)
        assertEquals("test", decision.message)
    }

    @Test
    fun handlesAdditionalShelliAliases() {
        for (alias in listOf("shalli", "shelli", "chelli", "challi", "shaly", "shali", "shelly")) {
            val decision = AgentWakeRouter.route("$alias test", sessions, activeSessionKey = "agent:main:main")

            assertEquals(AgentWakeRouter.Action.SendToAgent, decision.action)
            assertEquals("Shelli", decision.session?.name)
            assertEquals("test", decision.message)
        }
    }

    @Test
    fun handlesAdditionalGideonAliases() {
        for (alias in listOf("gideon", "gidon", "gidaon")) {
            val decision = AgentWakeRouter.route("$alias test", sessions, activeSessionKey = "agent:main:main")

            assertEquals(AgentWakeRouter.Action.SendToAgent, decision.action)
            assertEquals("Gideon", decision.session?.name)
            assertEquals("test", decision.message)
        }
    }

    @Test
    fun handlesAdditionalChappiAliases() {
        for (alias in listOf("chappi", "charpi", "cheppi", "krappi", "crappy", "crapy", "crapi", "krapi")) {
            val decision = AgentWakeRouter.route("$alias test", sessions, activeSessionKey = "agent:main:main")

            assertEquals(AgentWakeRouter.Action.SendToAgent, decision.action)
            assertEquals("Chappi", decision.session?.name)
            assertEquals("test", decision.message)
        }
    }

    @Test
    fun handlesAdditionalSteelAliases() {
        for (alias in listOf("steel", "stil", "stiil", "steal", "seel")) {
            val decision = AgentWakeRouter.route("$alias test", sessions, activeSessionKey = "agent:main:main")

            assertEquals(AgentWakeRouter.Action.SendToAgent, decision.action)
            assertEquals("Steel", decision.session?.name)
            assertEquals("test", decision.message)
        }
    }

    @Test
    fun handlesAdditionalGokuAliases() {
        for (alias in listOf("goku", "gorku", "gocu", "goko")) {
            val decision = AgentWakeRouter.route("$alias test", sessions, activeSessionKey = "agent:main:main")

            assertEquals(AgentWakeRouter.Action.SendToAgent, decision.action)
            assertEquals("Goku", decision.session?.name)
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
