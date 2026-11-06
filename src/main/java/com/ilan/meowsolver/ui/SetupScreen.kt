package com.ilan.meowsolver.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

data class SetupColors(val bg: Color, val card: Color, val ink: Color, val inkMuted: Color) {
    companion object {
        val Light = SetupColors(Meow.Cream, Color.White, Meow.Ink, Meow.InkMuted)
        val Dark = SetupColors(Color(0xFF1C1714), Color(0xFF2A2420), Color(0xFFF3ECE6), Color(0xFFB3A59B))
    }
}

private val LocalSetupColors = staticCompositionLocalOf { SetupColors.Light }

data class SetupState(val serviceOn: Boolean, val doubleTapMs: Int, val themeMode: ThemeMode = ThemeMode.SYSTEM)

@Composable
fun SetupScreen(
    state: SetupState,
    dark: Boolean,
    onStart: () -> Unit, onAppInfo: () -> Unit, onAccessibility: () -> Unit, onDoubleTapMs: (Int) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
) {
    val c = if (dark) SetupColors.Dark else SetupColors.Light
    CompositionLocalProvider(LocalSetupColors provides c) {
        Column(
            Modifier.fillMaxSize().background(c.bg).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 32.dp),
        ) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(Meow.Clay), contentAlignment = Alignment.Center) {
                Icon(MeowIcons.Cat, contentDescription = null, tint = Meow.OnCard, modifier = Modifier.size(36.dp))
            }
            Text("Meow Solver", fontSize = 28.sp, fontWeight = FontWeight.Medium, color = c.ink, modifier = Modifier.padding(top = 16.dp))
            Text(
                "Hint, place, or solve Meowdoku from a floating panel.",
                fontSize = 15.sp, color = c.inkMuted, modifier = Modifier.padding(top = 4.dp),
            )
            Button(
                onClick = onStart, shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Meow.Clay, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp).height(56.dp),
            ) { Text(if (state.serviceOn) "Start overlay" else "Turn on overlay", fontSize = 17.sp) }

            SectionTitle("Setup")
            Card {
                Step(1, "Allow restricted settings", "App info → ⋮ → Allow restricted settings", state.serviceOn, "App info", onAppInfo)
                HorizontalDivider(color = c.ink.copy(alpha = 0.08f))
                Step(2, "Turn on accessibility", "Installed apps → Meow Solver overlay → On", state.serviceOn, "Open", onAccessibility)
            }

            SectionTitle("How it works")
            Card {
                HowRow(MeowIcons.Eye, "Reveal", "Rings every remaining cat. Tap again to hide.")
                HowRow(MeowIcons.Bulb, "Hint", "Shows the next cat for 5 seconds")
                HowRow(MeowIcons.Cat, "Cat", "Places the next cat for you")
                HowRow(MeowIcons.X, "X", "Marks every cell a cat rules out")
                HowRow(MeowIcons.FastForward, "Answer", "Solves the level. Tap Stop to halt.")
            }

            SectionTitle("Appearance")
            Card {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (m in ThemeMode.entries) {
                        val selected = m == state.themeMode
                        Box(
                            Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (selected) Meow.Clay else Color.Transparent)
                                .border(1.dp, if (selected) Meow.Clay else c.ink.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                .clickable(onClickLabel = m.label) { onThemeMode(m) },
                            contentAlignment = Alignment.Center,
                        ) { Text(m.label, color = if (selected) Color.White else c.ink, fontSize = 14.sp) }
                    }
                }
            }

            SectionTitle("Tuning")
            Card {
                Text("Double-tap speed · ${state.doubleTapMs} ms", color = c.ink, fontSize = 15.sp)
                Slider(
                    value = state.doubleTapMs.toFloat(), onValueChange = { onDoubleTapMs((it / 10).roundToInt() * 10) },
                    valueRange = 60f..200f, steps = 13,
                    colors = SliderDefaults.colors(
                        thumbColor = Meow.Clay, activeTrackColor = Meow.Clay, inactiveTrackColor = c.ink.copy(alpha = 0.12f),
                        activeTickColor = Color.White.copy(alpha = 0.6f), inactiveTickColor = c.inkMuted.copy(alpha = 0.5f),
                    ),
                )
                Text("Raise it if Cat places an X instead.", color = c.inkMuted, fontSize = 13.sp)
            }
        }
    }
}

@Composable private fun SectionTitle(t: String) =
    Text(t, color = LocalSetupColors.current.inkMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 28.dp, bottom = 8.dp))

@Composable private fun Card(content: @Composable () -> Unit) =
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(LocalSetupColors.current.card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }

@Composable
private fun Step(num: Int, title: String, detail: String, done: Boolean, action: String, onAction: () -> Unit) {
    val ink = LocalSetupColors.current.ink; val inkMuted = LocalSetupColors.current.inkMuted
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(if (done) Meow.Clay else ink.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            if (done) Icon(MeowIcons.Check, contentDescription = "Done", tint = Color.White, modifier = Modifier.size(16.dp))
            else Text("$num", color = ink, fontSize = 13.sp)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, color = ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(detail, color = inkMuted, fontSize = 13.sp)
        }
        OutlinedButton(onClick = onAction, shape = RoundedCornerShape(12.dp)) { Text(action, color = ink, fontSize = 13.sp) }
    }
}

@Composable
private fun HowRow(icon: ImageVector, name: String, text: String) {
    val ink = LocalSetupColors.current.ink; val inkMuted = LocalSetupColors.current.inkMuted
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Meow.Clay, modifier = Modifier.size(22.dp))
        Spacer(Modifier.size(12.dp))
        Column {
            Text(name, color = ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(text, color = inkMuted, fontSize = 13.sp)
        }
    }
}
