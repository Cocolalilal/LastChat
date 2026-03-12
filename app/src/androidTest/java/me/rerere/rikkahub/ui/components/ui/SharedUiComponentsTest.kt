package me.rerere.rikkahub.ui.components.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
}
