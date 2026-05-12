package io.aequicor.pdf

import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController

fun makeIosDocumentPicker(
    onBytes: (ByteArray) -> Unit,
    onCancel: () -> Unit,
): UIViewController {
    val picker = UIDocumentPickerViewController(
        forOpeningContentTypes = listOf(platform.UniformTypeIdentifiers.UTType.PDF),
    )
    // Delegate wiring is done in MainViewController via UIDocumentPickerDelegate
    return picker
}
