package dev.aura.auradroid.data.standalone

data class FactoryModel(
    val id: String,
    val label: String,
    val speed: String = "",
    val isFree: Boolean = false,
)

data class FactoryProvider(
    val id: String,
    val name: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val models: List<FactoryModel>,
    val signupUrl: String = "",
    val isLocal: Boolean = false,
)

object ModelFactory {
    val PROVIDERS = listOf(
        FactoryProvider(
            id = "openrouter",
            name = "OpenRouter",
            defaultBaseUrl = "https://openrouter.ai/api/v1",
            defaultModel = "openrouter/auto",
            signupUrl = "https://openrouter.ai/keys",
            models = listOf(
                FactoryModel("openrouter/auto", "Auto (Best Available)", "Adaptive routing"),
                FactoryModel("deepseek/deepseek-r1:free", "DeepSeek R1 (Free)", "Reasoning", isFree = true),
                FactoryModel("meta-llama/llama-3.3-70b-instruct:free", "Llama 3.3 70B (Free)", "Fast general", isFree = true),
                FactoryModel("google/gemini-2.0-flash-lite-001:free", "Gemini 2.0 Flash Lite (Free)", "Ultra fast", isFree = true),
                FactoryModel("qwen/qwen-2.5-coder-32b-instruct:free", "Qwen 2.5 Coder 32B (Free)", "Code", isFree = true),
                FactoryModel("stepfun/step-5-preview", "Step 5 Preview", "Flagship agentic"),
                FactoryModel("anthropic/claude-3.5-sonnet", "Claude 3.5 Sonnet", "High intelligence"),
            ),
        ),
        FactoryProvider(
            id = "kilocode",
            name = "KiloCode",
            defaultBaseUrl = "https://api.kilocode.ai/api/openrouter",
            defaultModel = "kilo-auto/free",
            signupUrl = "https://kilocode.ai/",
            models = listOf(
                FactoryModel("kilo-auto/free", "Kilo Auto (Free)", "Free multi-model", isFree = true),
                FactoryModel("kilo-auto/efficient", "Kilo Efficient", "Fast & cost-effective"),
                FactoryModel("nvidia/nemotron-3-ultra-550b-a55b:free", "Nemotron 3 Ultra 550B (Free)", "Flagship free", isFree = true),
                FactoryModel("deepseek/deepseek-v4.1-flash", "DeepSeek V4.1 Flash", "Fast reasoning"),
                FactoryModel("moonshotai/kimi-k3", "Kimi K3", "Agentic 1M"),
                FactoryModel("z-ai/glm-5.3-flash", "GLM 5.3 Flash", "Fast agentic"),
                FactoryModel("google/gemini-4-argon", "Gemini 4 Argon", "Next-gen preview"),
            ),
        ),
        FactoryProvider(
            id = "deepinfra",
            name = "DeepInfra",
            defaultBaseUrl = "https://api.deepinfra.com/v1/openai",
            defaultModel = "meta-llama/Llama-3.3-70B-Instruct",
            signupUrl = "https://deepinfra.com/dash",
            models = listOf(
                FactoryModel("meta-llama/Llama-3.3-70B-Instruct", "Llama 3.3 70B", "Fast 131k"),
                FactoryModel("deepseek-ai/DeepSeek-R1", "DeepSeek R1", "Reasoning"),
                FactoryModel("deepseek-ai/DeepSeek-V3", "DeepSeek V3", "Flagship chat"),
                FactoryModel("Qwen/Qwen2.5-72B-Instruct", "Qwen 2.5 72B", "Coding & logic"),
                FactoryModel("zai-org/GLM-5.3", "GLM 5.3", "1M context"),
            ),
        ),
        FactoryProvider(
            id = "xiaomi",
            name = "Xiaomi Vimo",
            defaultBaseUrl = "https://token-plan-sgp.xiaomimimo.com/v1",
            defaultModel = "mimo-v2.6-pro",
            signupUrl = "https://platform.xiaomimimo.com/#/console/api-keys",
            models = listOf(
                FactoryModel("mimo-v2.6-pro", "MiMo V2.6 Pro", "Flagship 1M context"),
                FactoryModel("mimo-v2.6-flash", "MiMo V2.6 Flash", "Fastest multimodal"),
                FactoryModel("mimo-v2.5-pro", "MiMo V2.5 Pro", "Previous gen pro"),
                FactoryModel("mimo-v2.5", "MiMo V2.5", "Fast & efficient"),
            ),
        ),
        FactoryProvider(
            id = "gemini",
            name = "Gemini",
            defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
            defaultModel = "gemini-3.6-flash",
            signupUrl = "https://aistudio.google.com/apikey",
            models = listOf(
                FactoryModel("gemini-3.6-flash", "Gemini 3.6 Flash", "Fast flagship"),
                FactoryModel("gemini-3.1-pro-preview", "Gemini 3.1 Pro (Preview)", "Deep reasoning"),
                FactoryModel("gemini-pro-latest", "Gemini Pro (Latest)", "Balanced pro"),
                FactoryModel("gemini-3.5-flash", "Gemini 3.5 Flash", "High throughput"),
            ),
        ),
        FactoryProvider(
            id = "deepseek",
            name = "DeepSeek",
            defaultBaseUrl = "https://api.deepseek.com/v1",
            defaultModel = "deepseek-chat",
            signupUrl = "https://platform.deepseek.com/api_keys",
            models = listOf(
                FactoryModel("deepseek-chat", "DeepSeek Chat (V3)", "General chat & coding"),
                FactoryModel("deepseek-reasoner", "DeepSeek Reasoner (R1)", "Chain of thought"),
                FactoryModel("deepseek-v4.1-flash", "DeepSeek V4.1 Flash", "Ultra fast"),
                FactoryModel("deepseek-v4.1", "DeepSeek V4.1", "Flagship"),
            ),
        ),
        FactoryProvider(
            id = "nvidia",
            name = "NVIDIA NIM",
            defaultBaseUrl = "https://integrate.api.nvidia.com/v1",
            defaultModel = "deepseek-ai/deepseek-v4.1-flash",
            signupUrl = "https://build.nvidia.com",
            models = listOf(
                FactoryModel("deepseek-ai/deepseek-v4.1-flash", "DeepSeek V4.1 Flash", "Fast inference"),
                FactoryModel("z-ai/glm-5.3-flash", "GLM 5.3 Flash", "Fast MoE"),
                FactoryModel("moonshotai/kimi-k3", "Kimi K3", "Long context"),
                FactoryModel("nvidia/llama-3.1-nemotron-70b-instruct", "Nemotron 70B", "Reasoning"),
            ),
        ),
        FactoryProvider(
            id = "groq",
            name = "Groq",
            defaultBaseUrl = "https://api.groq.com/openai/v1",
            defaultModel = "llama-3.3-70b-versatile",
            signupUrl = "https://console.groq.com/keys",
            models = listOf(
                FactoryModel("llama-3.3-70b-versatile", "Llama 3.3 70B", "Ultra-fast LPU"),
                FactoryModel("deepseek-r1-distill-llama-70b", "DeepSeek R1 70B", "Ultra-fast reasoning"),
                FactoryModel("llama-3.1-8b-instant", "Llama 3.1 8B", "Instant response"),
            ),
        ),
        FactoryProvider(
            id = "zhipu",
            name = "Zhipu GLM",
            defaultBaseUrl = "https://api.z.ai/api/paas/v4",
            defaultModel = "glm-5.3-flash",
            signupUrl = "https://z.ai",
            models = listOf(
                FactoryModel("glm-5.3-flash", "GLM 5.3 Flash", "Fast & cost-effective"),
                FactoryModel("glm-5.2", "GLM 5.2", "1M context flagship"),
            ),
        ),
        FactoryProvider(
            id = "ollama",
            name = "Ollama",
            defaultBaseUrl = "http://<pc-ip>:11434/v1",
            defaultModel = "gemma3:1b",
            models = emptyList(),
        ),
        FactoryProvider(
            id = "local",
            name = "Local (On-Device)",
            defaultBaseUrl = "local",
            defaultModel = "",
            isLocal = true,
            models = emptyList(),
        ),
    )

    fun findById(id: String): FactoryProvider? =
        PROVIDERS.firstOrNull { it.id.equals(id, ignoreCase = true) }

    fun detectProvider(baseUrl: String): FactoryProvider? {
        val lower = baseUrl.lowercase()
        return when {
            lower.contains("openrouter.ai") -> findById("openrouter")
            lower.contains("kilocode.ai") -> findById("kilocode")
            lower.contains("deepinfra.com") -> findById("deepinfra")
            lower.contains("xiaomimimo.com") || lower.contains("vimo") -> findById("xiaomi")
            lower.contains("generativelanguage.googleapis.com") -> findById("gemini")
            lower.contains("api.deepseek.com") -> findById("deepseek")
            lower.contains("integrate.api.nvidia.com") -> findById("nvidia")
            lower.contains("api.groq.com") -> findById("groq")
            lower.contains("z.ai") || lower.contains("bigmodel.cn") -> findById("zhipu")
            lower.contains(":11434") || lower.contains("ollama") -> findById("ollama")
            lower == "local" -> findById("local")
            else -> null
        }
    }
}
