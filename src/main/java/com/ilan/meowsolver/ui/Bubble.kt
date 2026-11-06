package com.ilan.meowsolver.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun Bubble(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(52.dp).clip(CircleShape).background(Meow.Clay).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(MeowIcons.Cat, contentDescription = "Open Meow Solver", tint = Meow.OnCard, modifier = Modifier.size(28.dp))
    }
}
