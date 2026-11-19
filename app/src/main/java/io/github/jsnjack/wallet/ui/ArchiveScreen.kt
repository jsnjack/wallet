package io.github.jsnjack.wallet.ui

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jsnjack.wallet.R
import io.github.jsnjack.wallet.core.Card
import io.github.jsnjack.wallet.core.CardKind
import io.github.jsnjack.wallet.core.Retention
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class ArchiveFilter(val label: Int, val kinds: Set<CardKind>?) {
    ALL(R.string.archive_all, null),
    VOUCHERS(R.string.archive_vouchers, setOf(CardKind.VOUCHER, CardKind.GIFT_CARD)),
    TRIPS(R.string.archive_trips, setOf(CardKind.BOARDING_PASS)),
    TICKETS(R.string.archive_tickets, setOf(CardKind.TICKET)),
}

/** Used vouchers, expired cards and past tickets and flights, newest first, one group per month, in a full-height sheet. */
@Composable
fun ArchiveSheet(vm: WalletViewModel) {
    val archived by vm.archived.collectAsStateWithLifecycle()
    val retention by vm.retention.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf(ArchiveFilter.ALL) }
    val locale = LocalConfiguration.current.locales[0]
    val month = remember(locale) { DateTimeFormatter.ofPattern("LLLL yyyy", locale) }
    val shown = archived.filter { (card, _) -> filter.kinds?.contains(card.kind) ?: true }

    WalletSheet(onDismiss = { vm.back() }) {
        Text(stringResource(R.string.archive_title), fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
        Text(
            if (retention == Retention.FOREVER) stringResource(R.string.archive_kept_forever)
            else stringResource(R.string.archive_kept_for, stringResource(retentionLabel(retention))),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, bottom = 10.dp),
        )
        LazyRow(horizontalArrangement = CHIP_GAP) {
            items(ArchiveFilter.entries) { option ->
                WalletChip(stringResource(option.label), selected = option == filter, onClick = { filter = option })
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        ) {
            if (shown.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.archive_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                    )
                }
            }
            shown.groupBy { it.second.format(month) }.forEach { (title, entries) ->
                item(key = "month-$title") {
                    Column {
                        Text(
                            title.replaceFirstChar { it.titlecase(locale) },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
                        )
                        Group {
                            entries.forEachIndexed { i, (card, at) ->
                                if (i > 0) GroupDivider()
                                ArchivedRow(card, at, vm)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArchivedRow(card: Card, archivedAt: LocalDateTime, vm: WalletViewModel) {
    var menu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val date = DateUtils.formatDateTime(
        context,
        archivedAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_NO_YEAR,
    )
    val reason = when {
        card.usedAt != null -> R.string.archive_used
        card.expires != null && !card.expires.end.isAfter(archivedAt) -> R.string.archive_expired
        else -> R.string.archive_past
    }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = { vm.openCard(card.id) }, onLongClick = { menu = true })
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Monogram(card, Modifier.size(36.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    card.flight?.let { "${it.code} · ${it.from} → ${it.to}" } ?: card.name,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(stringResource(reason, date), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (menu) {
            WalletSheet(onDismiss = { menu = false }) {
                if (card.usedAt != null) SheetRow(stringResource(R.string.action_restore), R.drawable.ic_archive) { menu = false; vm.restore(card.id) }
                SheetRow(stringResource(R.string.menu_delete), R.drawable.ic_close, color = colorResource(R.color.danger)) { menu = false; vm.delete(card.id) }
            }
        }
    }
}
