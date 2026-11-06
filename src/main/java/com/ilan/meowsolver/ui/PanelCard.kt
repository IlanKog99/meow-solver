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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class PanelState(val status: String, val busy: Boolean, val revealOn: Boolean, val autoRunning: Boolean = false)

@Composable
fun PanelCard(
    state: PanelState,
    onReveal: () -> Unit, onHint: () -> Unit, onCat: () -> Unit, onX: () -> Unit, onAnswer: () -> Unit,
    onMinimize: () -> Unit, onClose: () -> Unit,
    dragModifier: Modifier = Modifier,
) {
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
        Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            val on = !state.busy
            ActionButton(MeowIcons.Eye, "Reveal", on, toggled = state.revealOn, onClick = onReveal)
            ActionButton(MeowIcons.Bulb, "Hint", on, onClick = onHint)
            ActionButton(MeowIcons.Cat, "Cat", on, filled = true, onClick = onCat)
            ActionButton(MeowIcons.X, "X", on, onClick = onX)
            if (state.autoRunning) ActionButton(MeowIcons.Stop, "Stop", true, toggled = true, onClick = onAnswer)
            else ActionButton(MeowIcons.FastForward, "Answer", on, onClick = onAnswer)
        }
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
        Modifier.width(54.dp).alpha(if (enabled) 1f else 0.4f).clip(RoundedCornerShape(12.dp)).background(bg)
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
