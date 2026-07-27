package com.jsos.phone.voice

import com.jsos.shared.SessionInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgentWakeRouterTest {
    private val sessions = listOf(
        SessionInfo(key = "agent:main:main"),
        SessionInfo(key = "agent:main:whatsapp:direct:sample-contact"),
        SessionInfo(key = "agent:build-helper:discord:channel:sample-build"),
        SessionInfo(key = "agent:research-assistant:discord:channel:sample-research"),
    )

    @Test
    fun routesLeadingAgentNameAndKeepsMessageText() {
        val decision = AgentWakeRouter.route("build helper was steht heute an", sessions, activeSessionKey = null)

        assertEquals(AgentWakeRouter.Action.SendToAgent, decision.action)
        assertEquals("Build-Helper", decision.session?.name)
        assertEquals("was steht heute an", decision.message)
    }

    @Test
    fun continuesInActiveSessionWithoutRepeatingAgentName() {
        val activeKey = "agent:build-helper:discord:channel:sample-build"
        val decision = AgentWakeRouter.route("und was ist um 15 40", sessions, activeSessionKey = activeKey)

        assertEquals(AgentWakeRouter.Action.ContinueActive, decision.action)
        assertEquals("Build-Helper", decision.session?.name)
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
        val decision = AgentWakeRouter.route("research assistant test", sessions, activeSessionKey = activeKey)

        assertEquals(AgentWakeRouter.Action.SendToAgent, decision.action)
        assertEquals("Research-Assistant", decision.session?.name)
        assertEquals("test", decision.message)
    }

    @Test
    fun derivesWakeLabelsFromVisibleSessions() {
        val decision = AgentWakeRouter.route(
            "research assistant test",
            sessions,
            activeSessionKey = "agent:main:main"
        )

        assertEquals(AgentWakeRouter.Action.SendToAgent, decision.action)
        assertEquals("Research-Assistant", decision.session?.name)
        assertEquals("test", decision.message)
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
