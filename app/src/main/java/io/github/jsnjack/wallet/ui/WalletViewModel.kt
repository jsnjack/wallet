package io.github.jsnjack.wallet.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.github.jsnjack.wallet.LOG_TAG
import io.github.jsnjack.wallet.R
import io.github.jsnjack.wallet.WalletApplication
import io.github.jsnjack.wallet.core.Agenda
import io.github.jsnjack.wallet.core.Card
import io.github.jsnjack.wallet.core.CardDates
import io.github.jsnjack.wallet.core.CardDraft
import io.github.jsnjack.wallet.core.CardGroups
import io.github.jsnjack.wallet.core.CardJson
import io.github.jsnjack.wallet.core.CardList
import io.github.jsnjack.wallet.core.CardOrder
import io.github.jsnjack.wallet.core.DraftJson
import io.github.jsnjack.wallet.core.ExportCrypto
import io.github.jsnjack.wallet.core.FlightCheck
import io.github.jsnjack.wallet.core.FlightRef
import io.github.jsnjack.wallet.core.NotAnExportException
import io.github.jsnjack.wallet.core.Pkpass
import io.github.jsnjack.wallet.core.Retention
import io.github.jsnjack.wallet.core.TicketText
import io.github.jsnjack.wallet.core.UpNext
import io.github.jsnjack.wallet.core.Usage
import io.github.jsnjack.wallet.core.WrongPasswordException
import io.github.jsnjack.wallet.platform.CardShare
import io.github.jsnjack.wallet.platform.CodeReader
import io.github.jsnjack.wallet.platform.FileReadResult
import io.github.jsnjack.wallet.platform.ReadCode
import io.github.jsnjack.wallet.platform.Shortcuts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import java.util.UUID

/** Screens on the navigation stack; the last one is shown. */
sealed interface Screen {
    /** The card grid. */
    data object Grid : Screen

    /** Full-screen codes, starting at [cardId]. */
    data class Code(val cardId: String) : Screen

    /** The camera scanner. */
    data object Scan : Screen

    /** The new-card or edit form; its content is [WalletViewModel.draft]. */
    data object Form : Screen

    /** Used, expired and past cards. */
    data object Archive : Screen

    /** Archive, notification, flight info and backup settings. */
    data object Settings : Screen
}

/** One-off messages for the snackbar. */
sealed interface UiEvent {
    /** [cards] were deleted (one card, or a tile's whole group) and can be restored. */
    data class Deleted(val cards: List<Card>) : UiEvent

    /** [card] was marked as used and can be put back. */
    data class MarkedUsed(val card: Card) : UiEvent

    /** A message from string resources. */
    data class Message(@param:StringRes val text: Int) : UiEvent

    /** A message from plural resources about [count] items. */
    data class Counted(@param:PluralsRes val text: Int, val count: Int) : UiEvent
}

/** State and actions for every screen. */
@OptIn(FlowPreview::class)
class WalletViewModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {
    private val wallet = app as WalletApplication
    private val store = wallet.store
    private val settings = wallet.settings
    private val zone: ZoneId = ZoneId.systemDefault()
    // With the latest flight lookups, so a late flight stays current until it has landed.
    private val agenda = Agenda(zone) { card -> FlightRef.of(card)?.let { wallet.flights.checks.value[it.id]?.flight } }

    private val _ready = MutableStateFlow(false)

    /** True once cards are loaded and the first screen is decided; the first frame waits for it. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /** The search text. */
    val query = MutableStateFlow("")

    /** The current time, updated every minute so time-based rules and date chips move on while the app is open. */
    val now: StateFlow<LocalDateTime> = flow {
        while (true) {
            emit(LocalDateTime.now(zone))
            delay(CLOCK_TICK_MS)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LocalDateTime.now(zone))

    /** How often each card was shown on this device, for tile info lines. */
    val usage: StateFlow<Map<String, Usage>> = store.usage

    /** Every card that is not deleted, archived ones and every member of a group included. */
    val allCards: StateFlow<List<Card>> = store.cards

    /** Current (not archived) cards, every member of a group included. */
    private val current: StateFlow<List<Card>> = combine(store.cards, now, wallet.flights.checks) { all, time, _ ->
        all.filterNot { agenda.isArchived(it, time) }
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** How the grid orders cards. */
    val order: StateFlow<CardOrder> = settings.order

    /** Current cards in grid order, one tile per group (passengers on one flight, tickets for one show). */
    val cards: StateFlow<List<Card>> = combine(current, store.usage, settings.order) { list, usage, order ->
        CardList.sorted(CardGroups.collapse(list), usage, order)
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** How many cards each grouped tile stands for, by the id of the card it shows. */
    val groupSizes: StateFlow<Map<String, Int>> = current.map(CardGroups::sizes)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Favourites for the rail above the bottom bar, most used first. */
    val favourites: StateFlow<List<Card>> = combine(cards, store.usage, CardList::favourites)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Tiles that match the search through any card in their group; favourites appear here as well as in the rail. */
    /** "Coming up" above the grid: tiles with an event ahead, or a start or end date within 30 days, soonest first. */
    val comingUp: StateFlow<List<Card>> = combine(cards, now) { shown, time -> CardDates.comingUp(shown, time) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * The grid's tiles: every tile matching a search, or, without one, the
     * tiles not already in "Coming up".
     */
    val visible: StateFlow<List<Card>> = combine(cards, current, query, comingUp) { shown, list, q, dated ->
        if (q.isBlank()) {
            val above = dated.map { it.id }.toSet()
            shown.filterNot { it.id in above }
        } else {
            shown.filter { tile -> CardGroups.members(tile, list).any { CardList.matches(it, q) } }
        }
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Archived cards, most recently archived first. */
    val archived: StateFlow<List<Pair<Card, LocalDateTime>>> = combine(store.cards, now, wallet.flights.checks) { all, time, _ ->
        all.mapNotNull { card -> agenda.archivedAt(card, time)?.let { card to it } }.sortedByDescending { it.second }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Archived cards matching a non-empty search, shown under the grid's results. */
    val archivedMatches: StateFlow<List<Card>> = combine(archived, query) { list, q ->
        if (q.isBlank()) emptyList() else list.map { it.first }.filter { CardList.matches(it, q) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Cards suggested in the "Up next" row. */
    val upNext: StateFlow<List<UpNext>> = combine(cards, now) { shown, time -> agenda.upNext(shown, time) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _stack = MutableStateFlow<List<Screen>>(listOf(Screen.Grid))

    /** The navigation stack. */
    val stack: StateFlow<List<Screen>> = _stack.asStateFlow()

    /** The card in the form, or null outside it. */
    val draft = MutableStateFlow<CardDraft?>(null)

    /** Changes whenever the form shows another card, so its error marks and pickers start afresh. */
    val formKey = MutableStateFlow(0)

    // True while a save runs, so a quick second tap on Save does nothing.
    private var saving = false

    /** Cards still waiting in the import queue after the current form. */
    val queued = MutableStateFlow(0)
    private val queue = ArrayDeque<CardDraft>()

    /** Codes found in one file when there are several to choose from. */
    val choices = MutableStateFlow<List<ReadCode>?>(null)

    // What came with the file behind [choices], applied to whichever code is picked.
    private var choiceHint: String? = null
    private var choicePages: List<String?> = emptyList()

    /** How long archived cards are kept. */
    val retention: StateFlow<Retention> = settings.retention

    /** Whether "today" notifications and reminders are on. */
    val notifications: StateFlow<Boolean> = settings.notifications

    /** Live flight results by [io.github.jsnjack.wallet.core.FlightRef.id]. */
    val flightChecks: StateFlow<Map<String, FlightCheck>> = wallet.flights.checks

    /** Whether boarding passes look their flight up on flightstats.com. */
    val liveFlights: StateFlow<Boolean> = settings.liveFlights

    /** True while an import or file read runs in the foreground. */
    val busy = MutableStateFlow(false)

    private val events = Channel<UiEvent>(Channel.BUFFERED)

    /** Snackbar messages. */
    val messages: Flow<UiEvent> = events.receiveAsFlow()

    private val permission = Channel<Unit>(Channel.CONFLATED)

    /** Requests to ask for the notification permission. */
    val notificationPermissionRequests: Flow<Unit> = permission.receiveAsFlow()

    init {
        saved.setSavedStateProvider(KEY_STATE) { saveState() }
        viewModelScope.launch {
            store.load()
            // After the cards load, so a restored code screen finds its card.
            restoreState(saved.get<Bundle>(KEY_STATE))
            _ready.value = true
            wallet.surfaces.refresh()
        }
        viewModelScope.launch {
            combine(cards, store.usage) { current, usage -> CardList.quickAccess(current, usage, SHORTCUT_COUNT) to store.cards.value }
                .debounce(SHORTCUT_DEBOUNCE_MS)
                .collect { (top, all) -> withContext(Dispatchers.Default) { Shortcuts.update(app, top, all) } }
        }
        viewModelScope.launch {
            combine(store.cards, settings.retention, settings.notifications) { _, _, _ -> }
                .drop(1)
                .debounce(SURFACE_DEBOUNCE_MS)
                .collect { wallet.surfaces.refresh() }
        }
    }

    // Saved state: the screens, the form and the import queue, for when Android stops the app in the background.

    private fun saveState(): Bundle = bundleOf(
        KEY_STACK to _stack.value.map(::screenKey).toTypedArray(),
        KEY_DRAFTS to DraftJson.encode(listOfNotNull(draft.value) + queue),
    )

    private fun restoreState(state: Bundle?) {
        val screens = state?.getStringArray(KEY_STACK)?.mapNotNull(::screenFor).orEmpty()
        if (screens.firstOrNull() != Screen.Grid) return
        val drafts = DraftJson.decode(state?.getString(KEY_DRAFTS))
        if (Screen.Form in screens && drafts.isNotEmpty()) {
            present(drafts.first())
            queue.addAll(drafts.drop(1))
            queued.value = queue.size
            _stack.value = screens
        } else {
            _stack.value = screens.filterNot { it == Screen.Form }
        }
    }

    private fun screenKey(screen: Screen): String = when (screen) {
        Screen.Grid -> "grid"
        is Screen.Code -> "code:" + screen.cardId
        Screen.Scan -> "scan"
        Screen.Form -> "form"
        Screen.Archive -> "archive"
        Screen.Settings -> "settings"
    }

    private fun screenFor(key: String): Screen? = when {
        key.startsWith("code:") -> Screen.Code(key.removePrefix("code:"))
        else -> listOf(Screen.Grid, Screen.Scan, Screen.Form, Screen.Archive, Screen.Settings).firstOrNull { screenKey(it) == key }
    }

    // Navigation

    /** Goes back one screen; false when already on the first one. */
    fun back(): Boolean {
        val stack = _stack.value
        if (stack.size <= 1) return false
        if (stack.last() == Screen.Form) {
            draft.value = null
            clearQueue()
        }
        _stack.value = stack.dropLast(1)
        return true
    }

    /** Opens the code screen at [cardId]. */
    fun openCard(cardId: String) = push(Screen.Code(cardId))

    /**
     * The pages of a code screen opened at [cardId]: its group (every
     * passenger on one flight, every ticket for one show), or the card alone.
     * An archived card pages through the archived members only.
     */
    fun pagesFor(cardId: String): List<String> {
        val card = store.get(cardId) ?: return emptyList()
        val time = now.value
        val archived = agenda.isArchived(card, time)
        return CardGroups.members(card, store.cards.value.filter { agenda.isArchived(it, time) == archived }).map { it.id }
    }

    /** Opens a card from a launcher shortcut, widget or notification, on top of the grid. */
    fun openFromShortcut(cardId: String) {
        viewModelScope.launch {
            ready.first { it }
            query.value = ""
            resetTo(Screen.Code(cardId))
        }
    }

    /** Opens the scanner from the launcher shortcut. */
    fun openScanFromShortcut() {
        viewModelScope.launch {
            ready.first { it }
            resetTo(Screen.Scan)
        }
    }

    // A new start from outside the app: an open form, its import queue and a code chooser are left behind.
    private fun resetTo(screen: Screen) {
        draft.value = null
        clearQueue()
        dismissChoices()
        _stack.value = listOf(Screen.Grid, screen)
    }

    /** Opens the scanner. */
    fun startScan() = push(Screen.Scan)

    /** Opens settings. */
    fun openSettings() = push(Screen.Settings)

    /** Opens the archive. */
    fun openArchive() = push(Screen.Archive)

    /** Replaces the scanner with an empty form for typing a number. */
    fun startTyping() = showForm(CardDraft(), replaceTop = true)

    /** Opens the form for [cardId]; from a code screen, Save or Cancel comes back to that card's page. */
    fun edit(cardId: String) {
        val card = store.get(cardId) ?: return
        if (_stack.value.lastOrNull() is Screen.Code) replaceTop(Screen.Code(cardId))
        showForm(CardDraft.editing(card, Locale.getDefault()), replaceTop = false)
    }

    private fun push(screen: Screen) {
        _stack.value = _stack.value + screen
    }

    private fun replaceTop(screen: Screen) {
        _stack.value = _stack.value.dropLast(1) + screen
    }

    private fun showForm(initial: CardDraft, replaceTop: Boolean) {
        present(initial)
        if (_stack.value.lastOrNull() == Screen.Form) return
        if (replaceTop) replaceTop(Screen.Form) else push(Screen.Form)
    }

    // Adding cards

    /**
     * A code was read by the camera or from a file. [pages] is the file's
     * printed text per page, which can fill in a ticket's name, date and
     * details; [nameHint] names the card when nothing else does, such as the
     * text that came with a card shared from another phone.
     */
    fun onScanned(code: ReadCode, nameHint: String? = null, pages: List<String?> = emptyList()) {
        dismissChoices()
        val draft = draftFor(code, nameHint, pages, LocalDate.now(zone))
        if (_stack.value.lastOrNull() == Screen.Scan) showForm(draft, replaceTop = true) else openFormFromOutside(draft)
    }

    /** A code was picked from several in one file. */
    fun onChosen(code: ReadCode) = onScanned(code, choiceHint, choicePages)

    /** Opens one form per code in the chooser, each filled from its own page, like several tickets in one PDF. */
    fun addAllChoices() {
        val codes = choices.value ?: return
        val today = LocalDate.now(zone)
        val drafts = codes.map { draftFor(it, choiceHint, choicePages, today) }
        dismissChoices()
        clearQueue()
        queue.addAll(drafts.drop(1))
        queued.value = queue.size
        openFormFromOutside(drafts.first())
    }

    /** Closes the chooser for files with several codes. */
    fun dismissChoices() {
        choices.value = null
        choiceHint = null
        choicePages = emptyList()
    }

    // The form for [code]: what its barcode says, then its page's text, then the hint for a name.
    private fun draftFor(code: ReadCode, nameHint: String?, pages: List<String?>, today: LocalDate): CardDraft {
        val file = pages.filterNotNull().joinToString("\n").ifBlank { null }
        val facts = TicketText.readPage(pages.getOrNull(code.page), file, today, code.text)
        val scanned = CardDraft.scanned(code.text, code.format, today).copy(altFormat = code.altFormat)
            .let { draft -> facts?.let(draft::withTicket) ?: draft }
        val hint = nameHint?.lineSequence()?.firstOrNull()?.trim()?.take(MAX_NAME_HINT).orEmpty()
        return if (scanned.name.isBlank() && hint.isNotEmpty()) scanned.copy(name = hint) else scanned
    }

    /**
     * Reads a picked or shared file: a .pkpass, a PDF or an image. One code
     * opens the form, named after [nameHint] when the code has no name of its
     * own; several open a chooser.
     */
    fun importFile(uri: Uri, fromShare: Boolean, nameHint: String? = null) {
        viewModelScope.launch {
            if (fromShare) ready.first { it }
            busy.value = true
            try {
                val context = getApplication<Application>()
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readNBytes(MAX_FILE_BYTES) }
                        ?: throw IOException("file could not be opened")
                }
                when {
                    Pkpass.looksLikePkpass(bytes) -> {
                        val draft = withContext(Dispatchers.Default) {
                            Pkpass.read(bytes, LocalDate.now(zone), System.currentTimeMillis())
                        }
                        openFormFromOutside(draft)
                    }
                    isPdf(bytes) -> handleRead(withContext(Dispatchers.IO) { CodeReader(liveCamera = false).readPdf(context, bytes) }, nameHint)
                    else -> handleRead(withContext(Dispatchers.Default) { CodeReader(liveCamera = false).readImage(context, uri) }, nameHint)
                }
            } catch (e: IOException) {
                Log.w(LOG_TAG, "file import failed: ${e.message}")
                events.send(UiEvent.Message(R.string.message_file_unreadable))
            } catch (e: SecurityException) {
                // A share without a read grant, or a grant that has expired.
                Log.w(LOG_TAG, "file import not allowed: ${e.message}")
                events.send(UiEvent.Message(R.string.message_file_unreadable))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Whatever an odd file makes a decoder throw: a message, not a crash. The type only, as messages can quote the file.
                Log.w(LOG_TAG, "file import failed: ${e::class.simpleName}")
                events.send(UiEvent.Message(R.string.message_file_unreadable))
            } finally {
                busy.value = false
            }
        }
    }

    /**
     * Reads several screenshots, for example from Google Wallet, and opens
     * the form for each card found, one after another.
     */
    fun importScreenshots(uris: List<Uri>, fromShare: Boolean) {
        if (uris.size == 1) return importFile(uris.single(), fromShare)
        viewModelScope.launch {
            if (fromShare) ready.first { it }
            busy.value = true
            val today = LocalDate.now(zone)
            val drafts = withContext(Dispatchers.Default) {
                val reader = CodeReader(liveCamera = false)
                uris.mapNotNull { uri ->
                    val result = try {
                        reader.readImage(getApplication(), uri)
                    } catch (e: IOException) {
                        Log.w(LOG_TAG, "screenshot import skipped one image: ${e.message}")
                        null
                    } catch (e: SecurityException) {
                        Log.w(LOG_TAG, "screenshot import not allowed for one image: ${e.message}")
                        null
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(LOG_TAG, "screenshot import skipped one image: ${e::class.simpleName}")
                        null
                    }
                    (result as? FileReadResult.Found)?.codes?.first()?.let { CardDraft.scanned(it.text, it.format, today) }
                }
            }
            busy.value = false
            val missing = uris.size - drafts.size
            if (missing > 0) events.send(UiEvent.Counted(R.plurals.message_screenshots_without_code, missing))
            if (drafts.isEmpty()) return@launch
            clearQueue()
            queue.addAll(drafts.drop(1))
            queued.value = queue.size
            openFormFromOutside(drafts.first())
        }
    }

    private suspend fun handleRead(result: FileReadResult, nameHint: String?) {
        when (result) {
            is FileReadResult.Found -> if (result.codes.size == 1) {
                onScanned(result.codes.single(), nameHint, result.pages)
            } else {
                choiceHint = nameHint
                choicePages = result.pages
                choices.value = result.codes
            }
            FileReadResult.Unsupported -> events.send(UiEvent.Message(R.string.message_unsupported_code))
            FileReadResult.Nothing -> events.send(UiEvent.Message(R.string.message_no_code_in_file))
        }
    }

    private fun openFormFromOutside(incoming: CardDraft) {
        val top = _stack.value.lastOrNull()
        // A form already open, such as an unsaved edit, waits behind the new card instead of being lost.
        if (top == Screen.Form) {
            draft.value?.let { interrupted ->
                queue.addLast(interrupted)
                queued.value = queue.size
            }
        }
        showForm(incoming, replaceTop = top == Screen.Scan || top == Screen.Form)
    }

    // Shows [next] in the form.
    private fun present(next: CardDraft) {
        draft.value = next
        formKey.value++
    }

    private fun clearQueue() {
        queue.clear()
        queued.value = 0
    }

    private fun isPdf(bytes: ByteArray) = bytes.size > PDF_MAGIC.size && bytes.copyOfRange(0, PDF_MAGIC.size).contentEquals(PDF_MAGIC)

    // Changing cards

    /** Updates the form. */
    fun updateDraft(change: (CardDraft) -> CardDraft) {
        draft.value = draft.value?.let(change)
    }

    /** Skips the current screenshot in an import and shows the next one. */
    fun skipDraft() {
        val next = queue.removeFirstOrNull()
        queued.value = queue.size
        if (next != null) present(next) else back()
    }

    /** Saves the form: a new card opens on its code screen, an edited one returns, a queued import moves on. */
    fun saveDraft() {
        val current = draft.value ?: return
        if (current.problem() != null || saving) return
        saving = true
        viewModelScope.launch {
            val saved = try {
                store.save(current.toCard(UUID.randomUUID().toString(), System.currentTimeMillis()))
            } finally {
                saving = false
            }
            if (saved.expires != null || saved.event != null) permission.trySend(Unit)
            val next = queue.removeFirstOrNull()
            queued.value = queue.size
            when {
                next != null -> present(next)
                current.editingId == null -> {
                    draft.value = null
                    replaceTop(Screen.Code(saved.id))
                }
                else -> {
                    draft.value = null
                    back()
                }
            }
        }
    }

    /** Deletes [cardId]; the snackbar offers undo. */
    fun delete(cardId: String) = delete(listOf(cardId))

    /** Deletes a grid tile: the card and the rest of its group, such as every passenger's pass for one flight. */
    fun deleteTile(cardId: String) {
        val card = store.get(cardId) ?: return
        delete(CardGroups.members(card, current.value).map { it.id })
    }

    private fun delete(ids: List<String>) {
        viewModelScope.launch {
            val removed = ids.mapNotNull { store.delete(it, System.currentTimeMillis()) }
            if (removed.isEmpty()) return@launch
            draft.value = null
            clearQueue()
            _stack.value = _stack.value.filterNot { it == Screen.Form || it is Screen.Code && it.cardId in ids }
            events.send(UiEvent.Deleted(removed))
        }
    }

    /** Restores cards deleted moments ago. */
    fun undoDelete(cards: List<Card>) {
        viewModelScope.launch { cards.forEach { store.save(it.copy(deleted = false, updatedAt = System.currentTimeMillis())) } }
    }

    /** Moves a voucher or ticket to the archive; the snackbar offers undo. */
    fun markUsed(cardId: String) {
        val card = store.get(cardId) ?: return
        // The code screen may have opened at another ticket of the same show; it closes either way.
        val group = CardGroups.members(card, current.value).map { it.id }.toSet() + cardId
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            store.update(cardId, now) { it.copy(usedAt = now) }
            _stack.value = _stack.value.filterNot { it is Screen.Code && it.cardId in group }
            events.send(UiEvent.MarkedUsed(card))
        }
    }

    /** Puts a used card back among the current ones. */
    fun restore(cardId: String) = changeCard(cardId) { it.copy(usedAt = null) }

    /** Keeps [cardId] above the cards sorted by use, or releases it. */
    fun togglePinned(cardId: String) = changeCard(cardId) { it.copy(pinned = !it.pinned) }

    /** Requires a fingerprint before [cardId]'s code shows, or stops requiring it. */
    fun toggleLocked(cardId: String) = changeCard(cardId) { it.copy(locked = !it.locked) }

    private fun changeCard(cardId: String, change: (Card) -> Card) {
        viewModelScope.launch { store.update(cardId, System.currentTimeMillis(), change) }
    }

    /** Shares [cardId] as a picture of its code with its name, through the system share sheet. */
    fun shareCard(cardId: String) {
        val card = store.get(cardId) ?: return
        viewModelScope.launch {
            val app = getApplication<Application>()
            val chooser = withContext(Dispatchers.IO) { CardShare.chooser(app, card) }
            if (chooser == null) {
                events.send(UiEvent.Message(R.string.message_share_failed))
                return@launch
            }
            app.startActivity(chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Asks the launcher to pin [cardId] to the home screen. */
    fun pinToHomeScreen(cardId: String) {
        val card = store.get(cardId) ?: return
        if (!Shortcuts.requestPin(getApplication(), card)) {
            viewModelScope.launch { events.send(UiEvent.Message(R.string.message_pin_unsupported)) }
        }
    }

    /** Counts a showing of [cardId] for the grid order and shortcuts. */
    fun recordUse(cardId: String) {
        viewModelScope.launch {
            store.recordUse(cardId, System.currentTimeMillis())
            Shortcuts.reportUsed(getApplication(), cardId)
        }
    }

    /** Shows a message from string resources. */
    fun message(@StringRes text: Int) {
        viewModelScope.launch { events.send(UiEvent.Message(text)) }
    }

    /** The card with [cardId], unless deleted. */
    fun card(cardId: String): Card? = store.get(cardId)

    /** Changes how long archived cards are kept. */
    fun setRetention(value: Retention) = settings.setRetention(value)

    /** Changes how the grid orders cards. */
    fun setOrder(value: CardOrder) = settings.setOrder(value)

    /**
     * Brings notifications, widgets and the tile up to date, for example after
     * the notification permission was granted or the app came back to the front.
     */
    fun refreshSurfaces() {
        viewModelScope.launch { wallet.surfaces.refresh() }
    }

    /** Turns notifications on or off; turning them on asks for the permission when needed. */
    fun setNotifications(enabled: Boolean) {
        settings.setNotifications(enabled)
        if (enabled) permission.trySend(Unit)
    }

    // Live flight info

    /** Turns live flight info on or off; turning it off cancels the lookups already planned. */
    fun setLiveFlights(enabled: Boolean) {
        settings.setLiveFlights(enabled)
        viewModelScope.launch { wallet.flightUpdater.schedule() }
    }

    /** Looks the flight of [cardId] up if its pass just opened and the last result is stale. */
    fun refreshFlight(cardId: String) {
        viewModelScope.launch { wallet.flightUpdater.refreshOnOpen(cardId) }
    }

    // Export and import

    /** Writes every card, encrypted with [password], to [uri]. */
    fun export(uri: Uri, password: CharArray) {
        viewModelScope.launch {
            busy.value = true
            try {
                val count = withContext(Dispatchers.IO) {
                    val cards = store.snapshot().filterNot { it.deleted }
                    val plain = CardJson.encodeExport(cards, System.currentTimeMillis()).encodeToByteArray()
                    val file = ExportCrypto.encrypt(plain, password)
                    val out = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                        ?: throw IOException("export destination could not be opened")
                    out.use { it.write(file) }
                    cards.size
                }
                events.send(UiEvent.Counted(R.plurals.message_exported, count))
            } catch (e: IOException) {
                Log.w(LOG_TAG, "export failed: ${e.message}")
                events.send(UiEvent.Message(R.string.message_export_failed))
            } finally {
                password.fill(' ')
                busy.value = false
            }
        }
    }

    /** Reads an export file at [uri]; cards newer than the local copies are taken. */
    fun import(uri: Uri, password: CharArray) {
        viewModelScope.launch {
            busy.value = true
            try {
                val cards = withContext(Dispatchers.IO) {
                    val input = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?: throw IOException("import file could not be opened")
                    val file = input.use { it.readBytes() }
                    CardJson.decodeExport(ExportCrypto.decrypt(file, password).decodeToString())
                }
                val taken = store.applyIncoming(cards)
                events.send(UiEvent.Counted(R.plurals.message_imported, taken))
            } catch (e: WrongPasswordException) {
                events.send(UiEvent.Message(R.string.message_wrong_password))
            } catch (e: NotAnExportException) {
                Log.w(LOG_TAG, "import rejected: ${e.message}")
                events.send(UiEvent.Message(R.string.message_not_an_export))
            } catch (e: IllegalArgumentException) {
                Log.w(LOG_TAG, "import rejected: ${e.message}")
                events.send(UiEvent.Message(R.string.message_not_an_export))
            } catch (e: IOException) {
                Log.w(LOG_TAG, "import failed: ${e.message}")
                events.send(UiEvent.Message(R.string.message_import_failed))
            } finally {
                password.fill(' ')
                busy.value = false
            }
        }
    }

    private companion object {
        const val KEY_STATE = "wallet"
        const val KEY_STACK = "stack"
        const val KEY_DRAFTS = "drafts"
        const val SHORTCUT_DEBOUNCE_MS = 2_000L
        const val SHORTCUT_COUNT = 3
        const val SURFACE_DEBOUNCE_MS = 1_000L
        const val CLOCK_TICK_MS = 60_000L
        const val MAX_FILE_BYTES = 20 * 1024 * 1024
        const val MAX_NAME_HINT = 60
        val PDF_MAGIC = "%PDF".toByteArray(Charsets.US_ASCII)
    }
}
