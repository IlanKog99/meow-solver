package com.ilan.meowsolver

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ilan.meowsolver.core.MovePlanner
import com.ilan.meowsolver.service.OverlayService
import com.ilan.meowsolver.ui.SetupScreen
import com.ilan.meowsolver.ui.SetupState

class MainActivity : ComponentActivity() {
    private var serviceOn by mutableStateOf(false)
    private var doubleTapMs by mutableIntStateOf(MovePlanner.GAP_MS.toInt())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(OverlayService.PREFS, MODE_PRIVATE)
        doubleTapMs = prefs.getInt(OverlayService.K_DOUBLE_MS, MovePlanner.GAP_MS.toInt())
        setContent {
            SetupScreen(
                SetupState(serviceOn, doubleTapMs),
                onStart = {
                    val svc = OverlayService.instance
                    if (svc == null) openAccessibility() else { svc.showOverlay(); moveTaskToBack(true) }
                },
                onAppInfo = {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                },
                onAccessibility = ::openAccessibility,
                onDoubleTapMs = { doubleTapMs = it; prefs.edit().putInt(OverlayService.K_DOUBLE_MS, it).apply() },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        serviceOn = OverlayService.instance != null
    }

    private fun openAccessibility() = startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}
