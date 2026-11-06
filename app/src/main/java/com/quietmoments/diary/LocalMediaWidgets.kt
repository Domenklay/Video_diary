package com.quietmoments.diary

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val thumbCache = object : LruCache<String, Bitmap>(24) {}

@Composable
fun MomentThumbnail(repo: LocalMediaRepository, moment: LocalMoment, modifier: Modifier = Modifier) {
    val file = remember(moment.id) { repo.thumbnailFile(moment) }
    val key = remember(file) { "${file.absolutePath}:${file.lastModified()}:${file.length()}" }
    val bmp by produceState<Bitmap?>(initialValue = thumbCache.get(key), key1 = key) {
        if (value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bitmap = if (moment.kind == "video") {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(file.absolutePath)
                        if (Build.VERSION.SDK_INT >= 27) retriever.getScaledFrameAtTime(
                            -1L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 256, 256)
                        else retriever.getFrameAtTime(-1L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    } finally { retriever.release() }
                } else {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.absolutePath, options)
                    val ratio = (maxOf(options.outWidth, options.outHeight) / 256).coerceAtLeast(1)
                    BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = ratio })
                }
                bitmap?.let { Bitmap.createScaledBitmap(it, 192, 192, true) }
            }.getOrNull()?.also { thumbCache.put(key, it) }
        }
    }
    Box(modifier.size(76.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center) {
        if (bmp != null) Image(bmp!!.asImageBitmap(), contentDescription = null,
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(if (moment.kind == "video") Icons.Filled.PlayCircle else Icons.Filled.ImageIcon,
            contentDescription = null)
        if (moment.kind == "video" && bmp != null)
            Icon(Icons.Filled.PlayCircle, contentDescription = "Видео",
                tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(26.dp))
    }
}
