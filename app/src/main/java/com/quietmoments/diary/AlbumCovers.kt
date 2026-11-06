package com.quietmoments.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** An album is a collection of memories; this is only its visual cover. */
@Composable
fun AlbumCoverCard(
    repo: LocalMediaRepository,
    name: String,
    count: Int,
    previewMoment: LocalMoment?,
    coverId: String,
    selected: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit
) {
    val art = DiaryThemes.byId(coverId)
    Card(
        onClick = onOpen,
        modifier = Modifier.width(184.dp).height(196.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = art.heroStart),
        border = if (selected) androidx.compose.foundation.BorderStroke(
            2.dp, MaterialTheme.colorScheme.primary
        ) else null
    ) {
        Box(Modifier.fillMaxSize()) {
            IllustratedThemeBackground(art, Modifier.matchParentSize(), alpha = 1f)
            Box(Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    listOf(Color(0x180C1020), Color(0x6A141024), Color(0xE31B1428))
                )))
            DecorativeFrame(art, Modifier.matchParentSize())
            if (previewMoment != null) {
                Box(Modifier.align(Alignment.TopStart).padding(12.dp)
                    .clip(RoundedCornerShape(14.dp))) {
                    MomentThumbnail(repo, previewMoment, Modifier.size(76.dp))
                }
            } else {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null,
                    modifier = Modifier.align(Alignment.TopStart).padding(19.dp).size(33.dp),
                    tint = art.accent)
            }
            IconButton(
                onClick = onEdit,
                modifier = Modifier.align(Alignment.TopEnd).padding(9.dp)
                    .size(38.dp).clip(RoundedCornerShape(19.dp))
                    .background(Color(0xD92B2038))
            ) {
                Icon(Icons.Default.Edit, contentDescription = "Изменить обложку альбома " + name,
                    modifier = Modifier.size(18.dp), tint = Color.White)
            }
            Column(
                Modifier.align(Alignment.BottomStart).padding(horizontal = 16.dp, vertical = 15.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(name, color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                Text(count.toString() + " фото и видео",
                    color = Color(0xFFECE1F3),
                    style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** Entirely offline artwork collection; no new albums or media are created. */
@Composable
fun AlbumCoverPicker(
    albumName: String,
    currentStyle: String,
    onChoose: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Обложка: " + albumName, maxLines = 2) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Text("Выбери рисунок для альбома. Фотографии и видео останутся без изменений.",
                    style = MaterialTheme.typography.bodySmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)) {
                    items(DiaryThemes.all, key = { it.id }) { art ->
                        Card(
                            onClick = { onChoose(art.id) },
                            modifier = Modifier.width(128.dp).height(190.dp),
                            border = if (currentStyle == art.id)
                                androidx.compose.foundation.BorderStroke(2.dp,
                                    MaterialTheme.colorScheme.primary)
                            else null,
                            shape = RoundedCornerShape(15.dp),
                            colors = CardDefaults.cardColors(containerColor = art.heroStart)
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                IllustratedThemeBackground(
                                    palette = art,
                                    modifier = Modifier.fillMaxSize(),
                                    preview = true
                                )
                                Box(Modifier.fillMaxSize().background(
                                    Brush.verticalGradient(listOf(Color.Transparent,
                                        Color(0xCB1B1429)))
                                ))
                                DecorativeFrame(art, Modifier.matchParentSize(), subtle = true)
                                Text(art.name, Modifier.align(Alignment.BottomStart).padding(11.dp),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } }
    )
}
