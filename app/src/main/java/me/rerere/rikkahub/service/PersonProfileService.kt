package me.rerere.rikkahub.service

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.db.entity.MemoryNodeEntity
import me.rerere.rikkahub.data.db.entity.NodeType
import me.rerere.rikkahub.data.db.entity.PersonProfileEntity
import me.rerere.rikkahub.data.repository.GraphMemoryRepository
import me.rerere.rikkahub.data.repository.PersonProfileRepository

/**
 * Service for managing person profile operations and AI summary generation.
 * Handles profile lifecycle, summary generation from related nodes, and categorization logic.
 */
class PersonProfileService(
    private val profileRepository: PersonProfileRepository,
    private val graphMemoryRepository: GraphMemoryRepository,
    private val providerManager: ProviderManager,
    private val settingsStore: SettingsStore
) {
    companion object {
        private const val TAG = "PersonProfileService"
    }

    /**
     * Generates profile summaries from related nodes using AI.
     * Categorizes nodes into physical attributes, personality traits, and other information.
     * 
     * @param nodeId The ID of the person node
     * @param assistantId The assistant identifier
     * @return ProfileSummaries containing generated summaries and source node IDs
     * @throws IllegalArgumentException if node not found or not a person node
     */
    suspend fun generateProfileSummaries(
        nodeId: Int,
        assistantId: String
    ): ProfileSummaries = withContext(Dispatchers.IO) {
        val node = graphMemoryRepository.getNodeById(nodeId) 
            ?: throw IllegalArgumentException("Node not found: $nodeId")
        
        if (node.nodeType != NodeType.PERSON) {
            throw IllegalArgumentException("Node is not a person: ${node.nodeType}")
        }
        
        // Get all related nodes
        val edges = graphMemoryRepository.getEdgesForNode(nodeId)
        val relatedNodeIds = edges.map { edge ->
            if (edge.sourceNodeId == nodeId) edge.targetNodeId else edge.sourceNodeId
        }.distinct()
        
        val relatedNodes = relatedNodeIds.mapNotNull { id ->
            graphMemoryRepository.getNodeById(id)
        }
        
        // Categorize nodes by type for summary generation
        val physicalNodes = relatedNodes.filter { isPhysicalAttribute(it) }
        val personalityNodes = relatedNodes.filter { isPersonalityTrait(it) }
        val otherNodes = relatedNodes.filter { 
            !isPhysicalAttribute(it) && !isPersonalityTrait(it) 
        }
        
        // Generate summaries using AI
        val physicalSummary = if (physicalNodes.isNotEmpty()) {
            generateSummary(node.name, physicalNodes, "physical attributes")
        } else null
        
        val personalitySummary = if (personalityNodes.isNotEmpty()) {
            generateSummary(node.name, personalityNodes, "personality traits")
        } else null
        
        val otherSummary = if (otherNodes.isNotEmpty()) {
            generateSummary(node.name, otherNodes, "other information")
        } else null
        
        ProfileSummaries(
            physical = physicalSummary,
            personality = personalitySummary,
            other = otherSummary,
            physicalSourceIds = physicalNodes.map { it.id },
            personalitySourceIds = personalityNodes.map { it.id },
            otherSourceIds = otherNodes.map { it.id }
        )
    }
    
    /**
     * Determines if a node represents a physical attribute.
     * Checks for keywords related to appearance and physical characteristics.
     * 
     * @param node The memory node to check
     * @return true if the node represents a physical attribute
     */
    private fun isPhysicalAttribute(node: MemoryNodeEntity): Boolean {
        val physicalKeywords = listOf(
            "appearance", "looks", "height", "build", "hair", "eyes", 
            "clothing", "style", "face", "skin", "body", "physique"
        )
        return physicalKeywords.any { keyword ->
            node.description.contains(keyword, ignoreCase = true) ||
            node.name.contains(keyword, ignoreCase = true)
        }
    }
    
    /**
     * Determines if a node represents a personality trait.
     * Checks node type and keywords related to personality and character.
     * 
     * @param node The memory node to check
     * @return true if the node represents a personality trait
     */
    private fun isPersonalityTrait(node: MemoryNodeEntity): Boolean {
        return node.nodeType == NodeType.EMOTION || 
               node.nodeType == NodeType.PREFERENCE ||
               node.description.contains("personality", ignoreCase = true) ||
               node.description.contains("trait", ignoreCase = true) ||
               node.description.contains("character", ignoreCase = true) ||
               node.description.contains("behavior", ignoreCase = true)
    }
    
    /**
     * Generates a summary using AI based on related nodes.
     * Uses the current chat model and provider from settings.
     * 
     * @param personName Name of the person
     * @param nodes List of related nodes to summarize
     * @param summaryType Type of summary (e.g., "physical attributes")
     * @return Generated summary text
     */
    private suspend fun generateSummary(
        personName: String,
        nodes: List<MemoryNodeEntity>,
        summaryType: String
    ): String {
        val context = nodes.joinToString("\n") { node ->
            "- ${node.name}: ${node.description}"
        }
        
        val prompt = """
            Generate a concise summary of $personName's $summaryType based on the following information:
            
            $context
            
            Requirements:
            - Write in third person
            - Be concise (2-3 sentences max)
            - Focus only on $summaryType
            - Do not include age information if this is physical attributes
            - Do not repeat information from other summary types
        """.trimIndent()
        
        return try {
            // Snapshot settings to avoid race conditions
            val settings = settingsStore.settingsFlow.first()
            val model = settings.findModelById(settings.chatModelId)
            if (model == null) {
                Log.w(TAG, "Chat model not found, returning empty summary")
                return ""
            }
            
            val provider = model.findProvider(settings.providers)
            if (provider == null) {
                Log.w(TAG, "Provider not found for model, returning empty summary")
                return ""
            }
            
            val providerHandler = providerManager.getProviderByType(provider)
            
            val response = providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(UIMessage.user(prompt)),
                params = TextGenerationParams(
                    model = model,
                    temperature = 0.3f,
                    thinkingBudget = 0
                )
            )
            
            response.choices.firstOrNull()?.message?.parts
                ?.filterIsInstance<me.rerere.ai.ui.UIMessagePart.Text>()
                ?.firstOrNull()?.text?.trim() ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate summary for $summaryType", e)
            ""
        }
    }
    
    /**
     * Ensures a profile exists for the given node.
     * Creates a new profile if one doesn't exist, otherwise returns the existing profile.
     * 
     * @param nodeId The ID of the person node
     * @param assistantId The assistant identifier
     * @return The existing or newly created profile
     */
    suspend fun ensureProfileExists(nodeId: Int, assistantId: String): PersonProfileEntity {
        return withContext(Dispatchers.IO) {
            val existing = profileRepository.getProfile(nodeId)
            if (existing != null) {
                existing
            } else {
                profileRepository.createProfile(nodeId, assistantId)
            }
        }
    }
}

/**
 * Container for generated profile summaries and their source nodes.
 * 
 * @property physical Generated physical attributes summary
 * @property personality Generated personality traits summary
 * @property other Generated other information summary
 * @property physicalSourceIds List of node IDs that contributed to physical summary
 * @property personalitySourceIds List of node IDs that contributed to personality summary
 * @property otherSourceIds List of node IDs that contributed to other summary
 */
data class ProfileSummaries(
    val physical: String?,
    val personality: String?,
    val other: String?,
    val physicalSourceIds: List<Int>,
    val personalitySourceIds: List<Int>,
    val otherSourceIds: List<Int>
)
