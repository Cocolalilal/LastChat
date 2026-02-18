# Implementation Plan: Enhanced Person Profiles

## Overview

This implementation plan breaks down the enhanced person profiles feature into discrete, incremental coding tasks. Each task builds on previous work, with testing integrated throughout to catch errors early. The implementation follows the existing LastChat architecture patterns and adheres to AGENTS.md guidelines for crash resistance, haptics, and Material You 3 design.

## Tasks

- [x] 1. Create database entities and schema
  - Create PersonProfileEntity with all fields and foreign key to MemoryNodeEntity
  - Create ProfileSummarySourceEntity with foreign keys to PersonProfileEntity and MemoryNodeEntity
  - Add appropriate indices for query performance
  - Create database migration from version 24 to 25
  - _Requirements: 1.1, 1.2, 1.6, 9.1, 9.2, 9.3, 9.4_

- [ ]* 1.1 Write property test for profile data round trip
  - **Property 1: Profile Data Round Trip**
  - **Validates: Requirements 1.1, 1.4**

- [ ]* 1.2 Write property test for cascade delete integrity
  - **Property 4: Cascade Delete Integrity**
  - **Validates: Requirements 1.6, 9.5**

- [x] 2. Implement PersonProfileDAO
  - Create DAO interface with insert, update, query, and delete operations
  - Add methods for summary source operations
  - Add Flow-based queries for reactive updates
  - Register DAO in AppDatabase
  - _Requirements: 1.1, 1.2_

- [ ]* 2.1 Write unit tests for DAO operations
  - Test CRUD operations
  - Test query methods with various filters
  - Test Flow emissions on data changes
  - _Requirements: 1.1, 1.2_

- [x] 3. Implement AgeCalculator utility
  - Create AgeCalculator object with calculateAge method
  - Support year-only format (YYYY) assuming January 1st
  - Support full date format (YYYY-MM-DD)
  - Handle null input by returning null
  - Handle future dates by returning null
  - Handle leap years correctly
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_

- [ ]* 3.1 Write property test for age calculation correctness
  - **Property 5: Age Calculation Correctness**
  - **Validates: Requirements 2.1, 2.2**

- [ ]* 3.2 Write unit tests for AgeCalculator edge cases
  - Test null input returns null
  - Test future dates return null
  - Test leap year dates (Feb 29)
  - Test today's date returns 0
  - _Requirements: 2.3, 2.4, 2.5_

- [x] 4. Implement PersonProfileRepository
  - Create repository class with constructor dependencies (profileDAO, nodeDAO, edgeDAO)
  - Implement createProfile, getProfile, updateProfile methods using Dispatchers.IO
  - Implement getPersonRelationships method filtering for person-to-person edges
  - Implement setSummarySourceNodes and getSummarySourceNodes methods
  - Use safe null handling (no !! assertions)
  - _Requirements: 1.1, 1.2, 1.4, 4.1, 4.2, 4.4, 10.1, 10.2, 10.3_

- [ ]* 4.1 Write property test for summary source tracking
  - **Property 2: Summary Source Tracking**
  - **Validates: Requirements 1.2, 3.6**

- [ ]* 4.2 Write property test for relationship retrieval completeness
  - **Property 7: Relationship Retrieval Completeness**
  - **Validates: Requirements 4.1, 4.4**

- [ ]* 4.3 Write property test for relationship type support
  - **Property 8: Relationship Type Support**
  - **Validates: Requirements 4.2**

- [ ]* 4.4 Write unit tests for repository CRUD operations
  - Test create profile with valid data
  - Test update profile with partial data
  - Test get profile that doesn't exist returns null
  - Test get relationships with no edges
  - _Requirements: 1.1, 4.1_

- [x] 5. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Implement PersonProfileService
  - Create service class with dependencies (profileRepository, graphMemoryRepository, providerManager, settingsStore)
  - Implement generateProfileSummaries method using Dispatchers.IO
  - Implement isPhysicalAttribute and isPersonalityTrait categorization logic
  - Implement generateSummary method calling AI provider
  - Implement ensureProfileExists method
  - Snapshot StateFlow values before complex transformations
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.6, 10.3, 10.5_

- [ ]* 6.1 Write property test for physical summary excludes age
  - **Property 6: Physical Summary Excludes Age**
  - **Validates: Requirements 3.4**

- [ ]* 6.2 Write unit tests for summary generation
  - Test with empty related nodes returns null summaries
  - Test with only physical attribute nodes
  - Test with only personality nodes
  - Test with mixed node types
  - Test categorization logic
  - _Requirements: 3.1, 3.2, 3.3_

- [ ]* 6.3 Write unit tests for error handling
  - Test AI service failure handling
  - Test token limit exceeded handling
  - _Requirements: 3.1, 3.2, 3.3_

- [x] 7. Register components in Koin DI
  - Add PersonProfileRepository to repositoryModule
  - Add PersonProfileService to repositoryModule (or create new module)
  - Ensure all dependencies are properly injected
  - _Requirements: 11.4_

- [x] 8. Implement automatic profile creation hook
  - Modify GraphMemoryRepository.upsertNode to create profile for person nodes
  - Call PersonProfileService.ensureProfileExists when person node is created
  - Execute on Dispatchers.IO
  - _Requirements: 11.1_

- [ ]* 8.1 Write property test for automatic profile creation
  - **Property 11: Automatic Profile Creation**
  - **Validates: Requirements 11.1**

- [x] 9. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 10. Implement PersonProfileBanner composable
  - Create PersonProfileBanner with ModalBottomSheet using AppShapes.CardLarge
  - Implement loading state with CircularProgressIndicator
  - Use rememberModalBottomSheetState(skipPartiallyExpanded = true)
  - Implement LaunchedEffect to load profile data on Dispatchers.IO
  - Add haptic feedback using rememberPremiumHaptics (HapticPattern.Pop on dismiss)
  - Animate sheet with standard spring (dampingRatio=0.5f, stiffness=400f)
  - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 8.1_

- [x] 11. Implement PersonProfileContent composable
  - Create LazyColumn with profile sections
  - Use derivedStateOf for list items to prevent recompositions
  - Display name, age (calculated), profile picture, relationships, summaries
  - Handle null values gracefully with placeholder text
  - _Requirements: 5.2, 10.4_

- [x] 12. Implement ProfileHeader composable
  - Display profile image with AsyncImage or placeholder
  - Display name and age
  - Support edit mode with TextField for name
  - Support image picker in edit mode
  - Add haptic feedback on image tap (HapticPattern.Pop)
  - _Requirements: 5.2, 7.1, 7.5_

- [x] 13. Implement ProfileActionButtons composable
  - Create edit button with HapticPattern.Pop and scale to 0.85f on press
  - Create save button with HapticPattern.Success on tap
  - Create cancel button with HapticPattern.Pop on tap
  - Use AppShapes.ButtonPill for button shapes
  - Disable save button when validation fails
  - _Requirements: 6.1, 6.2, 8.2, 8.3, 8.4_

- [x] 14. Implement ProfileSection composable
  - Display section title and content
  - Support view mode (read-only text)
  - Support edit mode (TextField or OutlinedTextField)
  - Use Material You 3 styling
  - _Requirements: 5.2, 6.2_

- [x] 15. Implement RelationshipsSection composable
  - Display list of person-to-person relationships
  - Show relationship type and description
  - Add haptic feedback on relationship tap (HapticPattern.Pop)
  - Support navigation to related person's profile
  - _Requirements: 4.3, 5.2_

- [x] 16. Implement date of birth validation
  - Validate format matches "YYYY" or "YYYY-MM-DD"
  - Show inline error for invalid formats
  - Disable save button when format is invalid
  - Show warning for future dates
  - _Requirements: 6.5_

- [ ]* 16.1 Write property test for date format validation
  - **Property 9: Date Format Validation**
  - **Validates: Requirements 6.5**

- [ ]* 16.2 Write property test for date format support
  - **Property 3: Date Format Support**
  - **Validates: Requirements 1.3**

- [x] 17. Implement edit mode save logic
  - Update node name if changed via GraphMemoryRepository
  - Update profile via PersonProfileRepository
  - Execute all operations on Dispatchers.IO
  - Reload data after save
  - Handle errors gracefully
  - _Requirements: 6.3, 6.4_

- [ ]* 17.1 Write property test for edit persistence
  - **Property 10: Edit Persistence**
  - **Validates: Requirements 6.3, 6.4**

- [x] 18. Implement special entity avatar handling
  - Check if nodeId corresponds to User_Entity or Character_Entity
  - Use user avatar for User_Entity profiles
  - Use character avatar for Character_Entity profiles
  - Allow custom images for other person entities
  - _Requirements: 1.5, 7.3, 7.4_

- [ ]* 18.1 Write unit tests for special entity handling
  - Test User_Entity uses user avatar
  - Test Character_Entity uses character avatar
  - Test regular person uses custom image
  - _Requirements: 1.5, 7.3, 7.4_

- [x] 19. Integrate PersonProfileBanner into entities tab
  - Add click handler to person cards in entities tab
  - Open PersonProfileBanner on person card tap
  - Pass nodeId and assistantId to banner
  - Add haptic feedback on card tap (HapticPattern.Pop)
  - _Requirements: 5.1, 8.1_

- [x] 20. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 21. Implement summary regeneration trigger
  - Add listener for person node updates in PersonProfileService
  - Check if new related nodes were added
  - Regenerate summaries if related nodes changed
  - Update summary source tracking
  - _Requirements: 11.2_

- [ ]* 21.1 Write property test for summary update trigger
  - **Property 13: Summary Update Trigger**
  - **Validates: Requirements 11.2**

- [x] 22. Implement backward compatibility verification
  - Run existing MemoryNodeEntity queries
  - Verify results are unchanged with profile system added
  - Test node creation, update, deletion
  - Test edge queries
  - _Requirements: 11.3, 11.5_

- [ ]* 22.1 Write property test for backward compatibility
  - **Property 12: Backward Compatibility**
  - **Validates: Requirements 11.3, 11.5**

- [ ]* 22.2 Write integration tests
  - Test complete profile creation flow
  - Test profile edit flow
  - Test relationship display flow
  - Test image upload flow
  - Test summary regeneration flow
  - _Requirements: All_

- [x] 23. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties
- Unit tests validate specific examples and edge cases
- All I/O operations must use Dispatchers.IO per AGENTS.md
- All haptic feedback must use PremiumHaptics per AGENTS.md
- All UI components must follow Material You 3 Expressive design per AGENTS.md
