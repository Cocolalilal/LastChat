package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.workspace.WorkspaceShellStatus

/**
 * Workspace 系统提示注入转换器
 *
 * 当助手绑定了一个 shell 已就绪的 workspace 时, 在系统提示词中追加一段引导,
 * 让模型了解 workspace 环境与 workspace_* 工具的使用方式。
 */
class WorkspaceReminderTransformer(
    private val workspaceRepository: WorkspaceRepository,
    private val cwd: String? = null,
) : InputMessageTransformer {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val id = ctx.assistant.workspaceId?.toString() ?: return messages
        val workspace = workspaceRepository.getById(id) ?: return messages
        // 与 ChatService.createWorkspaceToolsIfReady 保持一致: 仅在 shell 就绪时注入
        val prompt = when {
            !ctx.model.abilities.contains(ModelAbility.TOOL) -> buildWorkspaceUnavailablePrompt(
                workspace = workspace,
                reason = "The selected model is not marked as tool-capable, so workspace_shell, workspace_read_file, workspace_write_file, and workspace_edit_file are not available in this chat."
            )
            workspace.shellStatus != WorkspaceShellStatus.READY.name -> buildWorkspaceUnavailablePrompt(
                workspace = workspace,
                reason = "The workspace rootfs is not ready. The user must install or repair the rootfs before shell and file tools can run."
            )
            else -> buildWorkspacePrompt(workspace, cwd)
        }

        // 追加到第一条 system 消息; 若不存在则插入一条
        val systemIndex = messages.indexOfFirst { it.role == MessageRole.SYSTEM }
        var modifiedMessages = if (systemIndex >= 0) {
            messages.toMutableList().apply {
                this[systemIndex] = this[systemIndex].appendText("\n\n$prompt")
            }
        } else {
            (listOf(UIMessage.system(prompt)) + messages).toMutableList()
        }

        if (cwd != null) {
            modifiedMessages = modifiedMessages.map { msg ->
                if (msg.role == MessageRole.USER) {
                    val syncedFiles = mutableListOf<String>()
                    msg.parts.forEach { part ->
                        val url = when(part) {
                            is UIMessagePart.Image -> part.url
                            is UIMessagePart.Document -> part.url
                            else -> null
                        }
                        val fileName = when(part) {
                            is UIMessagePart.Document -> part.fileName ?: url?.substringAfterLast("/")?.substringBefore("?")
                            else -> url?.substringAfterLast("/")?.substringBefore("?")
                        }
                        if (url != null && url.startsWith("file://")) {
                            syncedFiles.add("$cwd/uploads/$fileName")
                        }
                    }
                    if (syncedFiles.isNotEmpty()) {
                        val systemNote = "\n[System: The attachments in this message have been synced to your workspace at: ${syncedFiles.joinToString(", ")}]\n"
                        msg.appendText(systemNote)
                    } else {
                        msg
                    }
                } else {
                    msg
                }
            }.toMutableList()
        }

        return modifiedMessages
    }
}

private fun buildWorkspacePrompt(workspace: WorkspaceEntity, cwd: String? = null): String = buildString {
    appendLine("<workspace>")
    appendLine("You have access to a persistent Linux workspace named \"${workspace.name}\", running in a sandboxed proot rootfs environment.")
    appendLine("- The workspace files area is mounted at `/workspace`. Use it as your working directory; files written there persist across turns of this conversation.")
    appendLine("- All paths passed to workspace tools must be absolute and inside the Rootfs (for example `/workspace/notes.md`).")
    appendLine("- Available tools:")
    appendLine("  - `workspace_read_file`: read file contents.")
    appendLine("  - `workspace_write_file` / `workspace_edit_file`: create files, or make precise edits to existing files.")
    appendLine("  - `workspace_shell`: run shell commands (the files area is mounted at /workspace).")
    appendLine("- If you need to inspect the environment, call `workspace_shell`. Do not claim that you checked, installed, read, wrote, or generated anything unless a workspace tool result is present in the conversation.")
    appendLine("- If a workspace tool call is pending user approval, wait for the approval/result instead of guessing the outcome.")
    appendLine("- Prefer `workspace_shell` for tasks that standard Unix tools handle well, and prefer `workspace_edit_file` for targeted edits over rewriting whole files.")
    appendLine("- The skills directory is mounted at `/skills`. Each skill is a subdirectory `/skills/<skill-name>/` containing a `SKILL.md` (with `name` and `description` frontmatter) plus any supporting files. Read a skill's `SKILL.md` before using it, and follow its instructions.")
    if (!cwd.isNullOrBlank()) {
        appendLine("- Current working directory: `$cwd`. Use this as the default context for file operations and shell commands.")
    }
    append("</workspace>")
}

private fun buildWorkspaceUnavailablePrompt(workspace: WorkspaceEntity, reason: String): String = buildString {
    appendLine("<workspace>")
    appendLine("A Linux workspace named \"${workspace.name}\" is configured, but it is not currently usable.")
    appendLine("- $reason")
    appendLine("- Do not claim that you can run workspace commands, inspect Python, read/write workspace files, or create files in the workspace during this turn.")
    appendLine("- If the user asks about the Linux environment, explain this limitation briefly and tell them what needs to be enabled or fixed.")
    append("</workspace>")
}

private fun UIMessage.appendText(extra: String): UIMessage {
    val updatedParts = parts.toMutableList()
    val firstTextIndex = updatedParts.indexOfFirst { it is UIMessagePart.Text }
    if (firstTextIndex >= 0) {
        val text = updatedParts[firstTextIndex] as UIMessagePart.Text
        updatedParts[firstTextIndex] = text.copy(text = text.text + extra)
    } else {
        updatedParts.add(UIMessagePart.Text(extra))
    }
    return copy(parts = updatedParts)
}
