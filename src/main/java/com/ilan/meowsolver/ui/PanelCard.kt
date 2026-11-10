package com.ilan.meowsolver.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ilan.meowsolver.core.PanelButton
import com.ilan.meowsolver.core.PanelLayout

data class PanelState(val status: String, val busy: Boolean, val revealOn: Boolean, val autoRunning: Boolean = false,
    val buttons: List<PanelButton> = PanelButton.entries, val quick: PanelButton? = null, val layout: PanelLayout = PanelLayout.HORIZONTAL)

private val CELL = 54.dp
private val GAP = 4.dp

@Composable
fun PanelCard(
    state: PanelState,
    onReveal: () -> Unit, onHint: () -> Unit, onCat: () -> Unit, onX: () -> Unit, onAnswer: () -> Unit,
    onMinimize: () -> Unit, onClose: () -> Unit,
    dragModifier: Modifier = Modifier,
) {
    val button: @Composable (PanelButton) -> Unit = { btn ->
        val on = !state.busy
        when (btn) {
            PanelButton.REVEAL -> ActionButton(btn.icon, btn.label, on, toggled = state.revealOn, onClick = onReveal)
            PanelButton.HINT -> ActionButton(btn.icon, btn.label, on, onClick = onHint)
            PanelButton.CAT -> ActionButton(btn.icon, btn.label, on, filled = true, onClick = onCat)
            PanelButton.X -> ActionButton(btn.icon, btn.label, on, onClick = onX)
            PanelButton.ANSWER ->
                if (state.autoRunning) ActionButton(MeowIcons.Stop, "Stop", true, toggled = true, onClick = onAnswer)
                else ActionButton(btn.icon, btn.label, on, onClick = onAnswer)
        }
    }
    if (state.layout.handleInGrid) return GridCard(state, button, onMinimize, onClose, dragModifier)
    Column(
        Modifier.width(300.dp).background(Meow.Card, RoundedCornerShape(16.dp)).padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.status, color = Meow.OnCardMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).then(dragModifier).padding(start = 6.dp),
            )
            HeaderIcon(MeowIcons.Minus, "Minimize", onMinimize)
            HeaderIcon(MeowIcons.Power, "Close", onClose)
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            state.buttons.forEach { button(it) }
        }
    }
}

/** Vertical / grid layouts: the handle (drag grip + minimize/close) is the first cell, status sits under the grid. */
@Composable
private fun GridCard(
    state: PanelState, button: @Composable (PanelButton) -> Unit,
    onMinimize: () -> Unit, onClose: () -> Unit, dragModifier: Modifier,
) {
    val cols = state.layout.cols
    val width = CELL * cols + GAP * (cols - 1)
    Column(
        Modifier.background(Meow.Card, RoundedCornerShape(16.dp)).padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(GAP),
    ) {
        (listOf<PanelButton?>(null) + state.buttons).chunked(cols).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
                row.forEach { if (it == null) Handle(onMinimize, onClose, dragModifier) else button(it) }
            }
        }
        Text(
            state.status, color = Meow.OnCardMuted, fontSize = 11.sp, lineHeight = 13.sp, maxLines = 4, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, modifier = Modifier.width(width).then(dragModifier),
        )
    }
}

@Composable
private fun Handle(onMinimize: () -> Unit, onClose: () -> Unit, dragModifier: Modifier) {
    Column(Modifier.width(CELL), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(Meow.Clay).then(dragModifier),
            contentAlignment = Alignment.Center,
        ) { Icon(MeowIcons.Cat, contentDescription = null, tint = Meow.OnCard, modifier = Modifier.size(18.dp)) }
        Row {
            SmallIcon(MeowIcons.Minus, "Minimize", onMinimize)
            SmallIcon(MeowIcons.Power, "Close", onClose)
        }
    }
}

@Composable
private fun SmallIcon(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(Modifier.size(26.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = desc, tint = Meow.OnCardMuted, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector, label: String, enabled: Boolean,
    filled: Boolean = false, toggled: Boolean = false, onClick: () -> Unit,
) {
    val bg = when {
        filled -> Meow.Clay
        toggled -> Meow.Clay.copy(alpha = 0.35f)
        else -> Color.Transparent
    }
    Column(
        Modifier.width(CELL).alpha(if (enabled) 1f else 0.4f).clip(RoundedCornerShape(12.dp)).background(bg)
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = Meow.OnCard, modifier = Modifier.size(22.dp))
        Text(label, color = Meow.OnCard, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun HeaderIcon(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = desc, tint = Meow.OnCardMuted, modifier = Modifier.size(18.dp))
    }
}
