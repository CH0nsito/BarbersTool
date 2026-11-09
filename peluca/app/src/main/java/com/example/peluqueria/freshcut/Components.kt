package com.example.peluqueria.freshcut

import android.graphics.BitmapFactory
import android.content.res.Resources
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.core.graphics.PathParser
import com.example.peluqueria.R

val Navy = Color(0xFF0F2D5C)
val Red = Color(0xFFC62828)
val Ink = Color(0xFF111D30)
val Muted = Color(0xFF737E90)
val Paper = Color(0xFFF4F6F9)
val Line = Color(0xFFE2E6ED)
val SoftBlue = Color(0xFFE8EFF9)
val Shape = RoundedCornerShape(20.dp)

private val iconPaths = mapOf(
    "home" to "M3 10L12 3L21 10M5 9V21H10V15H14V21H19V9",
    "cut" to "M8 8L20 20M8 16L20 4M7 4A3 3 0 1 0 7 10A3 3 0 1 0 7 4M7 14A3 3 0 1 0 7 20A3 3 0 1 0 7 14",
    "calendar" to "M5 5H19Q21 5 21 7V20Q21 22 19 22H5Q3 22 3 20V7Q3 5 5 5M7 2V8M17 2V8M3 11H21M7 15H9M12 15H14M7 18H9M12 18H14",
    "user" to "M12 3A4 4 0 1 0 12 11A4 4 0 1 0 12 3M4 21V19C4 12 20 12 20 19V21Z",
    "heart" to "M12 21L3 12C-2 5 6 0 12 7C18 0 26 5 21 12Z",
    "search" to "M10 3A7 7 0 1 0 10 17A7 7 0 1 0 10 3M15 15L21 21",
    "back" to "M14 5L7 12L14 19M7 12H22",
    "arrow" to "M4 12H20M14 6L20 12L14 18",
    "chevron" to "M9 5L16 12L9 19",
    "bell" to "M5 17H19L17 13V9C17 2 7 2 7 9V13ZM10 21H14M12 2V3",
    "mail" to "M3 5H21V19H3ZM3 6L12 13L21 6",
    "lock" to "M5 10H19V21H5ZM8 10V6C8 0 16 0 16 6V10M12 14V17",
    "eye" to "M2 12C7 3 17 3 22 12C17 21 7 21 2 12M12 9A3 3 0 1 0 12 15A3 3 0 1 0 12 9",
    "flame" to "M12 2C14 9 20 8 20 15C20 25 3 25 4 14C4 10 7 8 8 7C7 14 13 13 12 2Z",
    "star" to "M12 2L15 8L22 9L17 14L18 21L12 18L6 21L7 14L2 9L9 8Z",
    "clock" to "M12 2A10 10 0 1 0 12 22A10 10 0 1 0 12 2M12 6V12L16 14",
    "check" to "M5 12L10 17L20 6",
    "close" to "M5 5L19 19M19 5L5 19",
    "bookmark" to "M6 3H18V22L12 17L6 22Z",
    "comment" to "M21 11C21 22 12 21 8 19L3 21L4 16C-3 1 21 -3 21 11Z",
    "settings" to "M12 8A4 4 0 1 0 12 16A4 4 0 1 0 12 8M9 2H15L16 5L20 6L22 11L20 14L19 19L15 20L12 23L8 20L4 19L3 14L1 11L4 6L8 5Z",
    "card" to "M3 5H21V20H3ZM3 10H21M6 16H10",
    "help" to "M12 2A10 10 0 1 0 12 22A10 10 0 1 0 12 2M9 8C9 4 17 5 15 10L12 13V14M12 17V18",
    "logout" to "M10 3H3V21H10M8 12H22M17 7L22 12L17 17",
    "edit" to "M4 16L16 4L20 8L8 20L3 21ZM14 6L18 10",
    "plus" to "M12 4V20M4 12H20",
    "pin" to "M12 22C9 17 4 13 4 9A8 8 0 0 1 20 9C20 13 15 18 12 22M12 6A3 3 0 1 0 12 12A3 3 0 1 0 12 6"
)
@Composable
fun Glyph(name: String, color: Color = Ink, size: Dp = 22.dp, filled: Boolean = false) {
    val path = remember(name) { PathParser.createPathFromPathData(iconPaths[name] ?: iconPaths.getValue("star"))!!.asComposePath() }
    Canvas(Modifier.size(size)) { scale(this.size.width / 24f, this.size.height / 24f, pivot = Offset.Zero) { drawPath(path, color, style = if (filled) androidx.compose.ui.graphics.drawscope.Fill else Stroke(1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round)) } }
}
@Composable
fun IconAction(icon: String, label: String, onClick: () -> Unit, tint: Color = Ink, filled: Boolean = false, modifier: Modifier = Modifier) {
    IconButton(onClick, modifier.semantics { contentDescription = label }) { Glyph(icon, tint, filled = filled) }
}

private val photoRegions = listOf(
    intArrayOf(789, 228, 997, 394), intArrayOf(1016, 113, 1082, 194),
    intArrayOf(916, 229, 981, 310), intArrayOf(1016, 229, 1082, 310),
    intArrayOf(916, 346, 981, 428), intArrayOf(1016, 346, 1082, 428),
    intArrayOf(277, 790, 310, 825)
)
// Keep one decoded atlas per source, shared by every photo in the composition.
private object PhotoAtlases {
    private val images = mutableMapOf<Int, ImageBitmap>()
    fun get(resources: Resources, source: Int): ImageBitmap = images.getOrPut(source) {
        BitmapFactory.decodeResource(resources, source).asImageBitmap()
    }
}
@Composable
fun Photo(id: Int, modifier: Modifier = Modifier, description: String? = null) {
    val resources = LocalContext.current.resources
    val source = if (id == 0) R.drawable.freshcut_showcase else R.drawable.freshcut_reference
    val bitmap = remember(source) { PhotoAtlases.get(resources, source) }
    val region = photoRegions[id.coerceIn(0, photoRegions.lastIndex)]
    val painter = remember(id, bitmap) { BitmapPainter(bitmap, IntOffset(region[0], region[1]), IntSize(region[2] - region[0], region[3] - region[1]), FilterQuality.High) }
    Image(painter, description, modifier, contentScale = ContentScale.Crop)
}
@Composable
fun Brand(light: Boolean = false, compact: Boolean = false) {
    val color = if (light) Color.White else Navy
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) { Box(Modifier.size(30.dp, 3.dp).background(Red)); Box(Modifier.size(30.dp, 3.dp).background(color)) }
            Glyph("cut", color, if (compact) 34.dp else 48.dp)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) { Box(Modifier.size(30.dp, 3.dp).background(color)); Box(Modifier.size(30.dp, 3.dp).background(Red)) }
        }
        Text("FreshCut", color = color, fontSize = if (compact) 30.sp else 42.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, letterSpacing = (-1.5).sp)
        Text("B A R B E R Í A", color = color, fontSize = if (compact) 10.sp else 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, red: Boolean = false, icon: String? = "arrow", enabled: Boolean = true) {
    Button(onClick, modifier.fillMaxWidth().height(54.dp), enabled, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = if (red) Red else Navy)) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        if (icon != null) { Spacer(Modifier.width(12.dp)); Glyph(icon, Color.White, 19.dp) }
    }
}
@Composable
fun Heading(title: String, subtitle: String? = null, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp); if (subtitle != null) Text(subtitle, color = Muted, fontSize = 13.sp) }
        if (action != null) TextButton(onAction) { Text(action, color = Red, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
    }
}
@Composable
fun ScreenHeader(title: String, back: (() -> Unit)? = null, action: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (back != null) IconAction("back", "Volver", back) else Spacer(Modifier.width(8.dp))
        Text(title, Modifier.weight(1f), fontSize = 23.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp)
        action?.invoke()
    }
}
@Composable
fun ChipRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option -> FilterChip(selected == option, { onSelect(option) }, { Text(option, fontSize = 12.sp, fontWeight = FontWeight.Medium) }, shape = RoundedCornerShape(24.dp), border = null, colors = FilterChipDefaults.filterChipColors(containerColor = Color(0xFFEAEDF2), selectedContainerColor = Red, selectedLabelColor = Color.White)) }
    }
}
@Composable
fun CardSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.shadow(3.dp, Shape, spotColor = Navy.copy(alpha = .09f)).background(Color.White, Shape).border(1.dp, Line.copy(alpha = .6f), Shape).padding(16.dp), content = content)
}
@Composable
fun Note(text: String, icon: String = "help") {
    Row(Modifier.fillMaxWidth().background(SoftBlue, RoundedCornerShape(12.dp)).padding(13.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { Glyph(icon, Navy, 18.dp); Text(text, color = Navy, fontSize = 12.sp, lineHeight = 18.sp) }
}
@Composable
fun EmptyState(title: String, description: String, icon: String, button: String? = null, onClick: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(80.dp).background(SoftBlue, CircleShape), contentAlignment = Alignment.Center) { Glyph(icon, Navy, 34.dp) }
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(description, color = Muted, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (button != null) PrimaryButton(button, onClick)
    }
}
@Composable
fun MenuRow(icon: String, title: String, subtitle: String? = null, onClick: () -> Unit, red: Boolean = false, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 16.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Glyph(icon, if (red) Red else Navy)
        Column(Modifier.weight(1f)) { Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (red) Red else Ink); if (subtitle != null) Text(subtitle, color = Muted, fontSize = 12.sp) }
        if (trailing != null) trailing() else Glyph("chevron", Muted, 16.dp)
    }
}
