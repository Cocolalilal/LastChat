package me.rerere.rikkahub.data.ai.mcp

data class McpConnectionPreset(
    val id: String,
    val name: String,
    val description: String,
    val url: String,
    val iconUri: String,
    val authMode: McpAuthMode,
    val badge: String,
    val setupNote: String? = null,
    val documentationUrl: String? = null,
    val featured: Boolean = false,
) {
    fun createConfig(): McpServerConfig.StreamableHTTPServer {
        return McpServerConfig.StreamableHTTPServer(
            commonOptions = McpCommonOptions(
                enable = authMode == McpAuthMode.NONE,
                name = name,
                authMode = authMode,
                presetId = id,
            ),
            url = url,
        )
    }
}

/**
 * Curated remote connections with first-party endpoints. Keep this list deliberately
 * smaller than a registry: a connection shown here should be useful, recognizable, and
 * safe to add without copying an endpoint from an untrusted directory.
 */
val POPULAR_MCP_CONNECTIONS = listOf(
    McpConnectionPreset(
        id = "notion",
        name = "Notion",
        description = "Search, create, and update pages and databases.",
        url = "https://mcp.notion.com/mcp",
        iconUri = "lobehub://notion",
        authMode = McpAuthMode.OAUTH,
        badge = "Free sign-in",
        documentationUrl = "https://developers.notion.com/guides/mcp/get-started-with-mcp",
        featured = true,
    ),
    McpConnectionPreset(
        id = "linear",
        name = "Linear",
        description = "Work with issues, projects, comments, and team planning.",
        url = "https://mcp.linear.app/mcp",
        iconUri = "lobehub://linear",
        authMode = McpAuthMode.OAUTH,
        badge = "Free sign-in",
        documentationUrl = "https://linear.app/docs/mcp",
        featured = true,
    ),
    McpConnectionPreset(
        id = "canva",
        name = "Canva",
        description = "Find designs, manage assets, and create visual content.",
        url = "https://mcp.canva.com/mcp",
        iconUri = "lobehub://canva",
        authMode = McpAuthMode.OAUTH,
        badge = "Free sign-in",
        documentationUrl = "https://www.canva.dev/docs/mcp/",
        featured = true,
    ),
    McpConnectionPreset(
        id = "atlassian",
        name = "Atlassian",
        description = "Search and update Jira, Confluence, Compass, and more.",
        url = "https://mcp.atlassian.com/v1/mcp/authv2",
        iconUri = "lobehub://atlassian",
        authMode = McpAuthMode.OAUTH,
        badge = "Free sign-in",
        documentationUrl = "https://developer.atlassian.com/cloud/rovo-mcp/guides/getting-started/",
    ),
    McpConnectionPreset(
        id = "github",
        name = "GitHub",
        description = "Explore repositories, issues, pull requests, and code.",
        url = "https://api.githubcopilot.com/mcp/",
        iconUri = "lobehub://github",
        authMode = McpAuthMode.EXTERNAL_OAUTH_SETUP,
        badge = "App setup",
        setupNote = "GitHub must approve or register LastChat as an OAuth client before this can become one-tap.",
        documentationUrl = "https://docs.github.com/en/copilot/how-tos/provide-context/use-mcp-in-your-ide/set-up-the-github-mcp-server",
    ),
    McpConnectionPreset(
        id = "google-drive",
        name = "Google Drive",
        description = "Find and work with files stored in Google Drive.",
        url = "https://drivemcp.googleapis.com/mcp/v1",
        iconUri = "lobehub://google-drive-color",
        authMode = McpAuthMode.EXTERNAL_OAUTH_SETUP,
        badge = "Developer preview",
        setupNote = "Requires a free Google Cloud OAuth client and access to the Google Workspace Developer Preview.",
        documentationUrl = "https://developers.google.com/workspace/drive/api/guides/configure-mcp-server",
    ),
    McpConnectionPreset(
        id = "gmail",
        name = "Gmail",
        description = "Search mail, manage labels, and create drafts.",
        url = "https://gmailmcp.googleapis.com/mcp/v1",
        iconUri = "lobehub://gmail-color",
        authMode = McpAuthMode.EXTERNAL_OAUTH_SETUP,
        badge = "Developer preview",
        setupNote = "Requires a free Google Cloud OAuth client and access to the Google Workspace Developer Preview.",
        documentationUrl = "https://developers.google.com/workspace/gmail/api/reference/mcp",
    ),
    McpConnectionPreset(
        id = "google-calendar",
        name = "Google Calendar",
        description = "Read calendars and help manage events and schedules.",
        url = "https://calendarmcp.googleapis.com/mcp/v1",
        iconUri = "lobehub://google-calendar-color",
        authMode = McpAuthMode.EXTERNAL_OAUTH_SETUP,
        badge = "Developer preview",
        setupNote = "Requires a free Google Cloud OAuth client and access to the Google Workspace Developer Preview.",
        documentationUrl = "https://developers.google.com/workspace/guides/configure-mcp-servers",
    ),
)
