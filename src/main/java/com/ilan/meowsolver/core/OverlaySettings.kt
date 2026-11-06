package com.ilan.meowsolver.core

import android.content.SharedPreferences

enum class PanelButton(val label: String) { REVEAL("Reveal"), HINT("Hint"), CAT("Cat"), X("X"), ANSWER("Answer") }

/** User choices from the setup screen that shape the overlay. */
data class OverlaySettings(
    val buttons: Set<PanelButton> = PanelButton.entries.toSet(),
    val order: CatOrder = CatOrder.RANDOM,
    val reverse: Boolean = false,
    val answerX: Boolean = true,
) {
    /** Turns a button on/off; the last visible button can't be turned off. */
    fun withButton(btn: PanelButton, on: Boolean): OverlaySettings {
        val next = if (on) buttons + btn else buttons - btn
        return if (next.isEmpty()) this else copy(buttons = next)
    }

    val orderLabel: String get() = Order.label(order, reverse)

    fun encodeButtons() = PanelButton.entries.filter { it in buttons }.joinToString(",") { it.name }

    companion object {
        const val K_BUTTONS = "panelButtons"
        const val K_ORDER = "catOrder"
        const val K_REVERSE = "orderReverse"
        const val K_ANSWER_X = "answerX"

        fun decode(buttons: String?, order: String?, reverse: Boolean, answerX: Boolean): OverlaySettings {
            val set = buttons.orEmpty().split(",").mapNotNull { n -> PanelButton.entries.firstOrNull { it.name == n } }.toSet()
            return OverlaySettings(
                buttons = set.ifEmpty { PanelButton.entries.toSet() },
                order = CatOrder.entries.firstOrNull { it.name == order } ?: CatOrder.RANDOM,
                reverse = reverse,
                answerX = answerX,
            )
        }
    }
}

fun SharedPreferences.overlaySettings() = OverlaySettings.decode(
    getString(OverlaySettings.K_BUTTONS, null), getString(OverlaySettings.K_ORDER, null),
    getBoolean(OverlaySettings.K_REVERSE, false), getBoolean(OverlaySettings.K_ANSWER_X, true),
)

fun SharedPreferences.Editor.putOverlaySettings(s: OverlaySettings): SharedPreferences.Editor =
    putString(OverlaySettings.K_BUTTONS, s.encodeButtons()).putString(OverlaySettings.K_ORDER, s.order.name)
        .putBoolean(OverlaySettings.K_REVERSE, s.reverse).putBoolean(OverlaySettings.K_ANSWER_X, s.answerX)
