package com.jsos.phone.voice

import com.jsos.shared.SessionInfo
import java.util.Locale

/**
 * Routes continuous speech transcripts to a JSOS session.
 *
 * Agent names are only matched at the beginning of an utterance so normal text in
 * the middle of a sentence does not accidentally switch sessions.
 */
object AgentWakeRouter {
    enum class Action {
        SendToAgent,
        SwitchOnly,
        ContinueActive,
        ClearActive,
        Sleep,
        NoMatch,
        Empty,
    }

    data class Decision(
        val action: Action,
        val session: SessionInfo? = null,
        val message: String = "",
        val label: String? = null,
    )

    private data class AgentAliases(
        val label: String,
        val aliases: List<String>,
    )

    private val agents = listOf(
        AgentAliases("Main", listOf("main", "assistant", "primary")),
        AgentAliases("WhatsApp", listOf("whatsapp", "whatsepp", "whatsap", "whats app", "what's app")),
        AgentAliases("GPT-5", listOf("gpt five", "gpt 5", "gpt")),
        AgentAliases("Codex Lab", listOf("codex lab", "codex")),
        AgentAliases("Coding Lab", listOf("coding lab", "coding")),
        AgentAliases("Qwen", listOf("qwen", "queen")),
        AgentAliases("CLI Lab", listOf("cli lab", "cli", "command line")),
        AgentAliases("General", listOf("general", "genelal", "generel", "generell")),
    )

    private val clearActiveCommands = setOf("stop", "stopp", "stop session", "session stop")
    private val sleepCommands = setOf("wake off", "agent wake off", "wake mode off", "ruhe", "pause")

    fun route(rawText: String, sessions: List<SessionInfo>, activeSessionKey: String?): Decision {
        val text = rawText.trim()
        val normalized = normalize(text)
        if (normalized.isBlank()) return Decision(Action.Empty)

        if (normalized in sleepCommands) return Decision(Action.Sleep)
        if (normalized in clearActiveCommands) return Decision(Action.ClearActive)

        val leadingAgent = findLeadingAgent(normalized)
        if (leadingAgent != null) {
            val (agent, aliasWordCount) = leadingAgent
            val session = sessions.firstOrNull { it.name.equals(agent.label, ignoreCase = true) }
            val message = dropLeadingWords(text, aliasWordCount).trim()
            return if (session != null) {
                if (message.isBlank()) {
                    Decision(Action.SwitchOnly, session = session, label = agent.label)
                } else {
                    Decision(Action.SendToAgent, session = session, message = message, label = agent.label)
                }
            } else {
                Decision(Action.NoMatch, message = text, label = agent.label)
            }
        }

        val activeSession = activeSessionKey
            ?.let { key -> sessions.firstOrNull { it.key == key } }
        return if (activeSession != null) {
            Decision(Action.ContinueActive, session = activeSession, message = text, label = activeSession.name)
        } else {
            Decision(Action.NoMatch, message = text)
        }
    }

    private fun findLeadingAgent(normalizedText: String): Pair<AgentAliases, Int>? {
        val words = normalizedText.split(' ').filter { it.isNotBlank() }
        for (agent in agents) {
            val sortedAliases = agent.aliases.sortedByDescending { normalize(it).length }
            for (alias in sortedAliases) {
                val normalizedAlias = normalize(alias)
                val aliasWords = normalizedAlias.split(' ').filter { it.isNotBlank() }
                if (normalizedText == normalizedAlias || normalizedText.startsWith("$normalizedAlias ")) {
                    return agent to aliasWords.size
                }
            }
        }

        for (agent in agents) {
            val sortedAliases = agent.aliases.sortedByDescending { normalize(it).length }
            for (alias in sortedAliases) {
                val normalizedAlias = normalize(alias)
                val aliasWords = normalizedAlias.split(' ').filter { it.isNotBlank() }
                if (aliasWords.size == 1 && words.isNotEmpty() && fuzzyWordMatch(words.first(), aliasWords.first())) {
                    return agent to 1
                }
            }
        }
        return null
    }

    private fun fuzzyWordMatch(word: String, alias: String): Boolean {
        if (word.length < 4 || alias.length < 4) return false
        if (word.firstOrNull() != alias.firstOrNull()) return false
        val maxDistance = if (alias.length >= 6) 2 else 1
        return levenshteinDistance(word, alias) <= maxDistance
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        val previous = IntArray(b.length + 1) { it }
        val current = IntArray(b.length + 1)
        for (i in a.indices) {
            current[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + cost
                )
            }
            for (j in previous.indices) previous[j] = current[j]
        }
        return previous[b.length]
    }

    private fun normalize(text: String): String = text
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9äöüß]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    private fun dropLeadingWords(text: String, count: Int): String {
        if (count <= 0) return text
        val parts = text.trim().split(Regex("\\s+"), limit = count + 1)
        return parts.getOrElse(count) { "" }
    }
}
