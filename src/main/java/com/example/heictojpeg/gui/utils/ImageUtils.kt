package com.example.heictojpeg.gui.utils

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.InputStream

object ImageUtils {
    
    /**
     * Get the dimensions of an image from its URI
     */
    fun getImageDimensions(context: Context, uri: Uri): Pair<Int, Int>? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStream, null, options)
                Pair(options.outWidth, options.outHeight)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Check if a file is a valid HEIC/HEIF image
     */
    fun isHeicFile(fileName: String): Boolean {
        return fileName.lowercase().let { 
            it.endsWith(".heic") || it.endsWith(".heif") 
        }
    }
    
    /**
     * Calculate aspect ratio preserving dimensions
     */
    fun calculateAspectRatioDimensions(
        originalWidth: Int, 
        originalHeight: Int, 
        newWidth: Int?, 
        newHeight: Int?
    ): Pair<Int, Int> {
        val aspectRatio = originalWidth.toFloat() / originalHeight.toFloat()
        
        return when {
            newWidth != null && newHeight == null -> {
                Pair(newWidth, (newWidth / aspectRatio).toInt())
            }
            newWidth == null && newHeight != null -> {
                Pair((newHeight * aspectRatio).toInt(), newHeight)
            }
            else -> Pair(originalWidth, originalHeight)
        }
    }
    
    /**
     * Format file size for display
     */
    fun formatFileSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1.0 -> String.format("%.1f MB", mb)
            kb >= 1.0 -> String.format("%.1f KB", kb)
            else -> "$bytes bytes"
        }
    }
}
