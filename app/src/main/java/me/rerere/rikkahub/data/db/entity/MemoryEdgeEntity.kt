package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = MemoryNodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_node_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MemoryNodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["target_node_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["assistant_id"]),
        Index(value = ["source_node_id"]),
        Index(value = ["target_node_id"]),
        Index(value = ["source_node_id", "target_node_id", "relation_type"], unique = true),
    ]
)
data class MemoryEdgeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo("assistant_id")
    val assistantId: String,
    @ColumnInfo("source_node_id")
    val sourceNodeId: Int,
    @ColumnInfo("target_node_id")
    val targetNodeId: Int,
    @ColumnInfo("relation_type")
    val relationType: String, // "knows", "likes", "dislikes", "scheduled_for", "happened_at", "related_to", "feels_about", "owns", "part_of"
    @ColumnInfo("strength")
    val strength: Float = 1.0f, // 0.0-1.0, decays over time
    @ColumnInfo("description")
    val description: String = "",
    @ColumnInfo("created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo("last_reinforced")
    val lastReinforced: Long = System.currentTimeMillis(),
    @ColumnInfo("episode_id")
    val episodeId: Int? = null,
)

object RelationType {
    // ═══════════════════════════════════════════════════════════════════
    // SEMANTIC GRAPH EDGES — General knowledge graph connections
    // ═══════════════════════════════════════════════════════════════════
    
    // Entity connections
    const val CONNECTED_TO = "connected_to"       // Generic connection
    const val ASSOCIATED_WITH = "associated_with" // Loose association
    const val INTERACTS_WITH = "interacts_with"   // Active interaction
    const val MENTIONED_WITH = "mentioned_with"   // Co-occurrence in conversation
    
    // Sentiment/preference edges
    const val LIKES = "likes"
    const val DISLIKES = "dislikes"
    const val FEELS_ABOUT = "feels_about"
    
    // Ownership/containment
    const val OWNS = "owns"
    const val PART_OF = "part_of"
    const val BELONGS_TO = "belongs_to"
    
    // Temporal edges
    const val SCHEDULED_FOR = "scheduled_for"
    const val HAPPENED_AT = "happened_at"
    
    // Similarity
    const val SIMILAR_TO = "similar_to"
    const val RELATED_TO = "related_to"
    
    // Legacy (kept for backward compatibility)
    const val KNOWS = "knows"
    
    // ═══════════════════════════════════════════════════════════════════
    // PROFILE CONTRIBUTING EDGES — Link trait/attribute nodes to persons
    // ═══════════════════════════════════════════════════════════════════
    const val DESCRIBES_PERSONALITY = "describes_personality"
    const val DESCRIBES_PHYSICAL = "describes_physical"
    const val DESCRIBES_OTHER = "describes_other"
    const val DESCRIBES_INTEREST = "describes_interest"
    
    // ═══════════════════════════════════════════════════════════════════
    // LEGACY PERSON-TO-PERSON EDGES — Now stored in PersonProfileEntity.relationshipsJson
    // Kept for backward compatibility but new extractions use profile storage
    // ═══════════════════════════════════════════════════════════════════
    const val FRIEND_OF = "friend_of"
    const val FAMILY_OF = "family_of"
    const val ROMANTIC_PARTNER_OF = "romantic_partner_of"
    const val COLLEAGUE_OF = "colleague_of"
    const val RIVAL_OF = "rival_of"
    const val MENTOR_OF = "mentor_of"
    const val ACQUAINTANCE_OF = "acquaintance_of"

    // ═══════════════════════════════════════════════════════════════════
    // CATEGORIZED LISTS
    // ═══════════════════════════════════════════════════════════════════
    
    val SEMANTIC = listOf(
        CONNECTED_TO, ASSOCIATED_WITH, INTERACTS_WITH, MENTIONED_WITH,
        LIKES, DISLIKES, FEELS_ABOUT,
        OWNS, PART_OF, BELONGS_TO,
        SCHEDULED_FOR, HAPPENED_AT,
        SIMILAR_TO, RELATED_TO, KNOWS
    )
    
    val PROFILE_SUMMARY = listOf(
        DESCRIBES_PERSONALITY, DESCRIBES_PHYSICAL, DESCRIBES_OTHER, DESCRIBES_INTEREST
    )
    
    // Legacy person-to-person edges (now prefer PersonProfileEntity.relationshipsJson)
    val PERSON_TO_PERSON_LEGACY = listOf(
        FRIEND_OF, FAMILY_OF, ROMANTIC_PARTNER_OF, COLLEAGUE_OF,
        RIVAL_OF, MENTOR_OF, ACQUAINTANCE_OF, KNOWS
    )
    
    // Alias for backward compatibility
    val PERSON_TO_PERSON = PERSON_TO_PERSON_LEGACY

    val ALL = SEMANTIC + PROFILE_SUMMARY + PERSON_TO_PERSON_LEGACY
}
