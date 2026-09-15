import re

with open('app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantPromptSubPage.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Add height import
content = content.replace(
    'import androidx.compose.foundation.layout.fillMaxWidth',
    'import androidx.compose.foundation.layout.fillMaxWidth\nimport androidx.compose.foundation.layout.height'
)

intros_ui = """        // ═══════════════════════════════════════════════════════════════════
        // INTROS
        // ═══════════════════════════════════════════════════════════════════
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            val mainIntro = assistant.presetMessages.firstOrNull { it.role == MessageRole.ASSISTANT }?.toText()
            val intros = remember(assistant.presetMessages, assistant.alternateGreetings) {
                val list = mutableListOf<String>()
                if (mainIntro != null) list.add(mainIntro)
                list.addAll(assistant.alternateGreetings)
                list
            }
            val updateIntros = { newIntros: List<String> ->
                if (newIntros.isEmpty()) {
                    onUpdate(assistant.copy(presetMessages = emptyList(), alternateGreetings = emptyList()))
                } else {
                    onUpdate(
                        assistant.copy(
                            presetMessages = listOf(UIMessage(role = MessageRole.ASSISTANT, parts = listOf(UIMessagePart.Text(newIntros.first())))),
                            alternateGreetings = newIntros.drop(1)
                        )
                    )
                }
            }

            var introsExpanded by remember { mutableStateOf(intros.isNotEmpty()) }

            if (intros.isEmpty()) {
                Button(
                    onClick = {
                        updateIntros(listOf(""))
                        introsExpanded = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add intro to this character")
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (me.rerere.rikkahub.ui.theme.LocalDarkMode.current)
                        MaterialTheme.colorScheme.surfaceContainerLow
                    else
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = me.rerere.rikkahub.ui.theme.AppShapes.CardLarge
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { introsExpanded = !introsExpanded }
                        ) {
                            Text(
                                text = "Intros",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.weight(1f))
                            Icon(
                                if (introsExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                contentDescription = null
                            )
                        }

                        AnimatedVisibility(visible = introsExpanded) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    itemsIndexed(intros) { index, intro ->
                                        var isEditing by remember { mutableStateOf(false) }
                                        Surface(
                                            color = MaterialTheme.colorScheme.background,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .width(280.dp)
                                                .height(140.dp)
                                                .clickable { isEditing = true }
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        "Intro #${index + 1}",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    Spacer(Modifier.weight(1f))
                                                    IconButton(
                                                        onClick = {
                                                            val newList = intros.toMutableList()
                                                            newList.removeAt(index)
                                                            updateIntros(newList)
                                                        },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(Icons.Rounded.Delete, null, modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    text = intro.ifEmpty { "Empty intro" },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 4,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        if (isEditing) {
                                            FullScreenSystemPromptEditor(
                                                systemPrompt = intro,
                                                onUpdate = { newText ->
                                                    val newList = intros.toMutableList()
                                                    newList[index] = newText
                                                    updateIntros(newList)
                                                },
                                                onDone = { isEditing = false }
                                            )
                                        }
                                    }

                                    item {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .width(120.dp)
                                                .height(140.dp)
                                                .clickable { updateIntros(intros + "") }
                                        ) {
                                            Column(
                                                modifier = Modifier.fillMaxSize(),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Icon(Icons.Rounded.Add, null)
                                                Text("Add Intro", style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                    }
                                }

                                FormItem(
                                    title = { Text("Cycle through intros on new chats") },
                                    tail = {
                                        HapticSwitch(
                                            checked = assistant.cycleIntrosOnNewChat,
                                            onCheckedChange = {
                                                onUpdate(assistant.copy(cycleIntrosOnNewChat = it))
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════════════════
        // SYSTEM PROMPT"""

content = content.replace(
    '        // ═══════════════════════════════════════════════════════════════════\n        // SYSTEM PROMPT',
    intros_ui
)

with open('app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantPromptSubPage.kt', 'w', encoding='utf-8') as f:
    f.write(content)
