package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import com.whl.quickjs.wrapper.QuickJSContext
import com.whl.quickjs.wrapper.QuickJSObject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import kotlin.uuid.Uuid

@Serializable
sealed class LocalToolOption {
    @Serializable
    @SerialName("javascript_engine")
    data object JavascriptEngine : LocalToolOption()

    @Serializable
    @SerialName("device_control")
    data object DeviceControl : LocalToolOption()

    @Serializable
    @SerialName("python_engine")
    data object PythonEngine : LocalToolOption()
}

class LocalTools(private val context: Context) {
    val javascriptTool by lazy {
        Tool(
            name = "eval_javascript",
            description = "Execute JavaScript code with QuickJS. If use this tool to calculate math, better to add `toFixed` to the code.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("code", buildJsonObject {
                            put("type", "string")
                            put("description", "The JavaScript code to execute")
                        })
                    },
                )
            },
            execute = {
                val context = QuickJSContext.create()
                val code = it.jsonObject["code"]?.jsonPrimitive?.contentOrNull
                val result = context.evaluate(code)
                buildJsonObject {
                    put(
                        "result", when (result) {
                            is QuickJSObject -> JsonPrimitive(result.stringify())
                            else -> JsonPrimitive(result.toString())
                        }
                    )
                }
            }
        )
    }

    private val pythonSandbox by lazy { PythonSandbox(context) }

    /**
     * Get Python tools for the conversation.
     * @param conversationId The conversation UUID
     * @param userImageUrls Image URLs from the most recent user message - will be auto-imported
     */
    fun getPythonTools(conversationId: Uuid, userImageUrls: List<String> = emptyList()): List<Tool> {
        val workingDir = pythonSandbox.getConversationDir(conversationId).absolutePath
        
        // Auto-import user attachments to sandbox
        val preloadedFiles = mutableListOf<String>()
        userImageUrls.forEachIndexed { index, url ->
            runCatching {
                val filename = "attachment_$index.png"
                pythonSandbox.importFile(conversationId, android.net.Uri.parse(url), filename)
                preloadedFiles.add(filename)
            }.onFailure { e ->
                android.util.Log.w("LocalTools", "Failed to auto-import attachment $index: ${e.message}")
            }
        }
        
        // Build description with info about pre-loaded files
        val preloadedInfo = if (preloadedFiles.isNotEmpty()) {
            " User attachments are pre-loaded in sandbox as: ${preloadedFiles.joinToString { it }}. Access them with Image.open(\"${'$'}{filename}\")."
        } else ""
        
        return listOf(
            Tool(
                name = "eval_python",
                description = "Execute Python code. Has access to numpy, pandas, matplotlib and Pillow. Use for calculations, data processing and chart/image generation.$preloadedInfo After execution, check `generated_files` and include any `markdown_link` in your reply (for images prefer Markdown image syntax like `![chart](content://...)`).",
                parameters = {
                    InputSchema.Obj(
                        properties = buildJsonObject {
                            put("code", buildJsonObject {
                                put("type", "string")
                                put("description", "The Python code to execute")
                            })
                        },
                        required = listOf("code")
                    )
                },
                execute = {
                    val code = it.jsonObject["code"]?.jsonPrimitive?.contentOrNull ?: ""
                    try {
                        val filesBefore = pythonSandbox.listFiles(conversationId)
                        val beforeNames = filesBefore.map { file -> file.name }.toSet()

                        val python = com.chaquo.python.Python.getInstance()
                        val executor = python.getModule("executor")
                        val resultJson = executor.callAttr("execute", code, workingDir).toString()
                        val baseResultObj = kotlinx.serialization.json.Json.parseToJsonElement(resultJson).jsonObject

                        val filesAfter = pythonSandbox.listFiles(conversationId)
                        val generatedFiles = filesAfter
                            .filter { file -> !beforeNames.contains(file.name) }
                            .map { file ->
                                val uri = pythonSandbox.getFileUri(conversationId, file.name)
                                buildJsonObject {
                                    put("name", file.name)
                                    put("size", file.size)
                                    put("is_image", file.isImage)
                                    put("mime", file.mimeType)
                                    put("uri", uri.toString())
                                    put("markdown_link", if (file.isImage) "![${file.name}]($uri)" else "[${file.name}]($uri)")
                                }
                            }

                        val finalResultObj = buildJsonObject {
                            baseResultObj.forEach { (k, v) -> put(k, v) }
                            if (preloadedFiles.isNotEmpty()) {
                                put("preloaded_attachments", JsonArray(preloadedFiles.map { JsonPrimitive(it) }))
                            }
                            if (generatedFiles.isNotEmpty()) {
                                put("generated_files", JsonArray(generatedFiles))
                                put("note", "Use generated_files[].markdown_link in your reply so users can open/download outputs directly in chat.")
                            }
                        }

                        // Truncate output if too long
                        val output = finalResultObj.toString()
                        if (output.length > 2000) {
                            buildJsonObject {
                                put("output", output.take(2000) + "... (truncated)")
                                put("note", "Output truncated to save context window. Use print() sparingly or save to file, and use list_sandbox_files to inspect files.")
                            }
                        } else {
                            finalResultObj
                        }
                    } catch (e: Exception) {
                        buildJsonObject { put("error", e.message ?: "Unknown error") }
                    }
                }
            ),
            Tool(
                name = "list_sandbox_files",
                description = "List all files in the Python sandbox for this conversation. Returns file names, sizes, whether they are images, and direct markdown links you can include in your response.",
                parameters = {
                    InputSchema.Obj(
                        properties = buildJsonObject { },
                        required = emptyList()
                    )
                },
                execute = {
                    try {
                        val files = pythonSandbox.listFiles(conversationId)
                        buildJsonObject {
                            put("files", kotlinx.serialization.json.JsonArray(
                                files.map { file ->
                                    buildJsonObject {
                                        val uri = pythonSandbox.getFileUri(conversationId, file.name)
                                        put("name", file.name)
                                        put("size", file.size)
                                        put("is_image", file.isImage)
                                        put("mime", file.mimeType)
                                        put("uri", uri.toString())
                                        put("markdown_link", if (file.isImage) "![${file.name}]($uri)" else "[${file.name}]($uri)")
                                    }
                                }
                            ))
                        }
                    } catch (e: Exception) {
                        buildJsonObject { put("error", e.message ?: "Failed to list files") }
                    }
                }
            ),
            Tool(
                name = "read_sandbox_file",
                description = "Read a text file from the Python sandbox.",
                parameters = {
                    InputSchema.Obj(
                        properties = buildJsonObject {
                            put("path", buildJsonObject {
                                put("type", "string")
                                put("description", "Relative path to the file in the sandbox")
                            })
                        },
                        required = listOf("path")
                    )
                },
                execute = {
                    val path = it.jsonObject["path"]?.jsonPrimitive?.contentOrNull ?: ""
                    try {
                        val python = com.chaquo.python.Python.getInstance()
                        val executor = python.getModule("executor")
                        val resultJson = executor.callAttr("read_file", path, workingDir).toString()
                        kotlinx.serialization.json.Json.parseToJsonElement(resultJson).jsonObject
                    } catch (e: Exception) {
                        buildJsonObject { put("error", e.message ?: "Failed to read file") }
                    }
                }
            ),
            Tool(
                name = "write_sandbox_file",
                description = "Write content to a file in the Python sandbox. Returns `markdown_link` which you MUST include in your response to let the user download the file. Example: 'Here is your file: [output.txt](content://...)'",
                parameters = {
                    InputSchema.Obj(
                        properties = buildJsonObject {
                            put("path", buildJsonObject {
                                put("type", "string")
                                put("description", "Relative path for the file (e.g. 'output.txt', 'images/result.png')")
                            })
                            put("content", buildJsonObject {
                                put("type", "string")
                                put("description", "Content to write to the file")
                            })
                        },
                        required = listOf("path", "content")
                    )
                },
                execute = {
                    val path = it.jsonObject["path"]?.jsonPrimitive?.contentOrNull ?: ""
                    val content = it.jsonObject["content"]?.jsonPrimitive?.contentOrNull ?: ""
                    try {
                        val python = com.chaquo.python.Python.getInstance()
                        val executor = python.getModule("executor")
                        val resultJson = executor.callAttr("write_file", path, content, workingDir).toString()
                        val resultObj = kotlinx.serialization.json.Json.parseToJsonElement(resultJson).jsonObject
                        
                        if (resultObj["success"]?.jsonPrimitive?.booleanOrNull == true) {
                            // Use original relative path since getFileUri constructs full path
                            val uri = pythonSandbox.getFileUri(conversationId, path)
                            kotlinx.serialization.json.buildJsonObject {
                                resultObj.forEach { (k, v) -> put(k, v) }
                                put("uri", uri.toString())
                                put("markdown_link", "[$path]($uri)")
                            }
                        } else {
                            resultObj
                        }
                    } catch (e: Exception) {
                        buildJsonObject { put("error", e.message ?: "Failed to write file") }
                    }
                }
            ),
            Tool(
                name = "delete_sandbox_file",
                description = "Delete a file from the Python sandbox.",
                parameters = {
                    InputSchema.Obj(
                        properties = buildJsonObject {
                            put("path", buildJsonObject {
                                put("type", "string")
                                put("description", "Relative path to the file to delete")
                            })
                        },
                        required = listOf("path")
                    )
                },
                execute = {
                    val path = it.jsonObject["path"]?.jsonPrimitive?.contentOrNull ?: ""
                    try {
                        val deleted = pythonSandbox.deleteFile(conversationId, path)
                        buildJsonObject {
                            put("success", deleted)
                            put("path", path)
                        }
                    } catch (e: Exception) {
                        buildJsonObject { put("error", e.message ?: "Failed to delete file") }
                    }
                }
            ),
            Tool(
                name = "import_attachment",
                description = "Import an attached file from the user's message into the Python sandbox. Use the file URL from image/document attachments in the conversation. Tip: you can also pass multiple attachments directly in eval_python.attachments for automatic import.",
                parameters = {
                    InputSchema.Obj(
                        properties = buildJsonObject {
                            put("url", buildJsonObject {
                                put("type", "string")
                                put("description", "The file URL from the message attachment (e.g. 'file:///...' or 'content://...')")
                            })
                            put("filename", buildJsonObject {
                                put("type", "string")
                                put("description", "Filename to save as in the sandbox (e.g. 'input.jpg', 'data.csv')")
                            })
                        },
                        required = listOf("url", "filename")
                    )
                },
                execute = {
                    val url = it.jsonObject["url"]?.jsonPrimitive?.contentOrNull ?: ""
                    val filename = it.jsonObject["filename"]?.jsonPrimitive?.contentOrNull ?: ""
                    try {
                        val uriArg = android.net.Uri.parse(url)
                        val savedPath = pythonSandbox.importFile(conversationId, uriArg, filename)
                         // Inject URI for file access
                        val fileUri = pythonSandbox.getFileUri(conversationId, filename)
                        buildJsonObject {
                            put("success", true)
                            put("path", savedPath)
                            put("filename", filename)
                            put("uri", fileUri.toString())
                            put("markdown_link", "[$filename]($fileUri)")
                        }
                    } catch (e: Exception) {
                        buildJsonObject {
                            put("success", false)
                            put("error", e.message ?: "Failed to import file")
                        }
                    }
                }
            )
        )
    }

    fun getDeviceControlTools(assistantId: Uuid, conversationId: Uuid): List<Tool> {
        return listOf(
            Tool(
                name = "send_notification",
                description = "Send a notification to the user",
                parameters = {
                    InputSchema.Obj(
                        properties = buildJsonObject {
                            put("title", buildJsonObject {
                                put("type", "string")
                                put("description", "Notification title")
                            })
                            put("content", buildJsonObject {
                                put("type", "string")
                                put("description", "Notification content")
                            })
                        },
                        required = listOf("title", "content")
                    )
                },
                execute = {
                    val title = it.jsonObject["title"]?.jsonPrimitive?.contentOrNull ?: "Notification"
                    val content = it.jsonObject["content"]?.jsonPrimitive?.contentOrNull ?: ""
                    
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                    val channelId = "assistant_notification"
                    val channel = android.app.NotificationChannel(
                        channelId,
                        "Assistant Notification",
                        android.app.NotificationManager.IMPORTANCE_DEFAULT
                    )
                    notificationManager.createNotificationChannel(channel)
                    
                    // Create pending intent to open the conversation when notification is clicked
                    val intent = android.content.Intent(context, me.rerere.rikkahub.RouteActivity::class.java).apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra("conversationId", conversationId.toString())
                    }
                    val pendingIntent = android.app.PendingIntent.getActivity(
                        context,
                        conversationId.hashCode(),
                        intent,
                        android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                    )

                    val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
                        .setSmallIcon(me.rerere.rikkahub.R.drawable.ic_notification)
                        .setContentTitle(title)
                        .setContentText(content)
                        .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                        .build()
                        
                    if (androidx.core.app.ActivityCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.POST_NOTIFICATIONS
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
                        buildJsonObject { put("status", "success") }
                    } else {
                        buildJsonObject { put("status", "error: permission denied") }
                    }
                }
            ),
            Tool(
                name = "schedule_message",
                description = "Schedule a message to be sent by the assistant after a certain delay.",
                parameters = {
                    InputSchema.Obj(
                        properties = buildJsonObject {
                            put("reason", buildJsonObject {
                                put("type", "string")
                                put("description", "The reason for scheduling this message (e.g., 'Remind user to drink water')")
                            })
                            put("delay_minutes", buildJsonObject {
                                put("type", "integer")
                                put("description", "Delay in minutes before sending the message")
                            })
                        },
                        required = listOf("reason", "delay_minutes")
                    )
                },
                execute = {
                    val reason = it.jsonObject["reason"]?.jsonPrimitive?.contentOrNull ?: ""
                    val delayMinutes = it.jsonObject["delay_minutes"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 1L
                    
                    try {
                        val currentTime = System.currentTimeMillis()
                        val targetTime = currentTime + (delayMinutes * 60 * 1000)
                        
                        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                        
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            if (!alarmManager.canScheduleExactAlarms()) {
                                    buildJsonObject { put("status", "error: permission SCHEDULE_EXACT_ALARM not granted") }
                            }
                        }

                        val intent = android.content.Intent(context, me.rerere.rikkahub.service.ScheduledMessageReceiver::class.java).apply {
                            putExtra("assistantId", assistantId.toString())
                            putExtra("conversationId", conversationId.toString())
                            putExtra("reason", reason)
                        }
                        
                        val pendingIntent = android.app.PendingIntent.getBroadcast(
                            context,
                            (assistantId.hashCode() + conversationId.hashCode() + reason.hashCode()),
                            intent,
                            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                        )
                        
                        alarmManager.setExactAndAllowWhileIdle(
                            android.app.AlarmManager.RTC_WAKEUP,
                            targetTime,
                            pendingIntent
                        )
                        
                        buildJsonObject { 
                            put("status", "success")
                            put("scheduled_at", java.time.Instant.ofEpochMilli(targetTime).toString())
                        }
                    } catch (e: Exception) {
                        buildJsonObject { put("status", "error: ${e.message}") }
                    }
                }
            ),
            Tool(
                name = "get_notifications",
                description = "Get recent notifications from the device",
                parameters = {
                    InputSchema.Obj(
                        properties = buildJsonObject {
                            put("limit", buildJsonObject {
                                put("type", "integer")
                                put("description", "Max number of notifications to retrieve (default 10)")
                            })
                        }
                    )
                },
                execute = {
                    val limit = it.jsonObject["limit"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 10
                    val notifications = me.rerere.rikkahub.service.AssistantNotificationListener.notifications.value.take(limit)
                    
                    buildJsonObject {
                        put("notifications", kotlinx.serialization.json.JsonArray(notifications.map { notification ->
                            buildJsonObject {
                                put("package", notification.packageName)
                                put("title", notification.title)
                                put("content", notification.content)
                                put("time", notification.postTime)
                            }
                        }))
                    }
                }
            ),
            Tool(
                name = "open_app",
                description = "Open an application by package name",
                parameters = {
                    InputSchema.Obj(
                        properties = buildJsonObject {
                            put("package_name", buildJsonObject {
                                put("type", "string")
                                put("description", "Package name of the app to open")
                            })
                        },
                        required = listOf("package_name")
                    )
                },
                execute = {
                    val packageName = it.jsonObject["package_name"]?.jsonPrimitive?.contentOrNull ?: ""
                    val pm = context.packageManager
                    try {
                        val intent = pm.getLaunchIntentForPackage(packageName)
                        if (intent != null) {
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                            buildJsonObject { put("status", "success") }
                        } else {
                            buildJsonObject { put("status", "error: app not found") }
                        }
                    } catch (e: Exception) {
                        buildJsonObject { put("status", "error: ${e.message}") }
                    }
                }
            ),
            Tool(
                name = "set_alarm",
                description = "Set an alarm at a specific time",
                parameters = {
                    InputSchema.Obj(
                        properties = buildJsonObject {
                            put("hour", buildJsonObject {
                                put("type", "integer")
                                put("description", "Hour (0-23)")
                            })
                            put("minute", buildJsonObject {
                                put("type", "integer")
                                put("description", "Minute (0-59)")
                            })
                            put("message", buildJsonObject {
                                put("type", "string")
                                put("description", "Alarm label/message")
                            })
                        },
                        required = listOf("hour", "minute")
                    )
                },
                execute = {
                    val hour = it.jsonObject["hour"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                    val minute = it.jsonObject["minute"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                    val message = it.jsonObject["message"]?.jsonPrimitive?.contentOrNull ?: "Alarm"
                    
                    try {
                        val intent = android.content.Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
                            putExtra(android.provider.AlarmClock.EXTRA_HOUR, hour)
                            putExtra(android.provider.AlarmClock.EXTRA_MINUTES, minute)
                            putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, message)
                            putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, false)
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        buildJsonObject { 
                            put("status", "success")
                            put("time", "$hour:${minute.toString().padStart(2, '0')}")
                        }
                    } catch (e: Exception) {
                        buildJsonObject { put("status", "error: ${e.message}") }
                    }
                }
            ),
            Tool(
                name = "set_reminder",
                description = "Create a reminder/task",
                parameters = {
                    InputSchema.Obj(
                        properties = buildJsonObject {
                            put("title", buildJsonObject {
                                put("type", "string")
                                put("description", "Reminder title")
                            })
                            put("description", buildJsonObject {
                                put("type", "string")
                                put("description", "Reminder description")
                            })
                            put("time_millis", buildJsonObject {
                                put("type", "integer")
                                put("description", "Time in milliseconds since epoch (optional)")
                            })
                        },
                        required = listOf("title")
                    )
                },
                execute = {
                    val title = it.jsonObject["title"]?.jsonPrimitive?.contentOrNull ?: "Reminder"
                    val description = it.jsonObject["description"]?.jsonPrimitive?.contentOrNull ?: ""
                    val timeMillis = it.jsonObject["time_millis"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                    
                    try {
                        // Try to use Calendar/Tasks app
                        val intent = android.content.Intent(android.content.Intent.ACTION_INSERT).apply {
                            data = android.provider.CalendarContract.Events.CONTENT_URI
                            putExtra(android.provider.CalendarContract.Events.TITLE, title)
                            putExtra(android.provider.CalendarContract.Events.DESCRIPTION, description)
                            if (timeMillis != null) {
                                putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, timeMillis)
                                putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, timeMillis + 3600000) // 1 hour duration
                            }
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        buildJsonObject { put("status", "success") }
                    } catch (e: Exception) {
                        buildJsonObject { put("status", "error: ${e.message}") }
                    }
                }
            )
        )
    }
    
    /**
     * Get all enabled local tools for the conversation.
     * @param userImageUrls Image URLs from the most recent user message (for Python auto-import)
     */
    fun getTools(options: List<LocalToolOption>, assistantId: Uuid, conversationId: Uuid, userImageUrls: List<String> = emptyList()): List<Tool> {
        val tools = mutableListOf<Tool>()
        if (options.contains(LocalToolOption.JavascriptEngine)) {
            tools.add(javascriptTool)
        }
        if (options.contains(LocalToolOption.DeviceControl)) {
            tools.addAll(getDeviceControlTools(assistantId, conversationId))
        }
        // Find Python engine option if present - pass user images for auto-import
        if (options.contains(LocalToolOption.PythonEngine)) {
            tools.addAll(getPythonTools(conversationId, userImageUrls))
        }
        return tools
    }
}
