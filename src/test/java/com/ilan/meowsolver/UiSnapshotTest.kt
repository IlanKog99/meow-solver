package com.ilan.meowsolver

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.ilan.meowsolver.core.CatOrder
import com.ilan.meowsolver.core.OverlaySettings
import com.ilan.meowsolver.core.PanelButton
import com.ilan.meowsolver.core.PanelLayout
import com.ilan.meowsolver.ui.Bubble
import com.ilan.meowsolver.ui.ButtonOrderEditor
import com.ilan.meowsolver.ui.SetupColors
import com.ilan.meowsolver.ui.PanelCard
import com.ilan.meowsolver.ui.PanelState
import com.ilan.meowsolver.ui.SetupScreen
import com.ilan.meowsolver.ui.SetupState
import com.ilan.meowsolver.ui.ThemeMode
import org.junit.Rule
import org.junit.Test

class UiSnapshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)

    @Composable private fun OnGame(content: @Composable () -> Unit) =
        Box(Modifier.fillMaxSize().background(Color(0xFFF7F1EE)).padding(24.dp)) { content() }

    @Composable private fun Panel(s: PanelState) = PanelCard(s, {}, {}, {}, {}, {}, {}, {})

    @Test fun panelIdle() = paparazzi.snapshot { OnGame { Panel(PanelState("Ready", busy = false, revealOn = false)) } }
    @Test fun panelBusy() = paparazzi.snapshot { OnGame { Panel(PanelState("Working…", busy = true, revealOn = false)) } }
    @Test fun panelRevealOn() = paparazzi.snapshot { OnGame { Panel(PanelState("Showing 7 cats", busy = false, revealOn = true)) } }
    @Test fun panelRunning() = paparazzi.snapshot { OnGame { Panel(PanelState("Placing cats… 3/10", busy = true, revealOn = false, autoRunning = true)) } }
    @Test fun panelSomeHidden() = paparazzi.snapshot { OnGame { Panel(PanelState("Ready", busy = false, revealOn = false, buttons = listOf(PanelButton.HINT, PanelButton.CAT, PanelButton.ANSWER))) } }
    @Test fun panelVertical() = paparazzi.snapshot { OnGame { Panel(PanelState("Next cat: row 3, col 4", busy = false, revealOn = false, layout = PanelLayout.VERTICAL)) } }
    @Test fun panelGrid2() = paparazzi.snapshot { OnGame { Panel(PanelState("Ready", busy = false, revealOn = true, layout = PanelLayout.GRID_2)) } }
    @Test fun panelGrid3Reordered() = paparazzi.snapshot { OnGame { Panel(PanelState("Ready", busy = false, revealOn = false, layout = PanelLayout.GRID_3,
        buttons = listOf(PanelButton.ANSWER, PanelButton.CAT, PanelButton.HINT, PanelButton.X, PanelButton.REVEAL))) } }
    @Test fun orderEditorGrid3() = paparazzi.snapshot { OnGame { ButtonOrderEditor(PanelLayout.GRID_3,
        listOf(PanelButton.ANSWER, PanelButton.CAT, PanelButton.HINT, PanelButton.REVEAL), SetupColors.Light, {}, {}) } }
    @Test fun orderEditorHorizontal() = paparazzi.snapshot { OnGame { ButtonOrderEditor(PanelLayout.HORIZONTAL, PanelButton.entries, SetupColors.Dark, {}, {}) } }
    @Test fun bubble() = paparazzi.snapshot { OnGame { Bubble(onClick = {}) } }
    @Test fun bubbleQuickHint() = paparazzi.snapshot { OnGame { Bubble(onClick = {}, quick = PanelButton.HINT, onLongClick = {}) } }
    @Test fun bubbleQuickRunning() = paparazzi.snapshot { OnGame { Bubble(onClick = {}, quick = PanelButton.ANSWER, busy = true, running = true, onLongClick = {}) } }
    @Test fun setupOff() { tall(); paparazzi.snapshot { SetupScreen(SetupState(serviceOn = false, doubleTapMs = 90), dark = false, {}, {}, {}, {}, {}, {}) } }
    @Test fun setupOn() { tall(); paparazzi.snapshot { SetupScreen(SetupState(serviceOn = true, doubleTapMs = 120, themeMode = ThemeMode.LIGHT,
        overlay = OverlaySettings(order = CatOrder.ROWS, reverse = true, answerX = false, buttons = PanelButton.entries.toSet() - PanelButton.X, quickAction = PanelButton.ANSWER)), dark = false, {}, {}, {}, {}, {}, {}) } }
    @Test fun setupDark() { tall(); paparazzi.snapshot { SetupScreen(SetupState(serviceOn = true, doubleTapMs = 90, themeMode = ThemeMode.DARK), dark = true, {}, {}, {}, {}, {}, {}) } }
    // full screen is scrollable; render tall so the slider section is visible
    private fun tall() = paparazzi.unsafeUpdateConfig(DeviceConfig.PIXEL_5.copy(screenHeight = 5600))
}
