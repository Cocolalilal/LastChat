package me.rerere.rikkahub.web

import android.content.Context
import androidx.core.net.toUri
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.utils.io.readAvailable
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.Writer
import java.net.URLConnection
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.rerere.ai.provider.ModelType
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantSearchMode
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.utils.JsonInstant

private const val MAX_UPLOAD_FILE_SIZE_BYTES = 20 * 1024 * 1024
private const val WEB_JWT_ISSUER = "lastchat-web"
private const val WEB_JWT_AUDIENCE = "lastchat-web-client"
private const val WEB_JWT_SUBJECT = "web-access"
private const val WEB_JWT_TTL_MILLIS = 30L * 24 * 60 * 60 * 1000
private const val WEB_ACCESS_TOKEN_QUERY_KEY = "access_token"
private const val WEB_AUTH_TOKEN_VERSION = "v1"
private const val WEB_AUTH_HMAC_ALGORITHM = "HmacSHA256"

fun Application.configureWebApi(
    context: Context,
    chatService: ChatService,
    conversationRepo: ConversationRepository,
    settingsStore: SettingsStore,
) {
    val jwtEnabled = settingsStore.settingsFlow.value.webServerJwtEnabled

    install(ContentNegotiation) {
        json(JsonInstant)
    }

    install(StatusPages) {
        exception<ApiException> { call, cause ->
            call.respond(cause.status, ErrorResponse(cause.message, cause.status.value))
        }
        exception<Throwable> { call, cause ->
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(cause.message ?: "Internal server error", HttpStatusCode.InternalServerError.value)
            )
        }
    }

    routing {
        get("/") {
            call.respondBuiltClientAsset(
                context = context,
                assetPath = "index.html",
                bootConfig = buildWebClientBootConfig(settingsStore.settingsFlow.value),
            )
        }

        get("/favicon.ico") {
            call.respondBuiltClientAsset(context, "favicon.ico")
        }

        get("/{assetName}") {
            val assetName = call.parameters["assetName"]
                ?: throw NotFoundException("Asset not found")
            if (!assetName.contains('.')) {
                throw NotFoundException("Asset not found")
            }
            validateRelativePath(assetName)
            call.respondBuiltClientAsset(context, assetName)
        }

        get("/assets/{path...}") {
            val relativePath = call.parameters.getAll("path")?.joinToString("/")
                ?: throw NotFoundException("Asset not found")
            validateRelativePath(relativePath)
            call.respondBuiltClientAsset(context, "assets/$relativePath")
        }

        route("/api") {
            post("/auth/token") {
                val settings = settingsStore.settingsFlow.value
                val request = call.receive<WebAuthTokenRequest>()
                call.respond(issueWebAuthToken(settings, request))
            }

            get("/bootstrap") {
                val settings = settingsStore.settingsFlow.value
                val generationJobs = chatService.getConversationJobs().first()
                val conversations = withContext(Dispatchers.IO) {
                    conversationRepo.getConversationsOfAssistant(settings.assistantId).first()
                }

                call.respond(
                    buildWebBootstrap(
                        settings = settings,
                        assistants = settings.assistants.map { it.toWebAssistantDto(context) },
                        conversations = conversations,
                        generationJobs = generationJobs,
                    )
                )
            }

            get("/ai-icon") {
                val name = call.request.queryParameters["name"]?.trim()
                    ?: throw BadRequestException("Missing name")
                if (name.isBlank()) {
                    throw BadRequestException("Missing name")
                }

                val assetPath = resolveAiIconAssetPath(name)
                if (assetPath != null) {
                    runCatching {
                        val bytes = withContext(Dispatchers.IO) {
                            context.assets.open("icons/$assetPath").use { it.readBytes() }
                        }
                        call.response.header(HttpHeaders.CacheControl, "public, max-age=86400")
                        call.respondBytes(bytes, contentType = guessAssetContentType(assetPath))
                    }.onSuccess {
                        return@get
                    }
                }

                call.response.header(HttpHeaders.CacheControl, "public, max-age=3600")
                call.respondText(
                    text = buildFallbackSvg(name),
                    contentType = ContentType.Image.SVG,
                )
            }

            webRoutes(
                context = context,
                chatService = chatService,
                conversationRepo = conversationRepo,
                settingsStore = settingsStore,
                requireAuth = jwtEnabled,
            )
        }

        get("/{path...}") {
            val segments = call.parameters.getAll("path").orEmpty()
            val rootSegment = segments.firstOrNull()
            if (rootSegment == "api" || rootSegment == "assets") {
                throw NotFoundException("Not Found")
            }
            call.respondBuiltClientAsset(
                context = context,
                assetPath = "index.html",
                bootConfig = buildWebClientBootConfig(settingsStore.settingsFlow.value),
            )
        }
    }
}

private fun Route.webRoutes(
    context: Context,
    chatService: ChatService,
    conversationRepo: ConversationRepository,
    settingsStore: SettingsStore,
    requireAuth: Boolean,
) {
    if (requireAuth) {
        install(createRouteScopedPlugin("LastChatWebAuth") {
            onCall { call ->
                if (call.isPublicWebApiPath()) {
                    return@onCall
                }

                val settings = settingsStore.settingsFlow.value
                val accessToken = extractAccessToken(
                    authorizationHeader = call.request.headers[HttpHeaders.Authorization],
                    queryToken = call.request.queryParameters[WEB_ACCESS_TOKEN_QUERY_KEY],
                )
                val status = validateWebAccessToken(settings, accessToken)
                if (status != null) {
                    when (status) {
                        HttpStatusCode.Forbidden -> throw ForbiddenException("Access password is not configured")
                        else -> throw UnauthorizedException("Unauthorized")
                    }
                }
            }
        })
    }

    route("/conversations") {
        post {
            val settings = settingsStore.settingsFlow.value
            val request = call.receive<CreateConversationRequest>()
            val response = createWebConversationResponse(settings, request) { assistantId ->
                chatService.createConversation(assistantId)
            }
            call.respond(HttpStatusCode.Created, response)
        }

        get {
            val settings = settingsStore.settingsFlow.value
            val generationJobs = chatService.getConversationJobs().first()
            val conversations = withContext(Dispatchers.IO) {
                conversationRepo.getConversationsOfAssistant(settings.assistantId).first()
            }.sortedForWeb()

            call.respond(
                conversations.map { conversation ->
                    conversation.toListDto(isGenerating = generationJobs[conversation.id] != null)
                }
            )
        }

        get("/paged") {
            val settings = settingsStore.settingsFlow.value
            val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
            val query = call.request.queryParameters["query"]?.trim().orEmpty()

            if (offset < 0) {
                throw BadRequestException("offset must be >= 0")
            }
            if (limit !in 1..100) {
                throw BadRequestException("limit must be in 1..100")
            }

            val generationJobs = chatService.getConversationJobs().first()
            val conversations = withContext(Dispatchers.IO) {
                conversationRepo.getConversationsOfAssistant(settings.assistantId).first()
            }
                .asSequence()
                .filter { conversation ->
                    query.isBlank() || conversation.title.contains(query, ignoreCase = true)
                }
                .sortedWith(compareByDescending<Conversation> { it.isPinned }.thenByDescending { it.updateAt })
                .toList()

            val items = conversations.drop(offset).take(limit)
            val nextOffset = (offset + items.size).takeIf { it < conversations.size }

            call.respond(
                PagedResult(
                    items = items.map { conversation ->
                        conversation.toListDto(isGenerating = generationJobs[conversation.id] != null)
                    },
                    nextOffset = nextOffset,
                )
            )
        }

        get("/search") {
            val settings = settingsStore.settingsFlow.value
            val query = call.request.queryParameters["query"]?.trim().orEmpty()
            if (query.isBlank()) {
                call.respond(emptyList<MessageSearchResultDto>())
                return@get
            }

            val results = withContext(Dispatchers.IO) {
                conversationRepo.getConversationsOfAssistant(settings.assistantId).first()
            }.flatMap { conversation ->
                conversation.safeSelectedMessages()
                    .mapNotNull { message ->
                        val searchableText = message.toSearchableText().trim()
                        val snippet = searchableText.highlightSnippet(query) ?: return@mapNotNull null
                        MessageSearchResultDto(
                            nodeId = conversation.getMessageNodeByMessageId(message.id)?.id?.toString().orEmpty(),
                            messageId = message.id.toString(),
                            conversationId = conversation.id.toString(),
                            title = conversation.title.ifBlank { "New chat" },
                            updateAt = conversation.updateAt.toEpochMilli(),
                            snippet = snippet,
                        )
                    }
            }
                .sortedByDescending { it.updateAt }
                .take(100)

            call.respond(results)
        }

        get("/stream") {
            call.respondSse(heartbeatMillis = 15_000L) {
                settingsStore.settingsFlow
                    .map { it.assistantId }
                    .distinctUntilChanged()
                    .collectLatest { assistantId ->
                        combine(
                            conversationRepo.getConversationsOfAssistant(assistantId),
                            chatService.getConversationJobs(),
                        ) { conversations, generationJobs ->
                            conversations.map { conversation ->
                                Triple(
                                    conversation.id,
                                    conversation.updateAt.toEpochMilli(),
                                    generationJobs[conversation.id] != null,
                                )
                            }
                        }.distinctUntilChanged().collect {
                            send(
                                event = "invalidate",
                                data = JsonInstant.encodeToString(
                                    ConversationListInvalidateEvent(
                                        assistantId = assistantId.toString(),
                                        timestamp = System.currentTimeMillis(),
                                    )
                                ),
                            )
                        }
                    }
            }
        }

        get("/{id}") {
            val conversationId = call.parameters["id"].toUuid("conversation id")
            val conversation = withContext(Dispatchers.IO) {
                conversationRepo.getConversationById(conversationId)
            } ?: throw NotFoundException("Conversation not found")
            val settings = settingsStore.settingsFlow.value
            val isGenerating = chatService.isGenerating(conversationId)

            call.respond(conversation.toDto(settings, context, isGenerating))
        }

        delete("/{id}") {
            val conversationId = call.parameters["id"].toUuid("conversation id")
            val conversation = withContext(Dispatchers.IO) {
                conversationRepo.getConversationById(conversationId)
            } ?: throw NotFoundException("Conversation not found")

            withContext(Dispatchers.IO) {
                conversationRepo.deleteConversation(conversation)
            }
            call.respond(HttpStatusCode.NoContent)
        }

        post("/{id}/pin") {
            val conversationId = call.parameters["id"].toUuid("conversation id")
            val conversation = withContext(Dispatchers.IO) {
                conversationRepo.getConversationById(conversationId)
            } ?: throw NotFoundException("Conversation not found")

            chatService.saveConversation(
                conversationId,
                conversation.copy(isPinned = !conversation.isPinned, updateAt = Instant.now()),
            )
            call.respond(HttpStatusCode.OK, mapOf("status" to "updated"))
        }

        post("/{id}/regenerate-title") {
            val conversationId = call.parameters["id"].toUuid("conversation id")
            val conversation = withContext(Dispatchers.IO) {
                conversationRepo.getConversationById(conversationId)
            } ?: throw NotFoundException("Conversation not found")

            chatService.generateTitle(conversationId, conversation, force = true)
            call.respond(HttpStatusCode.Accepted, mapOf("status" to "accepted"))
        }

        post("/{id}/title") {
            val conversationId = call.parameters["id"].toUuid("conversation id")
            val request = call.receive<UpdateConversationTitleRequest>()
            val nextTitle = request.title.trim()
            if (nextTitle.isBlank()) {
                throw BadRequestException("Title must not be blank")
            }

            val conversation = withContext(Dispatchers.IO) {
                conversationRepo.getConversationById(conversationId)
            } ?: throw NotFoundException("Conversation not found")

            chatService.saveConversation(
                conversationId = conversationId,
                conversation = conversation.copy(title = nextTitle, updateAt = Instant.now()),
                preserveConsolidation = true,
            )
            call.respond(HttpStatusCode.OK, mapOf("status" to "updated"))
        }

        post("/{id}/move") {
            val conversationId = call.parameters["id"].toUuid("conversation id")
            val request = call.receive<MoveConversationRequest>()
            val assistantId = request.assistantId.toUuid("assistant id")
            val settings = settingsStore.settingsFlow.value
            if (settings.assistants.none { it.id == assistantId }) {
                throw BadRequestException("Assistant not found")
            }

            val conversation = withContext(Dispatchers.IO) {
                conversationRepo.getConversationById(conversationId)
            } ?: throw NotFoundException("Conversation not found")

            chatService.saveConversation(
                conversationId,
                conversation.copy(assistantId = assistantId, updateAt = Instant.now()),
            )
            call.respond(HttpStatusCode.OK, mapOf("status" to "updated"))
        }

        post("/{id}/skills") {
            val conversationId = call.parameters["id"].toUuid("conversation id")
            val request = call.receive<UpdateConversationSkillsRequest>()
            val settings = settingsStore.settingsFlow.value
            val validSkillIds = settings.skills.map { it.id }.toSet()
            val requestedSkillIds = request.skillIds.map { it.toUuid("skill id") }.toSet()
            if (!validSkillIds.containsAll(requestedSkillIds)) {
                throw BadRequestException("skillIds contains unknown skill id")
            }

            val conversation = withContext(Dispatchers.IO) {
                conversationRepo.getConversationById(conversationId)
            } ?: throw NotFoundException("Conversation not found")

            chatService.saveConversation(
                conversationId,
                conversation.copy(enabledModeIds = requestedSkillIds, updateAt = Instant.now()),
            )
            call.respond(HttpStatusCode.OK, mapOf("status" to "updated"))
        }

        post("/{id}/messages") {
            val conversationId = call.parameters["id"].toUuid("conversation id")
            val request = call.receive<SendMessageRequest>()

            chatService.initializeConversation(conversationId)
            chatService.sendMessage(
                conversationId = conversationId,
                content = request.parts.toUiMessageParts(),
                answer = true,
                suppressCompletionNotification = true,
            )

            call.respond(HttpStatusCode.Accepted, mapOf("status" to "accepted"))
        }

        post("/{id}/messages/{messageId}/edit") {
            val conversationId = call.parameters["id"].toUuid("conversation id")
            val messageId = call.parameters["messageId"].toUuid("message id")
            val request = call.receive<EditMessageRequest>()

            chatService.editMessage(conversationId, messageId, request.parts.toUiMessageParts())
            call.respond(HttpStatusCode.Accepted, mapOf("status" to "accepted"))
        }

        post("/{id}/fork") {
            val conversationId = call.parameters["id"].toUuid("conversation id")
            val request = call.receive<ForkConversationRequest>()
            val messageId = request.messageId.toUuid("message id")

            val fork = chatService.forkConversationAtMessage(conversationId, messageId)
            call.respond(
                HttpStatusCode.Created,
                ForkConversationResponse(conversationId = fork.id.toString()),
            )
        }

        delete("/{id}/messages/{messageId}") {
            val conversationId = call.parameters["id"].toUuid("conversation id")
            val messageId = call.parameters["messageId"].toUuid("message id")

            chatService.deleteMessage(conversationId, messageId)
            call.respond(HttpStatusCode.OK, mapOf("status" to "deleted"))
        }

        post("/{id}/nodes/{nodeId}/select") {
            val conversationId = call.parameters["id"].toUuid("conversation id")
            val nodeId = call.parameters["nodeId"].toUuid("node id")
            val request = call.receive<SelectMessageNodeRequest>()

            chatService.selectMessageNode(conversationId, nodeId, request.selectIndex)
            call.respond(HttpStatusCode.Accepted, mapOf("status" to "accepted"))
        }

        post("/{id}/regenerate") {
            val conversationId = call.parameters["id"].toUuid("conversation id")
            val request = call.receive<RegenerateRequest>()
            val messageId = request.messageId.toUuid("message id")
            val conversation = chatService.ensureConversationLoaded(conversationId)
                ?: throw NotFoundException("Conversation not found")
            val message = conversation.messageNodes
                .flatMap { it.messages }
                .firstOrNull { it.id == messageId }
                ?: throw NotFoundException("Message not found")

            chatService.regenerateAtMessage(
                conversationId = conversationId,
                message = message,
                suppressCompletionNotification = true,
            )
            call.respond(HttpStatusCode.Accepted, mapOf("status" to "accepted"))
        }

        post("/{id}/stop") {
            val conversationId = call.parameters["id"].toUuid("conversation id")
            chatService.stopGeneration(conversationId)
            call.respond(HttpStatusCode.OK, mapOf("status" to "stopped"))
        }

        post("/{id}/tool-approval") {
            val conversationId = call.parameters["id"].toUuid("conversation id")
            val request = call.receive<ToolApprovalRequest>()

            chatService.handleToolApproval(
                conversationId = conversationId,
                toolCallId = request.toolCallId,
                approved = request.approved,
                reason = request.reason,
                answer = request.answer,
            )
            call.respond(HttpStatusCode.Accepted, mapOf("status" to "accepted"))
        }

        get("/{id}/stream") {
            val conversationId = call.parameters["id"].toUuid("conversation id")
            chatService.initializeConversation(conversationId)

            call.respondSse(heartbeatMillis = 1_000L) {
                chatService.addConversationReference(conversationId)
                try {
                    var sequence = 0L
                    var previousDto: ConversationDto? = null

                    combine(
                        chatService.getConversationFlow(conversationId),
                        chatService.getGenerationJobStateFlow(conversationId).map { it != null }.distinctUntilChanged(),
                    ) { conversation, isGenerating ->
                        val settings = settingsStore.settingsFlow.value
                        conversation.toDto(settings, context, isGenerating)
                    }.collect { conversationDto ->
                        sequence += 1
                        val nodeDiff = previousDto?.singleNodeDiffOrNull(conversationDto)
                        if (nodeDiff != null) {
                            send(
                                event = "node_update",
                                id = sequence.toString(),
                                data = JsonInstant.encodeToString(
                                    ConversationNodeUpdateEvent(
                                        seq = sequence,
                                        conversationId = conversationDto.id,
                                        nodeId = nodeDiff.node.id,
                                        nodeIndex = nodeDiff.nodeIndex,
                                        node = nodeDiff.node,
                                        updateAt = conversationDto.updateAt,
                                        isGenerating = conversationDto.isGenerating,
                                    )
                                ),
                            )
                        } else {
                            send(
                                event = "snapshot",
                                id = sequence.toString(),
                                data = JsonInstant.encodeToString(
                                    ConversationSnapshotEvent(
                                        seq = sequence,
                                        conversation = conversationDto,
                                    )
                                ),
                            )
                        }
                        previousDto = conversationDto
                    }
                } finally {
                    chatService.removeConversationReference(conversationId)
                }
            }
        }
    }

    route("/settings") {
        post("/assistant") {
            val request = call.receive<UpdateAssistantRequest>()
            val assistantId = request.assistantId.toUuid("assistant id")
            val settings = settingsStore.settingsFlow.value
            if (settings.assistants.none { it.id == assistantId }) {
                throw NotFoundException("Assistant not found")
            }

            settingsStore.updateAssistant(assistantId)
            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
        }

        post("/assistant/model") {
            val request = call.receive<UpdateAssistantModelRequest>()
            val assistantId = request.assistantId.toUuid("assistant id")
            val modelId = request.modelId.toUuid("model id")
            val settings = settingsStore.settingsFlow.value
            if (settings.assistants.none { it.id == assistantId }) {
                throw NotFoundException("Assistant not found")
            }

            val model = settings.findModelById(modelId) ?: throw NotFoundException("Model not found")
            if (model.type != ModelType.CHAT) {
                throw BadRequestException("modelId must be a chat model")
            }

            settingsStore.update { current ->
                current.copy(
                    assistants = current.assistants.replaceAssistant(assistantId) { assistant ->
                        assistant.copy(chatModelId = modelId)
                    }
                )
            }
            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
        }

        post("/assistant/thinking-budget") {
            val request = call.receive<UpdateAssistantThinkingBudgetRequest>()
            val assistantId = request.assistantId.toUuid("assistant id")
            val settings = settingsStore.settingsFlow.value
            if (settings.assistants.none { it.id == assistantId }) {
                throw NotFoundException("Assistant not found")
            }

            settingsStore.update { current ->
                current.copy(
                    assistants = current.assistants.replaceAssistant(assistantId) { assistant ->
                        assistant.copy(thinkingBudget = request.thinkingBudget)
                    }
                )
            }
            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
        }

        post("/assistant/mcp") {
            val request = call.receive<UpdateAssistantMcpServersRequest>()
            val assistantId = request.assistantId.toUuid("assistant id")
            val settings = settingsStore.settingsFlow.value
            if (settings.assistants.none { it.id == assistantId }) {
                throw NotFoundException("Assistant not found")
            }

            val validServerIds = settings.mcpServers.map { it.id }.toSet()
            val requestedIds = request.mcpServerIds.map { it.toUuid("mcp server id") }.toSet()
            if (!validServerIds.containsAll(requestedIds)) {
                throw BadRequestException("mcpServerIds contains unknown server id")
            }

            settingsStore.update { current ->
                current.copy(
                    assistants = current.assistants.replaceAssistant(assistantId) { assistant ->
                        assistant.copy(mcpServers = requestedIds)
                    }
                )
            }
            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
        }

        post("/assistant/injections") {
            val request = call.receive<UpdateAssistantInjectionsRequest>()
            val assistantId = request.assistantId.toUuid("assistant id")
            val settings = settingsStore.settingsFlow.value
            if (settings.assistants.none { it.id == assistantId }) {
                throw NotFoundException("Assistant not found")
            }

            val validModeIds = settings.skills.map { it.id }.toSet()
            val requestedModeIds = request.modeInjectionIds.map { it.toUuid("mode injection id") }.toSet()
            if (!validModeIds.containsAll(requestedModeIds)) {
                throw BadRequestException("modeInjectionIds contains unknown injection id")
            }

            val validLorebookIds = settings.lorebooks.map { it.id }.toSet()
            val requestedLorebookIds = request.lorebookIds.map { it.toUuid("lorebook id") }.toSet()
            if (!validLorebookIds.containsAll(requestedLorebookIds)) {
                throw BadRequestException("lorebookIds contains unknown lorebook id")
            }

            settingsStore.update { current ->
                current.copy(
                    assistants = current.assistants.replaceAssistant(assistantId) { assistant ->
                        assistant.copy(
                            enabledSkillIds = requestedModeIds,
                            enabledLorebookIds = requestedLorebookIds,
                        )
                    }
                )
            }
            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
        }

        post("/search/enabled") {
            val request = call.receive<UpdateSearchEnabledRequest>()
            settingsStore.update { current ->
                val assistant = current.getCurrentAssistant()
                val nextSearchMode = if (request.enabled) {
                    if (current.searchServices.isEmpty()) {
                        throw BadRequestException("No search services configured")
                    }
                    AssistantSearchMode.Provider(
                        current.searchServiceSelected.coerceIn(0, current.searchServices.lastIndex)
                    )
                } else {
                    AssistantSearchMode.Off
                }

                current.copy(
                    enableWebSearch = request.enabled,
                    assistants = current.assistants.replaceAssistant(assistant.id) {
                        it.copy(searchMode = nextSearchMode)
                    },
                )
            }
            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
        }

        post("/search/service") {
            val request = call.receive<UpdateSearchServiceRequest>()
            settingsStore.update { current ->
                if (current.searchServices.isEmpty()) {
                    throw BadRequestException("No search services configured")
                }
                if (request.index !in current.searchServices.indices) {
                    throw BadRequestException("search service index out of range")
                }

                val assistant = current.getCurrentAssistant()
                current.copy(
                    enableWebSearch = true,
                    searchServiceSelected = request.index,
                    assistants = current.assistants.replaceAssistant(assistant.id) {
                        it.copy(searchMode = AssistantSearchMode.Provider(request.index))
                    },
                )
            }
            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
        }

        post("/model/built-in-tool") {
            val request = call.receive<UpdateBuiltInToolRequest>()
            val modelId = request.modelId.toUuid("model id")
            if (request.tool.trim().lowercase(Locale.ROOT) != "search") {
                throw BadRequestException("Unsupported built-in tool")
            }

            settingsStore.update { current ->
                val model = current.findModelById(modelId) ?: throw NotFoundException("Model not found")
                if (model.type != ModelType.CHAT) {
                    throw BadRequestException("modelId must be a chat model")
                }

                val assistant = current.getCurrentAssistant()
                current.copy(
                    assistants = current.assistants.replaceAssistant(assistant.id) {
                        it.copy(
                            preferBuiltInSearch = request.enabled,
                            searchMode = if (!request.enabled && it.searchMode is AssistantSearchMode.BuiltIn) {
                                AssistantSearchMode.Off
                            } else {
                                it.searchMode
                            },
                        )
                    }
                )
            }
            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
        }

        post("/favorite-models") {
            val request = call.receive<UpdateFavoriteModelsRequest>()
            val favoriteModelIds = request.modelIds.map { it.toUuid("model id") }
            val settings = settingsStore.settingsFlow.value
            val knownModelIds = settings.providers.flatMap { provider -> provider.models.map { it.id } }.toSet()
            if (!knownModelIds.containsAll(favoriteModelIds)) {
                throw BadRequestException("modelIds contains unknown model id")
            }

            settingsStore.update { current ->
                current.copy(favoriteModels = favoriteModelIds)
            }
            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
        }

        get("/stream") {
            call.respondSse(heartbeatMillis = 15_000L) {
                settingsStore.settingsFlow.collect { settings ->
                    send(
                        event = "update",
                        data = JsonInstant.encodeToString(settings.toWebSettingsDto(context)),
                    )
                }
            }
        }
    }

    route("/files") {
        post("/upload") {
            val multipart = call.receiveMultipart()
            val uploadedFiles = mutableListOf<UploadedFileDto>()

            while (true) {
                val part = multipart.readPart() ?: break
                try {
                    if (part is PartData.FileItem) {
                        val originalFileName = part.originalFileName?.takeIf { it.isNotBlank() } ?: "file"
                        val mimeType = part.contentType?.toString()?.takeIf { it.isNotBlank() }
                            ?: "application/octet-stream"
                        val bytes = readPartBytes(part, MAX_UPLOAD_FILE_SIZE_BYTES)
                        if (bytes.isEmpty()) {
                            throw BadRequestException("Uploaded file is empty")
                        }

                        val record = WebUploadRegistry.saveUpload(
                            context = context,
                            originalFileName = originalFileName,
                            mimeType = mimeType,
                            bytes = bytes,
                        )
                        uploadedFiles += UploadedFileDto(
                            id = record.id,
                            url = record.uri,
                            fileName = record.fileName,
                            mime = record.mime,
                            size = record.size,
                        )
                    }
                } finally {
                    part.dispose()
                }
            }

            if (uploadedFiles.isEmpty()) {
                throw BadRequestException("No files uploaded")
            }

            call.respond(HttpStatusCode.Created, UploadFilesResponseDto(files = uploadedFiles))
        }

        delete("/{id}") {
            val id = call.parameters["id"]?.toLongOrNull()
                ?: throw BadRequestException("Invalid file id")
            val deleted = WebUploadRegistry.delete(context, id)
            if (!deleted) {
                throw NotFoundException("File not found")
            }
            call.respond(HttpStatusCode.OK, mapOf("status" to "deleted"))
        }

        get("/path/{path...}") {
            val relativePath = call.parameters.getAll("path")?.joinToString("/")
                ?: throw BadRequestException("Missing file path")
            validateRelativePath(relativePath)

            val file = withContext(Dispatchers.IO) {
                context.filesDir.resolve(relativePath).canonicalFile
            }
            val filesDir = withContext(Dispatchers.IO) { context.filesDir.canonicalFile }
            if (!file.path.startsWith(filesDir.path + File.separator) && file.path != filesDir.path) {
                throw BadRequestException("Invalid file path")
            }
            if (!file.exists() || !file.isFile) {
                throw NotFoundException("File not found")
            }

            val mime = WebUploadRegistry.getByRelativePath(relativePath)?.mime
                ?: URLConnection.guessContentTypeFromName(file.name)
                ?: "application/octet-stream"
            call.response.header(HttpHeaders.ContentType, mime)
            call.respondOutputStream(contentType = ContentType.parse(mime)) {
                withContext(Dispatchers.IO) {
                    file.inputStream().use { input -> input.copyTo(this@respondOutputStream) }
                }
            }
        }

        get("/content") {
            val uriValue = call.request.queryParameters["uri"]
                ?: throw BadRequestException("Missing uri")
            val uri = runCatching { uriValue.toUri() }.getOrNull()
                ?: throw BadRequestException("Invalid uri")
            val media = openAllowedWebMedia(context, uri)
                ?: throw NotFoundException("File not found")
            val mimeOverride = call.request.queryParameters["mime"]?.trim().orEmpty()
            val fileName = call.request.queryParameters["name"]?.trim().takeUnless { it.isNullOrBlank() }
                ?: media.fileName
            val contentType = mimeOverride.takeIf { it.isNotBlank() }?.let(ContentType::parse) ?: media.contentType

            media.use { content ->
                fileName?.let { name ->
                    call.response.header(
                        HttpHeaders.ContentDisposition,
                        ContentDisposition.Inline.withParameter(ContentDisposition.Parameters.FileName, name).toString()
                    )
                }
                call.response.header(HttpHeaders.CacheControl, "public, max-age=86400")
                call.respondOutputStream(contentType = contentType) {
                    withContext(Dispatchers.IO) {
                        content.inputStream.copyTo(this@respondOutputStream)
                    }
                }
            }
        }
    }
}

private fun ApplicationCall.isPublicWebApiPath(): Boolean {
    return isPublicWebApiPath(request.path())
}

internal fun isPublicWebApiPath(path: String): Boolean {
    return path == "/api/auth/token" ||
        path == "/api/bootstrap" ||
        path == "/api/ai-icon"
}

private suspend fun ApplicationCall.respondBuiltClientAsset(
    context: Context,
    assetPath: String,
    bootConfig: WebClientBootConfig? = null,
) {
    val bytes = withContext(Dispatchers.IO) {
        runCatching {
            context.assets.open(assetPath).use { it.readBytes() }
        }.getOrElse {
            throw NotFoundException("Asset not found")
        }
    }

    if (assetPath == "index.html") {
        val html = bytes.toString(Charsets.UTF_8)
        val content = bootConfig?.let { injectWebBootConfig(html, it) } ?: html
        respondText(content, contentType = guessAssetContentType(assetPath))
        return
    }

    if (assetPath.startsWith("assets/")) {
        response.header(HttpHeaders.CacheControl, "public, max-age=31536000, immutable")
    }
    respondBytes(bytes, contentType = guessAssetContentType(assetPath))
}

private suspend fun ApplicationCall.respondSse(
    heartbeatMillis: Long,
    block: suspend SseWriter.() -> Unit,
) {
    response.header(HttpHeaders.CacheControl, "no-cache")
    response.header(HttpHeaders.Connection, "keep-alive")
    respondTextWriter(contentType = ContentType.Text.EventStream, status = HttpStatusCode.OK) {
        coroutineScope {
            val sseWriter = SseWriter(this@respondTextWriter)
            val heartbeatJob = launch {
                while (true) {
                    delay(heartbeatMillis)
                    sseWriter.comment("heartbeat")
                }
            }
            try {
                sseWriter.block()
            } finally {
                heartbeatJob.cancel()
            }
        }
    }
}

private class SseWriter(
    private val writer: Writer,
) {
    private val mutex = Mutex()

    suspend fun send(event: String, data: String, id: String? = null) {
        mutex.withLock {
            if (!id.isNullOrBlank()) {
                writer.write("id: $id\n")
            }
            writer.write("event: $event\n")
            data.lineSequence().forEach { line ->
                writer.write("data: $line\n")
            }
            writer.write("\n")
            writer.flush()
        }
    }

    suspend fun comment(comment: String) {
        mutex.withLock {
            writer.write(": $comment\n\n")
            writer.flush()
        }
    }
}

private data class NodeDiff(
    val nodeIndex: Int,
    val node: MessageNodeDto,
)

private fun ConversationDto.singleNodeDiffOrNull(current: ConversationDto): NodeDiff? {
    if (id != current.id || assistantId != current.assistantId || createAt != current.createAt) {
        return null
    }
    if (
        title != current.title ||
        isPinned != current.isPinned ||
        enabledSkillIds != current.enabledSkillIds ||
        truncateIndex != current.truncateIndex ||
        chatSuggestions != current.chatSuggestions
    ) {
        return null
    }
    if (messages.size > current.messages.size) {
        return null
    }

    var changedIndex = -1
    val maxSize = maxOf(messages.size, current.messages.size)
    for (index in 0 until maxSize) {
        val previousNode = messages.getOrNull(index)
        val currentNode = current.messages.getOrNull(index)
        if (previousNode == currentNode) continue
        if (changedIndex != -1) {
            return null
        }
        changedIndex = index
    }

    if (changedIndex == -1) {
        return null
    }

    val changedNode = current.messages.getOrNull(changedIndex) ?: return null
    return NodeDiff(nodeIndex = changedIndex, node = changedNode)
}

private fun validateRelativePath(relativePath: String) {
    if (relativePath.contains("..") || relativePath.startsWith("/")) {
        throw BadRequestException("Invalid path")
    }
}

private fun guessAssetContentType(path: String): ContentType {
    return when (path.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
        "css" -> ContentType.Text.CSS
        "html" -> ContentType.Text.Html
        "ico" -> ContentType.parse("image/x-icon")
        "js", "mjs" -> ContentType.Application.JavaScript
        "json" -> ContentType.Application.Json
        "map" -> ContentType.Application.Json
        "png" -> ContentType.Image.PNG
        "svg" -> ContentType.Image.SVG
        "txt" -> ContentType.Text.Plain
        "webp" -> ContentType("image", "webp")
        "woff" -> ContentType("font", "woff")
        "woff2" -> ContentType("font", "woff2")
        else -> ContentType.Application.OctetStream
    }
}

private suspend fun readPartBytes(part: PartData.FileItem, maxBytes: Int): ByteArray {
    val input = part.provider()
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var totalBytes = 0

    while (true) {
        val read = input.readAvailable(buffer, 0, buffer.size)
        if (read <= 0) break
        totalBytes += read
        if (totalBytes > maxBytes) {
            throw BadRequestException("File too large: max ${maxBytes / (1024 * 1024)} MB")
        }
        output.write(buffer, 0, read)
    }

    return output.toByteArray()
}

private fun List<Conversation>.sortedForWeb(): List<Conversation> {
    return sortedWith(compareByDescending<Conversation> { it.isPinned }.thenByDescending { it.updateAt })
}

private fun Conversation.safeSelectedMessages(): List<UIMessage> {
    return messageNodes.mapNotNull { node ->
        node.messages.getOrNull(node.selectIndex) ?: node.messages.firstOrNull()
    }
}

private fun UIMessage.toSearchableText(): String {
    return parts.joinToString("\n") { part ->
        when (part) {
            is UIMessagePart.Text -> part.text
            is UIMessagePart.Reasoning -> ""
            is UIMessagePart.Thinking -> ""
            is UIMessagePart.Document -> listOf(part.fileName, part.mime).joinToString(" ")
            is UIMessagePart.ToolCall -> ""
            is UIMessagePart.ToolResult -> ""
            is UIMessagePart.Image -> part.url
            is UIMessagePart.Video -> part.url
            is UIMessagePart.Audio -> part.url
            UIMessagePart.Search -> ""
        }
    }
}

private fun String.highlightSnippet(query: String, radius: Int = 56): String? {
    if (isBlank()) return null

    val source = replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
    if (source.isBlank()) return null

    val lowerSource = source.lowercase(Locale.ROOT)
    val lowerQuery = query.lowercase(Locale.ROOT)
    val matchIndex = lowerSource.indexOf(lowerQuery)
    if (matchIndex == -1) return null

    val start = (matchIndex - radius).coerceAtLeast(0)
    val end = (matchIndex + query.length + radius).coerceAtMost(source.length)
    val prefix = if (start > 0) "..." else ""
    val suffix = if (end < source.length) "..." else ""
    val before = source.substring(start, matchIndex)
    val match = source.substring(matchIndex, matchIndex + query.length)
    val after = source.substring(matchIndex + query.length, end)

    return prefix + before + "[" + match + "]" + after + suffix
}

private fun List<Assistant>.replaceAssistant(
    assistantId: Uuid,
    transform: (Assistant) -> Assistant,
): List<Assistant> {
    var found = false
    val updated = map { assistant ->
        if (assistant.id == assistantId) {
            found = true
            transform(assistant)
        } else {
            assistant
        }
    }
    if (!found) {
        throw NotFoundException("Assistant not found")
    }
    return updated
}

private fun String?.toUuid(name: String): Uuid {
    if (this == null) {
        throw BadRequestException("Missing $name")
    }
    return runCatching { Uuid.parse(this) }.getOrElse {
        throw BadRequestException("Invalid $name")
    }
}

private fun createWebJwt(secret: String): Pair<String, Long> {
    val now = System.currentTimeMillis()
    val expiresAt = now + WEB_JWT_TTL_MILLIS
    val payload = listOf(
        WEB_AUTH_TOKEN_VERSION,
        WEB_JWT_ISSUER,
        WEB_JWT_AUDIENCE,
        WEB_JWT_SUBJECT,
        expiresAt.toString(),
    ).joinToString(":")
    val encodedPayload = base64UrlEncode(payload.toByteArray(Charsets.UTF_8))
    val signature = base64UrlEncode(hmacSha256(secret, encodedPayload))
    val token = "$encodedPayload.$signature"
    return token to expiresAt
}

internal fun validateWebAccessToken(settings: Settings, token: String?): HttpStatusCode? {
    if (settings.webServerAccessPassword.isBlank()) {
        return HttpStatusCode.Forbidden
    }

    if (token.isNullOrBlank()) {
        return HttpStatusCode.Unauthorized
    }

    val parts = token.split('.')
    if (parts.size != 2) {
        return HttpStatusCode.Unauthorized
    }

    val payload = runCatching {
        String(Base64.getUrlDecoder().decode(parts[0]), Charsets.UTF_8)
    }.getOrNull() ?: return HttpStatusCode.Unauthorized

    val payloadParts = payload.split(':')
    if (payloadParts.size != 5) {
        return HttpStatusCode.Unauthorized
    }

    val (version, issuer, audience, subject, expiresAtText) = payloadParts
    if (
        version != WEB_AUTH_TOKEN_VERSION ||
        issuer != WEB_JWT_ISSUER ||
        audience != WEB_JWT_AUDIENCE ||
        subject != WEB_JWT_SUBJECT
    ) {
        return HttpStatusCode.Unauthorized
    }

    val expiresAt = expiresAtText.toLongOrNull() ?: return HttpStatusCode.Unauthorized
    if (System.currentTimeMillis() >= expiresAt) {
        return HttpStatusCode.Unauthorized
    }

    val expectedSignature = hmacSha256(settings.webServerAccessPassword, parts[0])
    val actualSignature = runCatching {
        Base64.getUrlDecoder().decode(parts[1])
    }.getOrNull() ?: return HttpStatusCode.Unauthorized

    return if (MessageDigest.isEqual(expectedSignature, actualSignature)) {
        null
    } else {
        HttpStatusCode.Unauthorized
    }
}

private fun hmacSha256(secret: String, value: String): ByteArray {
    val mac = Mac.getInstance(WEB_AUTH_HMAC_ALGORITHM)
    val key = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), WEB_AUTH_HMAC_ALGORITHM)
    mac.init(key)
    return mac.doFinal(value.toByteArray(Charsets.UTF_8))
}

private fun base64UrlEncode(bytes: ByteArray): String {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

private fun extractBearerToken(authorizationHeader: String?): String? {
    if (authorizationHeader.isNullOrBlank()) return null
    val prefix = "Bearer "
    if (!authorizationHeader.startsWith(prefix, ignoreCase = true)) return null
    return authorizationHeader.substring(prefix.length).trim().takeIf { it.isNotEmpty() }
}

private fun extractAccessToken(authorizationHeader: String?, queryToken: String?): String? {
    return extractBearerToken(authorizationHeader)
        ?: queryToken?.trim()?.takeIf { it.isNotEmpty() }
}

private fun secureEquals(left: String, right: String): Boolean {
    return MessageDigest.isEqual(left.toByteArray(Charsets.UTF_8), right.toByteArray(Charsets.UTF_8))
}

private fun resolveAiIconAssetPath(name: String): String? {
    val lowerName = name.lowercase(Locale.ROOT)
    return when {
        "bing" in lowerName -> "bing.svg"
        "bocha" in lowerName -> "bocha.svg"
        "brave" in lowerName -> "brave.svg"
        "elevenlabs" in lowerName || "eleven labs" in lowerName -> "elevenlabs.svg"
        "exa" in lowerName -> "exa.svg"
        "firecrawl" in lowerName -> "firecrawl.svg"
        "gemini" in lowerName || "google" in lowerName -> "gemini.svg"
        "grok" in lowerName || "xai" in lowerName -> "grok.svg"
        "jina" in lowerName -> "jina.svg"
        "linkup" in lowerName -> "linkup.svg"
        "metaso" in lowerName -> "metaso.svg"
        "minimax" in lowerName -> "minimax.svg"
        "nano-gpt" in lowerName || "nanogpt" in lowerName -> "nanogpt.svg"
        "ollama" in lowerName -> "ollama.svg"
        "openai" in lowerName -> "openai.svg"
        "perplexity" in lowerName -> "perplexity.svg"
        "qwen" in lowerName || "dashscope" in lowerName -> "qwen.svg"
        "searxng" in lowerName || "searx" in lowerName -> "searxng.svg"
        "tavily" in lowerName -> "tavily.svg"
        "zhipu" in lowerName || "glm" in lowerName -> "zhipu.svg"
        else -> null
    }
}

private fun buildFallbackSvg(name: String): String {
    val text = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "A"
    val escapedText = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    return """
        <svg xmlns="http://www.w3.org/2000/svg" width="64" height="64" viewBox="0 0 64 64">
          <rect x="0" y="0" width="64" height="64" rx="32" fill="#E9EAEE"/>
          <text x="32" y="36" font-family="system-ui, sans-serif" font-size="24" font-weight="600" text-anchor="middle" fill="#4E5969">$escapedText</text>
        </svg>
    """.trimIndent()
}

internal fun buildWebClientBootConfig(settings: Settings): WebClientBootConfig {
    return WebClientBootConfig(authRequired = settings.webServerJwtEnabled)
}

internal fun issueWebAuthToken(
    settings: Settings,
    request: WebAuthTokenRequest,
): WebAuthTokenResponse {
    if (!settings.webServerJwtEnabled) {
        throw BadRequestException("JWT auth is disabled")
    }

    val accessPassword = settings.webServerAccessPassword
    if (accessPassword.isBlank()) {
        throw BadRequestException("Access password is not configured")
    }

    if (!secureEquals(request.password, accessPassword)) {
        throw UnauthorizedException("Invalid password")
    }

    val (token, expiresAt) = createWebJwt(accessPassword)
    return WebAuthTokenResponse(token = token, expiresAt = expiresAt)
}

internal fun buildWebBootstrap(
    settings: Settings,
    assistants: List<WebAssistantDto>,
    conversations: List<Conversation>,
    generationJobs: Map<Uuid, *>,
): WebBootstrapDto {
    return WebBootstrapDto(
        assistantId = settings.assistantId.toString(),
        assistants = assistants,
        conversations = conversations
            .sortedForWeb()
            .map { conversation -> conversation.toListDto(isGenerating = generationJobs[conversation.id] != null) },
    )
}

internal suspend fun createWebConversationResponse(
    settings: Settings,
    request: CreateConversationRequest,
    createConversation: suspend (Uuid) -> Conversation,
): CreateConversationResponse {
    val assistantId = resolveWebAssistantId(settings, request.assistantId)
    val conversation = createConversation(assistantId)
    return CreateConversationResponse(
        id = conversation.id.toString(),
        assistantId = conversation.assistantId.toString(),
    )
}

internal fun resolveWebAssistantId(settings: Settings, requestedAssistantId: String?): Uuid {
    if (requestedAssistantId.isNullOrBlank()) {
        return settings.assistantId
    }

    val assistantId = requestedAssistantId.toUuid("assistant id")
    if (settings.assistants.none { it.id == assistantId }) {
        throw BadRequestException("Assistant not found")
    }
    return assistantId
}

private fun injectWebBootConfig(
    html: String,
    bootConfig: WebClientBootConfig,
): String {
    val json = JsonInstant.encodeToString(WebClientBootConfig.serializer(), bootConfig)
        .replace("<", "\\u003c")
    val script = "<script>window.__LASTCHAT_WEB_BOOT__=$json;</script>"

    return when {
        html.contains("</head>", ignoreCase = true) -> {
            html.replace("</head>", "$script</head>", ignoreCase = true)
        }

        html.contains("<body>", ignoreCase = true) -> {
            html.replace("<body>", "<body>$script", ignoreCase = true)
        }

        else -> script + html
    }
}
