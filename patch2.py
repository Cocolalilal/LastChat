import sys

with open(r'c:\Users\julia\Documents\Github\LastChat_dev\app\src\main\java\me\rerere\rikkahub\ui\pages\backup\BackupVM.kt', 'r', encoding='utf-8') as f:
    content = f.read()

target = """    suspend fun restoreFromChatBox(file: File) {"""

replacement = """    suspend fun restoreFromChatBox(file: File) {
        var importedConversations = 0
        val importedProviders = withContext(Dispatchers.IO) {
            val importProviders = arrayListOf<ProviderSetting>()
            val newAssistants = mutableListOf<me.rerere.rikkahub.data.model.Assistant>()
            val copilotIdToAssistantId = mutableMapOf<String, kotlin.uuid.Uuid>()

            val jsonElements = JsonInstant.parseToJsonElement(file.readText()).jsonObject
            val settingsObj = jsonElements["settings"]?.jsonObject
            if (settingsObj != null) {
                settingsObj["providers"]?.jsonObject?.let { providers ->
                    providers["openai"]?.jsonObject?.let { openai ->
                        val apiHost = openai["apiHost"]?.jsonPrimitive?.contentOrNull ?: "https://api.openai.com"
                        val apiKey = openai["apiKey"]?.jsonPrimitive?.contentOrNull ?: ""
                        val models = openai["models"]?.jsonArray?.map { element ->
                            val modelId = element.jsonObject["modelId"]?.jsonPrimitive?.contentOrNull ?: ""
                            val capabilities = element.jsonObject["capabilities"]?.jsonArray
                                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                                ?: emptyList()
                            Model(
                                modelId = modelId,
                                displayName = modelId,
                                inputModalities = buildList {
                                    if (capabilities.contains("vision")) add(Modality.IMAGE)
                                },
                                abilities = buildList {
                                    if (capabilities.contains("tool_use")) add(ModelAbility.TOOL)
                                    if (capabilities.contains("reasoning")) add(ModelAbility.REASONING)
                                }
                            )
                        } ?: emptyList()
                        if (apiKey.isNotBlank()) {
                            importProviders.add(
                                ProviderSetting.OpenAI(
                                    name = "OpenAI",
                                    baseUrl = "$apiHost/v1",
                                    apiKey = apiKey,
                                    models = models,
                                )
                            )
                        }
                    }
                    providers["claude"]?.jsonObject?.let { claude ->
                        val apiHost =
                            claude["apiHost"]?.jsonPrimitive?.contentOrNull ?: "https://api.anthropic.com"
                        val apiKey = claude["apiKey"]?.jsonPrimitive?.contentOrNull ?: ""
                        if (apiKey.isNotBlank()) {
                            importProviders.add(
                                ProviderSetting.Claude(
                                    name = "Claude",
                                    baseUrl = "$apiHost/v1",
                                    apiKey = apiKey,
                                )
                            )
                        }
                    }
                    providers["gemini"]?.jsonObject?.let { gemini ->
                        val apiHost = gemini["apiHost"]?.jsonPrimitive?.contentOrNull
                            ?: "https://generativelanguage.googleapis.com"
                        val apiKey = gemini["apiKey"]?.jsonPrimitive?.contentOrNull ?: ""
                        if (apiKey.isNotBlank()) {
                            importProviders.add(
                                ProviderSetting.Google(
                                    name = "Gemini",
                                    baseUrl = "$apiHost/v1beta",
                                    apiKey = apiKey,
                                )
                            )
                        }
                    }
                }
            }

            // Parse Copilots (Assistants)
            jsonElements["myCopilots"]?.jsonArray?.forEach { element ->
                try {
                    val copilotObj = element.jsonObject
                    val copilotId = copilotObj["id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                    val name = copilotObj["name"]?.jsonPrimitive?.contentOrNull ?: "Imported Copilot"
                    val description = copilotObj["description"]?.jsonPrimitive?.contentOrNull ?: ""
                    val config = copilotObj["config"]?.jsonObject
                    val systemPrompt = config?.get("systemPrompt")?.jsonPrimitive?.contentOrNull ?: ""
                    
                    val assistantId = kotlin.uuid.Uuid.random()
                    copilotIdToAssistantId[copilotId] = assistantId
                    
                    val assistant = me.rerere.rikkahub.data.model.Assistant(
                        id = assistantId,
                        name = name,
                        description = description,
                        systemPrompt = systemPrompt
                    )
                    newAssistants.add(assistant)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse copilot", e)
                }
            }

            // Parse conversations
            jsonElements.forEach { (key, element) ->
                if (key.startsWith("session:")) {
                    try {
                        val sessionObj = element.jsonObject
                        val messagesArray = sessionObj["messages"]?.jsonArray ?: return@forEach
                        val title = sessionObj["name"]?.jsonPrimitive?.contentOrNull ?: "Chatbox Import"
                        val copilotId = sessionObj["copilotId"]?.jsonPrimitive?.contentOrNull
                        
                        val mappedAssistantId = copilotIdToAssistantId[copilotId] 
                            ?: me.rerere.rikkahub.data.datastore.DEFAULT_ASSISTANT_ID
                        
                        val uiMessages = messagesArray.mapNotNull { msgElement ->
                            val msgObj = msgElement.jsonObject
                            val roleStr = msgObj["role"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: "user"
                            val content = msgObj["content"]?.jsonPrimitive?.contentOrNull ?: ""
                            
                            val role = when (roleStr) {
                                "user" -> me.rerere.ai.core.MessageRole.USER
                                "assistant" -> me.rerere.ai.core.MessageRole.ASSISTANT
                                "system" -> me.rerere.ai.core.MessageRole.SYSTEM
                                else -> me.rerere.ai.core.MessageRole.USER
                            }
                            
                            if (content.isNotBlank()) {
                                me.rerere.ai.ui.UIMessage(
                                    id = kotlin.uuid.Uuid.random(),
                                    role = role,
                                    parts = listOf(me.rerere.ai.ui.UIMessagePart.Text(content))
                                )
                            } else {
                                null
                            }
                        }
                        
                        if (uiMessages.isNotEmpty()) {
                            val conversation = me.rerere.rikkahub.data.model.Conversation(
                                id = kotlin.uuid.Uuid.random(),
                                assistantId = mappedAssistantId,
                                title = title,
                                messageNodes = uiMessages.map { 
                                    me.rerere.rikkahub.data.model.MessageNode(
                                        messages = listOf(it), 
                                        selectIndex = 0
                                    ) 
                                }
                            )
                            conversationRepository.insertConversation(conversation)
                            importedConversations++
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse session $key", e)
                    }
                }
            }
            
            Triple(importProviders.toList(), importedConversations, newAssistants)
        }

        val (importedProvidersList, importedConversationsCount, newAssistantsList) = importedProviders
        val resolvedProviders = importedProvidersList.map(modelMetadataResolver::applyToProvider)

        if (resolvedProviders.isEmpty() && importedConversationsCount == 0 && newAssistantsList.isEmpty()) {
            throw IllegalArgumentException("No importable data found in ChatBox export")
        }

        Log.i(TAG, "restoreFromChatBox: import ${resolvedProviders.size} providers, $importedConversationsCount conversations, ${newAssistantsList.size} assistants")
        
        settingsStore.update { current ->
            var updated = current
            if (resolvedProviders.isNotEmpty()) {
                updated = updated.copy(
                    providers = mergeImportedProviders(current.providers, resolvedProviders)
                )
            }
            if (newAssistantsList.isNotEmpty()) {
                updated = updated.copy(
                    assistants = current.assistants + newAssistantsList
                )
            }
            updated
        }
    }"""

def normalize_crlf(text):
    return text.replace('\r\n', '\n')

content_norm = normalize_crlf(content)
target_norm = normalize_crlf(target)

start_idx = content_norm.find(target_norm)
if start_idx != -1:
    end_target = """    suspend fun restoreFromCherryStudio(file: File) {"""
    end_idx = content_norm.find(end_target)
    
    if end_idx != -1:
        new_content = content_norm[:start_idx] + replacement + '\n\n' + content_norm[end_idx:]
        with open(r'c:\Users\julia\Documents\Github\LastChat_dev\app\src\main\java\me\rerere\rikkahub\ui\pages\backup\BackupVM.kt', 'w', encoding='utf-8') as f:
            f.write(new_content)
        print("Success")
    else:
        print("End target not found")
else:
    print("Target not found in file")
