package io.github.jsnjack.wallet.core

/**
 * Where a card's event takes place, from its details: a venue, an address
 * or coordinates, as one search for a maps app.
 */
object CardPlace {
    /** The detail label for a venue's name. */
    const val VENUE = "Venue"

    /** The detail label for a street address. */
    const val ADDRESS = "Address"

    /** The detail label for coordinates, as "52.09170, 5.11310". */
    const val LOCATION = "Location"

    private val VENUE_LABELS = setOf("venue", "locatie", "location name")
    private val ADDRESS_LABELS = setOf("address", "adres", "venue address")
    private val COORDINATES = Regex("""^-?\d{1,2}\.\d+, ?-?\d{1,3}\.\d+$""")

    /** A maps search for [card], or null when its details name no place. Coordinates win, since they need no lookup. */
    fun query(card: Card): String? {
        fun value(labels: Set<String>) = card.details.firstOrNull { it.label.trim().lowercase() in labels && it.value.isNotBlank() }?.value?.trim()
        card.details.firstOrNull { it.label.equals(LOCATION, ignoreCase = true) && COORDINATES.matches(it.value.trim()) }?.let { return it.value.trim() }
        return listOfNotNull(value(VENUE_LABELS), value(ADDRESS_LABELS)).distinct().joinToString(", ").ifEmpty { null }
    }

    /** The name to show for the place: the venue, else the address; null for coordinates alone. */
    fun label(card: Card): String? = card.details.firstOrNull {
        val label = it.label.trim().lowercase()
        (label in VENUE_LABELS || label in ADDRESS_LABELS) && it.value.isNotBlank()
    }?.value?.trim()
}
