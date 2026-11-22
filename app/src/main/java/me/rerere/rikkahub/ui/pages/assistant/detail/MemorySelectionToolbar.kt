package me.rerere.rikkahub.ui.pages.assistant.detail

import android.graphics.Rect as AndroidRect
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.AnnotatedString
import me.rerere.rikkahub.data.memory.MemoryPointerParser
import kotlin.math.roundToInt

@Suppress("DEPRECATION")
internal class InterceptingClipboardManager(
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

internal class MemorySelectionTextToolbar(
    private val view: View,
    private val clipboardProxy: InterceptingClipboardManager,
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
                    menu.add(Menu.NONE, ID_INSPECT_RECEIPT, 1, "Inspect Receipt")
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
 * Provides the custom ChatGPT-style selection toolbar scoped to a single summary section.
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
    @Suppress("DEPRECATION")
    val systemClipboard = LocalClipboardManager.current

    val latestContent by rememberUpdatedState(sectionContent)
    val latestHasCitations by rememberUpdatedState(sectionHasCitations)
    val latestOnQuote by rememberUpdatedState(onQuoteText)
    val latestOnInspect by rememberUpdatedState(onInspectReceipt)
    val latestOnNoReceipt by rememberUpdatedState(onNoReceiptFound)

    val clipboardProxy = remember(systemClipboard) { InterceptingClipboardManager(systemClipboard) }
    val toolbar = remember(view, clipboardProxy) {
        MemorySelectionTextToolbar(
            view = view,
            clipboardProxy = clipboardProxy,
            hasSectionCitations = { latestHasCitations },
            onQuoteSelected = { rawSelection ->
                val clean = MemoryPointerParser.stripPointers(rawSelection)
                if (clean.isNotBlank()) latestOnQuote(clean)
            },
            onInspectReceiptSelected = { rawSelection ->
                val ids = MemoryPointerParser.resolveIdsForSelection(rawSelection, latestContent)
                if (ids.isNotEmpty()) {
                    latestOnInspect(ids.first())
                } else {
                    latestOnNoReceipt()
                }
            },
        )
    }

    DisposableEffect(toolbar) {
        onDispose { toolbar.hide() }
    }

    @Suppress("DEPRECATION")
    CompositionLocalProvider(
        LocalClipboardManager provides clipboardProxy,
        LocalTextToolbar provides toolbar,
        content = content,
    )
}
