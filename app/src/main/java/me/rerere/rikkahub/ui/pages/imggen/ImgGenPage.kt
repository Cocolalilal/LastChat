package me.rerere.rikkahub.ui.pages.imggen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import coil3.compose.AsyncImage
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import android.content.ClipData
import me.rerere.rikkahub.ui.components.ui.ToastType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.ModelType
import me.rerere.ai.ui.ImageAspectRatio
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.components.ui.ImagePreviewDialog
import me.rerere.rikkahub.ui.components.ui.OutlinedNumberInput
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.utils.saveMessageImage
import org.koin.androidx.compose.koinViewModel
import java.io.File
import androidx.compose.material3.AlertDialog

@Composable
fun ImageGenPage(
    modifier: Modifier = Modifier,
    vm: ImgGenVM = koinViewModel()
) {
    val scope = rememberCoroutineScope()
    val isGenerating by vm.isGenerating.collectAsStateWithLifecycle()
    
    // State for switching between Generation and Gallery views
    var showGallery by remember { mutableStateOf(false) }
    
    // Settings sheet state - lifted to page level for TopBar access
    var showSettingsSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    // Cancel dialog for back handler during generation
    var showCancelDialog by remember { mutableStateOf(false) }
    
    // Back handler: if in gallery, go back to image gen; if generating, show cancel dialog
    BackHandler(showGallery || isGenerating) {
        when {
            showGallery -> showGallery = false
            isGenerating -> showCancelDialog = true
        }
    }
    if (showCancelDialog) {
        CancelDialog(
            onDismiss = { showCancelDialog = false },
            onConfirm = {
                showCancelDialog = false
                vm.cancelGeneration()
            }
        )
    }

    Scaffold(
        topBar = {
            TopBar(
                showGallery = showGallery,
                onToggleView = { showGallery = !showGallery },
                onShowSettings = { showSettingsSheet = true }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        // Crossfade between Generation and Gallery
        AnimatedContent(
            targetState = showGallery,
            transitionSpec = {
                fadeIn(animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f)) togetherWith
                        fadeOut(animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f))
            },
            modifier = modifier.padding(innerPadding),
            label = "view_crossfade"
        ) { isGallery ->
            if (isGallery) {
                ImageGalleryScreen(vm = vm)
            } else {
                ImageGenScreen(
                    vm = vm,
                    showSettingsSheet = showSettingsSheet,
                    sheetState = sheetState,
                    onShowSettings = { showSettingsSheet = true },
                    onDismissSettings = { showSettingsSheet = false }
                )
            }
        }
    }
}

@Composable
private fun TopBar(
    showGallery: Boolean,
    onToggleView: () -> Unit,
    onShowSettings: () -> Unit
) {
    val navController = LocalNavController.current
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        navigationIcon = {
            BackButton()
        },
        title = {
            // Crossfade title
            AnimatedContent(
                targetState = showGallery,
                transitionSpec = {
                    fadeIn(animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f)) togetherWith
                            fadeOut(animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f))
                },
                label = "title_crossfade"
            ) { isGallery ->
                Text(
                    text = stringResource(
                        if (isGallery) R.string.imggen_page_gallery
                        else R.string.imggen_page_title
                    ),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        },
        actions = {
            // Settings button (only visible when not showing gallery)
            if (!showGallery) {
                IconButton(onClick = onShowSettings) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = stringResource(R.string.imggen_page_settings_title)
                    )
                }
            }
            // Crossfade action button (Gallery <-> Generate icons)
            IconButton(onClick = onToggleView) {
                AnimatedContent(
                    targetState = showGallery,
                    transitionSpec = {
                        fadeIn(animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f)) togetherWith
                                fadeOut(animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f))
                    },
                    label = "action_crossfade"
                ) { isGallery ->
                    Icon(
                        imageVector = if (isGallery) Icons.Rounded.Palette else Icons.Rounded.Collections,
                        contentDescription = stringResource(
                            if (isGallery) R.string.imggen_page_title else R.string.imggen_page_gallery
                        )
                    )
                }
            }
        }
    )
}

@Composable
private fun CancelDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.imggen_page_cancel_generation_title)) },
        text = { Text(stringResource(R.string.imggen_page_cancel_generation_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.imggen_page_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.imggen_page_cancel))
            }
        }
    )
}

@Composable
private fun ImageGenScreen(
    vm: ImgGenVM,
    showSettingsSheet: Boolean,
    sheetState: SheetState,
    onShowSettings: () -> Unit,
    onDismissSettings: () -> Unit
) {
    val prompt by vm.prompt.collectAsStateWithLifecycle()
    val aspectRatio by vm.aspectRatio.collectAsStateWithLifecycle()
    val isGenerating by vm.isGenerating.collectAsStateWithLifecycle()
    val currentGeneratedImages by vm.currentGeneratedImages.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val settings by vm.settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val selectedImageUri by vm.selectedImageUri.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val haptics = me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics()

    // Animation phase tracking
    // Phases: 0=Idle, 1=Generating, 2=ImageArrived, 3=Printing, 4=Ejecting, 5=Falling, 6=Settled
    var animationPhase by remember { mutableStateOf(0) }
    
    // Session history - accumulates all generated images in this session
    val sessionImages = remember { mutableStateListOf<GeneratedImage>() }
    val listState = rememberLazyListState()
    
    // Track generation state changes
    LaunchedEffect(isGenerating) {
        if (isGenerating) {
            animationPhase = 1 // Generating
        }
    }
    
    // Track when new images are generated
    LaunchedEffect(currentGeneratedImages) {
        if (currentGeneratedImages.isNotEmpty()) {
            // Add new images to session history (at the beginning for newest-first)
            currentGeneratedImages.forEach { newImage ->
                if (sessionImages.none { it.filePath == newImage.filePath }) {
                    sessionImages.add(0, newImage)
                }
            }
            
            // Phase sequence with delays and haptics
            animationPhase = 2 // ImageArrived
            kotlinx.coroutines.delay(200)
            
            animationPhase = 3 // Printing - image starts emerging
            haptics.perform(me.rerere.rikkahub.ui.hooks.HapticPattern.Tick)
            kotlinx.coroutines.delay(300)
            haptics.perform(me.rerere.rikkahub.ui.hooks.HapticPattern.Tick)
            kotlinx.coroutines.delay(300)
            
            animationPhase = 4 // Ejecting
            haptics.perform(me.rerere.rikkahub.ui.hooks.HapticPattern.Pop)
            kotlinx.coroutines.delay(250)
            
            animationPhase = 5 // Falling
            kotlinx.coroutines.delay(400)
            
            animationPhase = 6 // Settled
            haptics.perform(me.rerere.rikkahub.ui.hooks.HapticPattern.Thud)
            
            // Scroll to top to show newest image
            listState.animateScrollToItem(0)
        }
    }

    LaunchedEffect(error) {
        error?.let { errorMessage ->
            toaster.show(message = errorMessage, type = ToastType.Error)
            vm.clearError()
            animationPhase = 0 // Reset on error
        }
    }

    // Image animation values - simplified direct fall
    // Random rotation for natural look (slight misalignment)
    val randomRotation = remember { kotlin.random.Random.nextFloat() * 6f - 3f } // -3 to 3 degrees
    
    val imageScale by animateFloatAsState(
        targetValue = when (animationPhase) {
            0, 1, 2 -> 0f  // Hidden during generation
            else -> 1f     // Full size when showing
        },
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "image_scale"
    )
    
    // Y offset: Image rises from bottom (input bar area) to center
    val imageOffsetY by animateFloatAsState(
        targetValue = when (animationPhase) {
            0, 1, 2 -> 600f   // Hidden below (near input bar)
            else -> 0f         // Final position (center)
        },
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 200f),
        label = "image_offset"
    )
    
    
    val imageAlpha by animateFloatAsState(
        targetValue = if (animationPhase >= 3) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "image_alpha"
    )
    
    // Random slight rotation for natural landing
    val imageRotation by animateFloatAsState(
        targetValue = when (animationPhase) {
            0, 1, 2 -> 0f
            else -> randomRotation  // Land with slight random tilt
        },
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "image_rotation"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Main content area with generated images - scrollable session history
        if (sessionImages.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 200.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.imggen_page_prompt_placeholder),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp)
                    .padding(bottom = 200.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(
                    items = sessionImages,
                    key = { _, image -> image.filePath }
                ) { index, image ->
                    val isNewest = index == 0
                    var showPreview by remember { mutableStateOf(false) }
                    
                    // Only animate the newest image
                    val itemScale = if (isNewest) imageScale else 1f
                    val itemOffsetY = if (isNewest) imageOffsetY else 0f
                    val itemAlpha = if (isNewest) imageAlpha else 1f
                    val itemRotation = if (isNewest) imageRotation else 0f
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = itemScale
                                scaleY = itemScale
                                translationY = itemOffsetY
                                alpha = itemAlpha
                                rotationZ = itemRotation
                            }
                    ) {
                        AsyncImage(
                            model = File(image.filePath),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(
                                    when (aspectRatio) {
                                        ImageAspectRatio.SQUARE -> 1f
                                        ImageAspectRatio.LANDSCAPE -> 16f / 9f
                                        ImageAspectRatio.PORTRAIT -> 9f / 16f
                                    }
                                )
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { showPreview = true },
                            contentScale = ContentScale.Crop
                        )
                        
                        if (showPreview) {
                            ImagePreviewDialog(
                                images = listOf("file://${image.filePath}"),
                                onDismissRequest = { showPreview = false },
                                prompt = image.prompt
                            )
                        }
                    }
                }
            }
        }
        
        // Top gradient removed per user request

        // Gradient behind floating toolbar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(220.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.9f)
                        )
                    )
                )
        )

        // Floating Input Toolbar with animated glow
        FloatingInputBar(
            prompt = prompt,
            selectedImage = selectedImageUri,
            vm = vm,
            settings = settings,
            isGenerating = isGenerating,
            animationPhase = animationPhase,
            onShowSettings = onShowSettings,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    if (showSettingsSheet) {
        SettingsBottomSheet(
            vm = vm,
            settings = settings,
            aspectRatio = aspectRatio,
            scope = scope,
            sheetState = sheetState,
            onDismiss = onDismissSettings
        )
    }
}

@Composable
private fun FloatingInputBar(
    prompt: String,
    selectedImage: android.net.Uri?,
    vm: ImgGenVM,
    settings: Settings,
    isGenerating: Boolean,
    animationPhase: Int, // 0=Idle, 1=Generating, 2=ImageArrived, 3=Printing, 4+=After
    onShowSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cornerRadius = 28.dp
    val innerCornerRadius = 20.dp
    val haptics = me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics()
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        vm.setSelectedImage(uri)
    }

    Column(
        modifier = modifier
            .imePadding()
            .navigationBarsPadding()
            .padding(bottom = 8.dp, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Main floating container - no glow
        Surface(
            shape = RoundedCornerShape(cornerRadius),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Top row: Image picker + TextField
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Image picker button (for image-to-image generation)
                    val model = settings.findModelById(settings.imageGenerationModelId)
                    val supportsImageInput = model?.inputModalities?.contains(Modality.IMAGE) == true
                    
                    if (supportsImageInput) {
                        Surface(
                            shape = RoundedCornerShape(innerCornerRadius),
                            color = Color.Transparent
                        ) {
                            if (selectedImage != null) {
                                Box(
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    AsyncImage(
                                        model = selectedImage,
                                        contentDescription = stringResource(R.string.a11y_selected_image),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(16.dp)
                                            .clickable { vm.clearSelectedImage() },
                                        shadowElevation = 2.dp
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = stringResource(R.string.a11y_remove_image),
                                            modifier = Modifier.padding(2.dp),
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            } else {
                                IconButton(
                                    onClick = {
                                        launcher.launch(
                                            PickVisualMediaRequest(
                                                ActivityResultContracts.PickVisualMedia.ImageOnly
                                            )
                                        )
                                    }
                                ) {
                                    Icon(
                                        Icons.Rounded.Photo,
                                        contentDescription = stringResource(R.string.a11y_add_image),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    // Text input with black background
                    Surface(
                        shape = RoundedCornerShape(innerCornerRadius),
                        color = if (LocalDarkMode.current) Color.Black.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.08f),
                        modifier = Modifier.weight(1f)
                    ) {
                        TextField(
                            value = prompt,
                            onValueChange = vm::updatePrompt,
                            placeholder = {
                                Text(
                                    stringResource(R.string.imggen_page_prompt_placeholder),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 1,
                            maxLines = 4,
                            colors = TextFieldDefaults.colors(
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                cursorColor = MaterialTheme.colorScheme.primary,
                            ),
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // Full-width Generate Button
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val scale by animateFloatAsState(
                    targetValue = if (isPressed) 0.97f else 1f,
                    animationSpec = spring(dampingRatio = 0.4f, stiffness = 400f),
                    label = "generate_scale"
                )

                Button(
                    onClick = {
                        if (!isGenerating) {
                            haptics.perform(me.rerere.rikkahub.ui.hooks.HapticPattern.Pop)
                            vm.generateImage()
                        } else {
                            haptics.perform(me.rerere.rikkahub.ui.hooks.HapticPattern.Thud)
                            vm.cancelGeneration()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        },
                    interactionSource = interactionSource,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isGenerating) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary,
                        contentColor = if (isGenerating) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(innerCornerRadius),
                    enabled = prompt.isNotBlank() || isGenerating
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.imggen_page_cancel))
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.imggen_page_generate_image))
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageGalleryScreen(
    vm: ImgGenVM,
) {
    val generatedImages = vm.generatedImages.collectAsLazyPagingItems()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val pullToRefreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = false,
        onRefresh = { generatedImages.refresh() },
        state = pullToRefreshState,
        modifier = Modifier.fillMaxSize()
    ) {
        if (generatedImages.itemCount == 0) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Collections,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.imggen_page_no_generated_images),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(
                    count = generatedImages.itemCount,
                    key = generatedImages.itemKey { it.id },
                    contentType = generatedImages.itemContentType { "GeneratedImage" }
                ) { index ->
                    val image = generatedImages[index]
                    image?.let {
                        var showPreview by remember { mutableStateOf(false) }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column {
                                AsyncImage(
                                    model = File(it.filePath),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clickable { showPreview = true },
                                    contentScale = ContentScale.Crop
                                )

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = it.model,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = it.prompt.take(20) + if (it.prompt.length > 20) "..." else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2
                                        )
                                    }

                                    Row {
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    clipboard.setClipEntry(
                                                        ClipEntry(
                                                            ClipData.newPlainText(null, it.prompt)
                                                        )
                                                    )
                                            toaster.show(
                                                message = context.getString(R.string.imggen_page_prompt_copied),
                                                type = ToastType.Success
                                            )
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.ContentCopy,
                                                contentDescription = stringResource(R.string.a11y_copy_prompt),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    try {
                                                        context.saveMessageImage("file://${it.filePath}")
                                                        toaster.show(
                                                            message = context.getString(R.string.imggen_page_image_saved_success),
                                                            type = ToastType.Success
                                                        )
                                                    } catch (e: Exception) {
                                                        toaster.show(
                                                            message = context.getString(
                                                                R.string.imggen_page_save_failed,
                                                                e.message
                                                            ),
                                                            type = ToastType.Error
                                                        )
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Save,
                                                contentDescription = stringResource(R.string.imggen_page_save),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = { vm.deleteImage(it) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Delete,
                                                contentDescription = stringResource(R.string.imggen_page_delete),
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (showPreview) {
                            ImagePreviewDialog(
                                images = listOf(it.filePath),
                                onDismissRequest = { showPreview = false }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsBottomSheet(
    vm: ImgGenVM,
    settings: Settings,
    aspectRatio: ImageAspectRatio,
    scope: CoroutineScope,
    sheetState: SheetState,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.imggen_page_settings_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            FormItem(
                label = { Text(stringResource(R.string.imggen_page_model_selection)) },
                description = { Text(stringResource(R.string.imggen_page_model_selection_desc)) }
            ) {
                ModelSelector(
                    modelId = settings.imageGenerationModelId,
                    providers = settings.providers,
                    type = ModelType.IMAGE,
                    onlyIcon = false,
                    onSelect = { model ->
                        scope.launch {
                            vm.settingsStore.update { oldSettings ->
                                oldSettings.copy(imageGenerationModelId = model.id)
                            }
                        }
                    }
                )
            }

            FormItem(
                label = { Text(stringResource(R.string.imggen_page_aspect_ratio)) },
                description = { Text(stringResource(R.string.imggen_page_aspect_ratio_desc)) }
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ImageAspectRatio.entries.forEach { ratio ->
                        FilterChip(
                            selected = aspectRatio == ratio,
                            onClick = { vm.updateAspectRatio(ratio) },
                            label = {
                                Text(
                                    stringResource(
                                        when (ratio) {
                                            ImageAspectRatio.SQUARE -> R.string.imggen_page_aspect_ratio_square
                                            ImageAspectRatio.LANDSCAPE -> R.string.imggen_page_aspect_ratio_landscape
                                            ImageAspectRatio.PORTRAIT -> R.string.imggen_page_aspect_ratio_portrait
                                        }
                                    )
                                )
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
