package com.example.peluqueria.freshcut

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(state: FreshCutState, navigate: (String) -> Unit, selectCut: (Int) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Photo(0, Modifier.size(47.dp).clip(CircleShape).clickable { navigate("profile") })
            Column(Modifier.weight(1f).padding(start = 12.dp)) { Text("Hola, ${state.name.substringBefore(' ')}", fontSize = 20.sp, fontWeight = FontWeight.Bold); Text("Hoy es un buen día para un buen corte.", color = Muted, fontSize = 11.sp) }
            IconAction("bell", "Notificaciones", { navigate("notifications") })
        }
        Surface(onClick = { navigate("cuts") }, color = Color.White, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Line)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { Glyph("search", Muted, 20.dp); Text("Busca tu próximo estilo…", color = Muted, fontSize = 13.sp) }
        }
        Box(Modifier.fillMaxWidth().height(208.dp).clip(Shape).background(Navy).clickable { navigate("cuts") }) {
            Photo(0, Modifier.fillMaxHeight().fillMaxWidth(.63f).align(Alignment.CenterEnd))
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Navy, Navy.copy(alpha = .93f), Color.Transparent))))
            Column(Modifier.padding(22.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                Text("HECHO PARA TI", color = Color(0xFFC9D9EF), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
                Text("Encuentra tu\nestilo ideal", fontSize = 27.sp, lineHeight = 29.sp, color = Color.White, fontWeight = FontWeight.ExtraBold, letterSpacing = (-.8).sp)
                Text("Inspírate. Elige. Renueva.", fontSize = 12.sp, color = Color.White.copy(alpha = .8f))
                Surface(color = Color.White, shape = RoundedCornerShape(9.dp)) { Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text("Explorar cortes", color = Navy, fontSize = 12.sp, fontWeight = FontWeight.Bold); Glyph("arrow", Navy, 16.dp) } }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(Triple("cut", "Cortes", "cuts"), Triple("calendar", "Citas", "appointments"), Triple("heart", "Favoritos", "favorites"), Triple("user", "Barberos", "barbers")).forEach { (icon, title, route) ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(15.dp)).background(Color.White).clickable { navigate(route) }.padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) { Glyph(icon, if (icon == "heart") Red else Navy, 23.dp); Text(title, fontSize = 11.sp, fontWeight = FontWeight.Medium) }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Heading("Los favoritos de todos", "Un poco de inspiración para tu próxima visita", "Ver más", { navigate("popular") })
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                cuts.take(4).forEach { cut -> Column(Modifier.width(137.dp).clickable { selectCut(cut.id) }) { Photo(cut.photo, Modifier.fillMaxWidth().height(145.dp).clip(RoundedCornerShape(14.dp)), cut.name); Spacer(Modifier.height(9.dp)); Text(cut.name, fontWeight = FontWeight.Bold, fontSize = 13.sp); Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) { Glyph("heart", Red, 12.dp, true); Text(cut.likes, color = Muted, fontSize = 11.sp) } } }
            }
        }
        CardSurface(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(46.dp).background(SoftBlue, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Glyph("calendar", Navy) }
                Column(Modifier.weight(1f)) { Text("Tu momento de desconexión", fontSize = 15.sp, fontWeight = FontWeight.Bold); Text("Elige el día. Nosotros ponemos el estilo.", fontSize = 12.sp, color = Muted) }
            }
            Spacer(Modifier.height(14.dp)); PrimaryButton("Agendar mi cita", { navigate("booking") }, red = true)
        }
        Spacer(Modifier.height(8.dp))
    }
}
@Composable
fun CatalogScreen(state: FreshCutState, favoritesOnly: Boolean, selectCut: (Int) -> Unit, back: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("Todos") }
    val filtered = cuts.filter { (!favoritesOnly || it.id in state.favorites) && (category == "Todos" || it.category == category || (category == "Taper" && it.name.contains("Taper"))) && it.name.contains(query.trim(), true) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (favoritesOnly) "Mis favoritos" else "Encuentra tu estilo", if (favoritesOnly) back else null)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (favoritesOnly) "Los cortes que van contigo." else "Un corte para cada versión de ti.", color = Muted, fontSize = 13.sp)
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Buscar corte…", fontSize = 14.sp) }, leadingIcon = { Glyph("search", Muted) }, trailingIcon = if (query.isNotEmpty()) { { IconAction("close", "Limpiar búsqueda", { query = "" }) } } else null, singleLine = true, shape = RoundedCornerShape(14.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Line))
            ChipRow(listOf("Todos", "Fade", "Taper", "Clásicos", "Modernos"), category, { category = it })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("${filtered.size} estilos para ti", color = Muted, fontSize = 12.sp); Text("COLECCIÓN FRESHCUT", color = Navy, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold) }
            if (filtered.isEmpty()) EmptyState(if (favoritesOnly && state.favorites.isEmpty()) "Tu estilo empieza aquí" else "Sin coincidencias", if (favoritesOnly && state.favorites.isEmpty()) "Toca el corazón de un corte para guardarlo aquí." else "Prueba con otro nombre o categoría.", "heart")
            filtered.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    pair.forEach { cut ->
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Color.White).clickable { selectCut(cut.id) }) {
                            Box { Photo(cut.photo, Modifier.fillMaxWidth().aspectRatio(.88f), cut.name); Box(Modifier.align(Alignment.TopEnd).padding(5.dp).background(Color.Black.copy(alpha = .18f), CircleShape)) { IconAction("heart", if (cut.id in state.favorites) "Quitar ${cut.name} de favoritos" else "Guardar ${cut.name}", { state.toggleFavorite(cut.id) }, Color.White, cut.id in state.favorites) } }
                            Column(Modifier.padding(12.dp)) { Text(cut.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(cut.category, color = Muted, fontSize = 11.sp) }
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}
@Composable
fun DetailScreen(cut: Cut, state: FreshCutState, back: () -> Unit, book: () -> Unit, selectCut: (Int) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(330.dp)) {
                Photo(cut.photo, Modifier.fillMaxSize(), cut.name)
                Box(Modifier.fillMaxWidth().height(100.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .4f), Color.Transparent))))
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) { IconAction("back", "Volver", back, Color.White); IconAction("heart", "Cambiar favorito", { state.toggleFavorite(cut.id) }, Color.White, cut.id in state.favorites) }
                Surface(Modifier.align(Alignment.BottomStart).padding(20.dp), color = Red, shape = RoundedCornerShape(8.dp)) { Text("EL ESTILO DEL MOMENTO", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
            }
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Heading(cut.name, "Precisión en cada detalle")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(color = SoftBlue, shape = RoundedCornerShape(8.dp)) { Text(cut.category, Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = Navy, fontSize = 12.sp) }
                    Surface(color = Color(0xFFFFEEEE), shape = RoundedCornerShape(8.dp)) { Text("Popular · ${cut.likes}", Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = Red, fontSize = 12.sp) }
                }
                Text(cut.description, color = Muted, fontSize = 14.sp, lineHeight = 23.sp)
                Row(Modifier.fillMaxWidth().background(Color.White, Shape).padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) { Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { Glyph("clock", Muted, 18.dp); Text("30 min aprox.", fontSize = 13.sp) }; Text("Desde $150 MXN", color = Navy, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                Heading("Más inspiración")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { cuts.filter { it.id != cut.id }.take(3).forEach { example -> Column(Modifier.weight(1f).clickable { selectCut(example.id) }) { Photo(example.photo, Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(12.dp))); Text(example.name, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp)) } } }
            }
        }
        Box(Modifier.background(Color.White).padding(20.dp)) { PrimaryButton("Agendar este corte", book, icon = "calendar") }
    }
}
@Composable
fun PopularScreen(state: FreshCutState, selectCut: (Int) -> Unit, message: (String) -> Unit, savedOnly: Boolean = false, back: () -> Unit = {}) {
    var filter by rememberSaveable { mutableStateOf("Tendencia") }
    var commentPost by remember { mutableStateOf<Int?>(null) }
    var comment by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (savedOnly) "Guardados" else "Populares", if (savedOnly) back else null, action = { Glyph("flame", Red, 27.dp); Spacer(Modifier.width(18.dp)) })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!savedOnly) ChipRow(listOf("Tendencia", "Recientes", "Más gustados"), filter, { filter = it })
            val order = when (filter) { "Recientes" -> listOf(3, 2, 0); "Más gustados" -> listOf(0, 3, 2); else -> listOf(0, 2, 3) }.filter { !savedOnly || it in state.savedPosts }
            if (order.isEmpty()) EmptyState("Guarda tu inspiración", "Usa el marcador de una publicación para encontrarla aquí.", "bookmark")
            order.forEachIndexed { index, id ->
                CardSurface(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Photo(barbers[index].photo, Modifier.size(36.dp).clip(CircleShape)); Column(Modifier.weight(1f).padding(start = 10.dp)) { Text(barbers[index].name, fontSize = 13.sp, fontWeight = FontWeight.Bold); Text("Hace ${index + 1} días · FreshCut", color = Muted, fontSize = 10.sp) }; Glyph("cut", Navy, 19.dp) }
                    Spacer(Modifier.height(13.dp)); Photo(cuts[id].photo, Modifier.fillMaxWidth().height(255.dp).clip(RoundedCornerShape(12.dp)).clickable { selectCut(id) }, cuts[id].name)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconAction("heart", "Me gusta", { state.togglePost(id) }, Red, id in state.likedPosts); Text("${listOf(428, 312, 286)[index] + if (id in state.likedPosts) 1 else 0}", fontSize = 12.sp)
                        IconAction("comment", "Comentar", { commentPost = id }); Text("${32 + index * 8}", fontSize = 12.sp)
                        Spacer(Modifier.weight(1f)); IconAction("bookmark", "Guardar publicación", { state.togglePost(id, true) }, Navy, id in state.savedPosts)
                    }
                    Text(cuts[id].name + " · Textura y actitud", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Detalles que hacen la diferencia. Encuentra tu próxima versión. #FreshCut #${cuts[id].category}", color = Muted, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    if (commentPost != null) AlertDialog(onDismissRequest = { commentPost = null }, title = { Text("Deja tu comentario") }, text = { Column { Text("Vista de demostración: tu comentario no se publicará en una red.", color = Muted, fontSize = 12.sp); OutlinedTextField(comment, { comment = it }, label = { Text("Tu comentario") }) } }, confirmButton = { TextButton({ message("Comentario de demostración recibido: ${comment.trim()}"); comment = ""; commentPost = null }, enabled = comment.isNotBlank()) { Text("Comentar") } }, dismissButton = { TextButton({ commentPost = null }) { Text("Cancelar") } })
}
