package me.rerere.rikkahub.data.deletion

/**
 * Shared grace window for destructive actions that offer Undo.
 *
 * Must match the default [me.rerere.rikkahub.ui.components.ui.AppToasterState] duration so
 * the Undo action cannot outlive the restore snapshot / delayed wipe job.
 */
const val DESTRUCTIVE_UNDO_WINDOW_MS = 6_000L
