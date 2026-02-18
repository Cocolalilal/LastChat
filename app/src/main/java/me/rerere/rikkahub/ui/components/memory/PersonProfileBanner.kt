package me.rerere.rikkahub.ui.components.memory

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.db.entity.MemoryNodeEntity
import me.rerere.rikkahub.data.db.entity.PersonProfileEntity
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.repository.GraphMemoryRepository
import me.rerere.rikkahub.data.repository.PersonProfileRepository
import me.rerere.rikkahub.data.repository.PersonRelationship
import me.rerere.rikkahub.service.PersonProfileService
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.PremiumHaptics
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import org.koin.compose.koinInject

/**
 * PersonProfileBanner displays a person's profile in a modal bottom sheet.
 * 
 * Features:
 * - Loading state with CircularProgressIndicator
 * - Displays profile information (name, age, image, relationships, summaries)
 * - Edit mode for modifying profile data
 * - Premium haptic feedback on interactions
 * - Smooth animations using standard spring
 * 
 * @param nodeId The ID of the person node to display
 * @param assistantId The assistant identifier
 * @param onDismiss Callback when the sheet is dismissed
 * @param modifier Optional modifier for the sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    
    val profileService: PersonProfileService = koinInject()
    val profileRepository: PersonProfileRepository = koinInject()
    val graphRepository: GraphMemoryRepository = koinInject()
    val settingsStore: SettingsStore = koinInject()
    
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
    
    // Validation state for date of birth
    var dateOfBirthError by remember { mutableStateOf<String?>(null) }
    var dateOfBirthWarning by remember { mutableStateOf<String?>(null) }
    
    // Validate date of birth format
    fun validateDateOfBirth(date: String) {
        if (date.isBlank()) {
            dateOfBirthError = null
            dateOfBirthWarning = null
            return
        }
        
        // Check format: YYYY or YYYY-MM-DD
        val yearOnlyRegex = Regex("^\\d{4}$")
        val fullDateRegex = Regex("^\\d{4}-\\d{2}-\\d{2}$")
        
        when {
            !yearOnlyRegex.matches(date) && !fullDateRegex.matches(date) -> {
                dateOfBirthError = "Invalid format. Use YYYY or YYYY-MM-DD"
                dateOfBirthWarning = null
            }
            else -> {
                dateOfBirthError = null
                // Check for future dates
                try {
                    val now = java.time.LocalDate.now()
                    val birthDate = when {
                        yearOnlyRegex.matches(date) -> {
                            java.time.LocalDate.of(date.toInt(), 1, 1)
                        }
                        else -> {
                            java.time.LocalDate.parse(date)
                        }
                    }
                    
                    if (birthDate.isAfter(now)) {
                        dateOfBirthWarning = "Date is in the future"
                    } else {
                        dateOfBirthWarning = null
                    }
                } catch (e: Exception) {
                    dateOfBirthError = "Invalid date"
                    dateOfBirthWarning = null
                }
            }
        }
    }
    
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
                fadeIn(
                    animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f)
                ) togetherWith fadeOut(
                    animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f)
                )
            },
            label = "loading_animation"
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
                    assistantId = assistantId,
                    settingsStore = settingsStore,
                    isEditMode = isEditMode,
                    editName = editName,
                    editDateOfBirth = editDateOfBirth,
                    editPhysicalSummary = editPhysicalSummary,
                    editPersonalitySummary = editPersonalitySummary,
                    editOtherInfo = editOtherInfo,
                    editImageUri = editImageUri,
                    dateOfBirthError = dateOfBirthError,
                    dateOfBirthWarning = dateOfBirthWarning,
                    isSaveEnabled = dateOfBirthError == null && editName.isNotBlank(),
                    onEditModeChange = { enabled ->
                        if (enabled) {
                            // Enter edit mode - populate edit fields
                            editName = personNode?.name ?: ""
                            editDateOfBirth = profile?.dateOfBirth ?: ""
                            editPhysicalSummary = profile?.physicalSummary ?: ""
                            editPersonalitySummary = profile?.personalitySummary ?: ""
                            editOtherInfo = profile?.otherInfoSummary ?: ""
                            editImageUri = profile?.imageUri
                            // Reset validation state
                            dateOfBirthError = null
                            dateOfBirthWarning = null
                        }
                        isEditMode = enabled
                        haptics.perform(HapticPattern.Pop)
                    },
                    onSave = {
                        scope.launch {
                            try {
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
                                        
                                        // Clear summary source tracking for manually edited summaries
                                        // When a user manually edits a summary, it's no longer AI-generated
                                        // from specific source nodes, so we clear the source references
                                        if (editPhysicalSummary != currentProfile.physicalSummary) {
                                            profileRepository.setSummarySourceNodes(
                                                currentProfile.id,
                                                me.rerere.rikkahub.data.db.entity.SummaryType.PHYSICAL,
                                                emptyList()
                                            )
                                        }
                                        if (editPersonalitySummary != currentProfile.personalitySummary) {
                                            profileRepository.setSummarySourceNodes(
                                                currentProfile.id,
                                                me.rerere.rikkahub.data.db.entity.SummaryType.PERSONALITY,
                                                emptyList()
                                            )
                                        }
                                        if (editOtherInfo != currentProfile.otherInfoSummary) {
                                            profileRepository.setSummarySourceNodes(
                                                currentProfile.id,
                                                me.rerere.rikkahub.data.db.entity.SummaryType.OTHER,
                                                emptyList()
                                            )
                                        }
                                    }
                                    
                                    // Reload data
                                    personNode = graphRepository.getNodeById(nodeId)
                                    profile = profileRepository.getProfile(nodeId)
                                }
                                isEditMode = false
                                haptics.perform(HapticPattern.Success)
                            } catch (e: Exception) {
                                // Log error and keep edit mode open so user can retry
                                android.util.Log.e("PersonProfileBanner", "Failed to save profile", e)
                                // TODO: Show error message to user via SnackBar or Toast
                            }
                        }
                    },
                    onCancel = {
                        isEditMode = false
                        dateOfBirthError = null
                        dateOfBirthWarning = null
                        haptics.perform(HapticPattern.Pop)
                    },
                    onNameChange = { editName = it },
                    onDateOfBirthChange = { 
                        editDateOfBirth = it
                        validateDateOfBirth(it)
                    },
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
    assistantId: String,
    settingsStore: SettingsStore,
    isEditMode: Boolean,
    editName: String,
    editDateOfBirth: String,
    editPhysicalSummary: String,
    editPersonalitySummary: String,
    editOtherInfo: String,
    editImageUri: String?,
    dateOfBirthError: String?,
    dateOfBirthWarning: String?,
    isSaveEnabled: Boolean,
    onEditModeChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onNameChange: (String) -> Unit,
    onDateOfBirthChange: (String) -> Unit,
    onPhysicalSummaryChange: (String) -> Unit,
    onPersonalitySummaryChange: (String) -> Unit,
    onOtherInfoChange: (String) -> Unit,
    onImageUriChange: (String?) -> Unit,
    haptics: me.rerere.rikkahub.ui.hooks.PremiumHaptics
) {
    // Calculate age using derivedStateOf to prevent unnecessary recompositions
    val age = remember(profile?.dateOfBirth) {
        me.rerere.rikkahub.utils.AgeCalculator.calculateAge(profile?.dateOfBirth)
    }
    
    // Use derivedStateOf for list items to prevent recompositions per AGENTS.MD requirement 10.4
    val hasRelationships = remember { androidx.compose.runtime.derivedStateOf { relationships.isNotEmpty() } }
    
    // Determine effective avatar based on special entity type
    val effectiveAvatar = getEffectiveAvatar(
        personNode = personNode,
        profile = profile,
        assistantId = assistantId,
        settingsStore = settingsStore
    )
    
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with image, name, and age
        item(key = "header") {
            ProfileHeader(
                name = if (isEditMode) editName else (personNode?.name ?: "Unknown"),
                age = age,
                imageUri = if (isEditMode) editImageUri else effectiveAvatar.value,
                isEditMode = isEditMode,
                onNameChange = onNameChange,
                onImageUriChange = onImageUriChange,
                haptics = haptics
            )
        }
        
        // Edit/Save/Cancel buttons
        item(key = "action_buttons") {
            ProfileActionButtons(
                isEditMode = isEditMode,
                onEditModeChange = onEditModeChange,
                onSave = onSave,
                onCancel = onCancel,
                haptics = haptics,
                isSaveEnabled = isSaveEnabled
            )
        }
        
        // Date of Birth section
        item(key = "date_of_birth") {
            ProfileSection(
                title = "Date of Birth",
                content = if (isEditMode) editDateOfBirth else (profile?.dateOfBirth ?: "Not set"),
                isEditMode = isEditMode,
                onContentChange = onDateOfBirthChange,
                placeholder = "YYYY or YYYY-MM-DD",
                errorMessage = if (isEditMode) dateOfBirthError else null,
                warningMessage = if (isEditMode) dateOfBirthWarning else null
            )
        }
        
        // Relationships section (only if relationships exist)
        if (hasRelationships.value) {
            item(key = "relationships") {
                RelationshipsSection(
                    relationships = relationships,
                    haptics = haptics
                )
            }
        }
        
        // Physical Attributes section
        item(key = "physical_attributes") {
            ProfileSection(
                title = "Physical Attributes",
                content = if (isEditMode) editPhysicalSummary else (profile?.physicalSummary ?: "No information"),
                isEditMode = isEditMode,
                onContentChange = onPhysicalSummaryChange,
                placeholder = "Describe physical attributes..."
            )
        }
        
        // Personality section
        item(key = "personality") {
            ProfileSection(
                title = "Personality",
                content = if (isEditMode) editPersonalitySummary else (profile?.personalitySummary ?: "No information"),
                isEditMode = isEditMode,
                onContentChange = onPersonalitySummaryChange,
                placeholder = "Describe personality traits..."
            )
        }
        
        // Other Information section
        item(key = "other_info") {
            ProfileSection(
                title = "Other Information",
                content = if (isEditMode) editOtherInfo else (profile?.otherInfoSummary ?: "No information"),
                isEditMode = isEditMode,
                onContentChange = onOtherInfoChange,
                placeholder = "Other information..."
            )
        }
        
        // Bottom spacing
        item(key = "bottom_spacer") {
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * ProfileHeader displays the profile image, name, and age.
 * 
 * In view mode:
 * - Shows profile image or placeholder icon
 * - Displays name and age as text
 * - Image is tappable with haptic feedback
 * 
 * In edit mode:
 * - Shows image picker on image tap
 * - Name is editable via TextField
 * - Haptic feedback on all interactions
 * 
 * @param name The person's name
 * @param age The person's calculated age (nullable)
 * @param imageUri The URI of the profile image (nullable)
 * @param isEditMode Whether the header is in edit mode
 * @param onNameChange Callback when name is changed in edit mode
 * @param onImageUriChange Callback when image is selected in edit mode
 * @param haptics PremiumHaptics instance for tactile feedback
 */
@Composable
private fun ProfileHeader(
    name: String,
    age: Int?,
    imageUri: String?,
    isEditMode: Boolean,
    onNameChange: (String) -> Unit,
    onImageUriChange: (String?) -> Unit,
    haptics: PremiumHaptics
) {
    // Image picker launcher for edit mode
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            onImageUriChange(it.toString())
        }
    }
    
    // Scale animation for image tap
    var isImagePressed by remember { mutableStateOf(false) }
    val imageScale by animateFloatAsState(
        targetValue = if (isImagePressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "image_scale"
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Profile Image
        Box(
            modifier = Modifier
                .size(80.dp)
                .scale(imageScale)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(enabled = isEditMode) {
                    isImagePressed = true
                    haptics.perform(HapticPattern.Pop)
                    imagePickerLauncher.launch(
                        PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                    // Reset press state after a short delay
                    isImagePressed = false
                },
            contentAlignment = Alignment.Center
        ) {
            if (imageUri != null) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = "Profile picture",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                )
            } else {
                // Placeholder icon
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = "No profile picture",
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // Name and Age
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (isEditMode) {
                // Editable name field
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.CardMedium
                )
            } else {
                // Display name
                Text(
                    text = name,
                    style = MaterialTheme.typography.headlineMedium
                )
            }
            
            // Display age (read-only in both modes)
            if (age != null) {
                Text(
                    text = "$age years old",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// Placeholder composables for sections that will be implemented in subsequent tasks
@Composable
private fun ProfileHeaderPlaceholder(
    name: String,
    age: Int?,
    imageUri: String?,
    isEditMode: Boolean
) {
    androidx.compose.material3.Text(
        text = buildString {
            append(name)
            age?.let { append(", $it years old") }
        },
        style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

/**
 * ProfileActionButtons displays edit/save/cancel buttons for the profile.
 * 
 * Features:
 * - Edit button with HapticPattern.Pop and scale animation (0.85f on press)
 * - Save button with HapticPattern.Success on tap
 * - Cancel button with HapticPattern.Pop on tap
 * - Uses AppShapes.ButtonPill for button shapes
 * - Save button can be disabled when validation fails
 * 
 * Requirements: 6.1, 6.2, 8.2, 8.3, 8.4
 * 
 * @param isEditMode Whether the profile is in edit mode
 * @param onEditModeChange Callback to toggle edit mode
 * @param onSave Callback when save button is tapped
 * @param onCancel Callback when cancel button is tapped
 * @param haptics PremiumHaptics instance for tactile feedback
 * @param isSaveEnabled Whether the save button should be enabled (default: true)
 */
@Composable
private fun ProfileActionButtons(
    isEditMode: Boolean,
    onEditModeChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    haptics: PremiumHaptics,
    isSaveEnabled: Boolean = true
) {
    // Scale animation states for buttons
    var isEditPressed by remember { mutableStateOf(false) }
    var isSavePressed by remember { mutableStateOf(false) }
    var isCancelPressed by remember { mutableStateOf(false) }
    
    val editScale by animateFloatAsState(
        targetValue = if (isEditPressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "edit_scale"
    )
    
    val saveScale by animateFloatAsState(
        targetValue = if (isSavePressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "save_scale"
    )
    
    val cancelScale by animateFloatAsState(
        targetValue = if (isCancelPressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "cancel_scale"
    )
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isEditMode) {
            // Save button
            androidx.compose.material3.Button(
                onClick = {
                    isSavePressed = true
                    haptics.perform(HapticPattern.Success)
                    onSave()
                    // Reset press state
                    isSavePressed = false
                },
                enabled = isSaveEnabled,
                shape = AppShapes.ButtonPill,
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer {
                        scaleX = saveScale
                        scaleY = saveScale
                    }
            ) {
                Text("Save")
            }
            
            // Cancel button
            androidx.compose.material3.OutlinedButton(
                onClick = {
                    isCancelPressed = true
                    haptics.perform(HapticPattern.Pop)
                    onCancel()
                    // Reset press state
                    isCancelPressed = false
                },
                shape = AppShapes.ButtonPill,
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer {
                        scaleX = cancelScale
                        scaleY = cancelScale
                    }
            ) {
                Text("Cancel")
            }
        } else {
            // Edit button
            androidx.compose.material3.Button(
                onClick = {
                    isEditPressed = true
                    haptics.perform(HapticPattern.Pop)
                    onEditModeChange(true)
                    // Reset press state
                    isEditPressed = false
                },
                shape = AppShapes.ButtonPill,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = editScale
                        scaleY = editScale
                    }
            ) {
                Text("Edit")
            }
        }
    }
}

/**
 * ProfileSection displays a section with a title and content.
 * 
 * Features:
 * - View mode: Displays title and content as read-only text
 * - Edit mode: Displays title and OutlinedTextField for editing
 * - Material You 3 styling with AppShapes.CardMedium
 * - Supports multiline content in edit mode
 * - Placeholder text for empty fields in edit mode
 * - Error and warning message support for validation
 * 
 * Requirements: 5.2, 6.2, 6.5
 * 
 * @param title The section title (e.g., "Date of Birth", "Physical Attributes")
 * @param content The section content to display or edit
 * @param isEditMode Whether the section is in edit mode
 * @param onContentChange Callback when content is changed in edit mode
 * @param placeholder Placeholder text for the edit field
 * @param errorMessage Optional error message to display (red text)
 * @param warningMessage Optional warning message to display (orange text)
 * @param modifier Optional modifier for the section
 */
@Composable
private fun ProfileSection(
    title: String,
    content: String,
    isEditMode: Boolean,
    onContentChange: (String) -> Unit,
    placeholder: String = "",
    errorMessage: String? = null,
    warningMessage: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Section title
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        if (isEditMode) {
            // Edit mode: Show OutlinedTextField
            OutlinedTextField(
                value = content,
                onValueChange = onContentChange,
                placeholder = { 
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium
                    ) 
                },
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.CardMedium,
                textStyle = MaterialTheme.typography.bodyMedium,
                minLines = 2,
                maxLines = 6,
                isError = errorMessage != null,
                supportingText = {
                    when {
                        errorMessage != null -> {
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        warningMessage != null -> {
                            Text(
                                text = warningMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            )
        } else {
            // View mode: Show read-only text
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProfileSectionPlaceholder(
    title: String,
    content: String,
    isEditMode: Boolean
) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        androidx.compose.material3.Text(
            text = title,
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium
        )
        androidx.compose.material3.Text(
            text = content,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * RelationshipsSection displays a list of person-to-person relationships.
 * 
 * Features:
 * - Displays relationship type and description for each relationship
 * - Shows the related person's name
 * - Haptic feedback (HapticPattern.Pop) on relationship tap
 * - Supports navigation to related person's profile (TODO: implement navigation)
 * - Material You 3 styling with AppShapes.CardMedium
 * 
 * Requirements: 4.3, 5.2
 * 
 * @param relationships List of PersonRelationship objects to display
 * @param haptics PremiumHaptics instance for tactile feedback
 * @param modifier Optional modifier for the section
 */
@Composable
private fun RelationshipsSection(
    relationships: List<PersonRelationship>,
    haptics: PremiumHaptics,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Section title
        Text(
            text = "Relationships",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        // Relationships list
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            relationships.forEach { relationship ->
                RelationshipItem(
                    relationship = relationship,
                    haptics = haptics
                )
            }
        }
    }
}

/**
 * RelationshipItem displays a single relationship with haptic feedback on tap.
 * 
 * Features:
 * - Shows related person's name
 * - Displays relationship type and description
 * - Scale animation (0.85f) on press with bouncy spring
 * - HapticPattern.Pop on tap
 * - Clickable to navigate to related person's profile
 * - Material You 3 styling with surface container
 * 
 * @param relationship The PersonRelationship to display
 * @param haptics PremiumHaptics instance for tactile feedback
 * @param modifier Optional modifier for the item
 */
@Composable
private fun RelationshipItem(
    relationship: PersonRelationship,
    haptics: PremiumHaptics,
    modifier: Modifier = Modifier
) {
    // Scale animation for tap feedback
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "relationship_scale"
    )
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(AppShapes.CardMedium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable {
                isPressed = true
                haptics.perform(HapticPattern.Pop)
                // TODO: Navigate to related person's profile
                // For now, just provide haptic feedback
                // Navigation will be implemented when profile navigation is added
                isPressed = false
            }
            .padding(12.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Related person's name
            Text(
                text = relationship.personName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            // Relationship type
            Text(
                text = relationship.relationType,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            
            // Relationship description (if available)
            if (relationship.description.isNotBlank()) {
                Text(
                    text = relationship.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RelationshipsSectionPlaceholder(
    relationshipCount: Int
) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        androidx.compose.material3.Text(
            text = "Relationships",
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium
        )
        androidx.compose.material3.Text(
            text = "$relationshipCount relationship(s)",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Determines the effective avatar for a person profile.
 * 
 * Special entity handling (Requirements 1.5, 7.3, 7.4):
 * - User_Entity: Uses user avatar from settings (displaySetting.userAvatar)
 * - Character_Entity: Uses character/assistant avatar from assistant data
 * - Other person entities: Uses custom image from profile.imageUri
 * 
 * Identification logic:
 * - User_Entity: Person node name matches user nickname from settings
 * - Character_Entity: Person node name matches assistant name
 * - Regular person: All other person nodes
 * 
 * @param personNode The person node entity
 * @param profile The person profile entity
 * @param assistantId The assistant identifier
 * @param settingsStore Settings store to access user and assistant data
 * @return The effective avatar URI string, or null if no avatar is set
 */
@Composable
private fun getEffectiveAvatar(
    personNode: MemoryNodeEntity?,
    profile: PersonProfileEntity?,
    assistantId: String,
    settingsStore: SettingsStore
): String? {
    // Get settings to check for special entities
    val settings by settingsStore.settingsFlow.collectAsState(initial = null)
    val currentSettings = settings ?: return profile?.imageUri
    
    val personName = personNode?.name ?: return profile?.imageUri
    
    // Check if this is the User_Entity (matches user nickname)
    val userNickname = currentSettings.displaySetting.userNickname
    if (userNickname.isNotBlank() && personName.equals(userNickname, ignoreCase = true)) {
        // This is the user entity - use user avatar
        return when (val userAvatar = currentSettings.displaySetting.userAvatar) {
            is Avatar.Image -> userAvatar.url
            else -> null // For Emoji, Dummy, Resource - return null to show placeholder
        }
    }
    
    // Check if this is the Character_Entity (matches assistant name)
    val assistant = try {
        currentSettings.getAssistantById(kotlin.uuid.Uuid.parse(assistantId))
    } catch (e: Exception) {
        null
    }
    
    if (assistant != null && personName.equals(assistant.name, ignoreCase = true)) {
        // This is the character entity - use assistant avatar
        return when (val assistantAvatar = assistant.avatar) {
            is Avatar.Image -> assistantAvatar.url
            else -> null // For Emoji, Dummy, Resource - return null to show placeholder
        }
    }
    
    // Regular person entity - use custom image from profile
    return profile?.imageUri
}
