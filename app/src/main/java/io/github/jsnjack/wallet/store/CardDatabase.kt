package io.github.jsnjack.wallet.store

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import androidx.core.database.sqlite.transaction
import io.github.jsnjack.wallet.LOG_TAG
import io.github.jsnjack.wallet.core.Card
import io.github.jsnjack.wallet.core.CardJson
import io.github.jsnjack.wallet.core.CardKind
import io.github.jsnjack.wallet.core.DayTime
import io.github.jsnjack.wallet.core.CodeFormat
import io.github.jsnjack.wallet.core.Usage

/** SQLite tables for cards and usage counts. Call from IO threads. */
internal class CardDatabase(context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE cards (
                id TEXT PRIMARY KEY, name TEXT NOT NULL, code TEXT NOT NULL,
                format TEXT NOT NULL, kind TEXT NOT NULL, color INTEGER NOT NULL,
                note TEXT NOT NULL, pinned INTEGER NOT NULL, locked INTEGER NOT NULL,
                created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, deleted INTEGER NOT NULL)""",
        )
        db.execSQL("CREATE TABLE usage (card_id TEXT PRIMARY KEY, count INTEGER NOT NULL, last_used_at INTEGER NOT NULL)")
        addVersion2Columns(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) addVersion2Columns(db)
    }

    private fun addVersion2Columns(db: SQLiteDatabase) {
        listOf(
            "pin TEXT NOT NULL DEFAULT ''",
            "expires TEXT",
            "event TEXT",
            "balance_cents INTEGER",
            "used_at INTEGER",
            "details TEXT NOT NULL DEFAULT '[]'",
            "flight TEXT",
        ).forEach { db.execSQL("ALTER TABLE cards ADD COLUMN $it") }
    }

    /** Every card, deleted tombstones included. */
    fun readCards(): List<Card> = readableDatabase.query("cards", null, null, null, null, null, null).use { c ->
        buildList {
            while (c.moveToNext()) c.toCard()?.let(::add)
        }
    }

    /** Usage per card id. */
    fun readUsage(): Map<String, Usage> =
        readableDatabase.query("usage", null, null, null, null, null, null).use { c ->
            buildMap {
                while (c.moveToNext()) {
                    put(c.str("card_id"), Usage(c.getInt(c.getColumnIndexOrThrow("count")), c.long("last_used_at")))
                }
            }
        }

    /** Inserts or replaces [cards] in one transaction. */
    fun writeCards(cards: List<Card>) {
        writableDatabase.transaction {
            cards.forEach { insertWithOnConflict("cards", null, it.toValues(), SQLiteDatabase.CONFLICT_REPLACE) }
        }
    }

    /** Stores the usage of one card. */
    fun writeUsage(cardId: String, usage: Usage) {
        val values = ContentValues().apply {
            put("card_id", cardId)
            put("count", usage.count)
            put("last_used_at", usage.lastUsedAt)
        }
        writableDatabase.insertWithOnConflict("usage", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** Forgets the usage of a deleted card. */
    fun deleteUsage(cardId: String) {
        writableDatabase.delete("usage", "card_id = ?", arrayOf(cardId))
    }

    private fun Card.toValues() = ContentValues().apply {
        put("id", id)
        put("name", name)
        put("code", code)
        put("format", format.name)
        put("kind", kind.name)
        put("color", color)
        put("note", note)
        put("pinned", if (pinned) 1 else 0)
        put("locked", if (locked) 1 else 0)
        put("created_at", createdAt)
        put("updated_at", updatedAt)
        put("deleted", if (deleted) 1 else 0)
        put("pin", pin)
        put("expires", expires?.toString())
        put("event", event?.toString())
        put("balance_cents", balanceCents)
        put("used_at", usedAt)
        put("details", CardJson.encodeDetails(details))
        put("flight", CardJson.encodeFlight(flight))
    }

    private fun Cursor.toCard(): Card? {
        val format = CodeFormat.entries.firstOrNull { it.name == str("format") }
        if (format == null) {
            Log.w(LOG_TAG, "skipping a stored card with unknown format ${str("format")}")
            return null
        }
        return Card(
            id = str("id"),
            name = str("name"),
            code = str("code"),
            format = format,
            kind = CardKind.entries.firstOrNull { it.name == str("kind") } ?: CardKind.OTHER,
            color = getInt(getColumnIndexOrThrow("color")),
            note = str("note"),
            pinned = getInt(getColumnIndexOrThrow("pinned")) != 0,
            locked = getInt(getColumnIndexOrThrow("locked")) != 0,
            createdAt = long("created_at"),
            updatedAt = long("updated_at"),
            deleted = getInt(getColumnIndexOrThrow("deleted")) != 0,
            pin = str("pin"),
            expires = DayTime.parse(optStr("expires")),
            event = DayTime.parse(optStr("event")),
            balanceCents = optLong("balance_cents"),
            usedAt = optLong("used_at"),
            details = CardJson.decodeDetails(optStr("details")),
            flight = CardJson.decodeFlight(optStr("flight")),
        )
    }

    private fun Cursor.str(column: String): String = getString(getColumnIndexOrThrow(column))
    private fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))
    private fun Cursor.optStr(column: String): String? = getColumnIndexOrThrow(column).let { if (isNull(it)) null else getString(it) }
    private fun Cursor.optLong(column: String): Long? = getColumnIndexOrThrow(column).let { if (isNull(it)) null else getLong(it) }

    companion object {
        /** Database file name; Android backup rules refer to it. */
        const val NAME = "wallet.db"
        private const val VERSION = 2
    }
}
