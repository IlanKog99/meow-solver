package com.ilan.meowsolver

import com.ilan.meowsolver.core.CatOrder
import com.ilan.meowsolver.core.OverlaySettings
import com.ilan.meowsolver.core.PanelButton
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
}
