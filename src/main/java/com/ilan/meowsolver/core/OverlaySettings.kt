package com.ilan.meowsolver.core

import android.content.SharedPreferences

enum class PanelButton(val label: String) { REVEAL("Reveal"), HINT("Hint"), CAT("Cat"), X("X"), ANSWER("Answer") }

/**
 * How the overlay card arranges its buttons. [cols] is the grid width; every layout but HORIZONTAL puts the
 * handle (drag grip + minimize/close) in the first grid cell, HORIZONTAL keeps it as a header bar.
 */
enum class PanelLayout(val label: String, val cols: Int) {
    HORIZONTAL("Row", 5), VERTICAL("Column", 1), GRID_2("2 wide", 2), GRID_3("3 wide", 3);

    val handleInGrid get() = this != HORIZONTAL

    /** Grid (row, col) of the [i]th button. */
    fun cellOf(i: Int): Pair<Int, Int> { val c = if (handleInGrid) i + 1 else i; return c / cols to c % cols }

    /** Button index at grid ([row], [col]), clamped to 0 until [count]. */
    fun indexAt(row: Int, col: Int, count: Int): Int {
        val c = row.coerceAtLeast(0) * cols + col.coerceIn(0, cols - 1)
        return (if (handleInGrid) c - 1 else c).coerceIn(0, count - 1)
    }
}

/** User choices from the setup screen that shape the overlay. */
data class OverlaySettings(
    val buttons: Set<PanelButton> = PanelButton.entries.toSet(),
    val order: CatOrder = CatOrder.RANDOM,
    val reverse: Boolean = false,
    val answerX: Boolean = true,
    /** X button also clears X's on cells where a cat belongs. */
    val clearWrongX: Boolean = false,
    /** Button the minimized bubble runs on tap (long-press then opens the panel); null = tap opens the panel. */
    val quickAction: PanelButton? = null,
    val layout: PanelLayout = PanelLayout.HORIZONTAL,
    /** Every button, in the user's order; [buttons] decides which of them show. */
    val buttonOrder: List<PanelButton> = PanelButton.entries,
) {
    /** Quick action only counts while its button is shown, so hiding a button also stops it from the bubble. */
    val activeQuickAction: PanelButton? get() = quickAction?.takeIf { it in buttons }
    val visibleButtons: List<PanelButton> get() = buttonOrder.filter { it in buttons }

    /** New order for the shown buttons; hidden ones keep their relative order after them. */
    fun withVisibleOrder(shown: List<PanelButton>) = copy(buttonOrder = shown + (buttonOrder - shown.toSet()))

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
        const val K_CLEAR_WRONG_X = "clearWrongX"
        const val K_QUICK = "bubbleQuickAction"
        const val K_LAYOUT = "panelLayout"
        const val K_BUTTON_ORDER = "buttonOrder"

        private fun parseButtons(s: String?) = s.orEmpty().split(",").mapNotNull { n -> PanelButton.entries.firstOrNull { it.name == n } }

        /** Saved order, minus unknown/duplicate names, with any missing buttons appended. */
        fun decodeOrder(s: String?): List<PanelButton> {
            val saved = parseButtons(s).distinct()
            return saved + (PanelButton.entries - saved.toSet())
        }

        fun decode(
            buttons: String?, order: String?, reverse: Boolean, answerX: Boolean, quick: String? = null,
            layout: String? = null, buttonOrder: String? = null, clearWrongX: Boolean = false,
        ): OverlaySettings {
            val set = parseButtons(buttons).toSet()
            return OverlaySettings(
                buttons = set.ifEmpty { PanelButton.entries.toSet() },
                order = CatOrder.entries.firstOrNull { it.name == order } ?: CatOrder.RANDOM,
                reverse = reverse,
                answerX = answerX,
                clearWrongX = clearWrongX,
                quickAction = PanelButton.entries.firstOrNull { it.name == quick },
                layout = PanelLayout.entries.firstOrNull { it.name == layout } ?: PanelLayout.HORIZONTAL,
                buttonOrder = decodeOrder(buttonOrder),
            )
        }
    }
}

fun SharedPreferences.overlaySettings() = OverlaySettings.decode(
    getString(OverlaySettings.K_BUTTONS, null), getString(OverlaySettings.K_ORDER, null),
    getBoolean(OverlaySettings.K_REVERSE, false), getBoolean(OverlaySettings.K_ANSWER_X, true),
    getString(OverlaySettings.K_QUICK, null),
    layout = getString(OverlaySettings.K_LAYOUT, null), buttonOrder = getString(OverlaySettings.K_BUTTON_ORDER, null),
    clearWrongX = getBoolean(OverlaySettings.K_CLEAR_WRONG_X, false),
)

fun SharedPreferences.Editor.putOverlaySettings(s: OverlaySettings): SharedPreferences.Editor =
    putString(OverlaySettings.K_BUTTONS, s.encodeButtons()).putString(OverlaySettings.K_ORDER, s.order.name)
        .putBoolean(OverlaySettings.K_REVERSE, s.reverse).putBoolean(OverlaySettings.K_ANSWER_X, s.answerX)
        .putString(OverlaySettings.K_QUICK, s.quickAction?.name)
        .putString(OverlaySettings.K_LAYOUT, s.layout.name).putString(OverlaySettings.K_BUTTON_ORDER, s.buttonOrder.joinToString(",") { it.name })
        .putBoolean(OverlaySettings.K_CLEAR_WRONG_X, s.clearWrongX)
