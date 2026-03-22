package com.fasonbot.app.action

data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0,
    val isParent: Boolean = false
) {
    val icon: String
        get() = when {
            isParent -> "⬆️"
            isDirectory -> "📁"
            else -> "📄"
        }

    val displaySize: String
        get() = when {
            isParent -> ""
            isDirectory -> ""
            else -> formatSize(size)
        }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / 1024 / 1024}MB"
        else -> "${bytes / 1024 / 1024 / 1024}GB"
    }
}
