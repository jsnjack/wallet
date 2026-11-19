package io.github.jsnjack.wallet.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardPlaceTest {
    private fun card(vararg details: Detail) = Card(
        id = "t", name = "Concert", code = "1", format = CodeFormat.QR, kind = CardKind.TICKET, color = 0, note = "",
        pinned = false, locked = false, createdAt = 0, updatedAt = 0, details = details.toList(),
    )

    @Test
    fun venueAndAddressMakeOneSearch() {
        val ticket = card(Detail("Zaal", "Grote Zaal"), Detail("Venue", "TivoliVredenburg"), Detail("Address", "Vredenburgkade 11, 3511 WC Utrecht"))
        assertEquals("TivoliVredenburg, Vredenburgkade 11, 3511 WC Utrecht", CardPlace.query(ticket))
        assertEquals("TivoliVredenburg", CardPlace.label(ticket))
    }

    @Test
    fun coordinatesWinAndNothingMeansNoPlace() {
        assertEquals("52.09170, 5.11310", CardPlace.query(card(Detail("Venue", "Tivoli"), Detail("Location", "52.09170, 5.11310"))))
        assertEquals("Ziggo Dome", CardPlace.query(card(Detail("Locatie", "Ziggo Dome"))))
        assertNull(CardPlace.query(card(Detail("Rij", "7"))))
    }
}
