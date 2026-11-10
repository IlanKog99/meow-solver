package com.ilan.meowsolver

import com.ilan.meowsolver.core.CatOrder
import com.ilan.meowsolver.core.OverlaySettings
import com.ilan.meowsolver.core.PanelButton
import com.ilan.meowsolver.core.PanelLayout
import com.ilan.meowsolver.core.PanelButton.*
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlaySettingsTest {
    @Test fun decodeDefaults() {
        assertEquals(OverlaySettings(), OverlaySettings.decode(null, null, false, true))
        assertEquals(PanelButton.entries.toSet(), OverlaySettings.decode("", "nope", false, true).buttons)
        assertEquals(CatOrder.RANDOM, OverlaySettings.decode(null, "nope", false, true).order)
        assertEquals(PanelButton.entries.toSet(), OverlaySettings.decode("FOO,BAR", null, false, true).buttons)
    }

    @Test fun encodeDecodeRoundTrip() {
        val s = OverlaySettings(setOf(PanelButton.HINT, PanelButton.ANSWER), CatOrder.COLUMNS, reverse = true, answerX = false)
        assertEquals("HINT,ANSWER", s.encodeButtons())
        assertEquals(s, OverlaySettings.decode(s.encodeButtons(), s.order.name, s.reverse, s.answerX))
    }

    @Test fun lastButtonCannotBeRemoved() {
        val one = OverlaySettings(buttons = setOf(PanelButton.CAT))
        assertEquals(one, one.withButton(PanelButton.CAT, false))
        assertEquals(setOf(PanelButton.CAT, PanelButton.X), one.withButton(PanelButton.X, true).buttons)
        assertEquals(PanelButton.entries.toSet() - PanelButton.X, OverlaySettings().withButton(PanelButton.X, false).buttons)
    }

    @Test fun quickActionRoundTripAndOnlyWhenShown() {
        assertEquals(null, OverlaySettings.decode(null, null, false, true).quickAction)
        assertEquals(null, OverlaySettings.decode(null, null, false, true, "NOPE").quickAction)
        assertEquals(PanelButton.CAT, OverlaySettings.decode(null, null, false, true, "CAT").quickAction)
        val s = OverlaySettings(quickAction = PanelButton.HINT)
        assertEquals(PanelButton.HINT, s.activeQuickAction)
        assertEquals(null, s.withButton(PanelButton.HINT, false).activeQuickAction)
        assertEquals(PanelButton.HINT, s.withButton(PanelButton.HINT, false).withButton(PanelButton.HINT, true).activeQuickAction)
    }

    @Test fun orderLabelFollowsSettings() {
        assertEquals("Bottom → top", OverlaySettings(order = CatOrder.ROWS, reverse = true).orderLabel)
    }

    @Test fun layoutAndOrderRoundTrip() {
        val s = OverlaySettings(layout = PanelLayout.GRID_3, buttonOrder = listOf(ANSWER, CAT, HINT, REVEAL, X), buttons = setOf(X, CAT))
        val back = OverlaySettings.decode(s.encodeButtons(), s.order.name, s.reverse, s.answerX, layout = s.layout.name, buttonOrder = s.buttonOrder.joinToString(",") { it.name })
        assertEquals(s, back)
        assertEquals(listOf(CAT, X), back.visibleButtons)
    }

    @Test fun reorderKeepsHiddenButtons() {
        val s = OverlaySettings(buttons = setOf(CAT, ANSWER, HINT), buttonOrder = listOf(REVEAL, HINT, CAT, X, ANSWER))
        val next = s.withVisibleOrder(listOf(ANSWER, HINT, CAT))
        assertEquals(listOf(ANSWER, HINT, CAT, REVEAL, X), next.buttonOrder)
        assertEquals(listOf(ANSWER, HINT, CAT), next.visibleButtons)
    }

    @Test fun badOrderIsRepaired() {
        assertEquals(PanelButton.entries, OverlaySettings.decodeOrder(null))
        assertEquals(listOf(X, HINT, REVEAL, CAT, ANSWER), OverlaySettings.decodeOrder("X,FOO,HINT,X"))
        assertEquals(PanelLayout.HORIZONTAL, OverlaySettings.decode(null, null, false, true, layout = "nope").layout)
    }

    @Test fun layoutCells() {
        // Handle takes cell 0 in grid layouts: "Fab 1 / 2 3 / 4 5" and "Fab 1 2 / 3 4 5".
        assertEquals(listOf(0 to 1, 1 to 0, 1 to 1, 2 to 0, 2 to 1), (0..4).map { PanelLayout.GRID_2.cellOf(it) })
        assertEquals(listOf(0 to 1, 0 to 2, 1 to 0, 1 to 1, 1 to 2), (0..4).map { PanelLayout.GRID_3.cellOf(it) })
        assertEquals(listOf(1 to 0, 2 to 0), (0..1).map { PanelLayout.VERTICAL.cellOf(it) })
        assertEquals(0 to 3, PanelLayout.HORIZONTAL.cellOf(3))
        for (l in PanelLayout.entries) for (i in 0..4) { val (r, c) = l.cellOf(i); assertEquals(i, l.indexAt(r, c, 5)) }
        assertEquals(0, PanelLayout.GRID_2.indexAt(0, 0, 5)) // handle cell → first slot
        assertEquals(4, PanelLayout.VERTICAL.indexAt(9, 3, 5)) // past the end → last slot
    }
}
