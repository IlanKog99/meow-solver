package com.ilan.meowsolver.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ilan.meowsolver.core.PanelButton
import com.ilan.meowsolver.core.PanelLayout
import kotlin.math.floor
import kotlin.math.roundToInt

private val TILE = 54.dp
private val PITCH = 60.dp // tile + gap

/** Drag tiles to reorder the shown overlay buttons, laid out exactly as the panel will show them in [layout]. */
@Composable
fun ButtonOrderEditor(
    layout: PanelLayout, initial: List<PanelButton>, colors: SetupColors,
    onSave: (List<PanelButton>) -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier,
) {
    val order = remember(initial) { initial.toMutableStateList() }
    var dragging by remember { mutableStateOf<PanelButton?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val pitch = with(LocalDensity.current) { PITCH.toPx() }
    val rows = layout.cellOf(order.size - 1).first + 1

    fun move(btn: PanelButton, to: Int) {
        val from = order.indexOf(btn)
        if (to == from || to !in order.indices) return
        order.removeAt(from); order.add(to, btn)
    }

    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(colors.card).padding(horizontal = 12.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Button order", color = colors.ink, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Text("Drag a button to move it", color = colors.inkMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
        Box(Modifier.background(Meow.Card, RoundedCornerShape(16.dp)).padding(6.dp)) {
            Box(Modifier.size(PITCH * layout.cols - (PITCH - TILE), PITCH * (rows + if (layout.handleInGrid) 0 else 1) - (PITCH - TILE))) {
                val shift = if (layout.handleInGrid) 0 else 1 // HORIZONTAL: handle bar is its own row above
                Box(Modifier.size(TILE), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(Meow.Clay), contentAlignment = Alignment.Center) {
                        Icon(MeowIcons.Cat, contentDescription = "Panel handle", tint = Meow.OnCard, modifier = Modifier.size(20.dp))
                    }
                }
                order.forEachIndexed { i, btn -> key(btn) {
                    val (r, c) = layout.cellOf(i)
                    val lifted = btn == dragging
                    val extra = if (lifted) dragOffset else Offset.Zero
                    Column(
                        Modifier
                            .offset { IntOffset((c * pitch + extra.x).roundToInt(), ((r + shift) * pitch + extra.y).roundToInt()) }
                            .zIndex(if (lifted) 1f else 0f)
                            .size(TILE)
                            .shadow(if (lifted) 8.dp else 0.dp, RoundedCornerShape(12.dp))
                            .background(Meow.Clay.copy(alpha = if (lifted) 0.6f else 0.3f), RoundedCornerShape(12.dp))
                            .semantics {
                                customActions = listOf(
                                    CustomAccessibilityAction("Move earlier") { move(btn, order.indexOf(btn) - 1); true },
                                    CustomAccessibilityAction("Move later") { move(btn, order.indexOf(btn) + 1); true },
                                )
                            }
                            .pointerInput(btn, layout) {
                                detectDragGestures(
                                    onDragStart = { dragging = btn; dragOffset = Offset.Zero },
                                    onDragEnd = { dragging = null; dragOffset = Offset.Zero },
                                    onDragCancel = { dragging = null; dragOffset = Offset.Zero },
                                ) { change, delta ->
                                    change.consume()
                                    dragOffset += delta
                                    // Slot under the tile's centre; move there and re-anchor so the tile stays under the finger.
                                    val (r0, c0) = layout.cellOf(order.indexOf(btn))
                                    val to = layout.indexAt(
                                        floor(r0 + dragOffset.y / pitch + 0.5f).toInt(),
                                        floor(c0 + dragOffset.x / pitch + 0.5f).toInt(), order.size,
                                    )
                                    move(btn, to)
                                    val (r1, c1) = layout.cellOf(to)
                                    dragOffset -= Offset((c1 - c0) * pitch, (r1 - r0) * pitch)
                                }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(btn.icon, contentDescription = null, tint = Meow.OnCard, modifier = Modifier.size(22.dp))
                        Text(btn.label, color = Meow.OnCard, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
                    }
                } }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            TextButton(onClick = { order.sortBy { it.ordinal } }) { Text("Reset", color = colors.inkMuted) }
            TextButton(onClick = onCancel) { Text("Cancel", color = colors.ink) }
            Button(
                onClick = { onSave(order.toList()) }, shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Meow.Clay, contentColor = androidx.compose.ui.graphics.Color.White),
            ) { Text("Save") }
        }
    }
}
