package me.rerere.ai.registry

object ModelRegistry {
    private val GPT4O = ModelMatcher.containsRegex("(?:chat)?gpt-4o")
    private val GPT_4_1 = ModelMatcher.containsRegex("gpt-4\\.1")
    val OPENAI_O_MODELS = ModelMatcher.containsRegex("o\\d")
    private val GPT_OSS = ModelMatcher.containsRegex("gpt-oss")
    val GPT_5 =
        ModelMatcher.containsRegex("gpt-(?!.*\\.)(?:5)") and ModelMatcher.containsRegex("gpt-5-chat", negated = true)
    val GPT_5_6 = ModelMatcher.containsRegex("gpt-5\\.[6-9]|gpt-5\\.6-(?:sol|terra|luna)")

    private val GEMINI_20_FLASH = ModelMatcher.containsRegex("gemini-2.0-flash")
    val GEMINI_2_5_FLASH = ModelMatcher.containsRegex("gemini-2.5-flash") and ModelMatcher.containsRegex("image", negated = true)
    val GEMINI_2_5_PRO = ModelMatcher.containsRegex("gemini-2.5-pro")
    val GEMINI_2_5_IMAGE = ModelMatcher.containsRegex("gemini-2.5-flash-image")
    val GEMINI_3_PRO = ModelMatcher.containsRegex("gemini-3-pro")
    val GEMINI_3_FLASH = ModelMatcher.containsRegex("gemini-3-flash")
    val GEMINI_FLASH_LATEST = ModelMatcher.exact("gemini-flash-latest")
    val GEMINI_PRO_LATEST = ModelMatcher.exact("gemini-pro-latest")
    val GEMINI_LATEST = GEMINI_FLASH_LATEST + GEMINI_PRO_LATEST
    val GEMINI_3_SERIES = GEMINI_3_PRO + GEMINI_3_FLASH
    val GEMINI_SERIES = GEMINI_20_FLASH + GEMINI_2_5_FLASH + GEMINI_2_5_PRO + GEMINI_3_SERIES + GEMINI_LATEST

    private val CLAUDE_SONNET_3_5 = ModelMatcher.containsRegex("claude-3.5-sonnet")
    private val CLAUDE_SONNET_3_7 = ModelMatcher.containsRegex("claude-3.7-sonnet")
    private val CLAUDE_4 = ModelMatcher.containsRegex("claude.*-4")
    val CLAUDE_4_5 = ModelMatcher.containsRegex("claude.*-4.5")
    val CLAUDE_5 = ModelMatcher.containsRegex("claude.*[-_.]?5")
    val CLAUDE_SERIES = CLAUDE_SONNET_3_5 + CLAUDE_SONNET_3_7 + CLAUDE_4 + CLAUDE_4_5 + CLAUDE_5

    private val DEEPSEEK_V3 = ModelMatcher.containsRegex("deepseek-(v3|chat)")
    private val DEEPSEEK_R1 = ModelMatcher.containsRegex("deepseek-(r1|reasoner)")
    private val DEEPSEEK_V3_1 = ModelMatcher.containsRegex("deepseek-(v3\\.1)")
    private val DEEPSEEK_V3_2 = ModelMatcher.containsRegex("deepseek-(v3\\.2)")
    private val DEEPSEEK_V4 = ModelMatcher.containsRegex("deepseek-(v4|v4-pro|v4-flash)")
    private val QWEN_3 = ModelMatcher.containsRegex("qwen-?3")
    private val QWEN_3_8 = ModelMatcher.containsRegex("qwen[-_ ]?3\\.[5-9]")
    private val QWQ = ModelMatcher.containsRegex("qwq")
    private val QVQ = ModelMatcher.containsRegex("qvq")
    private val DOUBAO_1_6 = ModelMatcher.containsRegex("doubao.+1([-.])6")
    private val GROK_3 = ModelMatcher.containsRegex("grok-3")
    private val GROK_4 = ModelMatcher.containsRegex("grok-4")
    private val KIMI_K2 = ModelMatcher.containsRegex("kimi-k2")
    private val KIMI_K3 = ModelMatcher.containsRegex("kimi-k3")
    private val STEP_3 = ModelMatcher.containsRegex("step-3")
    private val INTERN_S1 = ModelMatcher.containsRegex("intern-s1")
    private val GLM_4_5 = ModelMatcher.containsRegex("glm-4.5")
    private val GLM_4_6V = ModelMatcher.containsRegex("glm-4\\.6v")
    private val GLM_4_6 = ModelMatcher.containsRegex("glm-4\\.6(?!v)")
    private val GLM_5 = ModelMatcher.containsRegex("glm-5")
    private val MINIMAX_M2 = ModelMatcher.containsRegex("minimax-m2")
    private val MINIMAX_M3 = ModelMatcher.containsRegex("minimax[-_ ]?m3|MiniMax-M3")
    private val HUNYUAN_T1 = ModelMatcher.containsRegex("hunyuan-(?:t1|turbos)")
    val QWEN_MT = ModelMatcher.containsRegex("qwen-mt")
    private val GEMMA_3 = ModelMatcher.containsRegex("gemma[-_ ]?3")
    private val GEMMA_4 = ModelMatcher.containsRegex("gemma[-_ ]?4")
    private val MISTRAL_SMALL_4 = ModelMatcher.containsRegex("mistral-small-4")
    private val DEVSTRAL = ModelMatcher.containsRegex("devstral")
    private val LLAMA_4 = ModelMatcher.containsRegex("llama[-_ ]?4")
    private val SEED_2 = ModelMatcher.containsRegex("seed[-_ ]?2")
    private val COMMAND_A = ModelMatcher.containsRegex("command[-_ ]?a")

    val VISION_MODELS =
        GPT4O + GPT_4_1 + GPT_5 + GPT_5_6 + OPENAI_O_MODELS + GEMINI_SERIES + CLAUDE_SERIES + DOUBAO_1_6 + SEED_2 + GROK_4 + STEP_3 + INTERN_S1 + GLM_4_6V + GLM_5 + QVQ + QWEN_3_8 + KIMI_K3 + GEMMA_3 + GEMMA_4 + MINIMAX_M3 + MISTRAL_SMALL_4 + LLAMA_4 + COMMAND_A
    val TOOL_MODELS =
        GPT4O + GPT_4_1 + GPT_OSS + GPT_5 + GPT_5_6 + OPENAI_O_MODELS + GEMINI_SERIES + CLAUDE_SERIES + QWEN_3 + QWEN_3_8 + QWQ + QVQ + DOUBAO_1_6 + SEED_2 + GROK_3 + GROK_4 + KIMI_K2 + KIMI_K3 + STEP_3 + INTERN_S1 + GLM_4_5 + DEEPSEEK_R1 + DEEPSEEK_V3 + DEEPSEEK_V3_1 + DEEPSEEK_V3_2 + DEEPSEEK_V4 + GLM_4_6 + GLM_4_6V + GLM_5 + MINIMAX_M2 + MINIMAX_M3 + HUNYUAN_T1 + GEMMA_3 + GEMMA_4 + MISTRAL_SMALL_4 + DEVSTRAL + LLAMA_4 + COMMAND_A
    val REASONING_MODELS =
        GPT_OSS + GPT_5 + GPT_5_6 + OPENAI_O_MODELS + GEMINI_2_5_FLASH + GEMINI_2_5_PRO + GEMINI_3_SERIES + GEMINI_LATEST + CLAUDE_SERIES + QWEN_3 + QWEN_3_8 + QWQ + QVQ + DOUBAO_1_6 + SEED_2 + GROK_3 + GROK_4 + KIMI_K2 + KIMI_K3 + STEP_3 + INTERN_S1 + GLM_4_5 + DEEPSEEK_R1 + DEEPSEEK_V3_1 + DEEPSEEK_V3_2 + DEEPSEEK_V4 + GLM_4_6 + GLM_5 + MINIMAX_M2 + MINIMAX_M3 + HUNYUAN_T1 + GEMMA_4 + MISTRAL_SMALL_4 + DEVSTRAL + LLAMA_4 + COMMAND_A
    val CHAT_IMAGE_GEN_MODELS = GEMINI_2_5_IMAGE
}

