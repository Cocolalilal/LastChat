package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import com.whl.quickjs.wrapper.QuickJSContext
import com.whl.quickjs.wrapper.QuickJSObject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
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
    @SerialName("shell_command")
    data object ShellCommand : LocalToolOption()
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
                    
                    val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
                        .setSmallIcon(me.rerere.rikkahub.R.drawable.small_icon)
                        .setContentTitle(title)
                        .setContentText(content)
                        .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
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
                name = "set_brightness",
                description = "Set screen brightness (0-255)",
                parameters = {
                    InputSchema.Obj(
                        properties = buildJsonObject {
                            put("level", buildJsonObject {
                                put("type", "integer")
                                put("description", "Brightness level (0-255)")
                            })
                        },
                        required = listOf("level")
                    )
                },
                execute = {
                    val level = it.jsonObject["level"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 128
                    try {
                        if (android.provider.Settings.System.canWrite(context)) {
                            android.provider.Settings.System.putInt(
                                context.contentResolver,
                                android.provider.Settings.System.SCREEN_BRIGHTNESS,
                                level.coerceIn(0, 255)
                            )
                            buildJsonObject { put("status", "success") }
                        } else {
                            buildJsonObject { put("status", "error: permission denied") }
                        }
                    } catch (e: Exception) {
                        buildJsonObject { put("status", "error: ${e.message}") }
                    }
                }
            ),
            Tool(
                name = "set_volume",
                description = "Set volume level (0-15)",
                parameters = {
                    InputSchema.Obj(
                        properties = buildJsonObject {
                            put("level", buildJsonObject {
                                put("type", "integer")
                                put("description", "Volume level (0-15)")
                            })
                            put("stream", buildJsonObject {
                                put("type", "string")
                                put("enum", kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive("music"), JsonPrimitive("ring"), JsonPrimitive("notification"), JsonPrimitive("alarm"))))
                                put("description", "Stream type (music, ring, notification, alarm)")
                            })
                        },
                        required = listOf("level")
                    )
                },
                execute = {
                    val level = it.jsonObject["level"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 5
                    val streamStr = it.jsonObject["stream"]?.jsonPrimitive?.contentOrNull ?: "music"
                    val stream = when(streamStr) {
                        "ring" -> android.media.AudioManager.STREAM_RING
                        "notification" -> android.media.AudioManager.STREAM_NOTIFICATION
                        "alarm" -> android.media.AudioManager.STREAM_ALARM
                        else -> android.media.AudioManager.STREAM_MUSIC
                    }
                    
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                    try {
                        audioManager.setStreamVolume(stream, level.coerceIn(0, audioManager.getStreamMaxVolume(stream)), 0)
                        buildJsonObject { put("status", "success") }
                    } catch (e: Exception) {
                        buildJsonObject { put("status", "error: ${e.message}") }
                    }
                }
            ),
            Tool(
                name = "toggle_torch",
                description = "Turn flashlight on or off",
                parameters = {
                    InputSchema.Obj(
                        properties = buildJsonObject {
                            put("enabled", buildJsonObject {
                                put("type", "boolean")
                                put("description", "True to turn on, false to turn off")
                            })
                        },
                        required = listOf("enabled")
                    )
                },
                execute = {
                    val enabled = it.jsonObject["enabled"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false
                    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
                    try {
                        val cameraId = cameraManager.cameraIdList[0]
                        cameraManager.setTorchMode(cameraId, enabled)
                        buildJsonObject { put("status", "success") }
                    } catch (e: Exception) {
                        buildJsonObject { put("status", "error: ${e.message}") }
                    }
                }
            ),
            Tool(
                name = "set_dnd",
                description = "Set Do Not Disturb mode",
                parameters = {
                    InputSchema.Obj(
                        properties = buildJsonObject {
                            put("enabled", buildJsonObject {
                                put("type", "boolean")
                                put("description", "True to enable DND, false to disable")
                            })
                        },
                        required = listOf("enabled")
                    )
                },
                execute = {
                    val enabled = it.jsonObject["enabled"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                    try {
                        if (notificationManager.isNotificationPolicyAccessGranted) {
                            if (enabled) {
                                notificationManager.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_NONE)
                            } else {
                                notificationManager.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_ALL)
                            }
                            buildJsonObject { put("status", "success") }
                        } else {
                            buildJsonObject { put("status", "error: permission denied") }
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
                name = "get_installed_apps",
                description = "Get list of installed applications",
                parameters = {
                    InputSchema.Obj(properties = buildJsonObject {})
                },
                execute = {
                    val pm = context.packageManager
                    val apps = pm.getInstalledPackages(0)
                        .filter { app -> pm.getLaunchIntentForPackage(app.packageName) != null }
                        .map { app ->
                            buildJsonObject {
                                put("name", app.applicationInfo?.loadLabel(pm)?.toString() ?: app.packageName)
                                put("package", app.packageName)
                            }
                        }
                    buildJsonObject {
                        put("apps", kotlinx.serialization.json.JsonArray(apps))
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
            ),
            Tool(
                name = "read_clipboard",
                description = "Read text from clipboard",
                parameters = {
                    InputSchema.Obj(properties = buildJsonObject {})
                },
                execute = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    try {
                        val clipData = clipboard.primaryClip
                        if (clipData != null && clipData.itemCount > 0) {
                            val text = clipData.getItemAt(0).text?.toString() ?: ""
                            buildJsonObject { 
                                put("status", "success")
                                put("text", text)
                            }
                        } else {
                            buildJsonObject { 
                                put("status", "success")
                                put("text", "")
                            }
                        }
                    } catch (e: Exception) {
                        buildJsonObject { put("status", "error: ${e.message}") }
                    }
                }
            ),
            Tool(
                name = "control_music",
                description = "Control music playback (play, pause, skip_next, skip_previous)",
                parameters = {
                    InputSchema.Obj(
                        properties = buildJsonObject {
                            put("action", buildJsonObject {
                                put("type", "string")
                                put("enum", kotlinx.serialization.json.JsonArray(listOf(
                                    JsonPrimitive("play"),
                                    JsonPrimitive("pause"),
                                    JsonPrimitive("play_pause"),
                                    JsonPrimitive("skip_next"),
                                    JsonPrimitive("skip_previous")
                                )))
                                put("description", "Music control action")
                            })
                        },
                        required = listOf("action")
                    )
                },
                execute = {
                    val action = it.jsonObject["action"]?.jsonPrimitive?.contentOrNull ?: "play_pause"
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                    
                    try {
                        val keyCode = when(action) {
                            "play" -> android.view.KeyEvent.KEYCODE_MEDIA_PLAY
                            "pause" -> android.view.KeyEvent.KEYCODE_MEDIA_PAUSE
                            "play_pause" -> android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                            "skip_next" -> android.view.KeyEvent.KEYCODE_MEDIA_NEXT
                            "skip_previous" -> android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS
                            else -> android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                        }
                        
                        val downEvent = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode)
                        val upEvent = android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode)
                        audioManager.dispatchMediaKeyEvent(downEvent)
                        audioManager.dispatchMediaKeyEvent(upEvent)
                        
                        buildJsonObject { 
                            put("status", "success")
                            put("action", action)
                        }
                    } catch (e: Exception) {
                        buildJsonObject { put("status", "error: ${e.message}") }
                    }
                }
            ),
            Tool(
                name = "get_music_status",
                description = "Get current music playback status",
                parameters = {
                    InputSchema.Obj(properties = buildJsonObject {})
                },
                execute = {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                    try {
                        val isMusicActive = audioManager.isMusicActive
                        buildJsonObject { 
                            put("status", "success")
                            put("is_playing", isMusicActive)
                        }
                    } catch (e: Exception) {
                        buildJsonObject { put("status", "error: ${e.message}") }
                    }
                }
            )
        )
    }
    
    val shellTools by lazy {
        listOf(
            Tool(
                name = "run_shell_command",
                description = "Execute a shell command on the Android device. Use for advanced operations like listing files (ls), checking network (ping), or running system utilities. Commands run in app sandbox with limited privileges.",
                parameters = {
                    InputSchema.Obj(
                        properties = buildJsonObject {
                            put("command", buildJsonObject {
                                put("type", "string")
                                put("description", "Shell command to execute")
                            })
                        },
                        required = listOf("command")
                    )
                },
                execute = {
                    val command = it.jsonObject["command"]?.jsonPrimitive?.contentOrNull ?: ""
                    try {
                        val process = Runtime.getRuntime().exec(command)
                        val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
                        val errorReader = java.io.BufferedReader(java.io.InputStreamReader(process.errorStream))
                        
                        val output = StringBuilder()
                        reader.forEachLine { line -> output.append(line).append("\n") }
                        
                        val errors = StringBuilder()
                        errorReader.forEachLine { line -> errors.append(line).append("\n") }
                        
                        process.waitFor()
                        
                        val result = if (output.isNotEmpty()) output.toString() else errors.toString()
                        buildJsonObject {
                            put("output", result.ifBlank { "Command executed (no output)" })
                        }
                    } catch (e: Exception) {
                        buildJsonObject { put("error", e.message ?: "Unknown error") }
                    }
                }
            )
        )
    }

    fun getTools(options: List<LocalToolOption>, assistantId: Uuid, conversationId: Uuid): List<Tool> {
        val tools = mutableListOf<Tool>()
        if (options.contains(LocalToolOption.JavascriptEngine)) {
            tools.add(javascriptTool)
        }
        if (options.contains(LocalToolOption.DeviceControl)) {
            tools.addAll(getDeviceControlTools(assistantId, conversationId))
        }
        if (options.contains(LocalToolOption.ShellCommand)) {
            tools.addAll(shellTools)
        }
        return tools
    }
}
