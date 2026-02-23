package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiPeople
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import me.rerere.rikkahub.data.db.entity.MemoryEdgeEntity
import me.rerere.rikkahub.data.db.entity.MemoryNodeEntity
import me.rerere.rikkahub.data.db.entity.PersonProfileEntity
import me.rerere.rikkahub.data.db.entity.PersonRelationship
import me.rerere.rikkahub.data.db.entity.PersonRelationType
import me.rerere.rikkahub.data.db.entity.RelationType
import me.rerere.rikkahub.data.db.entity.CategorizedAttribute
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import java.util.Calendar
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PersonProfileSheet(
    nodeId: Int,
    node: MemoryNodeEntity,
    vm: AssistantDetailVM,
    userAvatar: Avatar,
    characterAvatar: Avatar,
    allPersonNodes: List<MemoryNodeEntity>,
    onDismiss: () -> Unit,
) {
    val haptics = rememberPremiumHaptics()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val profile by vm.getProfileFlow(nodeId).collectAsState(initial = null)

    var isEditing by remember { mutableStateOf(false) }

    // Loaded data
    var relationships by remember { mutableStateOf<List<MemoryEdgeEntity>>(emptyList()) }

    LaunchedEffect(nodeId) {
        vm.getPersonRelationships(nodeId) { relationships = it }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = AppShapes.BottomSheet,
        dragHandle = null,
    ) {
        val p = profile
        if (p == null) {
            // Profile not created yet — show minimal info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    node.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    node.description.ifBlank { "No profile data yet." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (isEditing) {
            ProfileEditMode(
                profile = p,
                onSave = { updated ->
                    vm.updateProfile(updated)
                    haptics.perform(HapticPattern.Success)
                    isEditing = false
                },
                onCancel = {
                    haptics.perform(HapticPattern.Cancel)
                    isEditing = false
                },
            )
        } else {
            ProfileViewMode(
                profile = p,
                node = node,
                userAvatar = userAvatar,
                characterAvatar = characterAvatar,
                relationships = relationships,
                allPersonNodes = allPersonNodes,
                calculateAge = { year, month, day -> vm.calculateAge(year, month, day) },
                onEdit = {
                    haptics.perform(HapticPattern.Pop)
                    isEditing = true
                },
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// VIEW MODE
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileViewMode(
    profile: PersonProfileEntity,
    node: MemoryNodeEntity,
    userAvatar: Avatar,
    characterAvatar: Avatar,
    relationships: List<MemoryEdgeEntity>,
    allPersonNodes: List<MemoryNodeEntity>,
    calculateAge: (Int?, Int?, Int?) -> Int?,
    onEdit: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val age = calculateAge(profile.birthYear, profile.birthMonth, profile.birthDay)
    val lenientJson = remember { kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 24.dp)
            .navigationBarsPadding()
            .animateContentSize(animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f)),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ─── Header: Avatar + Name + Edit ────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar
            ProfileAvatar(
                profile = profile,
                userAvatar = userAvatar,
                characterAvatar = characterAvatar,
                size = 64,
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.displayName.ifBlank { node.name },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                if (profile.isUserProfile) {
                    ProfileBadge("You")
                } else if (profile.isCharacterProfile) {
                    ProfileBadge("Character")
                }
            }

            // Edit button with spring press scale
            HapticIconButton(
                onClick = onEdit,
                icon = Icons.Rounded.Edit,
                contentDescription = "Edit profile",
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        // ─── Birthday (conditional) ──────────────────────────────────
        if (profile.birthYear != null) {
            ProfileSection(
                icon = Icons.Rounded.Cake,
                title = "Birthday",
            ) {
                val dateStr = buildString {
                    if (profile.birthMonth != null && profile.birthDay != null) {
                        append(monthName(profile.birthMonth))
                        append(" ${profile.birthDay}, ")
                    } else if (profile.birthMonth != null) {
                        append(monthName(profile.birthMonth))
                        append(", ")
                    }
                    append("${profile.birthYear}")
                }
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (age != null) {
                    Text(
                        text = "$age years old",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ─── Pronouns / Occupation / Location (inline) ────────────────
        val hasBasicInfo = profile.pronouns.isNotBlank() || profile.occupation.isNotBlank() || profile.location.isNotBlank()
        if (hasBasicInfo) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (profile.pronouns.isNotBlank()) {
                    InfoChip(Icons.Rounded.Person, profile.pronouns)
                }
                if (profile.occupation.isNotBlank()) {
                    InfoChip(Icons.Rounded.Work, profile.occupation)
                }
                if (profile.location.isNotBlank()) {
                    InfoChip(Icons.Rounded.LocationOn, profile.location)
                }
            }
        }

        // ─── Interests (conditional) ───────────────────────────────────
        val interests = try {
            JsonInstant.decodeFromString<List<String>>(profile.interestsJson)
        } catch (e: Exception) { emptyList() }
        if (interests.isNotEmpty()) {
            ProfileSection(
                icon = Icons.Rounded.Favorite,
                title = "Interests",
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    interests.forEach { interest ->
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = AppShapes.Chip,
                        ) {
                            Text(
                                interest,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
            }
        }

        // ─── Person Relationships (from profile JSON) ────────────────
        val profileRelationships = try {
            JsonInstant.decodeFromString<List<PersonRelationship>>(profile.relationshipsJson)
        } catch (e: Exception) { emptyList() }
        if (profileRelationships.isNotEmpty()) {
            ProfileSection(
                icon = Icons.Rounded.Group,
                title = "Relationships",
            ) {
                // Group by category
                val familyRels = profileRelationships.filter { it.relationType in PersonRelationType.FAMILY }
                val socialRels = profileRelationships.filter { it.relationType in PersonRelationType.SOCIAL }
                val professionalRels = profileRelationships.filter { it.relationType in PersonRelationType.PROFESSIONAL }
                val romanticRels = profileRelationships.filter { it.relationType in PersonRelationType.ROMANTIC }
                val otherRels = profileRelationships.filter { 
                    it.relationType !in PersonRelationType.FAMILY &&
                    it.relationType !in PersonRelationType.SOCIAL &&
                    it.relationType !in PersonRelationType.PROFESSIONAL &&
                    it.relationType !in PersonRelationType.ROMANTIC
                }
                
                @Composable
                fun RelationshipGroup(title: String, rels: List<PersonRelationship>) {
                    if (rels.isEmpty()) return
                    Text(
                        title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rels.forEach { rel ->
                            val label = rel.relationLabel ?: rel.relationType.replace("_", " ")
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = AppShapes.Chip,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Rounded.Person,
                                        null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    )
                                    Text(
                                        "${rel.targetName} · $label",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                
                RelationshipGroup("Family", familyRels)
                RelationshipGroup("Social", socialRels)
                RelationshipGroup("Professional", professionalRels)
                RelationshipGroup("Romantic", romanticRels)
                RelationshipGroup("Other", otherRels)
            }
        }

        // ─── Legacy Relationships from edges (only if no profile relationships) ──
        if (relationships.isNotEmpty() && profileRelationships.isEmpty()) {
            ProfileSection(
                icon = Icons.Rounded.Group,
                title = "Relationships",
            ) {
                val nodeMap = allPersonNodes.associateBy { it.id }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    relationships.forEach { edge ->
                        val otherId = if (edge.sourceNodeId == profile.nodeId) edge.targetNodeId else edge.sourceNodeId
                        val otherName = nodeMap[otherId]?.name ?: "Unknown"
                        val label = edge.relationType.replace("_", " ").removeSuffix(" of")

                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = AppShapes.Chip,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Rounded.Person,
                                    null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                Text(
                                    "$otherName · $label",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                    }
                }
            }
        }

        // ─── Personality (conditional) ──────────────────────────────
        val personalityAttrs = try {
            lenientJson.decodeFromString<List<CategorizedAttribute>>(profile.personalityJson)
        } catch (e: Exception) { emptyList() }
        if (personalityAttrs.isNotEmpty()) {
            ProfileSection(
                icon = Icons.Rounded.Psychology,
                title = "Personality",
            ) {
                Text(
                    text = personalityAttrs.joinToString(", ") { it.value },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // ─── Physical Attributes (conditional) ───────────────────────
        val physicalAttrs = try {
            lenientJson.decodeFromString<List<CategorizedAttribute>>(profile.physicalJson)
        } catch (e: Exception) { emptyList() }
        if (physicalAttrs.isNotEmpty()) {
            ProfileSection(
                icon = Icons.Rounded.EmojiPeople,
                title = "Physical Attributes",
            ) {
                Text(
                    text = physicalAttrs.joinToString(", ") { it.value },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // ─── Other Info (conditional) ────────────────────────────────
        val otherInfoAttrs = try {
            lenientJson.decodeFromString<List<CategorizedAttribute>>(profile.otherInfoJson)
        } catch (e: Exception) { emptyList() }
        if (otherInfoAttrs.isNotEmpty()) {
            ProfileSection(
                icon = Icons.Rounded.Info,
                title = "Other Info",
            ) {
                Text(
                    text = otherInfoAttrs.joinToString(", ") { it.value },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // If absolutely nothing to show beyond name
        val hasAnyContent = profile.birthYear != null ||
            relationships.isNotEmpty() ||
            personalityAttrs.isNotEmpty() ||
            physicalAttrs.isNotEmpty() ||
            otherInfoAttrs.isNotEmpty()

        if (!hasAnyContent) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = AppShapes.CardMedium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "No details yet — they'll be gathered from conversations over time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// EDIT MODE
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ProfileEditMode(
    profile: PersonProfileEntity,
    onSave: (PersonProfileEntity) -> Unit,
    onCancel: () -> Unit,
) {
    val haptics = rememberPremiumHaptics()
    var displayName by remember { mutableStateOf(profile.displayName) }
    var birthYear by remember { mutableStateOf(profile.birthYear) }
    var birthMonth by remember { mutableStateOf(profile.birthMonth) }
    var birthDay by remember { mutableStateOf(profile.birthDay) }
    var pronouns by remember { mutableStateOf(profile.pronouns) }
    var occupation by remember { mutableStateOf(profile.occupation) }
    var location by remember { mutableStateOf(profile.location) }
    var interestsList by remember {
        mutableStateOf(
            try { JsonInstant.decodeFromString<List<String>>(profile.interestsJson).filter { it.isNotBlank() } }
            catch (e: Exception) { emptyList() }
        )
    }
    var newInterest by remember { mutableStateOf("") }
    val lenientJson = remember { kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true } }
    var personalityText by remember {
        mutableStateOf(
            try { lenientJson.decodeFromString<List<CategorizedAttribute>>(profile.personalityJson).joinToString(", ") { it.value } }
            catch (e: Exception) { "" }
        )
    }
    var physicalText by remember {
        mutableStateOf(
            try { lenientJson.decodeFromString<List<CategorizedAttribute>>(profile.physicalJson).joinToString(", ") { it.value } }
            catch (e: Exception) { "" }
        )
    }
    var otherInfoText by remember {
        mutableStateOf(
            try { lenientJson.decodeFromString<List<CategorizedAttribute>>(profile.otherInfoJson).joinToString(", ") { it.value } }
            catch (e: Exception) { "" }
        )
    }
    var notes by remember { mutableStateOf(profile.notes) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Native date picker dialog
    if (showDatePicker) {
        val initialMillis = if (birthYear != null) {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            cal.set(Calendar.YEAR, birthYear ?: 2000)
            cal.set(Calendar.MONTH, (birthMonth ?: 1) - 1)
            cal.set(Calendar.DAY_OF_MONTH, birthDay ?: 1)
            cal.timeInMillis
        } else null
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                        cal.timeInMillis = millis
                        birthYear = cal.get(Calendar.YEAR)
                        birthMonth = cal.get(Calendar.MONTH) + 1
                        birthDay = cal.get(Calendar.DAY_OF_MONTH)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    fun buildSaveProfile() {
        fun textToAttrJson(text: String): String {
            val items = text.split(",").map { it.trim() }.filter { it.isNotBlank() }
            val attrs = items.map { CategorizedAttribute(category = "general", value = it) }
            return lenientJson.encodeToString(attrs)
        }
        onSave(profile.copy(
            displayName = displayName,
            birthYear = birthYear,
            birthMonth = birthMonth,
            birthDay = birthDay,
            pronouns = pronouns,
            occupation = occupation,
            location = location,
            interestsJson = JsonInstant.encodeToString(interestsList),
            personalityJson = textToAttrJson(personalityText),
            physicalJson = textToAttrJson(physicalText),
            otherInfoJson = textToAttrJson(otherInfoText),
            notes = notes,
        ))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 24.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ─── Header ──────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    onClick = onCancel,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Close, "Cancel", modifier = Modifier.size(18.dp))
                    }
                }
                Text(
                    "Edit Profile",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            FilledTonalButton(
                onClick = {
                    haptics.perform(HapticPattern.Success)
                    buildSaveProfile()
                },
            ) {
                Icon(Icons.Rounded.Check, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Save")
            }
        }

        // ═══ IDENTITY SECTION ═══════════════════════════════════════
        EditSection(icon = Icons.Rounded.Person, title = "Identity") {
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Display Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = AppShapes.InputField,
                colors = editFieldColors(),
            )
            OutlinedTextField(
                value = pronouns,
                onValueChange = { pronouns = it },
                label = { Text("Pronouns") },
                placeholder = { Text("e.g. she/her, he/him, they/them") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = AppShapes.InputField,
                colors = editFieldColors(),
            )
        }

        // ═══ DETAILS SECTION ════════════════════════════════════════
        EditSection(icon = Icons.Rounded.Work, title = "Details") {
            // Birthday — tappable row that opens native DatePickerDialog
            val birthdayText = if (birthYear != null) {
                buildString {
                    if (birthMonth != null && birthDay != null) {
                        append(monthName(birthMonth!!))
                        append(" $birthDay, ")
                    } else if (birthMonth != null) {
                        append(monthName(birthMonth!!))
                        append(", ")
                    }
                    append("$birthYear")
                }
            } else "Not set"

            Surface(
                onClick = { showDatePicker = true },
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = AppShapes.InputField,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.CalendarMonth,
                        null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Birthday",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            birthdayText,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (birthYear != null) FontWeight.Medium else FontWeight.Normal,
                            color = if (birthYear != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (birthYear != null) {
                        Surface(
                            onClick = {
                                birthYear = null
                                birthMonth = null
                                birthDay = null
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            modifier = Modifier.size(28.dp),
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Close,
                                    "Clear date",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = occupation,
                onValueChange = { occupation = it },
                label = { Text("Occupation") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = AppShapes.InputField,
                colors = editFieldColors(),
            )
            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("Location") },
                leadingIcon = { Icon(Icons.Rounded.LocationOn, null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = AppShapes.InputField,
                colors = editFieldColors(),
            )
        }

        // ═══ INTERESTS SECTION (chip-based) ═════════════════════════
        EditSection(icon = Icons.Rounded.Favorite, title = "Interests") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                interestsList.forEachIndexed { idx, interest ->
                    InputChip(
                        selected = false,
                        onClick = {
                            haptics.perform(HapticPattern.Pop)
                            interestsList = interestsList.toMutableList().also { it.removeAt(idx) }
                        },
                        label = { Text(interest) },
                        trailingIcon = {
                            Icon(Icons.Rounded.Close, "Remove", modifier = Modifier.size(14.dp))
                        },
                        colors = InputChipDefaults.inputChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = newInterest,
                    onValueChange = { newInterest = it },
                    placeholder = { Text("Add interest…") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = AppShapes.InputField,
                    colors = editFieldColors(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (newInterest.isNotBlank()) {
                                interestsList = interestsList + newInterest.trim()
                                newInterest = ""
                            }
                        }
                    ),
                )
                Surface(
                    onClick = {
                        if (newInterest.isNotBlank()) {
                            haptics.perform(HapticPattern.Pop)
                            interestsList = interestsList + newInterest.trim()
                            newInterest = ""
                        }
                    },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Add,
                            "Add",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }

        // ═══ PERSONALITY & TRAITS SECTION ═══════════════════════════
        EditSection(icon = Icons.Rounded.Psychology, title = "Personality & Traits") {
            OutlinedTextField(
                value = personalityText,
                onValueChange = { personalityText = it },
                label = { Text("Personality") },
                placeholder = { Text("e.g. kind, introverted, creative") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                shape = AppShapes.InputField,
                colors = editFieldColors(),
            )
            OutlinedTextField(
                value = physicalText,
                onValueChange = { physicalText = it },
                label = { Text("Physical Attributes") },
                placeholder = { Text("e.g. tall, brown hair") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                shape = AppShapes.InputField,
                colors = editFieldColors(),
            )
        }

        // ═══ OTHER SECTION ══════════════════════════════════════════
        EditSection(icon = Icons.Rounded.Notes, title = "Other") {
            OutlinedTextField(
                value = otherInfoText,
                onValueChange = { otherInfoText = it },
                label = { Text("Other Info") },
                placeholder = { Text("Additional details…") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                shape = AppShapes.InputField,
                colors = editFieldColors(),
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Personal Notes") },
                placeholder = { Text("Your own notes about this person…") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                shape = AppShapes.InputField,
                colors = editFieldColors(),
            )
        }
    }
}

// ─── Edit helpers ────────────────────────────────────────────────────────────

@Composable
private fun EditSection(
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = AppShapes.CardMedium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    icon,
                    null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            content()
        }
    }
}

@Composable
private fun editFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.3f),
)

// ═══════════════════════════════════════════════════════════════════════════════
// SHARED COMPONENTS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ProfileAvatar(
    profile: PersonProfileEntity,
    userAvatar: Avatar,
    characterAvatar: Avatar,
    size: Int = 64,
) {
    val avatar = when {
        profile.profileImageUri != null -> Avatar.Image(profile.profileImageUri)
        profile.isUserProfile -> userAvatar
        profile.isCharacterProfile -> characterAvatar
        else -> Avatar.Dummy
    }

    Surface(
        modifier = Modifier.size(size.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 4.dp,
    ) {
        when (avatar) {
            is Avatar.Image -> {
                AsyncImage(
                    model = avatar.url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            is Avatar.Emoji -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(avatar.content, style = MaterialTheme.typography.headlineMedium)
                }
            }
            is Avatar.Resource -> {
                AsyncImage(
                    model = avatar.id,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            is Avatar.Dummy -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Face,
                        null,
                        modifier = Modifier.size((size * 0.5f).dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileBadge(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = AppShapes.Tag,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun InfoChip(
    icon: ImageVector,
    text: String,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = AppShapes.Chip,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProfileSection(
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = AppShapes.CardMedium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    icon,
                    null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            content()
        }
    }
}

@Composable
private fun ContributingNodesRow(nodes: List<MemoryNodeEntity>) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        nodes.forEach { node ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = AppShapes.Chip,
            ) {
                Text(
                    node.name,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HapticIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val haptics = rememberPremiumHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 400f),
        label = "iconBtnScale",
    )

    IconButton(
        onClick = {
            haptics.perform(HapticPattern.Pop)
            onClick()
        },
        interactionSource = interactionSource,
        modifier = Modifier.scale(scale),
    ) {
        Icon(icon, contentDescription, tint = tint)
    }
}

private fun monthName(month: Int): String = when (month) {
    1 -> "January"; 2 -> "February"; 3 -> "March"; 4 -> "April"
    5 -> "May"; 6 -> "June"; 7 -> "July"; 8 -> "August"
    9 -> "September"; 10 -> "October"; 11 -> "November"; 12 -> "December"
    else -> "?"
}
