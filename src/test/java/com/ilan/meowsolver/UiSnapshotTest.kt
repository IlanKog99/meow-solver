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
import com.ilan.meowsolver.ui.Bubble
import com.ilan.meowsolver.ui.PanelCard
import com.ilan.meowsolver.ui.PanelState
import com.ilan.meowsolver.ui.SetupScreen
import com.ilan.meowsolver.ui.SetupState
import org.junit.Rule
import org.junit.Test

class UiSnapshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)

    @Composable private fun OnGame(content: @Composable () -> Unit) =
        Box(Modifier.fillMaxSize().background(Color(0xFFF7F1EE)).padding(24.dp)) { content() }

    @Composable private fun Panel(s: PanelState) = PanelCard(s, {}, {}, {}, {}, {}, {}, {})

    @Test fun panelIdle() = paparazzi.snapshot { OnGame { Panel(PanelState("10×10 · 3 cats · solvable", busy = false, answerOn = false)) } }
    @Test fun panelBusy() = paparazzi.snapshot { OnGame { Panel(PanelState("Working…", busy = true, answerOn = false)) } }
    @Test fun panelAnswerOn() = paparazzi.snapshot { OnGame { Panel(PanelState("Showing 7 cats", busy = false, answerOn = true)) } }
    @Test fun bubble() = paparazzi.snapshot { OnGame { Bubble(onClick = {}) } }
    @Test fun setupOff() { tall(); paparazzi.snapshot { SetupScreen(SetupState(serviceOn = false, doubleTapMs = 90), {}, {}, {}, {}) } }
    @Test fun setupOn() { tall(); paparazzi.snapshot { SetupScreen(SetupState(serviceOn = true, doubleTapMs = 120), {}, {}, {}, {}) } }
    // full screen is scrollable; render tall so the slider section is visible
    private fun tall() = paparazzi.unsafeUpdateConfig(DeviceConfig.PIXEL_5.copy(screenHeight = 2800))
}
