package com.example.filemanager.model

data class NetworkItem(
    val name: String,
    val path: String,       // full path on the server
    val isDirectory: Boolean,
    val size: Long = 0L,
    val lastModified: Long = 0L,
    val extension: String = ""
) {
    companion object {
        fun from(name: String, parentPath: String, isDir: Boolean, size: Long = 0L, modified: Long = 0L): NetworkItem {
            val sep = if (parentPath.endsWith("/")) "" else "/"
            val fullPath = "$parentPath$sep$name"
            return NetworkItem(
                name = name,
                path = fullPath,
                isDirectory = isDir,
                size = size,
                lastModified = modified,
                extension = if (isDir) "" else name.substringAfterLast(".", "").lowercase()
            )
        }
    }
}

