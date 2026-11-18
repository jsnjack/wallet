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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.jsnjack.wallet.LOG_TAG
import io.github.jsnjack.wallet.R
import io.github.jsnjack.wallet.WalletApplication
import io.github.jsnjack.wallet.core.Card
import io.github.jsnjack.wallet.core.CardDraft
import io.github.jsnjack.wallet.core.CardJson
import io.github.jsnjack.wallet.core.CardList
import io.github.jsnjack.wallet.core.ExportCrypto
import io.github.jsnjack.wallet.core.NotAnExportException
import io.github.jsnjack.wallet.core.WrongPasswordException
import io.github.jsnjack.wallet.drive.AuthOutcome
import io.github.jsnjack.wallet.drive.DriveAuth
import io.github.jsnjack.wallet.drive.SyncStatus
import io.github.jsnjack.wallet.platform.CodeReader
import io.github.jsnjack.wallet.platform.ImageReadResult
import io.github.jsnjack.wallet.platform.ReadCode
import io.github.jsnjack.wallet.platform.Shortcuts
import java.io.IOException
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

    /** Sync and backup settings. */
    data object Settings : Screen

    /** Restore from Drive or start fresh. */
    data object FirstRun : Screen
}

/** One-off messages for the snackbar. */
sealed interface UiEvent {
    /** [card] was deleted and can be restored. */
    data class Deleted(val card: Card) : UiEvent

    /** A message from string resources. */
    data class Message(@param:StringRes val text: Int) : UiEvent

    /** A message from plural resources about [count] cards. */
    data class Counted(@param:PluralsRes val text: Int, val count: Int) : UiEvent
}

/** State and actions for every screen. */
@OptIn(FlowPreview::class)
class WalletViewModel(app: Application) : AndroidViewModel(app) {
    private val wallet = app as WalletApplication
    private val store = wallet.store
    private val sync = wallet.sync

    private val _ready = MutableStateFlow(false)

    /** True once cards are loaded and the first screen is decided; the first frame waits for it. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /** The search text. */
    val query = MutableStateFlow("")

    /** All cards in grid order. */
    val cards: StateFlow<List<Card>> = combine(store.cards, store.usage, CardList::sorted)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Cards matching the search. */
    val visible: StateFlow<List<Card>> = combine(cards, query) { all, q -> all.filter { CardList.matches(it, q) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _stack = MutableStateFlow<List<Screen>>(listOf(Screen.Grid))

    /** The navigation stack. */
    val stack: StateFlow<List<Screen>> = _stack.asStateFlow()

    /** The card in the form, or null outside it. */
    val draft = MutableStateFlow<CardDraft?>(null)

    /** Drive sync state. */
    val syncStatus: StateFlow<SyncStatus> = sync.status

    /** True while a restore or import runs in the foreground. */
    val busy = MutableStateFlow(false)

    private val events = Channel<UiEvent>(Channel.BUFFERED)

    /** Snackbar messages. */
    val messages: Flow<UiEvent> = events.receiveAsFlow()

    private val consent = Channel<PendingIntent>(Channel.BUFFERED)

    /** Google consent screens the activity must launch. */
    val consentRequests: Flow<PendingIntent> = consent.receiveAsFlow()

    init {
        viewModelScope.launch {
            store.load()
            if (store.cards.value.isEmpty() && !sync.firstRunDone) _stack.value = listOf(Screen.FirstRun)
            _ready.value = true
            sync.requestNow()
        }
        viewModelScope.launch {
            combine(store.cards, store.usage) { all, usage -> CardList.mostUsed(all, usage) to all }
                .debounce(SHORTCUT_DEBOUNCE_MS)
                .collect { (top, all) -> withContext(Dispatchers.Default) { Shortcuts.update(app, top, all) } }
        }
    }

    // Navigation

    /** Goes back one screen; false when already on the first one. */
    fun back(): Boolean {
        val stack = _stack.value
        if (stack.size <= 1) return false
        if (stack.last() == Screen.Form) draft.value = null
        _stack.value = stack.dropLast(1)
        return true
    }

    /** Opens the code screen at [cardId]. */
    fun openCard(cardId: String) = push(Screen.Code(cardId))

    /** Opens a card from a launcher shortcut, on top of the grid. */
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

    /** Replaces the scanner with an empty form for typing a number. */
    fun startTyping() = showForm(CardDraft(), replaceTop = true)

    /** Opens the form for [cardId]. */
    fun edit(cardId: String) {
        store.get(cardId)?.let { showForm(CardDraft.editing(it), replaceTop = false) }
    }

    private fun push(screen: Screen) {
        _stack.value = _stack.value + screen
    }

    private fun replaceTop(screen: Screen) {
        _stack.value = _stack.value.dropLast(1) + screen
    }

    private fun showForm(initial: CardDraft, replaceTop: Boolean) {
        draft.value = initial
        if (replaceTop) replaceTop(Screen.Form) else push(Screen.Form)
    }

    // Adding and changing cards

    /** A code was read by the camera. */
    fun onScanned(code: ReadCode) = showForm(CardDraft.scanned(code.text, code.format), replaceTop = true)

    /** Reads a code from a picked or shared image and opens the form for it. */
    fun readImage(uri: Uri, fromShare: Boolean) {
        viewModelScope.launch {
            if (fromShare) ready.first { it }
            val result = try {
                withContext(Dispatchers.Default) { CodeReader(liveCamera = false).readImage(getApplication(), uri) }
            } catch (e: IOException) {
                Log.w(LOG_TAG, "image read failed: ${e.message}")
                null
            }
            when (result) {
                is ImageReadResult.Found -> {
                    if (fromShare) _stack.value = listOf(Screen.Grid, Screen.Scan)
                    onScanned(result.code)
                }
                ImageReadResult.Unsupported -> events.send(UiEvent.Message(R.string.message_unsupported_code))
                ImageReadResult.Nothing, null -> events.send(UiEvent.Message(R.string.message_no_code_in_image))
            }
        }
    }

    /** Updates the form. */
    fun updateDraft(change: (CardDraft) -> CardDraft) {
        draft.value = draft.value?.let(change)
    }

    /** Saves the form; a new card opens on its code screen, an edited one returns. */
    fun saveDraft() {
        val current = draft.value ?: return
        if (current.problem() != null) return
        viewModelScope.launch {
            val saved = store.save(current.toCard(UUID.randomUUID().toString(), System.currentTimeMillis()))
            draft.value = null
            if (current.editingId == null) replaceTop(Screen.Code(saved.id)) else back()
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

    // First run and sync

    /** Starts Drive access; may ask the activity to show Google's consent screen. */
    fun connectDrive() {
        viewModelScope.launch {
            try {
                handleAuth(DriveAuth.authorize(getApplication()))
            } catch (e: IOException) {
                Log.w(LOG_TAG, "Drive connect failed: ${e.message}")
                events.send(UiEvent.Message(R.string.message_drive_connect_failed))
            }
        }
    }

    /** Continues after Google's consent screen returned [data]. */
    fun onConsentResult(data: Intent?) {
        viewModelScope.launch {
            try {
                handleAuth(DriveAuth.afterConsent(getApplication(), data))
            } catch (e: IOException) {
                Log.w(LOG_TAG, "Drive consent not completed: ${e.message}")
                events.send(UiEvent.Message(R.string.message_drive_connect_failed))
            }
        }
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
    }
}
