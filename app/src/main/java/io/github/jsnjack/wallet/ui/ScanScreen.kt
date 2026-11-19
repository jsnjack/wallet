package io.github.jsnjack.wallet.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.jsnjack.wallet.LOG_TAG
import io.github.jsnjack.wallet.R
import io.github.jsnjack.wallet.platform.CodeReader
import io.github.jsnjack.wallet.platform.ReadCode
import java.util.concurrent.Executors

/** Camera scanner; the code is taken as soon as it is in view. */
@Composable
fun ScanScreen(vm: WalletViewModel) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        asked = true
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_SCREENSHOTS)) { uris ->
        if (uris.isNotEmpty()) vm.importScreenshots(uris, fromShare = false)
    }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFile(uri, fromShare = false)
    }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }
    var torch by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            CameraPreview(onCamera = { camera = it }, onCode = vm::onScanned, onUnsupported = { vm.message(R.string.message_unsupported_code) })
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(width = 260.dp, height = 170.dp)
                    .border(3.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(18.dp)),
            )
            Text(
                stringResource(R.string.scan_hint),
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.Center).padding(bottom = 230.dp),
            )
        } else if (asked) {
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.scan_no_camera), color = Color.White, textAlign = TextAlign.Center)
                Spacer(Modifier.height(14.dp))
                Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.action_allow_camera)) }
            }
        }

        // Close and torch sit at the bottom with the sources, within reach of the thumb.
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(14.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                if (camera?.cameraInfo?.hasFlashUnit() == true) {
                    RoundButton(R.drawable.ic_flash, stringResource(R.string.action_torch), active = torch) {
                        torch = !torch
                        camera?.cameraControl?.enableTorch(torch)
                    }
                } else {
                    Spacer(Modifier.size(52.dp))
                }
                RoundButton(R.drawable.ic_close, stringResource(R.string.action_close)) { vm.back() }
            }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SourceButton(R.drawable.ic_image, stringResource(R.string.scan_from_image), Modifier.weight(1f)) {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            SourceButton(R.drawable.ic_file, stringResource(R.string.scan_from_file), Modifier.weight(1f)) {
                files.launch(IMPORT_TYPES)
            }
            SourceButton(R.drawable.ic_keyboard, stringResource(R.string.scan_type_number), Modifier.weight(1f)) { vm.startTyping() }
        }
        }
    }
}

@Composable
private fun CameraPreview(onCamera: (Camera) -> Unit, onCode: (ReadCode) -> Unit, onUnsupported: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val view = LocalView.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    var bound by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

    LaunchedEffect(lifecycle) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        bound = provider
        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(ResolutionStrategy(ANALYSIS_SIZE, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                    .build(),
            )
            .build()
        val reader = CodeReader(liveCamera = true)
        val main = Handler(Looper.getMainLooper())
        var lastUnsupported = 0L
        analysis.setAnalyzer(analysisExecutor) { frame ->
            reader.analyze(
                frame,
                onCode = { code ->
                    main.post {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        onCode(code)
                    }
                },
                onUnsupported = {
                    val now = System.currentTimeMillis()
                    if (now - lastUnsupported > UNSUPPORTED_REPEAT_MS) {
                        lastUnsupported = now
                        main.post(onUnsupported)
                    }
                },
            )
        }
        try {
            provider.unbindAll()
            onCamera(provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis))
        } catch (e: IllegalArgumentException) {
            Log.w(LOG_TAG, "no back camera to bind: ${e.message}")
        }
    }
    DisposableEffect(Unit) {
        onDispose { bound?.unbindAll() }
    }
}

@Composable
private fun RoundButton(icon: Int, label: String, active: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (active) Color.White else Color.Black.copy(alpha = 0.4f),
        modifier = Modifier.size(52.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = label, tint = if (active) Color.Black else Color.White)
        }
    }
}

@Composable
private fun SourceButton(icon: Int, label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = 0.16f), modifier = modifier) {
        Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painterResource(icon), contentDescription = null, tint = Color.White)
            Spacer(Modifier.height(4.dp))
            Text(label, color = Color.White, fontSize = 12.sp)
        }
    }
}

/** Most screenshots one import takes, for example when moving cards over from Google Wallet. */
const val MAX_SCREENSHOTS = 30

/** File types offered by the file picker: PDFs and Apple Wallet passes, which some apps label generically. */
val IMPORT_TYPES = arrayOf(
    "application/pdf",
    "application/vnd.apple.pkpass",
    "application/vnd-com.apple.pkpass",
    "application/zip",
    "application/octet-stream",
)

private val ANALYSIS_SIZE = Size(1280, 720)
private const val UNSUPPORTED_REPEAT_MS = 4_000L
private val analysisExecutor = Executors.newSingleThreadExecutor()
