package com.example.filemanager.model

data class DriveItem(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: Long = 0L,
    val modifiedTime: String = "",
    val parents: List<String> = emptyList(),
    val webViewLink: String = "",
    val thumbnailLink: String = ""
) {
    val isFolder: Boolean get() = mimeType == "application/vnd.google-apps.folder"
    val isGoogleDoc: Boolean get() = mimeType.startsWith("application/vnd.google-apps.")
        && mimeType != "application/vnd.google-apps.folder"

    val extension: String get() = when {
        isFolder -> ""
        isGoogleDoc -> when (mimeType) {
            "application/vnd.google-apps.document" -> "gdoc"
            "application/vnd.google-apps.spreadsheet" -> "gsheet"
            "application/vnd.google-apps.presentation" -> "gslide"
            else -> "gdrive"
        }
        else -> name.substringAfterLast(".", "")
    }

    // MIME type để export Google Docs
    val exportMimeType: String get() = when (mimeType) {
        "application/vnd.google-apps.document" ->
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "application/vnd.google-apps.spreadsheet" ->
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "application/vnd.google-apps.presentation" ->
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        else -> mimeType
    }

    val exportFileName: String get() = when (mimeType) {
        "application/vnd.google-apps.document" -> "$name.docx"
        "application/vnd.google-apps.spreadsheet" -> "$name.xlsx"
        "application/vnd.google-apps.presentation" -> "$name.pptx"
        else -> name
    }
}

