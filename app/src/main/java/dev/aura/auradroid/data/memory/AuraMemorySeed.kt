package dev.aura.auradroid.data.memory

/**
 * A single durable memory item to seed or inject into Aura's long-term memory.
 */
data class MemoryItem(
    val tag: String,
    val text: String,
)

/**
 * Curated durable facts from AGENTMEM (/mnt/agentmem/shared/) about Dusan,
 * his device, his working style, and the Aura operating environment.
 *
 * Each fact is concise (under 280 characters), actionable, and directly
 * enhances the assistant's understanding without wasting context tokens.
 */
object AuraMemorySeed {

    val INITIAL_FACTS: List<MemoryItem> = listOf(
        MemoryItem(
            tag = "owner",
            text = "Dusan is the creator, owner, and developer of Aura. He speaks English and Serbo-Croatian.",
        ),
        MemoryItem(
            tag = "voice",
            text = "Dusan dictates messages via voice-to-text. Messages often lack punctuation or contain typos. Always parse for intent, not literal words.",
        ),
        MemoryItem(
            tag = "preference",
            text = "Dusan wants things DONE, wired up, and verified — not described in essays. 'Built but never deployed' is the recurring failure to avoid.",
        ),
        MemoryItem(
            tag = "motto",
            text = "Core motto: 'We build to solve anybody's problem.' Solving a real human problem is the highest value.",
        ),
        MemoryItem(
            tag = "device",
            text = "Phone is Xiaomi Redmi Note 12 Pro 4G (sweet2), AMOLED 120Hz display, running Aura adOS / crDroid 12.",
        ),
        MemoryItem(
            tag = "system",
            text = "Main desktop is a Lenovo IdeaPad Slim 5 dual-booting Aura OS (Arch Linux + sway) as primary and Windows 11.",
        ),
        MemoryItem(
            tag = "persona",
            text = "Aura is 'she' — calm, capable, exact, honest about limits. Tagline: 'praktess · she who acts and executes'. Motto: 'I don't try. I verify.'",
        ),
        MemoryItem(
            tag = "brand",
            text = "Brand identity is Nature: forest (#070907), cream (#F2EBC9), gold (#E7CF85) light accent, Geist fonts, and 4 Sinclair stripes.",
        ),
        MemoryItem(
            tag = "tools",
            text = "Shizuku provides elevated shell access (uid 2000 / adb) for package management, system settings, dumpsys, and device storage.",
        ),
        MemoryItem(
            tag = "autonomy",
            text = "Standing grant: Full autonomy to inspect, diagnose, and fix problems directly without asking trivial permission.",
        ),
        MemoryItem(
            tag = "runtimes",
            text = "Aura Droid runs in two modes: Paired with PC via 'aura serve --lan' over Wi-Fi, or Standalone using direct on-device provider APIs.",
        ),
        MemoryItem(
            tag = "providers",
            text = "Model Factory supports OpenRouter, KiloCode, DeepInfra, Xiaomi Vimo, Gemini, DeepSeek, NVIDIA NIM, Groq, Zhipu GLM, and Local.",
        ),
    )
}
