package me.rerere.rikkahub.ui.components.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import me.rerere.rikkahub.ui.components.nav.AppCompactTopBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class SharedUiComponentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun appSearchField_clearButtonClearsValue() {
        var value by mutableStateOf("hello")

        composeRule.setContent {
            MaterialTheme {
                AppSearchField(
                    value = value,
                    onValueChange = { value = it },
                    placeholder = { Text("Search") }
                )
            }
        }

        composeRule.onNodeWithContentDescription("Clear").assertExists().performClick()
        composeRule.runOnIdle {
            assertEquals("", value)
        }
        composeRule.onNodeWithContentDescription("Clear").assertDoesNotExist()
    }

    @Test
    fun appModalSheet_rendersTitleBodyAndCloseButton() {
        var visible by mutableStateOf(true)

        composeRule.setContent {
            MaterialTheme {
                if (visible) {
                    AppModalSheet(
                        onDismissRequest = { visible = false },
                        title = "Sheet title",
                        showCloseButton = true,
                    ) {
                        Text("Sheet body")
                    }
                }
            }
        }

        composeRule.onNodeWithText("Sheet title").assertExists()
        composeRule.onNodeWithText("Sheet body").assertExists()
        composeRule.onNodeWithContentDescription("Close").assertExists().performClick()
        composeRule.runOnIdle {
            assertFalse(visible)
        }
    }

    @Test
    fun appAlertDialog_rendersTitleMessageAndButtons() {
        composeRule.setContent {
            MaterialTheme {
                AppAlertDialog(
                    onDismissRequest = {},
                    title = { Text("Dialog title") },
                    text = { Text("Dialog body") },
                    confirmButton = { TextButton(onClick = {}) { Text("Confirm") } },
                    dismissButton = { TextButton(onClick = {}) { Text("Cancel") } },
                )
            }
        }

        composeRule.onNodeWithText("Dialog title").assertExists()
        composeRule.onNodeWithText("Dialog body").assertExists()
        composeRule.onNodeWithText("Confirm").assertExists()
        composeRule.onNodeWithText("Cancel").assertExists()
    }

    @Test
    fun appCompactTopBar_rendersProvidedTitle() {
        composeRule.setContent {
            MaterialTheme {
                AppCompactTopBar(
                    title = { Text("Compact title") },
                    navigationIcon = {
                        IconButton(onClick = {}) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        }

        composeRule.onNodeWithText("Compact title").assertExists()
        composeRule.onNodeWithContentDescription("Back").assertExists()
    }

    @Test
    fun floatingControls_renderOutlinedTabsAndActions() {
        composeRule.setContent {
            MaterialTheme {
                AppFloatingTabBar {
                    AppFloatingTabButton(
                        selected = true,
                        onClick = {},
                        icon = Icons.Rounded.Category,
                        contentDescription = "Skills"
                    )
                    AppFloatingTabButton(
                        selected = false,
                        onClick = {},
                        icon = Icons.Rounded.Book,
                        contentDescription = "Lorebooks"
                    )
                }
                AppFloatingActionButton(onClick = {}) {
                    Icon(Icons.Rounded.Category, contentDescription = "Add")
                }
            }
        }

        composeRule.onNodeWithContentDescription("Skills").assertExists()
        composeRule.onNodeWithContentDescription("Lorebooks").assertExists()
        composeRule.onNodeWithContentDescription("Add").assertExists()
    }
}
