package io.github.jsnjack.wallet.ui

import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * The pickers for Google Wallet screenshots and for pass or PDF files.
 * A picker's result reaches only a screen that is still shown when the
 * picker closes, so remember them in the screen, not in a sheet that closes
 * as the picker opens.
 */
class ImportPickers(
    private val screenshots: ManagedActivityResultLauncher<PickVisualMediaRequest, List<Uri>>,
    private val files: ManagedActivityResultLauncher<Array<String>, Uri?>,
) {
    /** Opens the photo picker for one or more screenshots. */
    fun pickScreenshots() = screenshots.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    /** Opens the file picker for a .pkpass, a PDF or an image. */
    fun pickFile() = files.launch(IMPORT_TYPES)
}

/** Pickers whose results go to [vm]'s imports. */
@Composable
fun rememberImportPickers(vm: WalletViewModel): ImportPickers {
    val screenshots = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_SCREENSHOTS)) { uris ->
        if (uris.isNotEmpty()) vm.importScreenshots(uris, fromShare = false)
    }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFile(uri, fromShare = false)
    }
    return remember(screenshots, files) { ImportPickers(screenshots, files) }
}
