package com.quietmoments.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val Night = Color(0xFF231B33)
private val Violet = Color(0xFF4F3A67)
private val Gold = Color(0xFFE4C78F)

/** Reusable high-contrast illustrated-feel surfaces, without network images. */
@Composable
fun UniverseBanner(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalDiaryPalette.current
    Card(onClick = onOpen, modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = palette.heroStart)) {
        Box(Modifier.background(Brush.linearGradient(listOf(palette.heroStart, palette.heroEnd))).fillMaxWidth()) {
            IllustratedThemeBackground(palette, modifier = Modifier.matchParentSize(), alpha = 0.85f)
            Box(Modifier.matchParentSize().background(
                Brush.verticalGradient(listOf(Color(0x66201530), Color(0xDD1B1630)))
            ))
            Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.AutoAwesome, null, tint = palette.accent,
                        modifier = Modifier.size(28.dp))
                    Text("МОЯ ВСЕЛЕННАЯ", color = palette.accent,
                        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
                Text("Сохрани магию каждого дня", color = Color.White,
                    style = MaterialTheme.typography.titleLarge)
                Text("Гороскопы · Луна · Карты · Сны", color = Color(0xFFECE3F4),
                    style = MaterialTheme.typography.bodyMedium)
                Text("Открыть раздел  ›", color = palette.accent,
                    style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun LocalDiaryHeading(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun DiarySectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}


/** Decorative, readable headers for the media and calendar sections. */
@Composable
fun IllustratedSectionHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val palette = LocalDiaryPalette.current
    Box(
        modifier = modifier.fillMaxWidth().height(if (compact) 100.dp else 162.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(palette.heroStart, palette.heroEnd)))
    ) {
        IllustratedThemeBackground(palette, Modifier.matchParentSize(), alpha = 0.94f)
        Box(
            Modifier.matchParentSize().background(
                Brush.horizontalGradient(listOf(
                    Color(0xEE181323), Color(0xA8241A32), Color(0x27181225)))
            )
        )
        Column(
            modifier = Modifier.align(Alignment.CenterStart).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(title, style = if (compact) MaterialTheme.typography.titleLarge
                else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = Color.White)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFF7EADB), maxLines = 2)
        }
    }
}
