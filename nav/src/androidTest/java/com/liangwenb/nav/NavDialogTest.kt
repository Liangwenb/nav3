package com.liangwenb.nav

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.pressBack
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NavDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dialogContent_dismissesOnlyWhenScrimIsClicked() {
        var dismissCount by mutableIntStateOf(0)
        composeRule.setContent {
            DialogContent(onDismissRequest = { dismissCount++ }) {
                Box(
                    Modifier
                        .width(120.dp)
                        .height(80.dp)
                        .testTag("dialog-content")
                        .clickable {},
                )
            }
        }

        composeRule.onNodeWithTag("dialog-content").performClick()
        composeRule.runOnIdle { assertEquals(0, dismissCount) }

        composeRule.onNodeWithTag("nav-dialog-scrim").performTouchInput {
            click(Offset(4f, 4f))
        }
        composeRule.runOnIdle { assertEquals(1, dismissCount) }
    }

    @Test
    fun bottomSheetDialog_dismissesOnlyWhenScrimIsClicked() {
        var dismissCount by mutableIntStateOf(0)
        composeRule.setContent {
            BottomSheetDialog(onDismissRequest = { dismissCount++ }) {
                Box(
                    Modifier
                        .width(120.dp)
                        .height(80.dp)
                        .testTag("bottom-dialog-content")
                        .clickable {},
                )
            }
        }

        composeRule.onNodeWithTag("bottom-dialog-content").performClick()
        composeRule.runOnIdle { assertEquals(0, dismissCount) }

        composeRule.onNodeWithTag("nav-bottom-dialog-scrim").performTouchInput {
            click(Offset(4f, 4f))
        }
        composeRule.runOnIdle { assertEquals(1, dismissCount) }
    }

    @Test
    fun dialogContent_dismissesWhenSystemBackIsPressed() {
        var dismissCount by mutableIntStateOf(0)
        composeRule.setContent {
            DialogContent(onDismissRequest = { dismissCount++ }) {
                Box(Modifier.testTag("dialog-back-content"))
            }
        }

        pressBack()

        composeRule.runOnIdle { assertEquals(1, dismissCount) }
    }

    @Test
    fun bottomSheetDialog_dismissesWhenSystemBackIsPressed() {
        var dismissCount by mutableIntStateOf(0)
        composeRule.setContent {
            BottomSheetDialog(onDismissRequest = { dismissCount++ }) {
                Box(Modifier.testTag("bottom-dialog-back-content"))
            }
        }

        pressBack()

        composeRule.runOnIdle { assertEquals(1, dismissCount) }
    }
}
