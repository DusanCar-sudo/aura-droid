package dev.aura.auradroid.data.model

data class Provider(
    val name: String,
    val baseUrl: String,
    val apiKeyEnv: String,
    val prefixes: List<String>,
    val models: List<Model>
)

data class Model(
    val id: String,
    val name: String,
    val speed: ModelSpeed,
    val contextLength: Int = 128000,
    val supportsStreaming: Boolean = true,
    val supportsTools: Boolean = true
)

enum class ModelSpeed {
    FAST,
    MEDIUM,
    REASONING,
    SLOW
}

data class AuraConfig(
    val model: String,
    /** Name of the currently selected provider, e.g. "DeepSeek". */
    val provider: String = "DeepSeek",
    val providers: List<Provider> = emptyList(),
    val baseUrl: String? = null,
    val apiKey: String? = null,
    val mode: SessionMode = SessionMode.CODER,
    val maxTurns: Int = 50,
    val autoApprove: Boolean = false,
    val enableGazelle: Boolean = true,
    val enableArchimedes: Boolean = false
)
