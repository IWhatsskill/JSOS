package com.jsos.shared

data class LlmModelOption(
    val label: String,
    val command: String,
    val description: String
)

val LLM_MODEL_OPTIONS = listOf(
    LlmModelOption("GPT-5.5", "/model openai/gpt-5.5", "openai"),
    LlmModelOption("Qwen 397B", "/model ollama/qwen3.5:397b-cloud", "ollama / cloud")
)
