package io.github.jsnjack.wallet.ui

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.ApiException
import io.github.jsnjack.wallet.LOG_TAG
import io.github.jsnjack.wallet.R
import io.github.jsnjack.wallet.WalletApplication
import io.github.jsnjack.wallet.core.Agenda
import io.github.jsnjack.wallet.core.Card
import io.github.jsnjack.wallet.core.CardDraft
import io.github.jsnjack.wallet.core.CardJson
import io.github.jsnjack.wallet.core.CardList
import io.github.jsnjack.wallet.core.ExportCrypto
import io.github.jsnjack.wallet.core.NotAnExportException
import io.github.jsnjack.wallet.core.Pkpass
import io.github.jsnjack.wallet.core.Retention
import io.github.jsnjack.wallet.core.Trips
import io.github.jsnjack.wallet.core.UpNext
import io.github.jsnjack.wallet.core.Usage
import io.github.jsnjack.wallet.core.WrongPasswordException
import io.github.jsnjack.wallet.drive.AuthOutcome
import io.github.jsnjack.wallet.drive.DriveAuth
import io.github.jsnjack.wallet.drive.SyncStatus
import io.github.jsnjack.wallet.platform.CodeReader
import io.github.jsnjack.wallet.platform.FileReadResult
import io.github.jsnjack.wallet.platform.ReadCode
import io.github.jsnjack.wallet.platform.Shortcuts
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

    /** Sync, archive, notification and backup settings. */
    data object Settings : Screen

    /** Restore from Drive or start fresh. */
    data object FirstRun : Screen
}

/** One-off messages for the snackbar. */
sealed interface UiEvent {
    /** [card] was deleted and can be restored. */
    data class Deleted(val card: Card) : UiEvent

    /** [card] was marked as used and can be put back. */
    data class MarkedUsed(val card: Card) : UiEvent

    /** A message from string resources. */
    data class Message(@param:StringRes val text: Int) : UiEvent

    /** A message from plural resources about [count] items. */
    data class Counted(@param:PluralsRes val text: Int, val count: Int) : UiEvent
}

/** State and actions for every screen. */
@OptIn(FlowPreview::class)
class WalletViewModel(app: Application) : AndroidViewModel(app) {
    private val wallet = app as WalletApplication
    private val store = wallet.store
    private val sync = wallet.sync
    private val settings = wallet.settings
    private val zone: ZoneId = ZoneId.systemDefault()
    private val agenda = Agenda(zone)

    private val _ready = MutableStateFlow(false)

    /** True once cards are loaded and the first screen is decided; the first frame waits for it. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /** The search text. */
    val query = MutableStateFlow("")

    /** The current time, updated every minute so time-based rules move on while the app is open. */
    private val now: StateFlow<LocalDateTime> = flow {
        while (true) {
            emit(LocalDateTime.now(zone))
            delay(CLOCK_TICK_MS)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LocalDateTime.now(zone))

    /** How often each card was shown on this device, for tile info lines. */
    val usage: StateFlow<Map<String, Usage>> = store.usage

    /** Every card that is not deleted, archived ones and every trip leg included. */
    val allCards: StateFlow<List<Card>> = store.cards

    /** Current (not archived) cards in grid order, one card per trip. */
    val cards: StateFlow<List<Card>> = combine(store.cards, store.usage, now) { all, usage, time ->
        CardList.sorted(Trips.collapse(all.filterNot { agenda.isArchived(it, time) }), usage)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Favourites for the rail above the bottom bar, most used first. */
    val favourites: StateFlow<List<Card>> = combine(cards, store.usage, CardList::favourites)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Cards in the grid: without a search the favourites live in the rail instead; a search covers every card. */
    val visible: StateFlow<List<Card>> = combine(cards, query) { all, q ->
        all.filter { CardList.matches(it, q) && (q.isNotBlank() || !it.pinned) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Archived cards, most recently archived first. */
    val archived: StateFlow<List<Pair<Card, LocalDateTime>>> = combine(store.cards, now) { all, time ->
        all.mapNotNull { card -> agenda.archivedAt(card, time)?.let { card to it } }.sortedByDescending { it.second }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Archived cards matching a non-empty search, shown under the grid's results. */
    val archivedMatches: StateFlow<List<Card>> = combine(archived, query) { list, q ->
        if (q.isBlank()) emptyList() else list.map { it.first }.filter { CardList.matches(it, q) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Cards suggested in the "Up next" row. */
    val upNext: StateFlow<List<UpNext>> = combine(store.cards, now) { all, time -> agenda.upNext(all, time) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _stack = MutableStateFlow<List<Screen>>(listOf(Screen.Grid))

    /** The navigation stack. */
    val stack: StateFlow<List<Screen>> = _stack.asStateFlow()

    /** The card in the form, or null outside it. */
    val draft = MutableStateFlow<CardDraft?>(null)

    /** Screenshots still waiting in the import queue after the current form. */
    val queued = MutableStateFlow(0)
    private val queue = ArrayDeque<CardDraft>()

    /** Codes found in one file when there are several to choose from. */
    val choices = MutableStateFlow<List<ReadCode>?>(null)

    /** Drive sync state. */
    val syncStatus: StateFlow<SyncStatus> = sync.status

    /** How long archived cards are kept. */
    val retention: StateFlow<Retention> = settings.retention

    /** Whether "today" notifications and reminders are on. */
    val notifications: StateFlow<Boolean> = settings.notifications

    /** True while a restore, import or file read runs in the foreground. */
    val busy = MutableStateFlow(false)

    private val events = Channel<UiEvent>(Channel.BUFFERED)

    /** Snackbar messages. */
    val messages: Flow<UiEvent> = events.receiveAsFlow()

    private val consent = Channel<PendingIntent>(Channel.BUFFERED)

    /** Google consent screens the activity must launch. */
    val consentRequests: Flow<PendingIntent> = consent.receiveAsFlow()

    private val permission = Channel<Unit>(Channel.CONFLATED)

    /** Requests to ask for the notification permission. */
    val notificationPermissionRequests: Flow<Unit> = permission.receiveAsFlow()

    init {
        viewModelScope.launch {
            store.load()
            if (store.cards.value.isEmpty() && !sync.firstRunDone) _stack.value = listOf(Screen.FirstRun)
            _ready.value = true
            sync.requestNow()
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
     * The page order for a code screen opened at [cardId]: the legs of its
     * trip, the grid's results, or the archive, whichever holds it.
     */
    fun pagesFor(cardId: String): List<String> {
        val card = store.get(cardId) ?: return emptyList()
        val trip = Trips.legs(card, store.cards.value)
        return when {
            trip.size > 1 -> trip
            visible.value.any { it.id == cardId } -> visible.value
            favourites.value.any { it.id == cardId } -> favourites.value
            archived.value.any { it.first.id == cardId } -> archived.value.map { it.first }
            else -> listOf(card)
        }.map { it.id }
    }

    /** Opens a card from a launcher shortcut, widget or notification, on top of the grid. */
    fun openFromShortcut(cardId: String) {
        viewModelScope.launch {
            ready.first { it }
            query.value = ""
            _stack.value = listOf(Screen.Grid, Screen.Code(cardId))
        }
    }

    /** Opens the scanner from the launcher shortcut. */
    fun openScanFromShortcut() {
        viewModelScope.launch {
            ready.first { it }
            _stack.value = listOf(Screen.Grid, Screen.Scan)
        }
    }

    /** Opens the scanner. */
    fun startScan() = push(Screen.Scan)

    /** Opens settings. */
    fun openSettings() = push(Screen.Settings)

    /** Opens the archive. */
    fun openArchive() = push(Screen.Archive)

    /** Replaces the scanner with an empty form for typing a number. */
    fun startTyping() = showForm(CardDraft(), replaceTop = true)

    /** Opens the form for [cardId]. */
    fun edit(cardId: String) {
        store.get(cardId)?.let { showForm(CardDraft.editing(it, Locale.getDefault()), replaceTop = false) }
    }

    private fun push(screen: Screen) {
        _stack.value = _stack.value + screen
    }

    private fun replaceTop(screen: Screen) {
        _stack.value = _stack.value.dropLast(1) + screen
    }

    private fun showForm(initial: CardDraft, replaceTop: Boolean) {
        draft.value = initial
        if (_stack.value.lastOrNull() == Screen.Form) return
        if (replaceTop) replaceTop(Screen.Form) else push(Screen.Form)
    }

    // Adding cards

    /** A code was read by the camera or picked from several in a file. */
    fun onScanned(code: ReadCode) {
        choices.value = null
        val draft = CardDraft.scanned(code.text, code.format, LocalDate.now(zone))
        if (_stack.value.lastOrNull() == Screen.Scan) showForm(draft, replaceTop = true) else openFormFromOutside(draft)
    }

    /** Closes the chooser for files with several codes. */
    fun dismissChoices() {
        choices.value = null
    }

    /**
     * Reads a picked or shared file: a .pkpass, a PDF or an image. One code
     * opens the form; several open a chooser.
     */
    fun importFile(uri: Uri, fromShare: Boolean) {
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
                    isPdf(bytes) -> handleRead(withContext(Dispatchers.Default) { CodeReader(liveCamera = false).readPdf(context, uri) })
                    else -> handleRead(withContext(Dispatchers.Default) { CodeReader(liveCamera = false).readImage(context, uri) })
                }
            } catch (e: IOException) {
                Log.w(LOG_TAG, "file import failed: ${e.message}")
                events.send(UiEvent.Message(R.string.message_file_unreadable))
            } catch (e: SecurityException) {
                // A share without a read grant, or a grant that has expired.
                Log.w(LOG_TAG, "file import not allowed: ${e.message}")
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

    private suspend fun handleRead(result: FileReadResult) {
        when (result) {
            is FileReadResult.Found -> if (result.codes.size == 1) onScanned(result.codes.single()) else choices.value = result.codes
            FileReadResult.Unsupported -> events.send(UiEvent.Message(R.string.message_unsupported_code))
            FileReadResult.Nothing -> events.send(UiEvent.Message(R.string.message_no_code_in_file))
        }
    }

    private fun openFormFromOutside(draft: CardDraft) {
        val top = _stack.value.lastOrNull()
        when {
            top == Screen.Scan || top == Screen.Form -> showForm(draft, replaceTop = true)
            top == Screen.FirstRun -> {
                sync.firstRunDone = true
                _stack.value = listOf(Screen.Grid)
                showForm(draft, replaceTop = false)
            }
            else -> showForm(draft, replaceTop = false)
        }
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
        if (next != null) draft.value = next else back()
    }

    /** Saves the form: a new card opens on its code screen, an edited one returns, a queued import moves on. */
    fun saveDraft() {
        val current = draft.value ?: return
        if (current.problem() != null) return
        viewModelScope.launch {
            val saved = store.save(current.toCard(UUID.randomUUID().toString(), System.currentTimeMillis()))
            if (saved.expires != null || saved.event != null) permission.trySend(Unit)
            val next = queue.removeFirstOrNull()
            queued.value = queue.size
            when {
                next != null -> draft.value = next
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
    fun delete(cardId: String) {
        viewModelScope.launch {
            val removed = store.delete(cardId, System.currentTimeMillis()) ?: return@launch
            draft.value = null
            _stack.value = _stack.value.filterNot { it == Screen.Form || it == Screen.Code(cardId) }
            events.send(UiEvent.Deleted(removed))
        }
    }

    /** Restores a card deleted moments ago. */
    fun undoDelete(card: Card) {
        viewModelScope.launch { store.save(card.copy(deleted = false, updatedAt = System.currentTimeMillis())) }
    }

    /** Moves a voucher or ticket to the archive; the snackbar offers undo. */
    fun markUsed(cardId: String) {
        val card = store.get(cardId) ?: return
        viewModelScope.launch {
            store.save(card.copy(usedAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
            _stack.value = _stack.value.filterNot { it == Screen.Code(cardId) }
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
        val card = store.get(cardId) ?: return
        viewModelScope.launch { store.save(change(card).copy(updatedAt = System.currentTimeMillis())) }
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

    // First run and sync

    /** Starts Drive access; may ask the activity to show Google's consent screen. */
    fun connectDrive() {
        viewModelScope.launch {
            try {
                handleAuth(DriveAuth.authorize(getApplication()))
            } catch (e: IOException) {
                reportAuthFailure(e)
            }
        }
    }

    /** Continues after Google's consent screen returned [data]. */
    fun onConsentResult(data: Intent?) {
        viewModelScope.launch {
            try {
                handleAuth(DriveAuth.afterConsent(getApplication(), data))
            } catch (e: IOException) {
                reportAuthFailure(e)
            }
        }
    }

    private suspend fun reportAuthFailure(e: IOException) {
        Log.w(LOG_TAG, "Drive access not granted: ${e.message}")
        val cancelled = (e.cause as? ApiException)?.statusCode == CommonStatusCodes.CANCELED
        if (!cancelled) events.send(UiEvent.Message(R.string.message_drive_connect_failed))
    }

    private suspend fun handleAuth(outcome: AuthOutcome) {
        when (outcome) {
            is AuthOutcome.NeedsConsent -> consent.send(outcome.intent)
            is AuthOutcome.Granted -> {
                busy.value = true
                try {
                    sync.enable(outcome.token)
                    if (_stack.value.lastOrNull() == Screen.FirstRun) finishFirstRun()
                } catch (e: IOException) {
                    events.send(UiEvent.Message(R.string.message_sync_failed))
                } finally {
                    busy.value = false
                }
            }
        }
    }

    /** Leaves the first-run screen for the grid. */
    fun finishFirstRun() {
        sync.firstRunDone = true
        _stack.value = listOf(Screen.Grid)
    }

    /** Runs a sync soon. */
    fun syncNow() = sync.requestNow()

    /** Turns sync off. */
    fun disableSync() = sync.disable()

    /** Allows or forbids syncing over mobile data. */
    fun setUseMobileData(allowed: Boolean) = sync.setUseMobileData(allowed)

    /** Changes how long archived cards are kept. */
    fun setRetention(value: Retention) = settings.setRetention(value)

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
                if (taken > 0) sync.requestNow()
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
        const val SHORTCUT_DEBOUNCE_MS = 2_000L
        const val SHORTCUT_COUNT = 3
        const val SURFACE_DEBOUNCE_MS = 1_000L
        const val CLOCK_TICK_MS = 60_000L
        const val MAX_FILE_BYTES = 20 * 1024 * 1024
        val PDF_MAGIC = "%PDF".toByteArray(Charsets.US_ASCII)
    }
}
