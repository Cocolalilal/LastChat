package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    indices = [
        Index(value = ["assistant_id", "node_type"]),
        Index(value = ["assistant_id", "status"]),
        Index(value = ["assistant_id", "last_mentioned"], orders = [Index.Order.DESC, Index.Order.DESC]),
    ]
)
data class MemoryNodeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo("assistant_id")
    val assistantId: String,
    @ColumnInfo("node_type")
    val nodeType: String, // "person", "place", "object", "event", "concept", "preference", "emotion", "plan"
    @ColumnInfo("name")
    val name: String,
    @ColumnInfo("description")
    val description: String = "",
    @ColumnInfo("importance")
    val importance: Int = 5, // 1-10
    @ColumnInfo("emotional_valence")
    val emotionalValence: Float = 0f, // -1.0 to 1.0
    @ColumnInfo("first_mentioned")
    val firstMentioned: Long = System.currentTimeMillis(),
    @ColumnInfo("last_mentioned")
    val lastMentioned: Long = System.currentTimeMillis(),
    @ColumnInfo("mention_count")
    val mentionCount: Int = 1,
    @ColumnInfo("status")
    val status: String = NodeStatus.ACTIVE, // "active", "completed", "expired", "archived"
    @ColumnInfo("valid_from")
    val validFrom: Long? = null,
    @ColumnInfo("valid_until")
    val validUntil: Long? = null,
    @ColumnInfo("embedding")
    val embedding: String? = null, // JSON string of float array
    @ColumnInfo("embedding_model_id")
    val embeddingModelId: String? = null,
)

object NodeType {
    const val PERSON = "person"
    const val PLACE = "place"
    const val OBJECT = "object"
    const val EVENT = "event"
    const val CONCEPT = "concept"
    const val PREFERENCE = "preference"
    const val EMOTION = "emotion"
    const val PLAN = "plan"

    val ALL = listOf(PERSON, PLACE, OBJECT, EVENT, CONCEPT, PREFERENCE, EMOTION, PLAN)
}

object NodeStatus {
    const val ACTIVE = "active"
    const val COMPLETED = "completed"
    const val EXPIRED = "expired"
    const val ARCHIVED = "archived"
}
