package com.quietmoments.diary

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** All themes are bundled with the app, no network or user media required. */
data class DiaryPalette(
    val id: String,
    val name: String,
    val description: String,
    val symbol: String,
    val dark: Boolean,
    val primary: Color,
    val page: Color,
    val surface: Color,
    val text: Color,
    val muted: Color,
    val soft: Color,
    val secondarySoft: Color,
    val accent: Color,
    val heroStart: Color,
    val heroEnd: Color,
    val heroText: Color = Color.White
) {
    fun scheme(): ColorScheme {
        val onPrimary = if (dark) Color(0xFF0E1720) else Color.White
        return if (dark) darkColorScheme(
            primary = accent, onPrimary = Color(0xFF121622),
            primaryContainer = soft, onPrimaryContainer = text,
            secondary = accent, secondaryContainer = secondarySoft,
            background = page, onBackground = text,
            surface = surface, onSurface = text, onSurfaceVariant = muted,
            outline = muted, surfaceVariant = soft
        ) else lightColorScheme(
            primary = primary, onPrimary = onPrimary,
            primaryContainer = soft, onPrimaryContainer = text,
            secondary = primary, secondaryContainer = secondarySoft,
            background = page, onBackground = text,
            surface = surface, onSurface = text, onSurfaceVariant = muted,
            outline = muted, surfaceVariant = soft
        )
    }
}

object DiaryThemes {
    val all = listOf(
        DiaryPalette(
            "moon", "Лунный бархат", "Лаванда, созвездия и золотые звёзды", "☾", false,
            Color(0xFF4A2B65), Color(0xFFFAF4FC), Color(0xFFFFFBFF),
            Color(0xFF302339), Color(0xFF6B5D73), Color(0xFFEDE2F6),
            Color(0xFFF8E4F2), Color(0xFFE8C787),
            Color(0xFF392553), Color(0xFF785A91)
        ),
        DiaryPalette(
            "rose", "Розовый сад", "Нежные цветы и розовое золото", "❀", false,
            Color(0xFF853E65), Color(0xFFFFF4F6), Color(0xFFFFFCFA),
            Color(0xFF4A283A), Color(0xFF765B67), Color(0xFFF9DDE8),
            Color(0xFFFFE8E5), Color(0xFFF1C59E),
            Color(0xFF78405F), Color(0xFFC17D9B)
        ),
        DiaryPalette(
            "forest", "Лесная сказка", "Изумрудные листья и светлый шалфей", "❧", false,
            Color(0xFF275A46), Color(0xFFF1F8F2), Color(0xFFFBFFF9),
            Color(0xFF203D30), Color(0xFF607568), Color(0xFFDBEDE0),
            Color(0xFFE9F3D9), Color(0xFFE3D398),
            Color(0xFF174D42), Color(0xFF4F8665)
        ),
        DiaryPalette(
            "sea", "Морская акварель", "Бирюза, волны и жемчужный свет", "≈", false,
            Color(0xFF255A73), Color(0xFFF0F9FC), Color(0xFFFCFEFF),
            Color(0xFF1E3A4C), Color(0xFF5E7987), Color(0xFFD9EEF5),
            Color(0xFFE2F7F2), Color(0xFFE7D4AA),
            Color(0xFF235C78), Color(0xFF5BA5AE)
        ),
        DiaryPalette(
            "dawn", "Медовый рассвет", "Тёплая охра, солнце и полевые цветы", "☼", false,
            Color(0xFF8A4D2F), Color(0xFFFFF8EB), Color(0xFFFFFDF5),
            Color(0xFF533C2D), Color(0xFF866E5B), Color(0xFFF9E8C9),
            Color(0xFFF7DFCF), Color(0xFFFFDB87),
            Color(0xFF9D543E), Color(0xFFD3A365)
        ),
        DiaryPalette(
            "night", "Ночной океан", "Тёмное небо, серебро и светящиеся звёзды", "✦", true,
            Color(0xFF94D5DB), Color(0xFF101D2A), Color(0xFF1C2E3D),
            Color(0xFFF2F7F9), Color(0xFFB8CDD9), Color(0xFF2A4555),
            Color(0xFF233E50), Color(0xFFB9E6DB),
            Color(0xFF0D223F), Color(0xFF24596A)
        )
    )
    fun byId(id: String?): DiaryPalette = all.firstOrNull { it.id == id } ?: all.first()
    fun load(context: Context): String = context.getSharedPreferences("diary_theme", Context.MODE_PRIVATE)
        .getString("chosen", "moon") ?: "moon"
    fun save(context: Context, id: String) {
        require(all.any { it.id == id })
        context.getSharedPreferences("diary_theme", Context.MODE_PRIVATE)
            .edit().putString("chosen", id).apply()
    }
}

val LocalDiaryPalette = staticCompositionLocalOf { DiaryThemes.all.first() }

/** Detailed illustration bundled in assets. Decode thumbnails at reduced resolution to save RAM. */
@Composable
fun IllustratedThemeBackground(
    palette: DiaryPalette,
    modifier: Modifier = Modifier,
    preview: Boolean = false,
    alpha: Float = 1f,
    alignment: Alignment = Alignment.TopCenter
) {
    val context = LocalContext.current
    val illustration: ImageBitmap? = remember(context, palette.id, preview) {
        runCatching {
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inSampleSize = if (preview) 4 else 1
            }
            context.assets.open("universe/themes/${palette.id}.webp").use { stream ->
                BitmapFactory.decodeStream(stream, null, options)?.asImageBitmap()
            }
        }.getOrNull()
    }
    if (illustration != null) {
        Image(
            bitmap = illustration,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
            alignment = alignment,
            alpha = alpha
        )
    } else {
        ThemeOrnament(palette, modifier)
    }
}


/** Vector decorations remain sharp on any density and are different for each theme. */
@Composable
fun ThemeOrnament(palette: DiaryPalette, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val c = palette.accent.copy(alpha = 0.55f)
        val faint = palette.accent.copy(alpha = 0.22f)
        when (palette.id) {
            "rose", "forest", "dawn" -> {
                for (i in 0..5) {
                    val x = w * (0.64f + i * .062f)
                    val stem = Path().apply {
                        moveTo(x, h * .96f)
                        cubicTo(x - w * .13f, h * .65f, x + w * .09f, h * .38f, x, h * .12f)
                    }
                    drawPath(stem, faint, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
                    for (j in 0..3) {
                        val yy = h * (.20f + j * .17f)
                        val leaf = Path().apply {
                            moveTo(x, yy)
                            quadraticTo(x + w * .085f, yy - h * .11f, x + w * .10f, yy - h * .03f)
                            quadraticTo(x + w * .07f, yy + h * .065f, x, yy)
                        }
                        drawPath(leaf, c.copy(alpha = if (palette.id == "rose") .21f else .28f))
                    }
                }
                if (palette.id == "dawn") {
                    drawCircle(palette.accent.copy(alpha=.22f), w*.19f,
                        Offset(w*.8f,h*.24f))
                }
                if (palette.id == "rose") {
                    for (i in 0..3) {
                        val center = Offset(w * (.72f + .062f * i), h * (.23f + .14f * (i%2)))
                        for (j in 0..5) {
                            val a = (j*PI/3).toFloat()
                            drawCircle(c.copy(alpha=.42f), w*.028f,
                                Offset(center.x+cos(a)*w*.028f,center.y+sin(a)*w*.028f))
                        }
                        drawCircle(palette.accent.copy(alpha=.7f), w*.018f,center)
                    }
                }
            }
            "sea" -> {
                for(i in 0..7) {
                    val y = h * (.16f + .11f * i)
                    val wave = Path().apply {
                        moveTo(w * .48f,y)
                        cubicTo(w*.66f, y-h*.13f, w*.78f,y+h*.13f,w,y-h*.04f)
                    }
                    drawPath(wave, faint, style=Stroke(2.6f,cap=StrokeCap.Round))
                }
                for(i in 0..14) {
                    val x=w*(.52f+(i%5)*.11f)
                    val y=h*(.13f+(i*7%13)*.062f)
                    drawCircle(c.copy(alpha=.23f),w*(.008f+(i%3)*.005f), Offset(x,y))
                }
            }
            else -> {
                val moon = Offset(w*.80f,h*.32f)
                drawCircle(c.copy(alpha=.38f),w*.13f,moon)
                drawCircle(palette.heroEnd,w*.105f,Offset(moon.x+w*.055f, moon.y-h*.035f))
                for(i in 0..27) {
                    val x=w*(.12f+(i*17%83)/100f)
                    val y=h*(.08f+(i*29%87)/100f)
                    val r=if(i%5==0)w*.009f else w*.0035f
                    drawCircle(c.copy(alpha=if(i%3==0).9f else .52f),r,Offset(x,y))
                    if(i%7==0){
                        drawLine(faint,Offset(x-r*2.4f,y),Offset(x+r*2.4f,y),1.6f)
                        drawLine(faint,Offset(x,y-r*2.4f),Offset(x,y+r*2.4f),1.6f)
                    }
                }
                if (palette.id=="night"){
                    val stars = listOf(Offset(w*.61f,h*.56f),Offset(w*.73f,h*.46f),
                        Offset(w*.86f,h*.61f),Offset(w*.92f,h*.42f))
                    for(i in 0 until stars.lastIndex) {
                        drawLine(faint,stars[i],stars[i+1],2f)
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeGallery(selectedId: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Темы оформления", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold)
        Text("Рисунки, узоры и цвета меняются во всём приложении.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        DiaryThemes.all.chunked(2).forEach { pair ->
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                pair.forEach { option ->
                    val checked = selectedId == option.id
                    Card(
                        modifier=Modifier.weight(1f).clickable { onSelect(option.id) },
                        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),
                        shape=RoundedCornerShape(20.dp),
                        border = if(checked) androidx.compose.foundation.BorderStroke(
                            2.dp, MaterialTheme.colorScheme.primary
                        ) else null
                    ) {
                        Column(Modifier.fillMaxWidth().padding(8.dp),
                            verticalArrangement=Arrangement.spacedBy(7.dp)) {
                            Box(Modifier.fillMaxWidth().height(104.dp)
                                .clip(RoundedCornerShape(15.dp))
                                .background(Brush.linearGradient(listOf(option.heroStart,option.heroEnd)))) {
                                IllustratedThemeBackground(option, Modifier.fillMaxSize(), preview = true)
                                Box(Modifier.fillMaxSize().background(
                                    Brush.verticalGradient(listOf(Color(0x22000000), Color(0x66000000)))
                                ))
                                Text(option.symbol, modifier=Modifier.align(Alignment.CenterStart)
                                    .padding(start=16.dp), color=option.accent,
                                    style=MaterialTheme.typography.headlineLarge)
                                if(checked) Icon(Icons.Default.CheckCircle,"Выбрано",
                                    modifier=Modifier.align(Alignment.TopEnd).padding(8.dp),
                                    tint=Color.White)
                            }
                            Text(option.name, style=MaterialTheme.typography.labelLarge,
                                fontWeight=FontWeight.Bold)
                            Text(option.description, style=MaterialTheme.typography.labelSmall,
                                color=MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines=2)
                        }
                    }
                }
                if(pair.size==1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
