package me.rerere.rikkahub.data.ai.tools

import me.rerere.ai.core.Tool
import me.rerere.ai.workspace.createPortableWorkspaceTools
import me.rerere.rikkahub.data.repository.WorkspaceRepository

suspend fun createWorkspaceTools(
    workspaceId: String?,
    workspaceRepository: WorkspaceRepository,
    cwd: String? = null,
): List<Tool> {
    if (workspaceId.isNullOrBlank()) return emptyList()
    val approvalOverrides = workspaceRepository.getById(workspaceId)?.toolApprovalOverrides().orEmpty()
    return createPortableWorkspaceTools(
        runtime = AndroidBoundWorkspaceRuntime(workspaceId, workspaceRepository),
        approvalOverrides = approvalOverrides,
        cwd = cwd,
    )
}
