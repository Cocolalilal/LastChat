# Design Document: Enhanced Person Profiles

## Overview

This design enhances the existing memory graph system in LastChat to provide rich, structured profiles for person entities. The enhancement transforms basic person nodes (MemoryNodeEntity with nodeType="person") into comprehensive profiles with biographical data, physical attributes, personality traits, relationships, and images.

The design maintains full compatibility with the existing memory graph architecture while adding new database tables and UI components specifically for person profiles. The system automatically generates profile summaries from related nodes in the graph using AI, and provides a polished UI for viewing and editing profiles.

### Key Design Principles

1. **Non-Breaking Extension**: All changes extend the existing MemoryNodeEntity system without modifying its core structure
2. **Automatic Enrichment**: Profile summaries are automatically generated from conversation context via AI
3. **Fidget Toy UX**: Every interaction includes premium haptics and smooth animations per AGENTS.md guidelines
4. **Crash Resistance**: All code follows strict null-safety and I/O dispatcher requirements
5. **Separation of Concerns**: Clear boundaries between data layer (Repository), business logic (Service), and UI (Composables)

## Architecture

### System Components

```
┌─────────────────────────────────────────────────────────────┐
│                         UI Layer                             │
│  ┌──────────────────┐  ┌─────────────────────────────────┐ │
│  │ PersonCardItem   │  │   PersonProfileBanner           │ │
│  │ (Entities Tab)   │──▶│   (ModalBottomSheet)            │ │
│  └──────────────────┘  └─────────────────────────────────┘ │
└────────────────────────────────┬────────────────────────────┘
                                 │
┌────────────────────────────────▼────────────────────────────┐
│                    Business Logic Layer                      │
│  ┌──────────────────────────────────────────────────────┐  │
│  │           PersonProfileService                        │  │
│  │  - Generate summaries from related nodes             │  │
│  │  - Coordinate AI summary generation                  │  │
│  │  - Manage profile lifecycle                          │  │
│  └──────────────────────────────────────────────────────┘  │
└────────────────────────────────┬────────────────────────────┘
                                 │
┌────────────────────────────────▼────────────────────────────┐
│                       Data Layer                             │
│  ┌──────────────────────────────────────────────────────┐  │
│  │         PersonProfileRepository                       │  │
│  │  - CRUD operations for profiles                      │  │
│  │  - Query profiles with related nodes                 │  │
│  │  - Manage summary source tracking                    │  │
│  └──────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              Room Database                            │  │
│  │  - PersonProfileEntity                               │  │
│  │  - ProfileSummarySourceEntity                        │  │
│  │  - PersonProfileDAO                                  │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### Data Flow

1. **Profile Creation**: When a Person_Node is created, PersonProfileRepository automatically creates a default PersonProfileEntity
2. **Summary Generation**: PersonProfileService analyzes Related_Nodes and generates summaries using AI
3. **Profile Display**: User taps person card → PersonProfileBanner opens → Repository loads profile + related data
4. **Profile Editing**: User taps edit → Banner enters edit mode → Changes saved via Repository → Service regenerates summaries if needed


## Components and Interfaces

### 1. PersonProfileEntity (Database Entity)

```kotlin
@Entity(
    tableName = "PersonProfileEntity",
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
        Index(value = ["assistant_id"])
    ]
)
data class PersonProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    @ColumnInfo("node_id")
    val nodeId: Int, // Foreign key to MemoryNodeEntity
    
    @ColumnInfo("assistant_id")
    val assistantId: String,
    
    @ColumnInfo("date_of_birth")
    val dateOfBirth: String? = null, // ISO format: "YYYY" or "YYYY-MM-DD"
    
    @ColumnInfo("physical_summary")
    val physicalSummary: String? = null,
    
    @ColumnInfo("personality_summary")
    val personalitySummary: String? = null,
    
    @ColumnInfo("other_info_summary")
    val otherInfoSummary: String? = null,
    
    @ColumnInfo("image_uri")
    val imageUri: String? = null,
    
    @ColumnInfo("last_updated")
    val lastUpdated: Long = System.currentTimeMillis()
)
```

### 2. ProfileSummarySourceEntity (Tracks Summary Sources)

```kotlin
@Entity(
    tableName = "ProfileSummarySourceEntity",
    foreignKeys = [
        ForeignKey(
            entity = PersonProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MemoryNodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_node_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["profile_id", "summary_type"]),
        Index(value = ["source_node_id"])
    ]
)
data class ProfileSummarySourceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    @ColumnInfo("profile_id")
    val profileId: Int,
    
    @ColumnInfo("summary_type")
    val summaryType: String, // "physical", "personality", "other"
    
    @ColumnInfo("source_node_id")
    val sourceNodeId: Int,
    
    @ColumnInfo("created_at")
    val createdAt: Long = System.currentTimeMillis()
)

object SummaryType {
    const val PHYSICAL = "physical"
    const val PERSONALITY = "personality"
    const val OTHER = "other"
}
```

### 3. PersonProfileDAO (Data Access Object)

```kotlin
@Dao
interface PersonProfileDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: PersonProfileEntity): Long
    
    @Update
    suspend fun update(profile: PersonProfileEntity)
    
    @Query("SELECT * FROM PersonProfileEntity WHERE node_id = :nodeId")
    suspend fun getByNodeId(nodeId: Int): PersonProfileEntity?
    
    @Query("SELECT * FROM PersonProfileEntity WHERE node_id = :nodeId")
    fun getByNodeIdFlow(nodeId: Int): Flow<PersonProfileEntity?>
    
    @Query("SELECT * FROM PersonProfileEntity WHERE assistant_id = :assistantId")
    suspend fun getAllForAssistant(assistantId: String): List<PersonProfileEntity>
    
    @Delete
    suspend fun delete(profile: PersonProfileEntity)
    
    @Query("DELETE FROM PersonProfileEntity WHERE assistant_id = :assistantId")
    suspend fun deleteAllForAssistant(assistantId: String)
    
    // Summary source operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSummarySource(source: ProfileSummarySourceEntity): Long
    
    @Query("SELECT * FROM ProfileSummarySourceEntity WHERE profile_id = :profileId AND summary_type = :summaryType")
    suspend fun getSummarySourcesForType(profileId: Int, summaryType: String): List<ProfileSummarySourceEntity>
    
    @Query("DELETE FROM ProfileSummarySourceEntity WHERE profile_id = :profileId AND summary_type = :summaryType")
    suspend fun deleteSummarySourcesForType(profileId: Int, summaryType: String)
}
```

### 4. PersonProfileRepository (Data Layer)

```kotlin
class PersonProfileRepository(
    private val profileDAO: PersonProfileDAO,
    private val nodeDAO: MemoryNodeDAO,
    private val edgeDAO: MemoryEdgeDAO
) {
    suspend fun createProfile(nodeId: Int, assistantId: String): PersonProfileEntity = withContext(Dispatchers.IO) {
        val profile = PersonProfileEntity(
            nodeId = nodeId,
            assistantId = assistantId
        )
        val id = profileDAO.insert(profile)
        profile.copy(id = id.toInt())
    }
    
    suspend fun getProfile(nodeId: Int): PersonProfileEntity? = withContext(Dispatchers.IO) {
        profileDAO.getByNodeId(nodeId)
    }
    
    fun getProfileFlow(nodeId: Int): Flow<PersonProfileEntity?> = 
        profileDAO.getByNodeIdFlow(nodeId)
    
    suspend fun updateProfile(profile: PersonProfileEntity) = withContext(Dispatchers.IO) {
        profileDAO.update(profile)
    }
    
    suspend fun getPersonRelationships(nodeId: Int, assistantId: String): List<PersonRelationship> = 
        withContext(Dispatchers.IO) {
            val edges = edgeDAO.getEdgesForNode(nodeId)
            val personEdges = edges.filter { edge ->
                val otherNodeId = if (edge.sourceNodeId == nodeId) edge.targetNodeId else edge.sourceNodeId
                val otherNode = nodeDAO.getById(otherNodeId)
                otherNode?.nodeType == NodeType.PERSON
            }
            
            personEdges.mapNotNull { edge ->
                val isSource = edge.sourceNodeId == nodeId
                val otherNodeId = if (isSource) edge.targetNodeId else edge.sourceNodeId
                val otherNode = nodeDAO.getById(otherNodeId) ?: return@mapNotNull null
                
                PersonRelationship(
                    personName = otherNode.name,
                    personNodeId = otherNode.id,
                    relationType = edge.relationType,
                    description = edge.description,
                    isOutgoing = isSource
                )
            }
        }
    
    suspend fun setSummarySourceNodes(
        profileId: Int,
        summaryType: String,
        sourceNodeIds: List<Int>
    ) = withContext(Dispatchers.IO) {
        profileDAO.deleteSummarySourcesForType(profileId, summaryType)
        sourceNodeIds.forEach { nodeId ->
            profileDAO.insertSummarySource(
                ProfileSummarySourceEntity(
                    profileId = profileId,
                    summaryType = summaryType,
                    sourceNodeId = nodeId
                )
            )
        }
    }
    
    suspend fun getSummarySourceNodes(
        profileId: Int,
        summaryType: String
    ): List<MemoryNodeEntity> = withContext(Dispatchers.IO) {
        val sources = profileDAO.getSummarySourcesForType(profileId, summaryType)
        sources.mapNotNull { source ->
            nodeDAO.getById(source.sourceNodeId)
        }
    }
}

data class PersonRelationship(
    val personName: String,
    val personNodeId: Int,
    val relationType: String,
    val description: String,
    val isOutgoing: Boolean
)
```

### 5. AgeCalculator (Utility)

```kotlin
object AgeCalculator {
    fun calculateAge(dateOfBirth: String?): Int? {
        if (dateOfBirth == null) return null
        
        return try {
            val now = LocalDate.now()
            val birthDate = when {
                dateOfBirth.length == 4 -> {
                    // Year only: assume January 1st
                    LocalDate.of(dateOfBirth.toInt(), 1, 1)
                }
                dateOfBirth.length == 10 -> {
                    // Full date: YYYY-MM-DD
                    LocalDate.parse(dateOfBirth)
                }
                else -> return null
            }
            
            // Return null for future dates
            if (birthDate.isAfter(now)) return null
            
            Period.between(birthDate, now).years
        } catch (e: Exception) {
            null
        }
    }
}
```

### 6. PersonProfileService (Business Logic)

```kotlin
class PersonProfileService(
    private val profileRepository: PersonProfileRepository,
    private val graphMemoryRepository: GraphMemoryRepository,
    private val providerManager: ProviderManager,
    private val settingsStore: SettingsStore
) {
    suspend fun generateProfileSummaries(
        nodeId: Int,
        assistantId: String
    ): ProfileSummaries = withContext(Dispatchers.IO) {
        val node = graphMemoryRepository.getNodeById(nodeId) 
            ?: throw IllegalArgumentException("Node not found")
        
        if (node.nodeType != NodeType.PERSON) {
            throw IllegalArgumentException("Node is not a person")
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
    
    private fun isPhysicalAttribute(node: MemoryNodeEntity): Boolean {
        val physicalKeywords = listOf("appearance", "looks", "height", "build", "hair", "eyes", "clothing", "style")
        return physicalKeywords.any { keyword ->
            node.description.contains(keyword, ignoreCase = true) ||
            node.name.contains(keyword, ignoreCase = true)
        }
    }
    
    private fun isPersonalityTrait(node: MemoryNodeEntity): Boolean {
        return node.nodeType == NodeType.EMOTION || 
               node.nodeType == NodeType.PREFERENCE ||
               node.description.contains("personality", ignoreCase = true) ||
               node.description.contains("trait", ignoreCase = true)
    }
    
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
        
        val provider = providerManager.getCurrentProvider()
        val response = provider.chat(
            messages = listOf(
                ChatMessage(role = "user", content = prompt)
            ),
            temperature = 0.3f
        )
        
        return response.content.trim()
    }
    
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

data class ProfileSummaries(
    val physical: String?,
    val personality: String?,
    val other: String?,
    val physicalSourceIds: List<Int>,
    val personalitySourceIds: List<Int>,
    val otherSourceIds: List<Int>
)
```


### 7. PersonProfileBanner (UI Component)

```kotlin
@Composable
fun PersonProfileBanner(
    nodeId: Int,
    assistantId: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val haptics = rememberPremiumHaptics()
    
    val profileService: PersonProfileService = get()
    val profileRepository: PersonProfileRepository = get()
    val graphRepository: GraphMemoryRepository = get()
    
    var profile by remember { mutableStateOf<PersonProfileEntity?>(null) }
    var personNode by remember { mutableStateOf<MemoryNodeEntity?>(null) }
    var relationships by remember { mutableStateOf<List<PersonRelationship>>(emptyList()) }
    var isEditMode by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Editable fields
    var editName by remember { mutableStateOf("") }
    var editDateOfBirth by remember { mutableStateOf("") }
    var editPhysicalSummary by remember { mutableStateOf("") }
    var editPersonalitySummary by remember { mutableStateOf("") }
    var editOtherInfo by remember { mutableStateOf("") }
    var editImageUri by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(nodeId) {
        withContext(Dispatchers.IO) {
            personNode = graphRepository.getNodeById(nodeId)
            profile = profileService.ensureProfileExists(nodeId, assistantId)
            relationships = profileRepository.getPersonRelationships(nodeId, assistantId)
            isLoading = false
        }
    }
    
    ModalBottomSheet(
        onDismissRequest = {
            haptics.perform(HapticPattern.Pop)
            onDismiss()
        },
        sheetState = sheetState,
        shape = AppShapes.CardLarge,
        modifier = modifier
    ) {
        AnimatedContent(
            targetState = isLoading,
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith 
                fadeOut(animationSpec = tween(300))
            }
        ) { loading ->
            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                PersonProfileContent(
                    personNode = personNode,
                    profile = profile,
                    relationships = relationships,
                    isEditMode = isEditMode,
                    editName = editName,
                    editDateOfBirth = editDateOfBirth,
                    editPhysicalSummary = editPhysicalSummary,
                    editPersonalitySummary = editPersonalitySummary,
                    editOtherInfo = editOtherInfo,
                    editImageUri = editImageUri,
                    onEditModeChange = { enabled ->
                        if (enabled) {
                            // Enter edit mode
                            editName = personNode?.name ?: ""
                            editDateOfBirth = profile?.dateOfBirth ?: ""
                            editPhysicalSummary = profile?.physicalSummary ?: ""
                            editPersonalitySummary = profile?.personalitySummary ?: ""
                            editOtherInfo = profile?.otherInfoSummary ?: ""
                            editImageUri = profile?.imageUri
                        }
                        isEditMode = enabled
                        haptics.perform(HapticPattern.Pop)
                    },
                    onSave = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                // Update node name if changed
                                if (editName != personNode?.name && personNode != null) {
                                    graphRepository.updateNode(
                                        personNode!!.copy(name = editName)
                                    )
                                }
                                
                                // Update profile
                                profile?.let { currentProfile ->
                                    profileRepository.updateProfile(
                                        currentProfile.copy(
                                            dateOfBirth = editDateOfBirth.ifBlank { null },
                                            physicalSummary = editPhysicalSummary.ifBlank { null },
                                            personalitySummary = editPersonalitySummary.ifBlank { null },
                                            otherInfoSummary = editOtherInfo.ifBlank { null },
                                            imageUri = editImageUri,
                                            lastUpdated = System.currentTimeMillis()
                                        )
                                    )
                                }
                                
                                // Reload data
                                personNode = graphRepository.getNodeById(nodeId)
                                profile = profileRepository.getProfile(nodeId)
                            }
                            isEditMode = false
                            haptics.perform(HapticPattern.Success)
                        }
                    },
                    onCancel = {
                        isEditMode = false
                        haptics.perform(HapticPattern.Pop)
                    },
                    onNameChange = { editName = it },
                    onDateOfBirthChange = { editDateOfBirth = it },
                    onPhysicalSummaryChange = { editPhysicalSummary = it },
                    onPersonalitySummaryChange = { editPersonalitySummary = it },
                    onOtherInfoChange = { editOtherInfo = it },
                    onImageUriChange = { editImageUri = it },
                    haptics = haptics
                )
            }
        }
    }
}

@Composable
private fun PersonProfileContent(
    personNode: MemoryNodeEntity?,
    profile: PersonProfileEntity?,
    relationships: List<PersonRelationship>,
    isEditMode: Boolean,
    editName: String,
    editDateOfBirth: String,
    editPhysicalSummary: String,
    editPersonalitySummary: String,
    editOtherInfo: String,
    editImageUri: String?,
    onEditModeChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onNameChange: (String) -> Unit,
    onDateOfBirthChange: (String) -> Unit,
    onPhysicalSummaryChange: (String) -> Unit,
    onPersonalitySummaryChange: (String) -> Unit,
    onOtherInfoChange: (String) -> Unit,
    onImageUriChange: (String?) -> Unit,
    haptics: PremiumHaptics
) {
    val age = remember(profile?.dateOfBirth) {
        AgeCalculator.calculateAge(profile?.dateOfBirth)
    }
    
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with image and name
        item {
            ProfileHeader(
                name = if (isEditMode) editName else (personNode?.name ?: ""),
                age = age,
                imageUri = if (isEditMode) editImageUri else profile?.imageUri,
                isEditMode = isEditMode,
                onNameChange = onNameChange,
                onImageUriChange = onImageUriChange,
                haptics = haptics
            )
        }
        
        // Edit/Save/Cancel buttons
        item {
            ProfileActionButtons(
                isEditMode = isEditMode,
                onEditModeChange = onEditModeChange,
                onSave = onSave,
                onCancel = onCancel,
                haptics = haptics
            )
        }
        
        // Date of Birth
        item {
            ProfileSection(
                title = "Date of Birth",
                content = if (isEditMode) editDateOfBirth else (profile?.dateOfBirth ?: "Not set"),
                isEditMode = isEditMode,
                onContentChange = onDateOfBirthChange,
                placeholder = "YYYY or YYYY-MM-DD"
            )
        }
        
        // Relationships
        if (relationships.isNotEmpty()) {
            item {
                RelationshipsSection(
                    relationships = relationships,
                    haptics = haptics
                )
            }
        }
        
        // Physical Attributes
        item {
            ProfileSection(
                title = "Physical Attributes",
                content = if (isEditMode) editPhysicalSummary else (profile?.physicalSummary ?: "No information"),
                isEditMode = isEditMode,
                onContentChange = onPhysicalSummaryChange,
                placeholder = "Describe physical attributes..."
            )
        }
        
        // Personality Traits
        item {
            ProfileSection(
                title = "Personality",
                content = if (isEditMode) editPersonalitySummary else (profile?.personalitySummary ?: "No information"),
                isEditMode = isEditMode,
                onContentChange = onPersonalitySummaryChange,
                placeholder = "Describe personality traits..."
            )
        }
        
        // Other Info
        item {
            ProfileSection(
                title = "Other Information",
                content = if (isEditMode) editOtherInfo else (profile?.otherInfoSummary ?: "No information"),
                isEditMode = isEditMode,
                onContentChange = onOtherInfoChange,
                placeholder = "Other information..."
            )
        }
        
        // Bottom spacing
        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
```


## Data Models

### PersonProfileEntity

The main entity storing profile data for person nodes.

**Fields:**
- `id`: Auto-generated primary key
- `nodeId`: Foreign key to MemoryNodeEntity (unique, cascade delete)
- `assistantId`: Assistant identifier for multi-assistant support
- `dateOfBirth`: ISO format string ("YYYY" or "YYYY-MM-DD"), nullable
- `physicalSummary`: AI-generated summary of physical attributes, nullable
- `personalitySummary`: AI-generated summary of personality traits, nullable
- `otherInfoSummary`: AI-generated summary of miscellaneous information, nullable
- `imageUri`: URI to profile image, nullable
- `lastUpdated`: Timestamp of last update

**Relationships:**
- One-to-one with MemoryNodeEntity (via nodeId)
- One-to-many with ProfileSummarySourceEntity

**Indices:**
- Unique index on `node_id` (ensures one profile per person node)
- Index on `assistant_id` (for efficient assistant-scoped queries)

### ProfileSummarySourceEntity

Tracks which related nodes contributed to each summary field.

**Fields:**
- `id`: Auto-generated primary key
- `profileId`: Foreign key to PersonProfileEntity (cascade delete)
- `summaryType`: Type of summary ("physical", "personality", "other")
- `sourceNodeId`: Foreign key to MemoryNodeEntity (cascade delete)
- `createdAt`: Timestamp when source was linked

**Relationships:**
- Many-to-one with PersonProfileEntity
- Many-to-one with MemoryNodeEntity

**Indices:**
- Composite index on `(profile_id, summary_type)` (for efficient summary source queries)
- Index on `source_node_id` (for reverse lookups)

### PersonRelationship (Data Class)

Runtime data structure for person-to-person relationships.

**Fields:**
- `personName`: Name of the related person
- `personNodeId`: Node ID of the related person
- `relationType`: Type of relationship (from MemoryEdgeEntity)
- `description`: Description of the relationship
- `isOutgoing`: Whether this is an outgoing edge from the current person

**Usage:**
- Not persisted directly (derived from MemoryEdgeEntity)
- Used for UI display of relationships
- Filtered to only include person-to-person edges

### ProfileSummaries (Data Class)

Container for generated summaries and their source nodes.

**Fields:**
- `physical`: Generated physical attributes summary, nullable
- `personality`: Generated personality traits summary, nullable
- `other`: Generated other information summary, nullable
- `physicalSourceIds`: List of node IDs that contributed to physical summary
- `personalitySourceIds`: List of node IDs that contributed to personality summary
- `otherSourceIds`: List of node IDs that contributed to other summary

**Usage:**
- Returned by PersonProfileService.generateProfileSummaries()
- Used to update PersonProfileEntity and ProfileSummarySourceEntity together

### Database Migration

Migration from version 24 to 25 adds the new tables:

```kotlin
@Database(
    entities = [
        // ... existing entities ...
        PersonProfileEntity::class,
        ProfileSummarySourceEntity::class
    ],
    version = 25,
    autoMigrations = [
        AutoMigration(from = 24, to = 25)
    ]
)
abstract class AppDatabase : RoomDatabase() {
    // ... existing DAOs ...
    abstract fun personProfileDAO(): PersonProfileDAO
}
```


## Correctness Properties

A property is a characteristic or behavior that should hold true across all valid executions of a system—essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.

### Property 1: Profile Data Round Trip

*For any* PersonProfileEntity with all fields populated (name, date of birth, summaries, image URI), saving it to the database then retrieving it should produce an equivalent profile with all fields matching.

**Validates: Requirements 1.1, 1.4**

### Property 2: Summary Source Tracking

*For any* profile with summary sources, storing the source node references for a summary type then retrieving them should return the same set of source node IDs.

**Validates: Requirements 1.2, 3.6**

### Property 3: Date Format Support

*For any* valid date of birth string (either "YYYY" format or "YYYY-MM-DD" format), storing it in a profile then retrieving it should return the exact same string format.

**Validates: Requirements 1.3**

### Property 4: Cascade Delete Integrity

*For any* PersonProfileEntity linked to a MemoryNodeEntity, deleting the MemoryNodeEntity should automatically delete the associated PersonProfileEntity due to foreign key cascade.

**Validates: Requirements 1.6, 9.5**

### Property 5: Age Calculation Correctness

*For any* valid date of birth (full date or year-only), the calculated age should equal the difference in years between the birth date and the current date, with year-only dates treated as January 1st of that year.

**Validates: Requirements 2.1, 2.2**

### Property 6: Physical Summary Excludes Age

*For any* generated physical attributes summary, the summary text should not contain age-related keywords such as "age", "years old", or numeric age references.

**Validates: Requirements 3.4**

### Property 7: Relationship Retrieval Completeness

*For any* Person_Node with edges to other Person_Nodes, querying relationships should return all person-to-person edges with correct directionality (isOutgoing flag).

**Validates: Requirements 4.1, 4.4**

### Property 8: Relationship Type Support

*For any* valid relationship type string (family, friend, colleague, romantic, or custom), creating a relationship with that type then retrieving it should preserve the relationship type exactly.

**Validates: Requirements 4.2**

### Property 9: Date Format Validation

*For any* invalid date of birth format (not matching "YYYY" or "YYYY-MM-DD"), attempting to save the profile should either reject the invalid format or normalize it to null.

**Validates: Requirements 6.5**

### Property 10: Edit Persistence

*For any* profile changes made in edit mode (name, date of birth, summaries, image), saving the changes then retrieving the profile should reflect all the modifications.

**Validates: Requirements 6.3, 6.4**

### Property 11: Automatic Profile Creation

*For any* newly created Person_Node, a corresponding PersonProfileEntity should be automatically created with the same nodeId and assistantId.

**Validates: Requirements 11.1**

### Property 12: Backward Compatibility

*For any* existing MemoryNodeEntity query or operation, adding the profile system should not break or modify the behavior of that query or operation.

**Validates: Requirements 11.3, 11.5**

### Property 13: Summary Update Trigger

*For any* Person_Node that is updated with new related nodes, the profile summaries should be regenerated to include information from the new related nodes.

**Validates: Requirements 11.2**


## Error Handling

### Database Errors

**Foreign Key Violations:**
- When attempting to create a PersonProfileEntity with an invalid nodeId, the operation should fail with a clear error message
- The UI should catch this error and display a user-friendly message
- Logging should capture the invalid nodeId for debugging

**Cascade Delete Handling:**
- When a MemoryNodeEntity is deleted, the cascade delete of PersonProfileEntity should be automatic and silent
- No error should be thrown for the profile deletion
- Related ProfileSummarySourceEntity records should also be cascade deleted

**Migration Failures:**
- If database migration from version 24 to 25 fails, the app should not crash
- The migration should be rolled back and the user should be notified
- Existing data should remain intact on the previous schema version

### AI Service Errors

**Summary Generation Failures:**
- When AI provider is unavailable or returns an error, summary generation should fail gracefully
- The profile should be saved with null summaries rather than failing the entire operation
- A retry mechanism should be available for users to regenerate summaries later
- Error should be logged with provider details and error message

**Token Limit Exceeded:**
- When related nodes contain too much text for AI context, the service should truncate or sample nodes
- Priority should be given to more recent and higher importance nodes
- A warning should be logged indicating truncation occurred

### Input Validation Errors

**Invalid Date Format:**
- When user enters an invalid date of birth format, the UI should show inline validation error
- The save button should be disabled until the format is corrected
- Accepted formats should be clearly displayed: "YYYY" or "YYYY-MM-DD"

**Future Date of Birth:**
- When user enters a future date, the UI should show a warning
- The date should be accepted but age calculation will return null
- A message should indicate "Date is in the future"

**Empty Required Fields:**
- When user attempts to save with an empty name, the operation should be rejected
- The UI should highlight the empty field and show an error message
- Other fields (summaries, date of birth) can be empty/null

### Concurrency Errors

**Concurrent Profile Updates:**
- When multiple operations attempt to update the same profile simultaneously, use database transactions
- Last write wins strategy should be applied
- StateFlow snapshots should be used to avoid race conditions in the service layer

**Node Deletion During Profile View:**
- When a MemoryNodeEntity is deleted while its profile is being viewed, the UI should detect the deletion
- The profile banner should automatically dismiss with a message "Person no longer exists"
- No crash should occur from attempting to access deleted data

### Image Handling Errors

**Invalid Image URI:**
- When an image URI is invalid or the file doesn't exist, display a placeholder image
- Log the error but don't crash or show error to user
- Allow user to select a new image

**Image Permission Denied:**
- When user denies image picker permissions, show a message explaining why permission is needed
- Provide a button to open app settings
- Allow profile to be saved without an image

### Network Errors

**Offline Mode:**
- When device is offline and AI summary generation is requested, queue the request
- Show a message "Summaries will be generated when online"
- Retry automatically when connection is restored

**Timeout:**
- When AI provider request times out (>30 seconds), cancel the request
- Show error message and allow manual retry
- Don't block the UI or prevent profile saving


## Testing Strategy

### Dual Testing Approach

This feature requires both unit tests and property-based tests for comprehensive coverage:

- **Unit tests**: Verify specific examples, edge cases, and error conditions
- **Property tests**: Verify universal properties across all inputs
- Both are complementary and necessary for comprehensive coverage

### Property-Based Testing

**Library:** Use [Kotest Property Testing](https://kotest.io/docs/proptest/property-based-testing.html) for Kotlin

**Configuration:**
- Minimum 100 iterations per property test
- Each property test must reference its design document property
- Tag format: `@Tag("Feature: enhanced-person-profiles, Property {number}: {property_text}")`

**Property Test Coverage:**

1. **Property 1: Profile Data Round Trip**
   - Generate random PersonProfileEntity instances with all fields
   - Save to database, retrieve, and verify equality
   - Test with various date formats, URIs, and summary lengths

2. **Property 2: Summary Source Tracking**
   - Generate random sets of source node IDs
   - Store for each summary type, retrieve, and verify sets match
   - Test with empty sets, single nodes, and many nodes

3. **Property 3: Date Format Support**
   - Generate random valid dates in both "YYYY" and "YYYY-MM-DD" formats
   - Store and retrieve, verifying exact string match
   - Include edge cases like leap years

4. **Property 4: Cascade Delete Integrity**
   - Create random person nodes with profiles
   - Delete nodes and verify profiles are automatically deleted
   - Test with and without summary sources

5. **Property 5: Age Calculation Correctness**
   - Generate random dates of birth (past dates only)
   - Calculate age and verify against manual calculation
   - Test both full dates and year-only dates

6. **Property 6: Physical Summary Excludes Age**
   - Generate random physical summaries (or use AI-generated ones)
   - Verify no age-related keywords appear in the text
   - Use regex patterns to detect age references

7. **Property 7: Relationship Retrieval Completeness**
   - Create random person nodes with random edges between them
   - Query relationships and verify all person-to-person edges are returned
   - Verify directionality is correct

8. **Property 8: Relationship Type Support**
   - Generate random relationship types (from predefined list + custom)
   - Create relationships, retrieve, and verify types match
   - Test with special characters in custom types

9. **Property 9: Date Format Validation**
   - Generate random invalid date strings
   - Attempt to save and verify rejection or normalization
   - Test various malformed formats

10. **Property 10: Edit Persistence**
    - Generate random profile changes
    - Save and retrieve, verifying all changes persisted
    - Test with partial updates (only some fields changed)

11. **Property 11: Automatic Profile Creation**
    - Create random person nodes
    - Verify profiles are automatically created
    - Test with various assistantId values

12. **Property 12: Backward Compatibility**
    - Run existing MemoryNodeEntity queries before and after adding profiles
    - Verify results are identical
    - Test with various query types (by name, by type, by importance)

13. **Property 13: Summary Update Trigger**
    - Create person nodes with related nodes
    - Add new related nodes and verify summaries are regenerated
    - Test with various types of related nodes

### Unit Testing

**Focus Areas:**

1. **AgeCalculator Edge Cases**
   - Test with null input (should return null)
   - Test with future dates (should return null)
   - Test with leap year dates (Feb 29)
   - Test with year-only format
   - Test with today's date (should return 0)

2. **PersonProfileService Summary Generation**
   - Test with empty related nodes (should return null summaries)
   - Test with only physical attribute nodes
   - Test with only personality nodes
   - Test with mixed node types
   - Test categorization logic (isPhysicalAttribute, isPersonalityTrait)

3. **PersonProfileRepository CRUD Operations**
   - Test create profile with valid data
   - Test update profile with partial data
   - Test get profile that doesn't exist (should return null)
   - Test delete profile
   - Test get relationships with no edges

4. **Database Migration**
   - Test migration from version 24 to 25
   - Verify existing MemoryNodeEntity data is preserved
   - Verify new tables are created with correct schema
   - Verify indices are created

5. **UI Component Behavior**
   - Test PersonProfileBanner opens on tap
   - Test edit mode toggle
   - Test save button enables/disables based on validation
   - Test cancel button discards changes
   - Test image picker integration

6. **Error Handling**
   - Test foreign key violation handling
   - Test AI service failure handling
   - Test invalid date format handling
   - Test concurrent update handling
   - Test node deletion during profile view

7. **Special Entity Handling**
   - Test User_Entity uses user avatar
   - Test Character_Entity uses character avatar
   - Test regular person entities use custom images

8. **Haptic Feedback**
   - Test HapticPattern.Pop on person card tap
   - Test HapticPattern.Pop on edit button tap
   - Test HapticPattern.Success on save
   - Test HapticPattern.Pop on cancel

### Integration Testing

**Test Flows:**

1. **Complete Profile Creation Flow**
   - Create person node → verify profile created → add related nodes → generate summaries → verify summaries stored with sources

2. **Profile Edit Flow**
   - Open profile → enter edit mode → modify fields → save → verify persistence → verify UI updates

3. **Relationship Display Flow**
   - Create multiple person nodes → create edges between them → open profile → verify relationships displayed

4. **Image Upload Flow**
   - Open profile → enter edit mode → select image → save → verify image URI stored → verify image displayed

5. **Summary Regeneration Flow**
   - Create profile with summaries → add new related nodes → trigger regeneration → verify summaries updated → verify new sources tracked

### Performance Testing

**Benchmarks:**

1. **Profile Load Time**
   - Target: <100ms to load profile with all related data
   - Test with profiles having 0, 10, 50, 100 related nodes

2. **Summary Generation Time**
   - Target: <5 seconds for summary generation
   - Test with varying numbers of related nodes (1, 10, 50)

3. **Relationship Query Performance**
   - Target: <50ms to query all relationships for a person
   - Test with persons having 0, 10, 50, 100 relationships

4. **Database Query Performance**
   - Verify indices are used for common queries
   - Test query plans for getByNodeId, getAllForAssistant

### Test Data Generators

**For Property-Based Testing:**

```kotlin
// Generate random PersonProfileEntity
fun Arb.Companion.personProfile(): Arb<PersonProfileEntity> = arbitrary {
    PersonProfileEntity(
        id = Arb.int(1..10000).bind(),
        nodeId = Arb.int(1..10000).bind(),
        assistantId = Arb.uuid().bind().toString(),
        dateOfBirth = Arb.choice(
            Arb.string().filter { it.matches(Regex("\\d{4}")) }, // Year only
            Arb.string().filter { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }, // Full date
            Arb.constant(null)
        ).bind(),
        physicalSummary = Arb.string(0..500).orNull().bind(),
        personalitySummary = Arb.string(0..500).orNull().bind(),
        otherInfoSummary = Arb.string(0..500).orNull().bind(),
        imageUri = Arb.string().orNull().bind(),
        lastUpdated = Arb.long(0..System.currentTimeMillis()).bind()
    )
}

// Generate random date of birth
fun Arb.Companion.dateOfBirth(): Arb<String> = arbitrary {
    val year = Arb.int(1900..2024).bind()
    val month = Arb.int(1..12).bind()
    val day = Arb.int(1..28).bind() // Avoid month-end edge cases
    
    Arb.choice(
        Arb.constant(year.toString()), // Year only
        Arb.constant("%04d-%02d-%02d".format(year, month, day)) // Full date
    ).bind()
}
```

### Continuous Integration

**CI Pipeline:**
1. Run all unit tests on every commit
2. Run property tests (with reduced iterations: 20) on every PR
3. Run full property tests (100 iterations) on main branch merges
4. Run integration tests on every PR
5. Run performance benchmarks weekly

**Coverage Goals:**
- Unit test coverage: >80% for repository and service layers
- Property test coverage: All 13 properties implemented
- Integration test coverage: All 5 major flows tested

