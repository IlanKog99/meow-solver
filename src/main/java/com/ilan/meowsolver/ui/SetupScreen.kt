package com.ilan.meowsolver.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

data class SetupState(val serviceOn: Boolean, val doubleTapMs: Int)

@Composable
fun SetupScreen(
    state: SetupState,
    onStart: () -> Unit, onAppInfo: () -> Unit, onAccessibility: () -> Unit, onDoubleTapMs: (Int) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(Meow.Cream).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 32.dp),
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(Meow.Clay), contentAlignment = Alignment.Center) {
            Icon(MeowIcons.Cat, contentDescription = null, tint = Meow.OnCard, modifier = Modifier.size(36.dp))
        }
        Text("Meow Solver", fontSize = 28.sp, fontWeight = FontWeight.Medium, color = Meow.Ink, modifier = Modifier.padding(top = 16.dp))
        Text(
            "Scan, hint, and place cats in Meowdoku from a floating panel.",
            fontSize = 15.sp, color = Meow.InkMuted, modifier = Modifier.padding(top = 4.dp),
        )
        Button(
            onClick = onStart, shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Meow.Clay, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp).height(56.dp),
        ) { Text(if (state.serviceOn) "Start overlay" else "Turn on overlay", fontSize = 17.sp) }

        SectionTitle("Setup")
        Card {
            Step(1, "Allow restricted settings", "App info → ⋮ → Allow restricted settings", state.serviceOn, "App info", onAppInfo)
            HorizontalDivider(color = Meow.Ink.copy(alpha = 0.08f))
            Step(2, "Turn on accessibility", "Installed apps → Meow Solver overlay → On", state.serviceOn, "Open", onAccessibility)
        }

        SectionTitle("How it works")
        Card {
            HowRow(MeowIcons.Scan, "Scan", "Reads the board and checks it's solvable")
            HowRow(MeowIcons.Bulb, "Hint", "Shows the next cat for 5 seconds")
            HowRow(MeowIcons.Cat, "Cat", "Places the next cat for you")
            HowRow(MeowIcons.X, "X", "Marks every cell a cat rules out")
            HowRow(MeowIcons.Eye, "Answer", "Toggles rings on all remaining cats")
        }

        SectionTitle("Tuning")
        Card {
            Text("Double-tap speed · ${state.doubleTapMs} ms", color = Meow.Ink, fontSize = 15.sp)
            Slider(
                value = state.doubleTapMs.toFloat(), onValueChange = { onDoubleTapMs((it / 10).roundToInt() * 10) },
                valueRange = 60f..200f, steps = 13,
                colors = SliderDefaults.colors(
                    thumbColor = Meow.Clay, activeTrackColor = Meow.Clay, inactiveTrackColor = Meow.Ink.copy(alpha = 0.12f),
                    activeTickColor = Color.White.copy(alpha = 0.6f), inactiveTickColor = Meow.InkMuted.copy(alpha = 0.5f),
                ),
            )
            Text("Raise it if Cat places an X instead.", color = Meow.InkMuted, fontSize = 13.sp)
        }
    }
}

@Composable private fun SectionTitle(t: String) =
    Text(t, color = Meow.InkMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 28.dp, bottom = 8.dp))

@Composable private fun Card(content: @Composable () -> Unit) =
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }

@Composable
private fun Step(num: Int, title: String, detail: String, done: Boolean, action: String, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(if (done) Meow.Clay else Meow.Ink.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            if (done) Icon(MeowIcons.Check, contentDescription = "Done", tint = Color.White, modifier = Modifier.size(16.dp))
            else Text("$num", color = Meow.Ink, fontSize = 13.sp)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, color = Meow.Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(detail, color = Meow.InkMuted, fontSize = 13.sp)
        }
        OutlinedButton(onClick = onAction, shape = RoundedCornerShape(12.dp)) { Text(action, color = Meow.Ink, fontSize = 13.sp) }
    }
}

@Composable
private fun HowRow(icon: ImageVector, name: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Meow.Clay, modifier = Modifier.size(22.dp))
        Spacer(Modifier.size(12.dp))
        Column {
            Text(name, color = Meow.Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(text, color = Meow.InkMuted, fontSize = 13.sp)
        }
    }
}
