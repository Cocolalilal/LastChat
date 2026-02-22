package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * A single categorized attribute for contradiction-safe merging.
 * When a new attribute arrives in the same category, it REPLACES the old one.
 * Different categories append to the list.
 *
 * Example: [{"category": "hair", "value": "blonde hair"}, {"category": "height", "value": "tall"}]
 */
@Serializable
data class CategorizedAttribute(
    val category: String,
    val value: String,
)

@Serializable
data class PersonRelationship(
    val targetNodeId: Int,
    val targetName: String,
    val relationType: String,
    val relationLabel: String? = null,
    val notes: String = "",
    val bidirectional: Boolean = true,
)

object PersonRelationType {
    // Family
    const val PARENT = "parent"
    const val CHILD = "child"
    const val SIBLING = "sibling"
    const val SPOUSE = "spouse"
    const val GRANDPARENT = "grandparent"
    const val GRANDCHILD = "grandchild"
    const val AUNT_UNCLE = "aunt_uncle"
    const val NIECE_NEPHEW = "niece_nephew"
    const val COUSIN = "cousin"
    const val IN_LAW = "in_law"

    // Social
    const val FRIEND = "friend"
    const val BEST_FRIEND = "best_friend"
    const val ACQUAINTANCE = "acquaintance"
    const val NEIGHBOR = "neighbor"

    // Professional
    const val COLLEAGUE = "colleague"
    const val BOSS = "boss"
    const val EMPLOYEE = "employee"
    const val MENTOR = "mentor"
    const val MENTEE = "mentee"
    const val CLIENT = "client"

    // Romantic
    const val PARTNER = "partner"
    const val EX_PARTNER = "ex_partner"
    const val CRUSH = "crush"

    // Other
    const val RIVAL = "rival"
    const val ENEMY = "enemy"
    const val ROOMMATE = "roommate"
    const val PET_OWNER = "pet_owner"
    const val PET = "pet"

    val ALL = listOf(
        PARENT, CHILD, SIBLING, SPOUSE, GRANDPARENT, GRANDCHILD, AUNT_UNCLE, NIECE_NEPHEW, COUSIN, IN_LAW,
        FRIEND, BEST_FRIEND, ACQUAINTANCE, NEIGHBOR,
        COLLEAGUE, BOSS, EMPLOYEE, MENTOR, MENTEE, CLIENT,
        PARTNER, EX_PARTNER, CRUSH,
        RIVAL, ENEMY, ROOMMATE, PET_OWNER, PET,
    )

    val FAMILY = listOf(PARENT, CHILD, SIBLING, SPOUSE, GRANDPARENT, GRANDCHILD, AUNT_UNCLE, NIECE_NEPHEW, COUSIN, IN_LAW)
    val SOCIAL = listOf(FRIEND, BEST_FRIEND, ACQUAINTANCE, NEIGHBOR, ROOMMATE)
    val PROFESSIONAL = listOf(COLLEAGUE, BOSS, EMPLOYEE, MENTOR, MENTEE, CLIENT)
    val ROMANTIC = listOf(PARTNER, EX_PARTNER, CRUSH)

    fun getReverse(type: String): String? = when (type) {
        PARENT -> CHILD
        CHILD -> PARENT
        GRANDPARENT -> GRANDCHILD
        GRANDCHILD -> GRANDPARENT
        AUNT_UNCLE -> NIECE_NEPHEW
        NIECE_NEPHEW -> AUNT_UNCLE
        BOSS -> EMPLOYEE
        EMPLOYEE -> BOSS
        MENTOR -> MENTEE
        MENTEE -> MENTOR
        PET_OWNER -> PET
        PET -> PET_OWNER
        else -> type // Symmetric relations return same type
    }
}

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = MemoryNodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["node_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["node_id"], unique = true),
        Index(value = ["assistant_id"]),
        Index(value = ["assistant_id", "is_user_profile"]),
        Index(value = ["assistant_id", "is_character_profile"]),
    ]
)
data class PersonProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo("node_id")
    val nodeId: Int,
    @ColumnInfo("assistant_id")
    val assistantId: String,
    @ColumnInfo("profile_image_uri")
    val profileImageUri: String? = null,
    @ColumnInfo("is_user_profile")
    val isUserProfile: Boolean = false,
    @ColumnInfo("is_character_profile")
    val isCharacterProfile: Boolean = false,
    @ColumnInfo("display_name")
    val displayName: String = "",

    // Identity
    @ColumnInfo(name = "aliases_json", defaultValue = "[]")
    val aliasesJson: String = "[]", // JSON array of alternative names: ["Mom", "Sarah"]

    // Birth info (SET ONCE — only overwrite if currently null)
    @ColumnInfo("birth_year")
    val birthYear: Int? = null,
    @ColumnInfo("birth_month")
    val birthMonth: Int? = null,
    @ColumnInfo("birth_day")
    val birthDay: Int? = null,

    // Single-value mutable fields (REPLACE semantics)
    @ColumnInfo(name = "pronouns", defaultValue = "")
    val pronouns: String = "",
    @ColumnInfo(name = "occupation", defaultValue = "")
    val occupation: String = "",
    @ColumnInfo(name = "location", defaultValue = "")
    val location: String = "",

    // Category-tagged structured lists (MERGE with category matching)
    @ColumnInfo(name = "personality_json", defaultValue = "[]")
    val personalityJson: String = "[]", // JSON array of CategorizedAttribute
    @ColumnInfo(name = "physical_json", defaultValue = "[]")
    val physicalJson: String = "[]", // JSON array of CategorizedAttribute
    @ColumnInfo(name = "other_info_json", defaultValue = "[]")
    val otherInfoJson: String = "[]", // JSON array of CategorizedAttribute

    // List-append fields (ADD, no duplicates)
    @ColumnInfo(name = "interests_json", defaultValue = "[]")
    val interestsJson: String = "[]", // JSON array of strings
    @ColumnInfo(name = "relationships_json", defaultValue = "[]")
    val relationshipsJson: String = "[]", // JSON array of PersonRelationship

    // Freeform notes (user-editable)
    @ColumnInfo(name = "notes", defaultValue = "")
    val notes: String = "",
)
