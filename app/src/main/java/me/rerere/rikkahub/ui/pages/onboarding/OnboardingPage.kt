package me.rerere.rikkahub.ui.pages.onboarding

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import me.rerere.rikkahub.ui.components.ui.AutoAIIconWithUrl
import me.rerere.rikkahub.ui.components.ui.ModelIcon
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.pages.setting.components.ProviderPreset
import me.rerere.rikkahub.ui.pages.setting.components.SecureOutlinedTextField
import me.rerere.rikkahub.ui.pages.setting.components.SettingsGroup
import me.rerere.rikkahub.ui.pages.setting.components.toProviderSetting
import me.rerere.rikkahub.ui.theme.AppShapes
import org.koin.androidx.compose.koinViewModel
import kotlin.uuid.Uuid

@Composable
fun OnboardingPage(vm: OnboardingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val snapshot by vm.modelCatalogSnapshot.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val haptics = rememberPremiumHaptics(enabled = settings.displaySetting.enableUIHaptics)
    val providerPresets = remember(snapshot) { vm.providerPresets(snapshot) }
    val guidedProviderPresets = remember(providerPresets) {
        providerPresets
            .filter { it.setupRecommended }
            .sortedBy { it.setupOrder }
    }
    val pageHistory = remember { mutableStateListOf(SetupPage.Intro) }
    val page = pageHistory.last()
    var selectedGuidedProvider by remember { mutableStateOf<ProviderPreset?>(null) }
    var manualProvider by remember { mutableStateOf<ProviderSetting?>(null) }
    var manualProviderPreset by remember { mutableStateOf<ProviderPreset?>(null) }
    var apiKey by remember { mutableStateOf("") }
    var guidedModelsLoading by remember { mutableStateOf(false) }
    var manualModelsLoading by remember { mutableStateOf(false) }
    var manualModels by remember { mutableStateOf<List<Model>>(emptyList()) }
    val selectedModels = remember { mutableStateListOf<Model>() }
    var roleModels by remember { mutableStateOf(SetupRoleModels()) }

    fun finishToChat() {
        navController.navigate(Screen.Chat(Uuid.random().toString())) {
            popUpTo(0) { inclusive = true }
        }
    }

    fun goTo(nextPage: SetupPage) {
        if (pageHistory.lastOrNull() != nextPage) {
            pageHistory.add(nextPage)
        }
    }

    fun goToApiKeyFlow(
        signupUrl: String?,
        apiKeyUrl: String?,
        signupPage: SetupPage,
        keyPage: SetupPage,
        pastePage: SetupPage,
    ) {
        when {
            !signupUrl.isNullOrBlank() -> goTo(signupPage)
            !apiKeyUrl.isNullOrBlank() -> goTo(keyPage)
            else -> goTo(pastePage)
        }
    }

    BackHandler(enabled = true) {
        if (pageHistory.size > 1) {
            pageHistory.removeAt(pageHistory.lastIndex)
        }
    }

    val isDark = isSystemInDarkTheme()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = if (isDark) Color.Black else MaterialTheme.colorScheme.surface,
        contentColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
    ) {
        AnimatedContent(
            targetState = page,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            label = "setup_page"
        ) { currentPage ->
            when (currentPage) {
                SetupPage.Intro -> IntroPage(
                    onSkip = {
                        haptics.perform(HapticPattern.Pop)
                        vm.skipSetup(::finishToChat)
                    },
                    onNext = {
                        haptics.perform(HapticPattern.Pop)
                        goTo(SetupPage.Bridge)
                    },
                )

                SetupPage.Bridge -> CenterTextPage(
                    text = "There are infinite\noptions when it\ncomes to LLM\nproviders...",
                    onNext = {
                        haptics.perform(HapticPattern.Pop)
                        goTo(SetupPage.ProviderOverview)
                    },
                )

                SetupPage.ProviderOverview -> ProviderOverviewPage(
                    providerPresets = providerPresets,
                    onSkip = {
                        haptics.perform(HapticPattern.Pop)
                        vm.skipSetup(::finishToChat)
                    },
                    onTooManyOptions = {
                        haptics.perform(HapticPattern.Pop)
                        goTo(SetupPage.GuidedChoice)
                    },
                    onSelectProvider = { provider, preset ->
                        haptics.perform(HapticPattern.Pop)
                        manualProvider = provider
                        manualProviderPreset = preset
                        apiKey = ""
                        if (preset?.apiKeyUrl.isNullOrBlank()) {
                            goTo(SetupPage.ManualKey)
                        } else {
                            goTo(SetupPage.ManualKeyLink)
                        }
                    },
                )

                SetupPage.GuidedChoice -> GuidedChoicePage(
                    providerPresets = guidedProviderPresets,
                    onSelect = {
                        haptics.perform(HapticPattern.Pop)
                        selectedGuidedProvider = it
                        apiKey = ""
                        goToApiKeyFlow(
                            signupUrl = it.signupUrl,
                            apiKeyUrl = it.apiKeyUrl,
                            signupPage = SetupPage.GuidedSignup,
                            keyPage = SetupPage.GuidedKeyLink,
                            pastePage = SetupPage.GuidedPasteKey,
                        )
                    },
                )

                SetupPage.GuidedSignup -> LinkOutPage(
                    text = "Let’s get your API key! Tap\nbelow to create your\n${selectedGuidedProvider?.name ?: "[Provider]"} account, then\nreturn to LastChat for the\nnext step.",
                    button = "Open signup page",
                    url = selectedGuidedProvider?.signupUrl,
                    onReturned = {
                        if (selectedGuidedProvider?.apiKeyUrl.isNullOrBlank()) {
                            goTo(SetupPage.GuidedPasteKey)
                        } else {
                            goTo(SetupPage.GuidedKeyLink)
                        }
                    },
                )

                SetupPage.GuidedKeyLink -> LinkOutPage(
                    text = "Now that you have an\naccount, let’s grab that API\nkey. API keys are like\npasswords, don’t share\nthem!",
                    button = "Get my API key",
                    url = selectedGuidedProvider?.apiKeyUrl,
                    onReturned = { goTo(SetupPage.GuidedPasteKey) },
                )

                SetupPage.GuidedPasteKey -> PasteKeyPage(
                    text = "Paste your API key here and\nwe’ll handle the rest.",
                    apiKey = apiKey,
                    onApiKeyChange = { apiKey = it },
                    loading = guidedModelsLoading,
                    onContinue = {
                        val provider = selectedGuidedProvider ?: return@PasteKeyPage
                        haptics.perform(HapticPattern.Pop)
                        guidedModelsLoading = true
                        vm.completeGuided(provider, apiKey) {
                            guidedModelsLoading = false
                            haptics.perform(HapticPattern.Success)
                            goTo(SetupPage.Success)
                        }
                    },
                )

                SetupPage.ManualKeyLink -> LinkOutPage(
                    text = "Get your API key from the\n${manualProviderPreset?.name ?: "provider"} dashboard.",
                    button = "Get API key",
                    url = manualProviderPreset?.apiKeyUrl,
                    onReturned = { goTo(SetupPage.ManualKey) },
                )

                SetupPage.ManualKey -> {
                    if (manualProvider?.name == "Custom provider") {
                        CustomProviderKeyPage(
                            provider = manualProvider!!,
                            onProviderChange = { manualProvider = it },
                            apiKey = apiKey,
                            onApiKeyChange = { apiKey = it },
                            loading = manualModelsLoading,
                            onContinue = {
                                val provider = manualProvider ?: return@CustomProviderKeyPage
                                haptics.perform(HapticPattern.Pop)
                                val keyedProvider = vm.providerWithKey(provider, apiKey)
                                manualProvider = keyedProvider
                                manualModelsLoading = true
                                vm.fetchModels(keyedProvider) { models ->
                                    manualModelsLoading = false
                                    manualModels = models
                                    selectedModels.clear()
                                    roleModels = SetupRoleModels()
                                    goTo(SetupPage.ManualModels)
                                }
                            }
                        )
                    } else {
                        PasteKeyPage(
                            text = "${manualProvider?.name ?: "Provider"} API key:",
                            apiKey = apiKey,
                            onApiKeyChange = { apiKey = it },
                            loading = manualModelsLoading,
                            onContinue = {
                                val provider = manualProvider ?: return@PasteKeyPage
                                haptics.perform(HapticPattern.Pop)
                                val keyedProvider = vm.providerWithKey(provider, apiKey)
                                manualProvider = keyedProvider
                                manualModelsLoading = true
                                vm.fetchModels(keyedProvider) { models ->
                                    manualModelsLoading = false
                                    manualModels = models
                                    selectedModels.clear()
                                    roleModels = SetupRoleModels()
                                    goTo(SetupPage.ManualModels)
                                }
                            },
                        )
                    }
                }

                SetupPage.ManualModels -> ManualModelsPage(
                    provider = manualProvider,
                    models = manualModels,
                    selected = selectedModels,
                    onToggle = { model ->
                        haptics.perform(HapticPattern.Pop)
                        if (selectedModels.any { it.id == model.id }) {
                            selectedModels.removeAll { it.id == model.id }
                        } else {
                            selectedModels.add(model)
                        }
                    },
                    onSkip = {
                        haptics.perform(HapticPattern.Pop)
                        selectedModels.clear()
                        vm.saveManualProvider(
                            provider = manualProvider ?: return@ManualModelsPage,
                            selectedModels = emptyList(),
                            roleModels = SetupRoleModels(),
                            onDone = { goTo(SetupPage.Success) },
                        )
                    },
                    onContinue = {
                        haptics.perform(HapticPattern.Pop)
                        goTo(SetupPage.ManualDefaults)
                    },
                )

                SetupPage.ManualDefaults -> ManualDefaultsPage(
                    provider = manualProvider,
                    models = selectedModels.toList(),
                    roleModels = roleModels,
                    onRoleModelsChange = { roleModels = it },
                    onSkip = {
                        haptics.perform(HapticPattern.Pop)
                        vm.saveManualProvider(
                            provider = manualProvider ?: return@ManualDefaultsPage,
                            selectedModels = selectedModels.toList(),
                            roleModels = roleModels,
                        ) {
                            goTo(SetupPage.Success)
                        }
                    },
                    onContinue = {
                        haptics.perform(HapticPattern.Pop)
                        vm.saveManualProvider(
                            provider = manualProvider ?: return@ManualDefaultsPage,
                            selectedModels = selectedModels.toList(),
                            roleModels = roleModels,
                        ) {
                            haptics.perform(HapticPattern.Success)
                            goTo(SetupPage.Success)
                        }
                    },
                )

                SetupPage.Success -> SuccessPage(
                    guided = selectedGuidedProvider != null,
                    onDone = {
                        haptics.perform(HapticPattern.Pop)
                        finishToChat()
                    },
                )
            }
        }
    }
}

private val SetupEdgePadding = 16.dp

@Composable
private fun IntroPage(
    onSkip: () -> Unit,
    onNext: () -> Unit,
) {
    val haptics = rememberPremiumHaptics()
    val density = LocalDensity.current
    val targetYOffsetPx = remember(density) {
        with(density) { -64.dp.toPx() }
    }
    val logoScale = remember { Animatable(2.4f) }
    val logoYOffset = remember { Animatable(0f) }
    val contentAlpha = remember { Animatable(0f) }
    val ripple = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay(220)
        logoScale.animateTo(1.0f, tween(980, easing = FastOutLinearInEasing))
        haptics.perform(HapticPattern.Thud)
        ripple.snapTo(0f)
        launch { ripple.animateTo(1f, tween(860, easing = FastOutSlowInEasing)) }
        logoScale.animateTo(1.2f, spring(dampingRatio = 0.34f, stiffness = 340f))
        delay(120)
        launch { logoYOffset.animateTo(targetYOffsetPx, tween(560, easing = FastOutSlowInEasing)) }
        contentAlpha.animateTo(1f, tween(460))
    }

    SetupScaffold(
        bottom = {
            TwoSetupButtons(
                leftText = "Skip intro",
                onLeft = onSkip,
                rightIcon = Icons.AutoMirrored.Rounded.ArrowForward,
                onRight = onNext,
            )
        }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            IntroRipple(progress = ripple.value)
            Image(
                painter = painterResource(R.drawable.lastchat_setup_icon),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(124.dp)
                    .graphicsLayer {
                        scaleX = logoScale.value
                        scaleY = logoScale.value
                        translationY = logoYOffset.value
                    }
            )
            Text(
                text = "Let’s get you set\nup on LastChat!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                lineHeight = MaterialTheme.typography.headlineSmall.lineHeight,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 96.dp, start = 54.dp, end = 54.dp)
                    .alpha(contentAlpha.value),
            )
        }
    }
}

@Composable
private fun IntroRipple(progress: Float) {
    val isDark = isSystemInDarkTheme()
    val blendColor = if (isDark) Color.Black else MaterialTheme.colorScheme.surface
    val colors = listOf(
        lerp(MaterialTheme.colorScheme.primary, blendColor, 0.34f),
        lerp(MaterialTheme.colorScheme.secondary, blendColor, 0.42f),
        lerp(MaterialTheme.colorScheme.tertiary, blendColor, 0.38f),
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { translationY = 18.dp.toPx() },
        contentAlignment = Alignment.Center,
    ) {
        colors.forEachIndexed { index, color ->
            val localProgress = (progress - index * 0.06f).coerceIn(0f, 1f)
            val visibleAlpha = if (localProgress <= 0.01f) 0f else (1f - localProgress).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .size((72 + index * 18).dp)
                    .graphicsLayer {
                        scaleX = 0.72f + localProgress * (3.8f + index * 0.55f)
                        scaleY = 0.34f + localProgress * (1.28f + index * 0.22f)
                        rotationZ = (index * 17f) - 12f + localProgress * (8f - index * 3f)
                        alpha = visibleAlpha * (0.28f - index * 0.035f)
                        translationY = (34 + index * 3).dp.toPx()
                    }
                    .blur((18 + index * 5).dp)
                    .clip(RoundedCornerShape(percent = 46 - index * 5))
                    .background(color)
            )
        }
    }
}

@Composable
private fun CenterTextPage(
    text: String,
    onNext: () -> Unit,
) {
    SetupScaffold(
        bottom = {
            SingleArrowButton(onClick = onNext)
        }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = MaterialTheme.typography.headlineMedium.lineHeight,
                modifier = Modifier.padding(horizontal = SetupEdgePadding),
            )
        }
    }
}

@Composable
private fun ProviderOverviewPage(
    providerPresets: List<ProviderPreset>,
    onSkip: () -> Unit,
    onTooManyOptions: () -> Unit,
    onSelectProvider: (ProviderSetting, ProviderPreset?) -> Unit,
) {
    SetupScaffold(
        bottom = {
            TwoSetupButtons(
                leftText = "Skip",
                onLeft = onSkip,
                rightText = "Too many options?",
                onRight = onTooManyOptions,
                leftWeight = 0.74f,
                rightWeight = 1.56f,
                rightNeutral = true,
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = SetupEdgePadding)
                .padding(top = 160.dp),
        ) {
            Text(
                text = "Here are some of the\nproviders you can access\nthrough LastChat:",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                lineHeight = MaterialTheme.typography.titleLarge.lineHeight,
            )
            Spacer(modifier = Modifier.height(24.dp))
            FadingLazyColumn {
                item {
                    ProviderCustomCard(
                        onClick = {
                            onSelectProvider(
                                ProviderSetting.OpenAI(
                                    name = "Custom provider",
                                    baseUrl = "",
                                ),
                                null,
                            )
                        },
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                if (providerPresets.isEmpty()) {
                    item {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(18.dp)
                                .size(28.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    itemsIndexed(providerPresets, key = { _, preset -> preset.name }) { index, preset ->
                        val position = when {
                            providerPresets.size == 1 -> ItemPosition.ONLY
                            index == 0 -> ItemPosition.FIRST
                            index == providerPresets.lastIndex -> ItemPosition.LAST
                            else -> ItemPosition.MIDDLE
                        }
                        ProviderPresetCard(
                            name = preset.name,
                            description = preset.description,
                            iconUri = preset.customIconUri,
                            position = position,
                            onClick = { onSelectProvider(preset.toProviderSetting(), preset) },
                        )
                        if (position != ItemPosition.LAST && position != ItemPosition.ONLY) {
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuidedChoicePage(
    providerPresets: List<ProviderPreset>,
    onSelect: (ProviderPreset) -> Unit,
) {
    SetupScaffold {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = SetupEdgePadding)
                .padding(top = 160.dp),
        ) {
            Text(
                text = "Here are two beginner-\nfriendly options. We’ll guide\nyou through the setup!",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                lineHeight = MaterialTheme.typography.titleLarge.lineHeight,
            )
            Spacer(modifier = Modifier.height(28.dp))
            providerPresets.forEachIndexed { index, preset ->
                if (index > 0) Spacer(modifier = Modifier.height(16.dp))
                GuidedProviderCard(
                    provider = preset,
                    description = preset.setupDescription ?: preset.description,
                    onClick = { onSelect(preset) },
                )
            }
        }
    }
}

@Composable
private fun LinkOutPage(
    text: String,
    button: String,
    url: String?,
    onReturned: () -> Unit,
) {
    val context = LocalContext.current
    var waitingForReturn by remember { mutableStateOf(false) }
    var leftApp by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, waitingForReturn) {
        val observer = LifecycleEventObserver { _, event ->
            if (!waitingForReturn) return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_PAUSE -> leftApp = true
                Lifecycle.Event.ON_RESUME -> if (leftApp) {
                    waitingForReturn = false
                    leftApp = false
                    onReturned()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SetupScaffold(
        bottom = {
            CenteredSetupButton(
                text = button,
                icon = Icons.AutoMirrored.Rounded.OpenInNew,
                onClick = {
                    val target = url ?: return@CenteredSetupButton
                    waitingForReturn = true
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
                },
            )
        }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = MaterialTheme.typography.titleLarge.lineHeight,
                modifier = Modifier.padding(horizontal = SetupEdgePadding),
            )
        }
    }
}

@Composable
private fun PasteKeyPage(
    text: String,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    loading: Boolean = false,
    onContinue: () -> Unit,
) {
    SetupScaffold(
        bottom = {
            SetupButton(
                filled = true,
                enabled = apiKey.isNotBlank() && !loading,
                icon = Icons.AutoMirrored.Rounded.ArrowForward,
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 110.dp),
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = SetupEdgePadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = MaterialTheme.typography.titleLarge.lineHeight,
            )
            Spacer(modifier = Modifier.height(26.dp))
            SecureOutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                label = "",
                modifier = Modifier.fillMaxWidth(),
            )
            AnimatedVisibility(visible = loading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(top = 18.dp)
                        .size(28.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ManualModelsPage(
    provider: ProviderSetting?,
    models: List<Model>,
    selected: List<Model>,
    onToggle: (Model) -> Unit,
    onSkip: () -> Unit,
    onContinue: () -> Unit,
) {
    var showInfo by remember { mutableStateOf(false) }
    SetupScaffold(
        bottom = {
            TwoSetupButtons(
                leftText = "Skip",
                onLeft = onSkip,
                rightIcon = Icons.AutoMirrored.Rounded.ArrowForward,
                onRight = onContinue,
                rightEnabled = selected.isNotEmpty(),
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = SetupEdgePadding)
                .padding(top = 82.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = "Choose the models\nyou want to use:",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    lineHeight = MaterialTheme.typography.titleLarge.lineHeight,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { showInfo = true }) {
                    Icon(Icons.Rounded.Info, contentDescription = null)
                }
            }
            Spacer(modifier = Modifier.height(22.dp))
            FadingLazyColumn {
                if (models.isEmpty()) {
                    item {
                        Text(
                            text = "No models came back from this provider yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
                itemsIndexed(models, key = { _, model -> model.id }) { _, model ->
                    SetupModelSelectableRow(
                        model = model,
                        provider = provider,
                        selected = selected.any { it.id == model.id },
                        onClick = { onToggle(model) },
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text("A tiny recommendation") },
            text = {
                Text("Pick one fast and cheap model for everyday chat, plus one stronger model for harder questions.")
            },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) {
                    Text("Got it")
                }
            }
        )
    }
}

@Composable
private fun ManualDefaultsPage(
    provider: ProviderSetting?,
    models: List<Model>,
    roleModels: SetupRoleModels,
    onRoleModelsChange: (SetupRoleModels) -> Unit,
    onSkip: () -> Unit,
    onContinue: () -> Unit,
) {
    val providers = remember(provider, models) {
        provider?.copyProvider(models = models)?.let(::listOf) ?: emptyList()
    }
    SetupScaffold(
        bottom = {
            TwoSetupButtons(
                leftText = "Skip",
                onLeft = onSkip,
                rightIcon = Icons.AutoMirrored.Rounded.ArrowForward,
                onRight = onContinue,
                rightEnabled = roleModels.chat != null &&
                    roleModels.title != null,
            )
        }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 72.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                Text(
                    text = "Choose what each model\nshould do:",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    lineHeight = MaterialTheme.typography.titleLarge.lineHeight,
                    modifier = Modifier.padding(horizontal = SetupEdgePadding, vertical = 16.dp),
                )
            }
            item {
                SettingsGroup(title = "Basics") {
                    SetupModelFeatureCard(
                        title = "Chat model",
                        description = "The model you’ll talk to most.",
                        icon = Icons.AutoMirrored.Rounded.Chat,
                    ) {
                        ModelSelector(
                            modelId = roleModels.chat,
                            type = ModelType.CHAT,
                            providers = providers,
                            onSelect = { onRoleModelsChange(roleModels.copy(chat = it.id)) },
                        )
                    }
                    SetupModelFeatureCard(
                        title = "Title generation model",
                        description = "Names new chats quietly in the background.",
                        icon = Icons.Rounded.Title,
                    ) {
                        ModelSelector(
                            modelId = roleModels.title,
                            type = ModelType.CHAT,
                            providers = providers,
                            onSelect = { onRoleModelsChange(roleModels.copy(title = it.id)) },
                        )
                    }
                    SetupModelFeatureCard(
                        title = "Summarizer model",
                        description = "Keeps long conversations manageable.",
                        icon = Icons.Rounded.Psychology,
                    ) {
                        ModelSelector(
                            modelId = roleModels.summarizer,
                            type = ModelType.CHAT,
                            providers = providers,
                            onSelect = { onRoleModelsChange(roleModels.copy(summarizer = it.id)) },
                        )
                    }
                    SetupModelFeatureCard(
                        title = "OCR model",
                        description = "Reads text from images when needed.",
                        icon = Icons.Rounded.DocumentScanner,
                    ) {
                        ModelSelector(
                            modelId = roleModels.ocr,
                            type = ModelType.CHAT,
                            providers = providers,
                            modelFilter = { it.inputModalities.contains(Modality.IMAGE) },
                            onSelect = { onRoleModelsChange(roleModels.copy(ocr = it.id)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuccessPage(
    guided: Boolean,
    onDone: () -> Unit,
) {
    val message = if (guided) {
        "You’re ready to chat.\n\nWe set up a basic setup with free models so you can start using LastChat without having to pay anything."
    } else {
        "You’re ready to chat.\n\nYou can tune the rest whenever you feel like it."
    }
    SetupScaffold(
        bottom = {
            CenteredSetupButton(
                text = "Start chatting",
                onClick = onDone,
            )
        }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = MaterialTheme.typography.titleLarge.lineHeight,
                modifier = Modifier.padding(horizontal = SetupEdgePadding),
            )
        }
    }
}

@Composable
private fun SetupScaffold(
    bottom: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) Color.Black else MaterialTheme.colorScheme.surface
    Box(modifier = Modifier.fillMaxSize()) {
        content()
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            bgColor.copy(alpha = 0.9f),
                            bgColor,
                        )
                    )
                )
                .padding(horizontal = SetupEdgePadding)
                .padding(bottom = 28.dp, top = 32.dp)
                .navigationBarsPadding()
        ) {
            bottom()
        }
    }
}

@Composable
private fun CenteredSetupButton(
    text: String,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        SetupButton(
            text = text,
            icon = icon,
            filled = true,
            enabled = enabled,
            onClick = onClick,
        )
    }
}

@Composable
private fun TwoSetupButtons(
    leftText: String,
    onLeft: () -> Unit,
    rightText: String? = null,
    rightIcon: ImageVector? = null,
    onRight: () -> Unit,
    rightEnabled: Boolean = true,
    leftWeight: Float = 1f,
    rightWeight: Float = 1f,
    rightNeutral: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SetupButton(
            text = leftText,
            filled = false,
            onClick = onLeft,
            modifier = Modifier.weight(leftWeight),
        )
        SetupButton(
            text = rightText,
            icon = rightIcon,
            filled = true,
            neutral = rightNeutral,
            enabled = rightEnabled,
            onClick = onRight,
            modifier = Modifier.weight(rightWeight),
        )
    }
}

@Composable
private fun SingleArrowButton(onClick: () -> Unit) {
    SetupButton(
        icon = Icons.AutoMirrored.Rounded.ArrowForward,
        filled = true,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 112.dp),
    )
}

@Composable
private fun SetupButton(
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: ImageVector? = null,
    filled: Boolean,
    neutral: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val haptics = rememberPremiumHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "setup_button_scale",
    )
    val shape = AppShapes.ButtonPill
    val content: @Composable () -> Unit = {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            text?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            icon?.let {
                if (text != null) Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }

    if (filled) {
        Button(
            enabled = enabled,
            onClick = {
                haptics.perform(HapticPattern.Pop)
                onClick()
            },
            interactionSource = interactionSource,
            shape = shape,
            contentPadding = PaddingValues(horizontal = 16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (neutral) setupCardColor() else MaterialTheme.colorScheme.primary,
                contentColor = if (neutral) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = modifier
                .height(52.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            content()
        }
    } else {
        OutlinedButton(
            enabled = enabled,
            onClick = {
                haptics.perform(HapticPattern.Pop)
                onClick()
            },
            interactionSource = interactionSource,
            shape = shape,
            border = BorderStroke(3.dp, setupCardColor()),
            contentPadding = PaddingValues(horizontal = 16.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = if (isSystemInDarkTheme()) Color.White else MaterialTheme.colorScheme.onSurface,
                containerColor = if (isSystemInDarkTheme()) Color.Black else MaterialTheme.colorScheme.surface,
            ),
            modifier = modifier
                .height(52.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            content()
        }
    }
}

@Composable
private fun ProviderPresetCard(
    name: String,
    description: String,
    iconUri: String?,
    position: ItemPosition,
    onClick: () -> Unit,
) {
    SetupListCard(
        onClick = onClick,
        shape = groupedCardShape(position),
        minHeight = 72.dp,
    ) {
        AutoAIIconWithUrl(
            name = name,
            customIconUri = iconUri,
            modifier = Modifier.size(40.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ProviderCustomCard(onClick: () -> Unit) {
    SetupListCard(
        onClick = onClick,
        minHeight = 82.dp,
        shape = AppShapes.CardMedium,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Custom provider",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = "Use your own OpenAI-compatible endpoint",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun GuidedProviderCard(
    provider: ProviderPreset,
    description: String,
    onClick: () -> Unit,
) {
    val haptics = rememberPremiumHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "guided_card_scale",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(134.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(AppShapes.CardLarge)
            .background(setupCardColor())
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) {
                haptics.perform(HapticPattern.Pop)
                onClick()
            }
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AutoAIIconWithUrl(
                name = provider.name,
                customIconUri = provider.customIconUri,
                modifier = Modifier.size(46.dp),
            )
            Text(
                text = provider.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SetupListCard(
    onClick: () -> Unit,
    minHeight: Dp = 82.dp,
    shape: androidx.compose.ui.graphics.Shape = AppShapes.CardMedium,
    color: Color = setupCardColor(),
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable RowScope.() -> Unit,
) {
    val haptics = rememberPremiumHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "setup_card_scale",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(minHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(color)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) {
                haptics.perform(HapticPattern.Pop)
                onClick()
            }
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides contentColor,
        ) {
            content()
        }
    }
}

@Composable
private fun SetupModelSelectableRow(
    model: Model,
    provider: ProviderSetting?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    SetupListCard(onClick = onClick) {
        ModelIcon(
            model = model,
            provider = provider,
            modifier = Modifier.size(38.dp),
            color = Color.Transparent,
            contentColor = if (isSystemInDarkTheme()) Color.White else MaterialTheme.colorScheme.onSurface,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = model.displayName.ifBlank { model.modelId },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = model.modelId,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SelectionCircle(selected = selected)
    }
}

@Composable
private fun SelectionCircle(selected: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.78f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "selection_circle_scale",
    )
    Box(
        modifier = Modifier
            .size(28.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .then(
                if (selected) {
                    Modifier.background(MaterialTheme.colorScheme.primary)
                } else {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SetupModelFeatureCard(
    title: String,
    description: String,
    icon: ImageVector,
    selector: @Composable () -> Unit,
) {
    Surface(
        color = setupCardColor(),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = null)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            selector()
        }
    }
}

@Composable
private fun FadingLazyColumn(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) Color.Black else MaterialTheme.colorScheme.surface
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds(),
    ) {
        LazyColumn(
            state = rememberLazyListState(),
            contentPadding = PaddingValues(bottom = 140.dp),
            content = content,
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(100.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, bgColor)
                    )
                )
        )
    }
}

private enum class ItemPosition {
    FIRST,
    MIDDLE,
    LAST,
    ONLY,
}

private fun groupedCardShape(position: ItemPosition) = when (position) {
    ItemPosition.FIRST -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 10.dp, bottomEnd = 10.dp)
    ItemPosition.MIDDLE -> RoundedCornerShape(10.dp)
    ItemPosition.LAST -> RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
    ItemPosition.ONLY -> RoundedCornerShape(24.dp)
}

@Composable
private fun setupCardColor(): Color {
    return MaterialTheme.colorScheme.surfaceContainerLow
}

private enum class SetupPage {
    Intro,
    Bridge,
    ProviderOverview,
    GuidedChoice,
    GuidedSignup,
    GuidedKeyLink,
    GuidedPasteKey,
    ManualKeyLink,
    ManualKey,
    ManualModels,
    ManualDefaults,
    Success,
}

@Composable
private fun CustomProviderKeyPage(
    provider: ProviderSetting,
    onProviderChange: (ProviderSetting) -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    loading: Boolean,
    onContinue: () -> Unit,
) {
    val apiTypes = listOf("OpenAI", "Google", "Claude")
    val selectedType = when (provider) {
        is ProviderSetting.OpenAI -> "OpenAI"
        is ProviderSetting.Google -> "Google"
        is ProviderSetting.Claude -> "Claude"
        else -> "OpenAI"
    }

    val baseUrl = when (provider) {
        is ProviderSetting.OpenAI -> provider.baseUrl
        is ProviderSetting.Google -> provider.baseUrl
        is ProviderSetting.Claude -> provider.baseUrl
        else -> ""
    }

    SetupScaffold(
        bottom = {
            SetupButton(
                filled = true,
                enabled = apiKey.isNotBlank() && baseUrl.isNotBlank() && !loading,
                icon = Icons.AutoMirrored.Rounded.ArrowForward,
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 110.dp),
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = SetupEdgePadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Configure your custom provider",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = MaterialTheme.typography.titleLarge.lineHeight,
            )
            Spacer(modifier = Modifier.height(26.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                apiTypes.forEachIndexed { index, type ->
                    SegmentedButton(
                        modifier = Modifier.weight(1f),
                        selected = selectedType == type,
                        onClick = {
                            val newProvider = when (type) {
                                "Google" -> ProviderSetting.Google(name = "Custom provider", baseUrl = "")
                                "Claude" -> ProviderSetting.Claude(name = "Custom provider", baseUrl = "")
                                else -> ProviderSetting.OpenAI(name = "Custom provider", baseUrl = "")
                            }
                            onProviderChange(newProvider)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = apiTypes.size),
                    ) {
                        Text(type)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { newUrl ->
                    val newProvider = when (provider) {
                        is ProviderSetting.OpenAI -> provider.copy(baseUrl = newUrl)
                        is ProviderSetting.Google -> provider.copy(baseUrl = newUrl)
                        is ProviderSetting.Claude -> provider.copy(baseUrl = newUrl)
                        else -> provider
                    }
                    onProviderChange(newProvider)
                },
                label = { Text("Base URL") },
                shape = AppShapes.InputField,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            SecureOutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                label = "API Key",
                modifier = Modifier.fillMaxWidth(),
            )
            AnimatedVisibility(visible = loading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(top = 18.dp)
                        .size(28.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private fun previousPage(page: SetupPage): SetupPage? {
    return when (page) {
        SetupPage.Intro -> null
        SetupPage.Bridge -> SetupPage.Intro
        SetupPage.ProviderOverview -> SetupPage.Bridge
        SetupPage.GuidedChoice -> SetupPage.ProviderOverview
        SetupPage.GuidedSignup -> SetupPage.GuidedChoice
        SetupPage.GuidedKeyLink -> SetupPage.GuidedSignup
        SetupPage.GuidedPasteKey -> SetupPage.GuidedKeyLink
        SetupPage.ManualKeyLink -> SetupPage.ProviderOverview
        SetupPage.ManualKey -> SetupPage.ManualKeyLink
        SetupPage.ManualModels -> SetupPage.ManualKey
        SetupPage.ManualDefaults -> SetupPage.ManualModels
        SetupPage.Success -> null
    }
}
