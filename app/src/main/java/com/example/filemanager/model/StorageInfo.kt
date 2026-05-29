package com.example.filemanager.model

data class StorageInfo(
    val name: String,
    val path: String,
    val totalBytes: Long,
    val usedBytes: Long,
    val isSdCard: Boolean
) {
    val freeBytes: Long get() = totalBytes - usedBytes
    val usagePercent: Float get() = if (totalBytes > 0) usedBytes.toFloat() / totalBytes else 0f
}

