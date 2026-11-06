@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.kuswa.clashtimer.ui

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.performTextReplacement
import androidx.core.view.WindowCompat
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kuswa.clashtimer.core.AppSettings
import com.kuswa.clashtimer.core.AppState
import com.kuswa.clashtimer.core.SyncLogic
import com.kuswa.clashtimer.core.SyncLog
import com.kuswa.clashtimer.core.SyncLogEntry
import com.kuswa.clashtimer.core.WidgetModelBuilder
import com.kuswa.clashtimer.data.Names
import com.kuswa.clashtimer.data.Times
import com.kuswa.clashtimer.ui.theme.ClashTimerTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppUiTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val settings = AppSettings(playerName = "Chief")

    private fun fixture(): AppState {
        val text = instrumentation.context.assets.open("fixtures/export_2.json").bufferedReader().use { it.readText() }
        return (SyncLogic.apply(AppState(), text) as SyncLogic.Outcome.Synced).state
    }

    private fun model(state: AppState, now: Long = state.export?.timestamp ?: 100L) =
        WidgetModelBuilder.build(state, settings, Names.get(context), Times.get(context), now)

    private fun screenshot(name: String, dark: Boolean = false) {
        // The test activity does not run MainActivity.enableEdgeToEdge(). Match its
        // system icons to the explicitly selected screenshot theme, as the app does.
        instrumentation.runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED).single()
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
        compose.waitForIdle()
        // SystemUI applies appearance changes in another process, after Compose is idle.
        instrumentation.uiAutomation.waitForIdle(300, 3_000)
        val config = context.resources.configuration
        val directory = File(context.filesDir, "ui-screenshots").apply { mkdirs() }
        val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name-${config.screenWidthDp}dp-font${config.fontScale}.png").outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        val background = image.getPixel(image.width / 2, image.height / 2)
        val varied = (image.width / 10 until image.width * 9 / 10 step (image.width / 32).coerceAtLeast(1)).any { x ->
            (image.height / 10 until image.height * 9 / 10 step (image.height / 32).coerceAtLeast(1)).any { y ->
                image.getPixel(x, y) != background
            }
        }
        image.recycle()
        assertTrue("Blank screenshot. On ATD emulators enable debug.hwui.drawing_enabled before visual tests.", varied)
    }

    private fun lazyText(text: String) = compose.onNode(hasScrollToIndexAction())
        .performScrollToNode(hasText(text))
        .let { compose.onNodeWithText(text) }

    @Test fun emptyDashboardExplainsSetupAndSyncWorks() {
        var taps = 0
        compose.setContent {
            ClashTimerTheme(dynamicColor = false) {
                Surface { TimerDashboard(model(AppState()), null, false, { taps++ }) }
            }
        }
        compose.onNodeWithText("Your village, at a glance").assertIsDisplayed()
        lazyText("Sync from clipboard").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, taps) }
        screenshot("empty")
    }

    @Test fun syncingDisablesTheAction() {
        compose.setContent {
            ClashTimerTheme { Surface { TimerDashboard(model(AppState()), null, true, {}) } }
        }
        lazyText("Syncing…").assertIsNotEnabled()
        screenshot("syncing")
    }

    @Test fun failedSyncShowsTheReasonAndRetry() {
        var retried = false
        compose.setContent {
            ClashTimerTheme {
                Surface {
                    TimerDashboard(model(AppState()), SyncLogEntry(100, "app", false, "Clipboard does not contain a village export."), false, { retried = true })
                }
            }
        }
        lazyText("Clipboard does not contain a village export.").assertIsDisplayed()
        lazyText("Retry from clipboard").performClick()
        compose.runOnIdle { assertTrue(retried) }
    }

    @Test fun filterStaysVisibleWhileTheTimerListScrolls() {
        val state = fixture()
        compose.setContent { ClashTimerTheme { Surface { TimerDashboard(model(state), null, false, {}) } } }
        compose.onNode(hasText("Lab") and hasClickAction()).performClick().assertIsSelected()
        lazyText("Dragon -> 13").assertIsDisplayed()
        compose.onNode(hasText("Lab") and hasClickAction()).assertIsDisplayed()
        compose.onNodeWithText("21:51:25").assertIsDisplayed()
        screenshot("lab-filter")
    }

    @Test fun numberEditorRejectsInvalidInputAndSavesValidInput() {
        var saved by mutableStateOf(settings)
        compose.setContent {
            ClashTimerTheme {
                Surface {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        SettingsSection(saved, null, 100) { saved = it(saved) }
                    }
                }
            }
        }
        compose.onNodeWithText("Builder potion").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("0")
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextReplacement("12")
        compose.onNodeWithText("Save").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(12 * 3600L, saved.boosts.builderPotionSavingSec) }
        compose.onNodeWithText("12 hours saved").assertIsDisplayed()
    }

    @Test fun automaticBuilderModeCanBeRestored() {
        var saved by mutableStateOf(settings.copy(totalBuildersOverride = 3))
        compose.setContent {
            ClashTimerTheme {
                Surface {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        SettingsSection(saved, null, 100) { saved = it(saved) }
                    }
                }
            }
        }
        compose.onNodeWithText("Total builders").performScrollTo().performClick()
        compose.onNodeWithText("Use automatic builder count").performClick()
        compose.runOnIdle { assertNull(saved.totalBuildersOverride) }
        compose.onNodeWithText("Automatic · 6 builders").assertIsDisplayed()
    }

    @Test fun appNavigationAndCrashTestConfirmation() {
        val state = fixture()
        val now = state.export!!.timestamp
        compose.setContent {
            ClashTimerTheme(dynamicColor = false) {
                ClashTimersContent(state, settings, model(state), SyncLog(listOf(SyncLogEntry(now, "app", true, "Village synced."))), now, Times.get(context).size, false, {}, {})
            }
        }
        compose.onNode(hasText("Lab") and hasClickAction()).performClick()
        compose.onNode(hasText("Settings") and hasClickAction()).performClick()
        compose.onNodeWithText("Upgrade finish alerts").performScrollTo().assertIsDisplayed()
        screenshot("settings")
        compose.onNode(hasText("Support") and hasClickAction()).performClick()
        compose.onNodeWithText("Test crash screen").performScrollTo().performClick()
        compose.onNodeWithText("Test the crash screen?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        Espresso.pressBack()
        compose.onNodeWithText("Clash Timers").assertIsDisplayed()
        compose.onNode(hasText("Lab") and hasClickAction()).assertIsSelected()
    }

    @Test fun completedAndIdleTimersRemainReadable() {
        val state = fixture()
        compose.setContent {
            ClashTimerTheme {
                Surface { TimerDashboard(model(state, state.export!!.timestamp + 30L * 86400), null, false, {}) }
            }
        }
        compose.onNode(hasText("Lab") and hasClickAction()).performClick()
        lazyText("Completed").assertIsDisplayed()
        screenshot("completed")
    }

    @Test fun idleExportHasAnHonestEmptyBuilderState() {
        val state = (SyncLogic.apply(AppState(), """{"tag":"#TEST000","timestamp":100,"buildings":[]}""")
            as SyncLogic.Outcome.Synced).state
        compose.setContent {
            ClashTimerTheme { Surface { TimerDashboard(model(state), null, false, {}) } }
        }
        compose.onNodeWithText("Builders").performClick()
        lazyText("All builders are free").assertIsDisplayed()
        screenshot("builders-idle")
    }

    @Test fun keyboardDoneSavesValidValue() {
        var saved: Int? = null
        compose.setContent {
            ClashTimerTheme {
                Surface { SettingValueDialog("Helper session interval", 23, 2..48, "Hours", onSave = { saved = it }, onDismiss = {}) }
            }
        }
        compose.onNode(hasSetTextAction()).performTextReplacement("24")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.runOnIdle { assertEquals(24, saved) }
    }

    @Test fun resetRequiresConfirmationAndPreservesNameAndWidgetPreference() {
        var saved by mutableStateOf(settings.copy(totalBuildersOverride = 3, liveCountdown = false))
        compose.setContent {
            ClashTimerTheme {
                Surface {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        SettingsSection(saved, null, 100) { saved = it(saved) }
                    }
                }
            }
        }
        compose.onNodeWithText("Upgrade finish alerts").assertIsOn().performClick()
        compose.onNodeWithText("Upgrade finish alerts").assertIsOff()
        compose.onNodeWithText("Reset settings").performScrollTo().performClick()
        compose.onNodeWithText("Reset settings?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle {
            assertEquals(3, saved.totalBuildersOverride)
            assertFalse(saved.notificationsEnabled)
        }
        compose.onNodeWithText("Reset settings").performClick()
        compose.onNodeWithText("Reset", substring = false).performClick()
        compose.runOnIdle {
            assertEquals(settings.playerName, saved.playerName)
            assertFalse(saved.liveCountdown)
            assertNull(saved.totalBuildersOverride)
            assertTrue(saved.notificationsEnabled)
        }
    }

    @Test fun destinationAndCategorySurviveSavedStateRestore() {
        val state = fixture()
        val now = state.export!!.timestamp
        val restore = StateRestorationTester(compose)
        restore.setContent {
            ClashTimerTheme {
                ClashTimersContent(state, settings, model(state), SyncLog(), now, 0, false, {}, {})
            }
        }
        compose.onNode(hasText("Lab") and hasClickAction()).performClick()
        compose.onNode(hasText("Settings") and hasClickAction()).performClick()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Upgrade finish alerts").performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("Timers") and hasClickAction()).performClick()
        compose.onNode(hasText("Lab") and hasClickAction()).assertIsSelected()
    }

    @Test fun expressiveLightMaterialTheme() = assertExpressiveTheme(false)
    @Test fun expressiveDarkMaterialTheme() = assertExpressiveTheme(true)

    private fun assertExpressiveTheme(dark: Boolean) {
        var colors: ColorScheme? = null
        var shapes: Shapes? = null
        var motion: MotionScheme? = null
        compose.setContent {
            ClashTimerTheme(darkTheme = dark, dynamicColor = false) {
                colors = MaterialTheme.colorScheme
                shapes = MaterialTheme.shapes
                motion = MaterialTheme.motionScheme
                Text("Material Expressive theme")
            }
        }
        compose.runOnIdle {
            assertSame(MotionScheme.expressive(), motion)
            val expected = if (dark) darkColorScheme() else expressiveLightColorScheme()
            val actual = checkNotNull(colors)
            assertEquals(expected.primary, actual.primary)
            assertEquals(expected.primaryContainer, actual.primaryContainer)
            assertEquals(expected.onPrimaryContainer, actual.onPrimaryContainer)
            assertEquals(expected.background, actual.background)
            assertEquals(expected.surfaceContainer, actual.surfaceContainer)
            val expectedShapes = Shapes()
            val actualShapes = checkNotNull(shapes)
            assertEquals(expectedShapes.extraSmall, actualShapes.extraSmall)
            assertEquals(expectedShapes.small, actualShapes.small)
            assertEquals(expectedShapes.medium, actualShapes.medium)
            assertEquals(expectedShapes.large, actualShapes.large)
            assertEquals(expectedShapes.extraLarge, actualShapes.extraLarge)
        }
    }

    @Test fun countdownStaysOnOneLineWithoutClipping() {
        val state = fixture()
        val now = state.export!!.timestamp + 3600
        compose.setContent {
            ClashTimerTheme(dynamicColor = false) {
                Surface { TimerDashboard(model(state, now), null, false, {}) }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        lazyText("19:43:27").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        compose.runOnIdle {
            assertEquals(1, layouts.single().lineCount)
            assertFalse("Countdown must remain fully readable", layouts.single().hasVisualOverflow)
        }
    }

    @Test fun dashboardLightScreenshot() = renderDashboard(false)
    @Test fun dashboardDarkScreenshot() = renderDashboard(true)

    private fun renderDashboard(dark: Boolean) {
        val state = fixture()
        val now = state.export!!.timestamp + 3600
        compose.setContent {
            ClashTimerTheme(darkTheme = dark, dynamicColor = false) {
                ClashTimersContent(state, settings, model(state, now), SyncLog(listOf(SyncLogEntry(now, "app", true, "Village synced."))), now, Times.get(context).size, false, {}, {})
            }
        }
        compose.onNodeWithText("Chief · Town Hall 18").assertIsDisplayed()
        screenshot(if (dark) "dashboard-dark" else "dashboard-light", dark)
        lazyText("Cake-A-Pult Damage -> 8").assertIsDisplayed()
        screenshot(if (dark) "timers-dark" else "timers-light", dark)
    }

    @Test fun playerNameDraftSurvivesRestoreAndSaveWorksWithKeyboard() {
        var saved: String? = null
        val restore = StateRestorationTester(compose)
        restore.setContent {
            ClashTimerTheme(dynamicColor = false) {
                PlayerNameDialog("", true, onDone = { saved = it }, onDismiss = {})
            }
        }
        compose.onNode(hasSetTextAction()).performTextReplacement("Village")
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Village").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performClick()
        screenshot("player-name-dialog-keyboard")
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertEquals("Village", saved) }
    }

    @Test fun valueDialogScreenshot() {
        var saved: Int? = null
        compose.setContent {
            ClashTimerTheme(dynamicColor = false) {
                Surface {
                    SettingValueDialog("Builder potion", 9, 1..47, "Hours saved", onSave = { saved = it }, onDismiss = {})
                }
            }
        }
        compose.onNodeWithText("Save").assertIsEnabled()
        screenshot("value-dialog")
        compose.onNode(hasSetTextAction()).performClick()
        screenshot("value-dialog-keyboard")
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertEquals(9, saved) }
    }
}
