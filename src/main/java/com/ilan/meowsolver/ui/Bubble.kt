package com.ilan.meowsolver.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ilan.meowsolver.core.PanelButton

/** Minimized panel. With a [quick] action, tap runs it and long-press opens the panel; its icon shows which action. */
@OptIn(ExperimentalFoundationApi::class) // combinedClickable: needed for TalkBack long-press
@Composable
fun Bubble(
    onClick: () -> Unit, modifier: Modifier = Modifier,
    quick: PanelButton? = null, busy: Boolean = false, running: Boolean = false, onLongClick: (() -> Unit)? = null,
) {
    val icon = when {
        running -> MeowIcons.Stop
        quick == null -> MeowIcons.Cat
        else -> quick.icon
    }
    Box(
        modifier.size(52.dp).alpha(if (busy && !running) 0.6f else 1f).clip(CircleShape).background(Meow.Clay)
            .combinedClickable(
                onClickLabel = if (running) "Stop" else quick?.label,
                onLongClickLabel = if (onLongClick != null) "Open Meow Solver" else null,
                onLongClick = onLongClick, onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = when { running -> "Meow Solver: Stop"; quick == null -> "Open Meow Solver"; else -> "Meow Solver: ${quick.label}" }, tint = Meow.OnCard, modifier = Modifier.size(28.dp))
    }
}

val PanelButton.icon get() = when (this) {
    PanelButton.REVEAL -> MeowIcons.Eye
    PanelButton.HINT -> MeowIcons.Bulb
    PanelButton.CAT -> MeowIcons.Cat
    PanelButton.X -> MeowIcons.X
    PanelButton.ANSWER -> MeowIcons.FastForward
}
