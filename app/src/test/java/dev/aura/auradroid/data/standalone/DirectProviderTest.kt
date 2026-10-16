package dev.aura.auradroid.data.standalone

import org.junit.Assert.assertEquals
import org.junit.Test

class DirectProviderTest {

    @Test
    fun endpointForDeepSeek() {
        assertEquals(
            "https://api.deepseek.com/v1/chat/completions",
            DirectProvider.endpointFor("https://api.deepseek.com"),
        )
        assertEquals(
            "https://api.deepseek.com/v1/chat/completions",
            DirectProvider.endpointFor("https://api.deepseek.com/v1"),
        )
        assertEquals(
            "https://api.deepseek.com/v1/chat/completions",
            DirectProvider.endpointFor("https://api.deepseek.com/v1/"),
        )
    }

    @Test
    fun endpointForNvidia() {
        assertEquals(
            "https://integrate.api.nvidia.com/v1/chat/completions",
            DirectProvider.endpointFor("https://integrate.api.nvidia.com/v1"),
        )
        assertEquals(
            "https://integrate.api.nvidia.com/v1/chat/completions",
            DirectProvider.endpointFor("https://integrate.api.nvidia.com"),
        )
    }

    @Test
    fun endpointForOpenRouter() {
        // User passes bare openrouter.ai without /api
        assertEquals(
            "https://openrouter.ai/api/v1/chat/completions",
            DirectProvider.endpointFor("https://openrouter.ai"),
        )
        // User passes openrouter.ai/api/v1
        assertEquals(
            "https://openrouter.ai/api/v1/chat/completions",
            DirectProvider.endpointFor("https://openrouter.ai/api/v1"),
        )
    }

    @Test
    fun endpointForGroq() {
        // User passes api.groq.com without /openai
        assertEquals(
            "https://api.groq.com/openai/v1/chat/completions",
            DirectProvider.endpointFor("https://api.groq.com"),
        )
        // User passes api.groq.com/openai/v1
        assertEquals(
            "https://api.groq.com/openai/v1/chat/completions",
            DirectProvider.endpointFor("https://api.groq.com/openai/v1"),
        )
    }

    @Test
    fun endpointForGemini() {
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
            DirectProvider.endpointFor("https://generativelanguage.googleapis.com/v1beta/openai"),
        )
    }

    @Test
    fun endpointForZhipu() {
        // Version 4 path (api.z.ai or open.bigmodel.cn)
        assertEquals(
            "https://api.z.ai/api/coding/paas/v4/chat/completions",
            DirectProvider.endpointFor("https://api.z.ai/api/coding/paas/v4"),
        )
        assertEquals(
            "https://open.bigmodel.cn/api/paas/v4/chat/completions",
            DirectProvider.endpointFor("https://open.bigmodel.cn/api/paas/v4"),
        )
    }

    @Test
    fun endpointForKiloCode() {
        assertEquals(
            "https://api.kilocode.ai/api/openrouter/chat/completions",
            DirectProvider.endpointFor("https://api.kilocode.ai/api/openrouter"),
        )
    }

    @Test
    fun endpointForDeepInfra() {
        assertEquals(
            "https://api.deepinfra.com/v1/openai/chat/completions",
            DirectProvider.endpointFor("https://api.deepinfra.com/v1/openai"),
        )
    }

    @Test
    fun endpointWithoutScheme() {
        assertEquals(
            "https://api.deepseek.com/v1/chat/completions",
            DirectProvider.endpointFor("api.deepseek.com"),
        )
    }

    @Test
    fun endpointAlreadyHasChatCompletions() {
        assertEquals(
            "https://my.proxy.com/chat/completions",
            DirectProvider.endpointFor("https://my.proxy.com/chat/completions"),
        )
    }

    @Test
    fun normalizeModelNvidia() {
        val base = "https://integrate.api.nvidia.com/v1"
        assertEquals(
            "deepseek-ai/deepseek-v4.1-flash",
            DirectProvider.normalizeModel(base, "nvidia/deepseek-ai/deepseek-v4.1-flash"),
        )
        assertEquals(
            "deepseek-ai/deepseek-v4.1-flash",
            DirectProvider.normalizeModel(base, "deepseek-v4.1-flash"),
        )
        assertEquals(
            "z-ai/glm-5.3-flash",
            DirectProvider.normalizeModel(base, "glm-5.3"),
        )
        assertEquals(
            "z-ai/glm-5.3-flash",
            DirectProvider.normalizeModel(base, "glm-5.3-flash"),
        )
        assertEquals(
            "moonshotai/kimi-k3",
            DirectProvider.normalizeModel(base, "kimi-k3"),
        )
    }

    @Test
    fun normalizeModelOtherProvider() {
        assertEquals(
            "deepseek-chat",
            DirectProvider.normalizeModel("https://api.deepseek.com", "deepseek-chat"),
        )
    }

    @Test
    fun normalizeModelKiloCode() {
        val base = "https://api.kilocode.ai/api/openrouter"
        assertEquals(
            "kilo-auto/free",
            DirectProvider.normalizeModel(base, "kilocode/kilo-auto/free"),
        )
        assertEquals(
            "nvidia/nemotron-3-ultra-550b-a55b:free",
            DirectProvider.normalizeModel(base, "kilocode/nvidia/nemotron-3-ultra-550b-a55b:free"),
        )
    }

    @Test
    fun normalizeModelDeepInfra() {
        val base = "https://api.deepinfra.com/v1/openai"
        assertEquals(
            "meta-llama/Llama-3.3-70B-Instruct",
            DirectProvider.normalizeModel(base, "deepinfra/meta-llama/Llama-3.3-70B-Instruct"),
        )
    }
}
