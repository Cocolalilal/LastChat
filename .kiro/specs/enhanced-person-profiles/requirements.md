# Requirements Document: Enhanced Person Profiles

## Introduction

LastChat is an Android application built with Kotlin and Jetpack Compose that uses a memory graph system to track entities like people, places, objects, and events. The memory graph consists of MemoryNodeEntity (nodes representing entities) and MemoryEdgeEntity (edges representing relationships between entities).

This feature enhances person entities (nodes with nodeType="person") to become rich profile entities with structured information including biographical data, physical attributes, personality traits, relationships, and images. The enhancement transforms basic person nodes into comprehensive profiles while maintaining compatibility with the existing memory graph architecture.

## Glossary

- **Memory_Graph**: The system of interconnected MemoryNodeEntity and MemoryEdgeEntity records that represent knowledge about entities and their relationships
- **Person_Node**: A MemoryNodeEntity with nodeType="person"
- **Profile**: The enhanced data structure and UI representation for a Person_Node
- **User_Entity**: The special Person_Node representing the user of the application
- **Character_Entity**: The special Person_Node representing the AI assistant
- **Profile_Summary**: AI-generated text summarizing information from related nodes in the graph
- **Related_Node**: A MemoryNodeEntity connected to a Person_Node through MemoryEdgeEntity relationships
- **Profile_Banner**: The bottom sheet UI component that displays a person's profile
- **Age_Calculator**: Component that computes current age from date of birth
- **Profile_Repository**: Data layer component managing profile data persistence and retrieval
- **Profile_Service**: Business logic layer managing profile operations and AI summary generation

## Requirements

### Requirement 1: Profile Data Structure

**User Story:** As a developer, I want person entities to have structured profile data, so that rich biographical and descriptive information can be stored and retrieved efficiently.

#### Acceptance Criteria

1. THE Profile_Repository SHALL store name, date of birth, physical attributes summary, personality traits summary, and other info summary for each Person_Node
2. THE Profile_Repository SHALL store references to Related_Nodes that contributed to each summary field
3. THE Profile_Repository SHALL support year-only or full date (year-month-day) for date of birth
4. THE Profile_Repository SHALL store image URIs for person profile pictures
5. WHEN a Person_Node is the User_Entity or Character_Entity, THE Profile_Repository SHALL automatically use their existing avatar images
6. THE Profile_Repository SHALL maintain referential integrity with MemoryNodeEntity through foreign key constraints

### Requirement 2: Age Calculation

**User Story:** As a user, I want to see a person's current age calculated from their date of birth, so that I have up-to-date age information without manual updates.

#### Acceptance Criteria

1. WHEN a date of birth is stored with full date, THE Age_Calculator SHALL compute age in years based on current date
2. WHEN a date of birth is stored with year only, THE Age_Calculator SHALL compute age assuming January 1st of that year
3. WHEN no date of birth is stored, THE Age_Calculator SHALL return null
4. THE Age_Calculator SHALL handle leap years correctly
5. THE Age_Calculator SHALL handle future dates by returning null or zero

### Requirement 3: Profile Summary Generation

**User Story:** As a user, I want profile summaries to be automatically generated from conversation context, so that person profiles stay current without manual data entry.

#### Acceptance Criteria

1. WHEN Related_Nodes contain information about physical attributes, THE Profile_Service SHALL generate a physical attributes summary
2. WHEN Related_Nodes contain information about personality traits, THE Profile_Service SHALL generate a personality traits summary
3. WHEN Related_Nodes contain miscellaneous information, THE Profile_Service SHALL generate an other info summary
4. THE Profile_Service SHALL ensure physical attributes summary does not include age information
5. THE Profile_Service SHALL ensure each summary type contains distinct, non-overlapping information
6. THE Profile_Service SHALL store references to source Related_Nodes for each generated summary

### Requirement 4: Person-to-Person Relationships

**User Story:** As a user, I want to see how people are related to each other, so that I can understand social connections and family structures.

#### Acceptance Criteria

1. WHEN two Person_Nodes are connected by a MemoryEdgeEntity, THE Profile_Repository SHALL retrieve the relationship
2. THE Profile_Repository SHALL support relationship types including family, friend, colleague, romantic, and custom types
3. WHEN displaying a profile, THE Profile_Banner SHALL show all person-to-person relationships for that Person_Node
4. THE Profile_Repository SHALL support bidirectional relationships with different labels (e.g., "parent of" / "child of")

### Requirement 5: Profile Display UI

**User Story:** As a user, I want to view detailed person profiles, so that I can see all information about a person in one place.

#### Acceptance Criteria

1. WHEN a user taps a person card in the entities tab, THE Profile_Banner SHALL open from the bottom of the screen
2. THE Profile_Banner SHALL display the person's name, age, profile picture, relationships, physical attributes, personality traits, and other info
3. THE Profile_Banner SHALL use Material You 3 Expressive design with AppShapes.CardLarge for the main container
4. THE Profile_Banner SHALL animate in using a spring animation with dampingRatio=0.5f and stiffness=400f
5. WHEN the Profile_Banner is dismissed, THE Profile_Banner SHALL animate out smoothly

### Requirement 6: Profile Editing

**User Story:** As a user, I want to manually edit person profiles, so that I can correct or add information that wasn't captured automatically.

#### Acceptance Criteria

1. WHEN the edit button is tapped in the Profile_Banner, THE Profile_Banner SHALL enter edit mode
2. WHEN in edit mode, THE Profile_Banner SHALL allow editing of name, date of birth, summaries, and image
3. WHEN changes are saved, THE Profile_Repository SHALL persist all changes to the database
4. WHEN changes are saved, THE Profile_Service SHALL update Related_Node references if summaries were modified
5. THE Profile_Banner SHALL validate date of birth format before saving

### Requirement 7: Image Upload Support

**User Story:** As a user, I want to upload profile pictures for people, so that profiles are visually identifiable.

#### Acceptance Criteria

1. WHEN in edit mode, THE Profile_Banner SHALL provide an image picker for selecting profile pictures
2. THE Profile_Repository SHALL store image URIs in the database
3. WHEN a Person_Node is the User_Entity, THE Profile_Repository SHALL use the user's avatar image by default
4. WHEN a Person_Node is the Character_Entity, THE Profile_Repository SHALL use the character's avatar image by default
5. THE Profile_Banner SHALL display a placeholder image when no profile picture is set

### Requirement 8: Haptic Feedback

**User Story:** As a user, I want tactile feedback when interacting with profiles, so that the interface feels responsive and satisfying.

#### Acceptance Criteria

1. WHEN a user taps a person card, THE Profile_Banner SHALL trigger HapticPattern.Pop
2. WHEN the edit button is tapped, THE Profile_Banner SHALL trigger HapticPattern.Pop and scale to 0.85f
3. WHEN the save button is tapped, THE Profile_Banner SHALL trigger HapticPattern.Success
4. WHEN the cancel button is tapped, THE Profile_Banner SHALL trigger HapticPattern.Pop
5. THE Profile_Banner SHALL use PremiumHaptics for all haptic feedback

### Requirement 9: Database Schema

**User Story:** As a developer, I want a proper database schema for profile data, so that data is stored efficiently with referential integrity.

#### Acceptance Criteria

1. THE Profile_Repository SHALL create a PersonProfileEntity table with foreign key to MemoryNodeEntity
2. THE Profile_Repository SHALL create a ProfileSummarySourceEntity table linking summaries to Related_Nodes
3. THE Profile_Repository SHALL use Room database migrations to add new tables without data loss
4. THE Profile_Repository SHALL create appropriate indices for query performance
5. THE Profile_Repository SHALL handle cascade deletes when a Person_Node is deleted

### Requirement 10: Crash Resistance

**User Story:** As a user, I want the profile system to be stable and crash-resistant, so that the app remains reliable.

#### Acceptance Criteria

1. THE Profile_Repository SHALL use safe JSON handling with jsonPrimitiveOrNull for all JSON operations
2. THE Profile_Repository SHALL never use non-null assertions (!!) on nullable values
3. THE Profile_Service SHALL execute all I/O operations on Dispatchers.IO
4. THE Profile_Banner SHALL use derivedStateOf for LazyColumn items to prevent unnecessary recompositions
5. THE Profile_Service SHALL snapshot StateFlow values before complex transformations to avoid race conditions

### Requirement 11: Integration with Existing Systems

**User Story:** As a developer, I want the profile system to integrate seamlessly with existing memory graph features, so that no existing functionality is broken.

#### Acceptance Criteria

1. WHEN a Person_Node is created through existing memory graph operations, THE Profile_Repository SHALL create a default profile entry
2. WHEN a Person_Node is updated through existing operations, THE Profile_Service SHALL check if profile summaries need regeneration
3. THE Profile_Repository SHALL maintain compatibility with existing MemoryNodeEntity queries and operations
4. THE Profile_Service SHALL use Koin dependency injection consistent with existing architecture
5. THE Profile_Repository SHALL preserve all existing MemoryNodeEntity fields and behavior
