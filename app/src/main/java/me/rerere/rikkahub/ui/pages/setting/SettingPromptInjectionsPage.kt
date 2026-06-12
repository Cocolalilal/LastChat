package me.rerere.rikkahub.ui.pages.setting

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Input
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.model.Skill
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.utils.LorebookExportImport
import me.rerere.rikkahub.utils.SkillExportImport
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingPromptInjectionsPage(
    vm: SettingVM = koinViewModel(),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val haptics = rememberPremiumHaptics()
    val navController = LocalNavController.current
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val useWideLayout = LocalSettingsWideLayout.current
    val pagerState = rememberPagerState { 2 }
    val skillsListState = rememberLazyListState()
    val lorebooksListState = rememberLazyListState()

    var showAddSkillDialog by remember { mutableStateOf(false) }
    var showAddLorebookDialog by remember { mutableStateOf(false) }
    var editingSkill by remember { mutableStateOf<Skill?>(null) }

    val skillImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        when (val result = SkillExportImport.importFromUri(context, uri)) {
            is SkillExportImport.ImportResult.Success -> {
                vm.updateSettings(settings.copy(skills = settings.skills + result.skill))
                haptics.perform(HapticPattern.Success)
                toaster.show(context.getString(R.string.skill_import_success, result.skill.name))
            }

            is SkillExportImport.ImportResult.Error -> {
                haptics.perform(HapticPattern.Error)
                toaster.show(result.message)
            }
        }
    }

    val lorebookImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        when (val result = LorebookExportImport.importFromUri(context, uri)) {
            is LorebookExportImport.ImportResult.Success -> {
                vm.updateSettings(settings.copy(lorebooks = settings.lorebooks + result.lorebook))
                haptics.perform(HapticPattern.Success)
                toaster.show(
                    message = context.getString(R.string.lorebook_import_success, result.lorebook.name),
                )
            }

            is LorebookExportImport.ImportResult.Error -> {
                haptics.perform(HapticPattern.Error)
                toaster.show(result.message)
            }
        }
    }

    Scaffold(
        topBar = {
            OneUITopAppBar(
                title = if (pagerState.currentPage == 0) {
                    stringResource(R.string.prompt_injections_page_skills)
                } else {
                    stringResource(R.string.prompt_injections_page_lorebooks)
                },
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackButton() }
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                if (!useWideLayout) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter),
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .then(
                                        if (pagerState.currentPage == 0) {
                                            Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                                        } else {
                                            Modifier.clickable {
                                                haptics.perform(HapticPattern.Tick)
                                                scope.launch { pagerState.animateScrollToPage(0) }
                                            }
                                        }
                                    )
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Category,
                                    contentDescription = null,
                                    tint = if (pagerState.currentPage == 0) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .then(
                                        if (pagerState.currentPage == 1) {
                                            Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                                        } else {
                                            Modifier.clickable {
                                                haptics.perform(HapticPattern.Tick)
                                                scope.launch { pagerState.animateScrollToPage(1) }
                                            }
                                        }
                                    )
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Book,
                                    contentDescription = null,
                                    tint = if (pagerState.currentPage == 1) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    FloatingActionButton(
                        onClick = {
                            haptics.perform(HapticPattern.Tick)
                            if (pagerState.currentPage == 0) {
                                skillImportLauncher.launch(arrayOf("application/json", "text/markdown", "*/*"))
                            } else {
                                lorebookImportLauncher.launch(arrayOf("application/json", "*/*"))
                            }
                        },
                        shape = AppShapes.CardLarge,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Input,
                            contentDescription = stringResource(R.string.import_label)
                        )
                    }

                    FloatingActionButton(
                        onClick = {
                            haptics.perform(HapticPattern.Pop)
                            if (pagerState.currentPage == 0) {
                                showAddSkillDialog = true
                            } else {
                                showAddLorebookDialog = true
                            }
                        },
                        shape = AppShapes.CardLarge
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.add))
                    }
                }
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { contentPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !useWideLayout,
        ) { page ->
            when (page) {
                0 -> {
                    SkillsPageContent(
                        settings = settings,
                        vm = vm,
                        haptics = haptics,
                        listState = skillsListState,
                        contentPadding = contentPadding,
                        onEditSkill = { editingSkill = it }
                    )
                }

                else -> {
                    LorebooksPageContent(
                        settings = settings,
                        vm = vm,
                        haptics = haptics,
                        listState = lorebooksListState,
                        contentPadding = contentPadding
                    )
                }
            }
        }
    }

    if (showAddSkillDialog || editingSkill != null) {
        SkillEditorSheet(
            skill = editingSkill,
            assistants = settings.assistants,
            onDismiss = {
                showAddSkillDialog = false
                editingSkill = null
            },
            onSave = { savedSkill ->
                if (editingSkill == null) {
                    vm.updateSettings(settings.copy(skills = settings.skills + savedSkill))
                } else {
                    vm.updateSettings(
                        settings.copy(
                            skills = settings.skills.map {
                                if (it.id == savedSkill.id) savedSkill else it
                            }
                        )
                    )
                }
                showAddSkillDialog = false
                editingSkill = null
            },
            onAutoSave = { savedSkill ->
                vm.updateSettings(
                    settings.copy(
                        skills = settings.skills.map {
                            if (it.id == savedSkill.id) savedSkill else it
                        }
                    )
                )
            }
        )
    }

    if (showAddLorebookDialog) {
        LorebookCreatorSheet(
            onDismiss = { showAddLorebookDialog = false },
            onSave = { lorebook ->
                vm.updateSettings(settings.copy(lorebooks = settings.lorebooks + lorebook))
                showAddLorebookDialog = false
                navController.navigate(Screen.SettingLorebookDetail(lorebook.id.toString()))
            }
        )
    }
}
