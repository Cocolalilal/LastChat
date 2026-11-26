package me.rerere.rikkahub.ui.pages.assistant.detail

import android.content.ClipData
import android.graphics.Rect as AndroidRect
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuItem
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSession
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.NativeClipboard
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.memory.MemoryPointerParser
import kotlin.math.roundToInt

private data object AskAgentContextMenuKey
private data object InspectSourceContextMenuKey

/**
 * Registers custom "Ask Agent" and "Inspect Source" items in the Compose Foundation 1.9+
 * text context menu hierarchy inside a [androidx.compose.foundation.text.selection.SelectionContainer].
 */
fun Modifier.memorySelectionContextMenuComponents(
    hasSectionCitations: Boolean,
): Modifier = this.appendTextContextMenuComponents {
    separator()
    item(key = AskAgentContextMenuKey, label = "Ask Agent") {
        close()
    }
    if (hasSectionCitations) {
        item(key = InspectSourceContextMenuKey, label = "Inspect Source") {
            close()
        }
    }
    separator()
}

internal class InterceptingClipboard(
    private val delegate: Clipboard,
) : Clipboard {
    private var captureCallback: ((String) -> Unit)? = null
    private var sanitizeNextCopy: Boolean = false

    fun captureNext(onCaptured: (String) -> Unit) {
        sanitizeNextCopy = false
        captureCallback = onCaptured
    }

    fun sanitizeNextToSystemClipboard() {
        captureCallback = null
        sanitizeNextCopy = true
    }

    override suspend fun getClipEntry(): ClipEntry? = delegate.getClipEntry()

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        val rawText = clipEntry?.clipData?.let { data ->
            if (data.itemCount > 0) data.getItemAt(0)?.text?.toString() else null
        }
        val interceptor = captureCallback
        if (interceptor != null) {
            captureCallback = null
            if (rawText != null) {
                interceptor(rawText)
            }
            return
        }
        if (sanitizeNextCopy && rawText != null) {
            sanitizeNextCopy = false
            val clean = MemoryPointerParser.stripPointers(rawText)
            val label = clipEntry.clipData.description?.label ?: "plain text"
            delegate.setClipEntry(ClipEntry(ClipData.newPlainText(label, clean)))
            return
        }
        sanitizeNextCopy = false
        delegate.setClipEntry(clipEntry)
    }

    override val nativeClipboard: NativeClipboard
        get() = delegate.nativeClipboard
}

@Suppress("DEPRECATION")
internal class LegacyInterceptingClipboardManager(
    private val delegate: ClipboardManager,
) : ClipboardManager {
    private var captureCallback: ((String) -> Unit)? = null
    private var sanitizeNextCopy: Boolean = false

    fun captureNext(onCaptured: (String) -> Unit) {
        sanitizeNextCopy = false
        captureCallback = onCaptured
    }

    fun sanitizeNextToSystemClipboard() {
        captureCallback = null
        sanitizeNextCopy = true
    }

    override fun setText(annotatedString: AnnotatedString) {
        val interceptor = captureCallback
        if (interceptor != null) {
            captureCallback = null
            interceptor(annotatedString.text)
            return
        }
        if (sanitizeNextCopy) {
            sanitizeNextCopy = false
            val clean = MemoryPointerParser.stripPointers(annotatedString.text)
            delegate.setText(AnnotatedString(clean))
            return
        }
        delegate.setText(annotatedString)
    }

    override fun getText(): AnnotatedString? = delegate.getText()
    override fun hasText(): Boolean = delegate.hasText()
}

internal class MemorySelectionContextMenuProvider(
    private val view: View,
    private val coordinatesProvider: () -> LayoutCoordinates?,
    private val clipboardProxy: InterceptingClipboard,
    private val hasSectionCitations: () -> Boolean,
    private val onQuoteSelected: (rawSelection: String) -> Unit,
    private val onInspectReceiptSelected: (rawSelection: String) -> Unit,
) : TextContextMenuProvider {

    private var currentMode: ActionMode? = null

    fun dispose() {
        currentMode?.finish()
        currentMode = null
    }

    override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {
        val closed = CompletableDeferred<Unit>()
        val session = object : TextContextMenuSession {
            override fun close() {
                closed.complete(Unit)
            }
        }
        var startedMode: ActionMode? = null
        withContext(Dispatchers.Main.immediate) {
            currentMode?.finish()
            val callback = createCallback(session, dataProvider)
            val started = view.startActionMode(callback, ActionMode.TYPE_FLOATING)
            startedMode = started
            currentMode = started
            if (started == null) {
                session.close()
            }
        }
        try {
            closed.await()
        } finally {
            withContext(NonCancellable + Dispatchers.Main.immediate) {
                startedMode?.finish()
                if (currentMode === startedMode) {
                    currentMode = null
                }
            }
        }
    }

    private fun createCallback(
        session: TextContextMenuSession,
        dataProvider: TextContextMenuDataProvider,
    ): ActionMode.Callback2 = object : ActionMode.Callback2() {
        private var extraItems: List<TextContextMenuItem> = emptyList()

        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            if (menu == null) return false
            populateMenu(menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            if (menu == null) return false
            menu.clear()
            populateMenu(menu)
            return true
        }

        private fun resolvedComponents(): List<TextContextMenuItem> =
            runCatching { dataProvider.data().components.filterIsInstance<TextContextMenuItem>() }
                .getOrDefault(emptyList())

        private fun populateMenu(menu: Menu) {
            val items = resolvedComponents()
            val copyItem = items.firstOrNull { it.key == TextContextMenuKeys.CopyKey }
            val selectAllItem = items.firstOrNull { it.key == TextContextMenuKeys.SelectAllKey }

            if (copyItem != null) {
                menu.add(Menu.NONE, ID_ASK_AGENT, 0, "Ask Agent")
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                if (hasSectionCitations()) {
                    menu.add(Menu.NONE, ID_INSPECT_SOURCE, 1, "Inspect Source")
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                }
                menu.add(Menu.NONE, ID_CLEAN_COPY, 2, "Copy")
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            }
            if (selectAllItem != null) {
                menu.add(Menu.NONE, ID_SELECT_ALL, 3, selectAllItem.label)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            }

            val knownKeys = setOf(
                TextContextMenuKeys.CopyKey,
                TextContextMenuKeys.SelectAllKey,
                AskAgentContextMenuKey,
                InspectSourceContextMenuKey,
            )
            extraItems = items.filter { it.key !in knownKeys }
            extraItems.forEachIndexed { idx, extra ->
                menu.add(Menu.NONE, ID_EXTRA_BASE + idx, 10 + idx, extra.label)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            }
        }

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
            val itemId = item?.itemId ?: return false
            val items = resolvedComponents()
            val copyItem = items.firstOrNull { it.key == TextContextMenuKeys.CopyKey }
            val selectAllItem = items.firstOrNull { it.key == TextContextMenuKeys.SelectAllKey }

            when (itemId) {
                ID_ASK_AGENT -> {
                    val triggerCopy = copyItem ?: return false
                    clipboardProxy.captureNext { raw -> onQuoteSelected(raw) }
                    triggerCopy.onClick.invoke(session)
                    session.close()
                    mode?.finish()
                    return true
                }
                ID_INSPECT_SOURCE -> {
                    val triggerCopy = copyItem ?: return false
                    clipboardProxy.captureNext { raw -> onInspectReceiptSelected(raw) }
                    triggerCopy.onClick.invoke(session)
                    session.close()
                    mode?.finish()
                    return true
                }
                ID_CLEAN_COPY -> {
                    val triggerCopy = copyItem ?: return false
                    clipboardProxy.sanitizeNextToSystemClipboard()
                    triggerCopy.onClick.invoke(session)
                    session.close()
                    mode?.finish()
                    return true
                }
                ID_SELECT_ALL -> {
                    val triggerSelectAll = selectAllItem ?: return false
                    triggerSelectAll.onClick.invoke(session)
                    mode?.invalidate()
                    return true
                }
                else -> {
                    val extraIdx = itemId - ID_EXTRA_BASE
                    val extra = extraItems.getOrNull(extraIdx) ?: return false
                    extra.onClick.invoke(session)
                    return true
                }
            }
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            if (currentMode === mode) {
                currentMode = null
            }
            session.close()
        }

        override fun onGetContentRect(mode: ActionMode?, view: View?, outRect: AndroidRect?) {
            val coords = coordinatesProvider()?.takeIf { it.isAttached } ?: return
            val bounds = runCatching { dataProvider.contentBounds(coords) }.getOrNull() ?: return
            val rootOffset = coords.positionInRoot()
            outRect?.set(
                (bounds.left + rootOffset.x).roundToInt(),
                (bounds.top + rootOffset.y).roundToInt(),
                (bounds.right + rootOffset.x).roundToInt(),
                (bounds.bottom + rootOffset.y).roundToInt(),
            )
        }
    }

    private companion object {
        const val ID_ASK_AGENT = 1001
        const val ID_INSPECT_SOURCE = 1002
        const val ID_CLEAN_COPY = 1003
        const val ID_SELECT_ALL = 1004
        const val ID_EXTRA_BASE = 2000
    }
}

@Suppress("DEPRECATION")
internal class LegacyMemorySelectionTextToolbar(
    private val view: View,
    private val clipboardProxy: LegacyInterceptingClipboardManager,
    private val hasSectionCitations: () -> Boolean,
    private val onQuoteSelected: (rawSelection: String) -> Unit,
    private val onInspectReceiptSelected: (rawSelection: String) -> Unit,
) : TextToolbar {

    private var actionMode: ActionMode? = null
    private var contentRect: Rect = Rect.Zero
    private var onCopyRequested: (() -> Unit)? = null
    private var onSelectAllRequested: (() -> Unit)? = null

    override var status: TextToolbarStatus = TextToolbarStatus.Hidden
        private set

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        this.contentRect = rect
        this.onCopyRequested = onCopyRequested
        this.onSelectAllRequested = onSelectAllRequested

        if (actionMode == null) {
            status = TextToolbarStatus.Shown
            actionMode = view.startActionMode(callback, ActionMode.TYPE_FLOATING)
        } else {
            actionMode?.invalidateContentRect()
            actionMode?.invalidate()
        }
    }

    override fun hide() {
        status = TextToolbarStatus.Hidden
        actionMode?.finish()
        actionMode = null
    }

    private val callback = object : ActionMode.Callback2() {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            if (menu == null) return false
            populateMenu(menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            if (menu == null) return false
            menu.clear()
            populateMenu(menu)
            return true
        }

        private fun populateMenu(menu: Menu) {
            if (onCopyRequested != null) {
                menu.add(Menu.NONE, ID_ASK_QUOTE, 0, "Ask Agent")
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                if (hasSectionCitations()) {
                    menu.add(Menu.NONE, ID_INSPECT_RECEIPT, 1, "Inspect Source")
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                }
                menu.add(Menu.NONE, ID_CLEAN_COPY, 2, "Copy")
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            }
            if (onSelectAllRequested != null) {
                menu.add(Menu.NONE, ID_SELECT_ALL, 3, "Select All")
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            }
        }

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
            when (item?.itemId) {
                ID_ASK_QUOTE -> {
                    val triggerCopy = onCopyRequested ?: return false
                    clipboardProxy.captureNext { raw -> onQuoteSelected(raw) }
                    triggerCopy()
                    mode?.finish()
                    return true
                }
                ID_INSPECT_RECEIPT -> {
                    val triggerCopy = onCopyRequested ?: return false
                    clipboardProxy.captureNext { raw -> onInspectReceiptSelected(raw) }
                    triggerCopy()
                    mode?.finish()
                    return true
                }
                ID_CLEAN_COPY -> {
                    val triggerCopy = onCopyRequested ?: return false
                    clipboardProxy.sanitizeNextToSystemClipboard()
                    triggerCopy()
                    mode?.finish()
                    return true
                }
                ID_SELECT_ALL -> {
                    onSelectAllRequested?.invoke()
                    return true
                }
            }
            return false
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            actionMode = null
            status = TextToolbarStatus.Hidden
        }

        override fun onGetContentRect(mode: ActionMode?, view: View?, outRect: AndroidRect?) {
            outRect?.set(
                contentRect.left.roundToInt(),
                contentRect.top.roundToInt(),
                contentRect.right.roundToInt(),
                contentRect.bottom.roundToInt(),
            )
        }
    }

    private companion object {
        const val ID_ASK_QUOTE = 1001
        const val ID_INSPECT_RECEIPT = 1002
        const val ID_CLEAN_COPY = 1003
        const val ID_SELECT_ALL = 1004
    }
}

/**
 * Provides the custom selection toolbar ("Ask Agent", "Inspect Source", clean "Copy")
 * scoped to a single summary section, compatible with Compose Foundation 1.9+/1.10+
 * ([LocalClipboard] + [LocalTextContextMenuToolbarProvider]) as well as legacy fallback.
 */
@Composable
fun ProvideMemorySelectionToolbar(
    sectionContent: String,
    sectionHasCitations: Boolean,
    onQuoteText: (cleanQuote: String) -> Unit,
    onInspectReceipt: (memoryId: Int) -> Unit,
    onNoReceiptFound: () -> Unit,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val systemClipboard = LocalClipboard.current
    @Suppress("DEPRECATION")
    val legacySystemClipboard = LocalClipboardManager.current

    val latestContent by rememberUpdatedState(sectionContent)
    val latestHasCitations by rememberUpdatedState(sectionHasCitations)
    val latestOnQuote by rememberUpdatedState(onQuoteText)
    val latestOnInspect by rememberUpdatedState(onInspectReceipt)
    val latestOnNoReceipt by rememberUpdatedState(onNoReceiptFound)

    var layoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val clipboardProxy = remember(systemClipboard) { InterceptingClipboard(systemClipboard) }
    @Suppress("DEPRECATION")
    val legacyClipboardProxy = remember(legacySystemClipboard) {
        LegacyInterceptingClipboardManager(legacySystemClipboard)
    }

    val quoteHandler: (String) -> Unit = remember {
        { rawSelection ->
            val clean = MemoryPointerParser.stripPointers(rawSelection)
            if (clean.isNotBlank()) latestOnQuote(clean)
        }
    }
    val inspectHandler: (String) -> Unit = remember {
        { rawSelection ->
            val ids = MemoryPointerParser.resolveIdsForSelection(rawSelection, latestContent)
            if (ids.isNotEmpty()) {
                latestOnInspect(ids.first())
            } else {
                latestOnNoReceipt()
            }
        }
    }

    val contextMenuProvider = remember(view, clipboardProxy) {
        MemorySelectionContextMenuProvider(
            view = view,
            coordinatesProvider = { layoutCoordinates },
            clipboardProxy = clipboardProxy,
            hasSectionCitations = { latestHasCitations },
            onQuoteSelected = quoteHandler,
            onInspectReceiptSelected = inspectHandler,
        )
    }

    @Suppress("DEPRECATION")
    val legacyToolbar = remember(view, legacyClipboardProxy) {
        LegacyMemorySelectionTextToolbar(
            view = view,
            clipboardProxy = legacyClipboardProxy,
            hasSectionCitations = { latestHasCitations },
            onQuoteSelected = quoteHandler,
            onInspectReceiptSelected = inspectHandler,
        )
    }

    DisposableEffect(contextMenuProvider, legacyToolbar) {
        onDispose {
            contextMenuProvider.dispose()
            legacyToolbar.hide()
        }
    }

    @Suppress("DEPRECATION")
    CompositionLocalProvider(
        LocalClipboard provides clipboardProxy,
        LocalTextContextMenuToolbarProvider provides contextMenuProvider,
        LocalClipboardManager provides legacyClipboardProxy,
        LocalTextToolbar provides legacyToolbar,
    ) {
        Box(
            propagateMinConstraints = true,
            modifier = Modifier.onGloballyPositioned { layoutCoordinates = it },
        ) {
            content()
        }
    }
}
