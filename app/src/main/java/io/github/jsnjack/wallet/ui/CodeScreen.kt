package io.github.jsnjack.wallet.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import io.github.jsnjack.wallet.R
import io.github.jsnjack.wallet.core.Card
import io.github.jsnjack.wallet.core.CardLooks
import io.github.jsnjack.wallet.core.CodeNumbers
import io.github.jsnjack.wallet.platform.Biometrics

private const val USE_AFTER_MS = 1_000L

/** Full-screen codes; swipe sideways for the next card. */
@Composable
fun CodeScreen(vm: WalletViewModel, startId: String) {
    val visible by vm.visible.collectAsStateWithLifecycle()
    val all by vm.cards.collectAsStateWithLifecycle()
    // Order is fixed when the screen opens, so counting a use never moves the page.
    val order = remember(startId) {
        val source = if (visible.any { it.id == startId }) visible else all
        source.map { it.id }
    }
    val byId = all.associateBy { it.id }
    val startPage = order.indexOf(startId)
    if (startPage < 0) {
        LaunchedEffect(startId) { vm.back() }
        return
    }
    val pager = rememberPagerState(initialPage = startPage) { order.size }
    var unlocked by remember { mutableStateOf(emptySet<String>()) }
    val activity = LocalActivity.current
    val unlockTitle = stringResource(R.string.unlock_prompt_title)

    MaxBrightness()
    val current = byId[order[pager.settledPage]]
    StatusBarIcons(dark = current != null && CardLooks.contentColor(current.color) == CardLooks.DARK_TEXT)
    LaunchedEffect(pager.settledPage) {
        delay(USE_AFTER_MS)
        vm.recordUse(order[pager.settledPage])
    }

    HorizontalPager(state = pager, key = { order[it] }, modifier = Modifier.fillMaxSize()) { page ->
        val card = byId[order[page]]
        if (card == null) {
            Box(Modifier.fillMaxSize().background(Color.Black))
            return@HorizontalPager
        }
        CodePage(
            card = card,
            page = page,
            pages = order.size,
            showCode = !card.locked || card.id in unlocked,
            onClose = { vm.back() },
            onEdit = { vm.edit(card.id) },
            onUnlock = {
                if (activity != null) {
                    Biometrics.authenticate(
                        activity,
                        unlockTitle,
                        onSuccess = { unlocked = unlocked + card.id },
                        onError = { vm.message(R.string.message_unlock_failed) },
                    )
                }
            },
        )
    }
}

@Composable
private fun CodePage(
    card: Card,
    page: Int,
    pages: Int,
    showCode: Boolean,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    onUnlock: () -> Unit,
) {
    val content = Color(CardLooks.contentColor(card.color))
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(card.color))
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(onClick = onClose, shape = CircleShape, color = Color.Black.copy(alpha = 0.16f), modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_close), tint = content)
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.code_brightness),
                color = content,
                fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
        Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(CardLooks.monogram(card.name), color = content, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(card.name, color = content, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                Text(stringResource(kindLabel(card.kind)), color = content.copy(alpha = 0.8f), fontSize = 13.sp)
            }
        }
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = colorResource(R.color.code_panel),
            contentColor = colorResource(R.color.code_ink),
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        ) {
            Box {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CodeImage(card.code, card.format, if (showCode) Modifier else Modifier.blur(18.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (showCode) CodeNumbers.display(card.code) else stringResource(R.string.code_hidden),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 15.sp,
                        letterSpacing = 1.5.sp,
                        textAlign = TextAlign.Center,
                    )
                    if (card.note.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(card.note, fontSize = 13.sp, color = colorResource(R.color.muted), textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (showCode) PanelAction(stringResource(R.string.action_copy_number)) { copy(context, card) }
                        PanelAction(stringResource(R.string.menu_edit), onClick = onEdit)
                    }
                }
                if (!showCode) {
                    Button(
                        onClick = onUnlock,
                        colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.code_ink), contentColor = colorResource(R.color.code_panel)),
                        modifier = Modifier.align(Alignment.Center).padding(bottom = 40.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_lock), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.action_unlock))
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (pages > 1) {
            Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalArrangement = Arrangement.Center) {
                val shown = minOf(pages, MAX_DOTS)
                val first = (page - shown / 2).coerceIn(0, pages - shown)
                repeat(shown) { i ->
                    val active = first + i == page
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (active) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(content.copy(alpha = if (active) 0.9f else 0.4f)),
                    )
                }
            }
        }
    }
}

private const val MAX_DOTS = 7

@Composable
private fun PanelAction(text: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(50), color = colorResource(R.color.code_ink).copy(alpha = 0.07f)) {
        Text(text, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
    }
}

private fun copy(context: Context, card: Card) {
    val clip = ClipData.newPlainText(card.name, card.code)
    if (card.locked) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
}

/** Full screen brightness and no screen timeout while a code is shown. */
@Composable
private fun MaxBrightness() {
    val activity = LocalActivity.current ?: return
    DisposableEffect(activity) {
        val window = activity.window
        val previous = window.attributes.screenBrightness
        window.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window.attributes = window.attributes.apply { screenBrightness = previous }
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

/** Dark status bar icons on light card colours; the theme's choice comes back on leaving. */
@Composable
private fun StatusBarIcons(dark: Boolean) {
    val activity = LocalActivity.current ?: return
    DisposableEffect(activity, dark) {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        val previous = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = dark
        onDispose { controller.isAppearanceLightStatusBars = previous }
    }
}
