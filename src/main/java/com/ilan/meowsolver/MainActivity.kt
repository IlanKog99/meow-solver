package com.ilan.meowsolver

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ilan.meowsolver.core.DebugLog
import com.ilan.meowsolver.core.MovePlanner
import com.ilan.meowsolver.core.OverlaySettings
import com.ilan.meowsolver.core.overlaySettings
import com.ilan.meowsolver.core.putOverlaySettings
import com.ilan.meowsolver.service.OverlayService
import com.ilan.meowsolver.ui.SetupScreen
import com.ilan.meowsolver.ui.SetupState
import com.ilan.meowsolver.ui.ThemeMode

class MainActivity : ComponentActivity() {
    private var serviceOn by mutableStateOf(false)
    private var themeMode by mutableStateOf(ThemeMode.SYSTEM)
    private var overlay by mutableStateOf(OverlaySettings())
    private var doubleTapMs by mutableIntStateOf(MovePlanner.GAP_MS.toInt())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugLog.init(filesDir)
        val prefs = getSharedPreferences(OverlayService.PREFS, MODE_PRIVATE)
        doubleTapMs = prefs.getInt(OverlayService.K_DOUBLE_MS, MovePlanner.GAP_MS.toInt())
        themeMode = ThemeMode.fromPref(prefs.getString(ThemeMode.PREF_KEY, null))
        overlay = prefs.overlaySettings()
        val systemDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        applyBars(themeMode.isDark(systemDark)) // before first frame: no inset jump, right icon colour
        setContent {
            val dark = themeMode.isDark(isSystemInDarkTheme())
            LaunchedEffect(dark) { applyBars(dark) }
            SetupScreen(
                SetupState(serviceOn, doubleTapMs, themeMode, overlay),
                dark = dark,
                onStart = {
                    val svc = OverlayService.instance
                    if (svc == null) openAccessibility() else { svc.showOverlay(); moveTaskToBack(true) }
                },
                onAppInfo = {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                },
                onAccessibility = ::openAccessibility,
                onDoubleTapMs = { doubleTapMs = it; prefs.edit().putInt(OverlayService.K_DOUBLE_MS, it).apply() },
                onThemeMode = { themeMode = it; prefs.edit().putString(ThemeMode.PREF_KEY, it.name).apply() },
                onOverlay = { overlay = it; prefs.edit().putOverlaySettings(it).apply() },
                onSendLogs = ::sendLogs,
                onClearLogs = { DebugLog.clear(); Toast.makeText(this, "Logs cleared", Toast.LENGTH_SHORT).show() },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        serviceOn = OverlayService.instance != null
    }

    private fun applyBars(dark: Boolean) {
        val bar = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                  else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bar, navigationBarStyle = bar)
    }

    /** Shares the log as plain text (any app: Gmail, WhatsApp, Drive, copy). A header says what device/app it came from. */
    private fun sendLogs() {
        val v = packageManager.getPackageInfo(packageName, 0)
        val head = "Meow Solver ${v.versionName} (${v.longVersionCode}) · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n" +
            "Overlay on: ${OverlayService.instance != null} · Settings: $overlay, doubleTapMs=$doubleTapMs\n\n"
        // Intents go through Binder (~1MB shared): send the newest ~150k chars at most.
        val body = DebugLog.text().ifEmpty { "(log is empty)" }.takeLast(150_000)
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "Meow Solver debug log")
            .putExtra(Intent.EXTRA_TEXT, head + body)
        try { startActivity(Intent.createChooser(send, "Send debug log")) }
        catch (e: Exception) { DebugLog.error("Send logs failed", e); Toast.makeText(this, "Couldn't open share", Toast.LENGTH_SHORT).show() }
    }

    private fun openAccessibility() = startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}
