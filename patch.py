import sys

with open(r'c:\Users\julia\Documents\Github\LastChat_dev\app\src\main\java\me\rerere\rikkahub\ui\pages\backup\BackupVM.kt', 'r', encoding='utf-8') as f:
    content = f.read()

target = """            importProviders.toList()
        }

        val resolvedProviders = importedProviders.map(modelMetadataResolver::applyToProvider)

        if (resolvedProviders.isEmpty()) {
            throw IllegalArgumentException("No importable providers found in ChatBox export")
        }

        Log.i(TAG, "restoreFromChatBox: import ${resolvedProviders.size} providers: $resolvedProviders")
        settingsStore.update { current ->
            current.copy(
                providers = mergeImportedProviders(current.providers, resolvedProviders)
            )
        }"""

replacement = """            // Parse conversations
            var importedConversations = 0
            jsonElements.forEach { (key, element) ->
                if (key.startsWith("session:")) {
                    try {
                        val sessionObj = element.jsonObject
                        val messagesArray = sessionObj["messages"]?.jsonArray ?: return@forEach
                        val title = sessionObj["name"]?.jsonPrimitive?.contentOrNull ?: "Chatbox Import"
                        
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
                                assistantId = me.rerere.rikkahub.data.datastore.DEFAULT_ASSISTANT_ID,
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
            
            Pair(importProviders.toList(), importedConversations)
        }

        val (importedProvidersList, importedConversations) = importedProviders
        val resolvedProviders = importedProvidersList.map(modelMetadataResolver::applyToProvider)

        if (resolvedProviders.isEmpty() && importedConversations == 0) {
            throw IllegalArgumentException("No importable data found in ChatBox export")
        }

        Log.i(TAG, "restoreFromChatBox: import ${resolvedProviders.size} providers, $importedConversations conversations")
        if (resolvedProviders.isNotEmpty()) {
            settingsStore.update { current ->
                current.copy(
                    providers = mergeImportedProviders(current.providers, resolvedProviders)
                )
            }
        }"""

def normalize_crlf(text):
    return text.replace('\r\n', '\n')

content_norm = normalize_crlf(content)
target_norm = normalize_crlf(target)

if target_norm in content_norm:
    new_content = content_norm.replace(target_norm, replacement)
    with open(r'c:\Users\julia\Documents\Github\LastChat_dev\app\src\main\java\me\rerere\rikkahub\ui\pages\backup\BackupVM.kt', 'w', encoding='utf-8') as f:
        f.write(new_content)
    print("Success")
else:
    print("Target not found in file")
