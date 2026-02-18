package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    indices = [
        Index(value = ["assistant_id"]),
        Index(value = ["node_id"], unique = true),
    ],
    foreignKeys = [
        ForeignKey(
            entity = MemoryNodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["node_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ]
)
data class PersonProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo("assistant_id")
    val assistantId: String,
    @ColumnInfo("node_id")
    val nodeId: Int,
    @ColumnInfo("display_name")
    val displayName: String = "",
    @ColumnInfo("avatar")
    val avatar: String? = null,
    @ColumnInfo("date_of_birth")
    val dateOfBirth: String? = null,
    @ColumnInfo("birth_year")
    val birthYear: Int? = null,
    @ColumnInfo("physical_summary")
    val physicalSummary: String = "",
    @ColumnInfo("physical_source_node_ids")
    val physicalSourceNodeIds: String = "[]",
    @ColumnInfo("personality_summary")
    val personalitySummary: String = "",
    @ColumnInfo("personality_source_node_ids")
    val personalitySourceNodeIds: String = "[]",
    @ColumnInfo("other_summary")
    val otherSummary: String = "",
    @ColumnInfo("updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
)
