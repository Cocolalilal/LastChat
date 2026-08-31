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
        iconUri = "icons/notion.svg",
        authMode = McpAuthMode.OAUTH,
        badge = "OAuth",
        documentationUrl = "https://developers.notion.com/guides/mcp/get-started-with-mcp",
        featured = true,
    ),
    McpConnectionPreset(
        id = "linear",
        name = "Linear",
        description = "Work with issues, projects, comments, and team planning.",
        url = "https://mcp.linear.app/mcp",
        iconUri = "icons/linear.svg",
        authMode = McpAuthMode.OAUTH,
        badge = "OAuth",
        documentationUrl = "https://linear.app/docs/mcp",
        featured = true,
    ),
    McpConnectionPreset(
        id = "canva",
        name = "Canva",
        description = "Find designs, manage assets, and create visual content.",
        url = "https://mcp.canva.com/mcp",
        iconUri = "icons/canva.svg",
        authMode = McpAuthMode.OAUTH,
        badge = "OAuth",
        documentationUrl = "https://www.canva.dev/docs/mcp/",
        featured = true,
    ),
    McpConnectionPreset(
        id = "figma",
        name = "Figma",
        description = "Explore designs, inspect variables, and create or update Figma content.",
        url = "https://mcp.figma.com/mcp",
        iconUri = "icons/figma.svg",
        authMode = McpAuthMode.OAUTH,
        badge = "OAuth",
        documentationUrl = "https://help.figma.com/hc/en-us/articles/32132100833559-Guide-to-the-Figma-MCP-server",
        featured = true,
    ),
    McpConnectionPreset(
        id = "atlassian",
        name = "Atlassian",
        description = "Search and update Jira, Confluence, Compass, and more.",
        url = "https://mcp.atlassian.com/v1/mcp/authv2",
        iconUri = "icons/atlassian.svg",
        authMode = McpAuthMode.OAUTH,
        badge = "OAuth",
        documentationUrl = "https://developer.atlassian.com/cloud/rovo-mcp/guides/getting-started/",
    ),
    McpConnectionPreset(
        id = "sentry",
        name = "Sentry",
        description = "Investigate errors, events, projects, and production issues.",
        url = "https://mcp.sentry.dev/mcp",
        iconUri = "icons/sentry.svg",
        authMode = McpAuthMode.OAUTH,
        badge = "OAuth",
        documentationUrl = "https://docs.sentry.io/product/sentry-mcp/",
    ),
    McpConnectionPreset(
        id = "monday",
        name = "monday.com",
        description = "Search and update boards, items, projects, and workspaces.",
        url = "https://mcp.monday.com/mcp",
        iconUri = "icons/monday.svg",
        authMode = McpAuthMode.OAUTH,
        badge = "OAuth",
        documentationUrl = "https://support.monday.com/hc/en-us/articles/28515034903314-Get-started-with-monday-MCP",
    ),
    McpConnectionPreset(
        id = "cloudflare",
        name = "Cloudflare",
        description = "Manage Workers, DNS, storage, security, and the wider Cloudflare API.",
        url = "https://mcp.cloudflare.com/mcp",
        iconUri = "icons/cloudflare.svg",
        authMode = McpAuthMode.OAUTH,
        badge = "OAuth",
        documentationUrl = "https://github.com/cloudflare/mcp",
    ),
    McpConnectionPreset(
        id = "neon",
        name = "Neon",
        description = "Manage Postgres projects, branches, schemas, queries, and migrations.",
        url = "https://mcp.neon.tech/mcp",
        iconUri = "icons/neon.svg",
        authMode = McpAuthMode.OAUTH,
        badge = "OAuth",
        documentationUrl = "https://neon.com/docs/ai/neon-mcp-server",
    ),
    McpConnectionPreset(
        id = "supabase",
        name = "Supabase",
        description = "Work with projects, databases, Edge Functions, logs, and documentation.",
        url = "https://mcp.supabase.com/mcp",
        iconUri = "icons/supabase.svg",
        authMode = McpAuthMode.OAUTH,
        badge = "OAuth",
        documentationUrl = "https://supabase.com/docs/guides/ai-tools/mcp",
    ),
    McpConnectionPreset(
        id = "hugging-face",
        name = "Hugging Face",
        description = "Search models, datasets, Spaces, papers, documentation, and Hub resources.",
        url = "https://huggingface.co/mcp",
        iconUri = "icons/huggingface.svg",
        authMode = McpAuthMode.OAUTH,
        badge = "OAuth",
        documentationUrl = "https://huggingface.co/docs/hub/en/agents-mcp",
    ),
    McpConnectionPreset(
        id = "webflow",
        name = "Webflow",
        description = "Build sites and manage pages, CMS content, assets, and design systems.",
        url = "https://mcp.webflow.com/mcp",
        iconUri = "icons/webflow.svg",
        authMode = McpAuthMode.OAUTH,
        badge = "OAuth",
        documentationUrl = "https://developers.webflow.com/mcp/reference/getting-started",
    ),
    McpConnectionPreset(
        id = "heroku",
        name = "Heroku",
        description = "Manage apps, deployments, dynos, logs, databases, and add-ons.",
        url = "https://mcp.heroku.com/mcp",
        iconUri = "icons/heroku.svg",
        authMode = McpAuthMode.OAUTH,
        badge = "OAuth",
        documentationUrl = "https://devcenter.heroku.com/articles/heroku-remote-mcp-server",
    ),
    McpConnectionPreset(
        id = "deepwiki",
        name = "DeepWiki",
        description = "Ask questions about public GitHub repositories and their code.",
        url = "https://mcp.deepwiki.com/mcp",
        iconUri = "icons/devin.svg",
        authMode = McpAuthMode.NONE,
        badge = "No sign-in",
        documentationUrl = "https://docs.devin.ai/work-with-devin/deepwiki-mcp",
    ),
    McpConnectionPreset(
        id = "cloudflare-docs",
        name = "Cloudflare Docs",
        description = "Search current Cloudflare developer documentation and examples.",
        url = "https://docs.mcp.cloudflare.com/mcp",
        iconUri = "icons/cloudflare.svg",
        authMode = McpAuthMode.NONE,
        badge = "No sign-in",
        documentationUrl = "https://github.com/cloudflare/mcp-server-cloudflare",
    ),
    McpConnectionPreset(
        id = "microsoft-learn",
        name = "Microsoft Learn",
        description = "Search official Microsoft documentation, articles, and code samples.",
        url = "https://learn.microsoft.com/api/mcp",
        iconUri = "icons/microsoft.svg",
        authMode = McpAuthMode.NONE,
        badge = "No sign-in",
        documentationUrl = "https://learn.microsoft.com/training/support/mcp",
    ),
    McpConnectionPreset(
        id = "context7",
        name = "Context7",
        description = "Retrieve current, version-specific documentation for software libraries.",
        url = "https://mcp.context7.com/mcp",
        iconUri = "icons/upstash.svg",
        authMode = McpAuthMode.NONE,
        badge = "No sign-in",
        setupNote = "Works without sign-in; an optional free CONTEXT7_API_KEY header raises rate limits.",
        documentationUrl = "https://github.com/upstash/context7",
    ),
)

fun findMcpConnectionPreset(presetId: String?, url: String? = null, name: String? = null): McpConnectionPreset? {
    if (!presetId.isNullOrBlank()) {
        POPULAR_MCP_CONNECTIONS.firstOrNull { it.id == presetId }?.let { return it }
    }
    if (!url.isNullOrBlank()) {
        val cleanUrl = url.trim().trimEnd('/')
        POPULAR_MCP_CONNECTIONS.firstOrNull { it.url.trimEnd('/') == cleanUrl }?.let { return it }
    }
    if (!name.isNullOrBlank()) {
        val cleanName = name.trim()
        POPULAR_MCP_CONNECTIONS.firstOrNull { it.name.equals(cleanName, ignoreCase = true) }?.let { return it }
    }
    return null
}

fun findMcpConnectionPreset(server: McpServerConfig): McpConnectionPreset? {
    return findMcpConnectionPreset(
        presetId = server.commonOptions.presetId,
        url = server.endpointUrl,
        name = server.commonOptions.name,
    )
}
