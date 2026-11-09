package com.example.peluqueria.freshcut

import android.util.Patterns
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ProfileScreen(state: FreshCutState, navigate: (String) -> Unit, logout: () -> Unit) {
    var confirmLogout by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(196.dp)) {
            Box(Modifier.fillMaxWidth().height(135.dp).background(Navy))
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) { Text("Mi perfil", Modifier.weight(1f), color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold); IconAction("settings", "Editar perfil", { navigate("settings") }, Color.White) }
            Canvas(Modifier.fillMaxWidth().height(20.dp).align(Alignment.Center)) { drawRect(Color.White); for (i in -2..20) { drawLine(if (i % 2 == 0) Red else Navy, Offset(i * 42.dp.toPx(), size.height), Offset((i * 42 + 24).dp.toPx(), 0f), 16.dp.toPx()) } }
            Box(Modifier.align(Alignment.BottomCenter)) { Photo(0, Modifier.size(106.dp).border(4.dp, Color.White, CircleShape).padding(4.dp).clip(CircleShape)); Box(Modifier.align(Alignment.BottomEnd).size(32.dp).clip(CircleShape).background(Red).clickable { navigate("settings") }, contentAlignment = Alignment.Center) { Glyph("edit", Color.White, 16.dp) } }
        }
        Column(Modifier.padding(horizontal = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(state.name, fontSize = 25.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp)); Text(state.email, color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(22.dp))
            CardSurface(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth()) {
                    listOf(Triple("calendar", "Citas", state.appointments.count { !it.cancelled }), Triple("heart", "Favoritos", state.favorites.size), Triple("bookmark", "Guardados", state.savedPosts.size)).forEachIndexed { i, (icon, label, count) ->
                        Column(Modifier.weight(1f).clickable { navigate(listOf("appointments", "favorites", "saved")[i]) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) { Glyph(icon, if (i == 1) Red else Navy); Text("$count", fontSize = 19.sp, fontWeight = FontWeight.Bold); Text(label, color = Muted, fontSize = 11.sp) }
                        if (i < 2) VerticalDivider(Modifier.height(65.dp), color = Line)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            CardSurface(Modifier.fillMaxWidth()) {
                listOf(Triple("calendar", "Mis citas", "appointments"), Triple("heart", "Mis favoritos", "favorites"), Triple("edit", "Mis publicaciones", "publications"), Triple("bell", "Notificaciones", "notifications"), Triple("card", "Métodos de pago", "payments"), Triple("help", "Ayuda y soporte", "support")).forEachIndexed { index, (icon, label, route) -> MenuRow(icon, label, onClick = { navigate(route) }); if (index < 5) HorizontalDivider(color = Line) }
            }
            MenuRow("logout", "Cerrar sesión", onClick = { confirmLogout = true }, red = true)
            Text("FRESHCUT · TU ESTILO, A TU MANERA", color = Muted, fontSize = 9.sp, letterSpacing = 1.2.sp, modifier = Modifier.padding(bottom = 22.dp))
        }
    }
    if (confirmLogout) AlertDialog(onDismissRequest = { confirmLogout = false }, title = { Text("¿Cerrar sesión?") }, text = { Text("Tus favoritos y citas de demostración seguirán guardados en este teléfono.") }, confirmButton = { TextButton(logout) { Text("Cerrar sesión", color = Red) } }, dismissButton = { TextButton({ confirmLogout = false }) { Text("Volver") } })
}
@Composable
fun BarbersScreen(select: (Int) -> Unit, back: () -> Unit) {
    Column {
        ScreenHeader("Nuestros barberos", back)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("Buenas manos. Grandes resultados.", color = Muted, fontSize = 14.sp)
            barbers.forEachIndexed { i, barber ->
                CardSurface(Modifier.fillMaxWidth().clickable { select(i) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Photo(barber.photo, Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(barber.name, fontWeight = FontWeight.Bold, fontSize = 17.sp); Text(barber.specialty, color = Muted, fontSize = 12.sp); Text("★ ${barber.rating} · Ver perfil", color = Navy, fontSize = 12.sp) }
                        Glyph("chevron", Navy, 18.dp)
                    }
                }
            }
            Note("Perfiles y valoraciones de ejemplo.", "user")
        }
    }
}
@Composable
fun BarberDetailScreen(id: Int, back: () -> Unit, book: () -> Unit, cut: (Int) -> Unit) {
    val person = barbers[id]
    Column {
        ScreenHeader("Tu barbero", back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Photo(person.photo, Modifier.size(130.dp).clip(CircleShape))
            Text(person.name, fontSize = 26.sp, fontWeight = FontWeight.Bold); Text(person.specialty, color = Muted)
            Text("★ ${person.rating}   ·   5 años de experiencia", color = Navy, fontSize = 14.sp)
            Text("Cada persona tiene su propio estilo. Mi trabajo es ayudarte a encontrarlo, con atención al detalle y un acabado que te haga sentir bien.", color = Muted, fontSize = 14.sp, lineHeight = 23.sp, textAlign = TextAlign.Center)
            Heading("Su trabajo")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { cuts.take(3).forEach { item -> Photo(item.photo, Modifier.weight(1f).height(132.dp).clip(RoundedCornerShape(12.dp)).clickable { cut(item.id) }, item.name) } }
            CardSurface(Modifier.fillMaxWidth()) { Text("Lo que dicen sus clientes", fontWeight = FontWeight.Bold, fontSize = 15.sp); Spacer(Modifier.height(10.dp)); Text("★★★★★", color = Red); Text("“Excelente atención y mucha precisión. Justo el corte que buscaba.”", color = Muted, fontSize = 13.sp); Text("Luis M. · Reseña de ejemplo", fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp)) }
        }
        Box(Modifier.padding(20.dp)) { PrimaryButton("Reservar con ${person.name.substringBefore(' ')}", book, red = true, icon = "calendar") }
    }
}
@Composable
fun SettingsScreen(state: FreshCutState, back: () -> Unit, message: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(state.name) }
    var email by rememberSaveable { mutableStateOf(state.email) }
    var error by rememberSaveable { mutableStateOf("") }
    Column {
        ScreenHeader("Editar perfil", back)
        Column(Modifier.verticalScroll(rememberScrollState()).imePadding().padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            InputField(name, { name = it }, "Nombre completo", "user")
            InputField(email, { email = it }, "Correo electrónico", "mail", email = true)
            if (error.isNotEmpty()) Text(error, color = Red, fontSize = 12.sp)
            PrimaryButton("Guardar cambios", {
                if (name.trim().length < 2 || !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) error = "Escribe un nombre y un correo válidos."
                else { state.updateProfile(name.trim(), email.trim()); message("Perfil actualizado"); back() }
            }, icon = "check")
            Note("Tus datos se guardan únicamente en este dispositivo.")
        }
    }
}
@Composable
fun AuxiliaryScreen(route: String, state: FreshCutState, back: () -> Unit, navigate: (String) -> Unit) {
    val title = when (route) { "notifications" -> "Notificaciones"; "payments" -> "Métodos de pago"; "publications" -> "Mis publicaciones"; else -> "Ayuda y soporte" }
    var expanded by rememberSaveable { mutableIntStateOf(-1) }
    var composePost by remember { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    Column {
        ScreenHeader(title, back)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            when (route) {
                "notifications" -> {
                    CardSurface { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Recordatorios de citas", fontWeight = FontWeight.Bold, fontSize = 15.sp); Text("Preferencia de demostración", color = Muted, fontSize = 12.sp) }; Switch(state.reminders, { state.setReminder(it) }) } }
                    Note("En esta versión no se envían notificaciones al sistema. Consulta tus próximas visitas en Mis citas.", "bell")
                    state.appointments.filter { !it.cancelled && appointmentTime(it.date, it.time) > System.currentTimeMillis() }.forEach { item -> CardSurface { MenuRow("calendar", "Tienes una cita guardada", "${dayLabel(item.date)} · ${item.time}", { navigate("appointments") }) } }
                    if (state.appointments.none { !it.cancelled }) EmptyState("Todo al día", "Cuando agendes una cita, encontrarás sus detalles aquí.", "bell")
                }
                "payments" -> {
                    CardSurface { MenuRow("card", "Pago en la barbería", "Efectivo · seleccionado", {}, trailing = { Glyph("check", Navy) }) }
                    Note("No se cobran reservas en esta demostración. No necesitas agregar una tarjeta.", "lock")
                    Text("Precios transparentes", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    services.forEach { item -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(item.name, fontSize = 14.sp); Text("$${item.price} MXN", fontSize = 14.sp, fontWeight = FontWeight.Bold) } }
                }
                "publications" -> {
                    if (state.publications.isEmpty()) EmptyState("Tu estilo tiene una historia", "Crea una publicación de demostración con tu experiencia.", "edit")
                    PrimaryButton("Crear publicación", { composePost = true }, red = true, icon = "plus")
                    state.publications.forEach { post -> CardSurface(Modifier.fillMaxWidth()) { Text(state.name, fontWeight = FontWeight.Bold); Text("Solo en este dispositivo", color = Muted, fontSize = 11.sp); Spacer(Modifier.height(12.dp)); Text(post, fontSize = 14.sp) } }
                }
                else -> {
                    Heading("Estamos para ayudarte", "Todo lo que necesitas para tu próxima visita.")
                    val faqs = listOf("¿Cómo agendo una cita?" to "Elige un servicio, tu barbero, la fecha y un horario disponible. Revisa el resumen y confirma. Tu cita aparecerá en Mis citas.", "¿Puedo cambiar mi horario?" to "Sí. En Mis citas, toca Reprogramar, selecciona otro horario y confirma el cambio.", "¿Cómo cancelo una cita?" to "En Mis citas, toca Cancelar y confirma. La cita quedará en tu historial.", "¿Se realiza una reserva real?" to "Esta versión es una demostración. Las citas, el perfil y los favoritos se guardan en el teléfono y no se envían a una barbería.", "¿Cómo guardo un corte?" to "Toca el corazón en el catálogo o en el detalle del corte. Lo encontrarás en Mis favoritos.")
                    faqs.forEachIndexed { i, (question, answer) -> CardSurface(Modifier.fillMaxWidth().clickable { expanded = if (expanded == i) -1 else i }) { Row(verticalAlignment = Alignment.CenterVertically) { Text(question, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold); Glyph(if (expanded == i) "close" else "plus", Navy, 18.dp) }; if (expanded == i) Text(answer, Modifier.padding(top = 12.dp), color = Muted, fontSize = 13.sp, lineHeight = 21.sp) } }
                    Brand(compact = true)
                }
            }
        }
    }
    if (composePost) AlertDialog(onDismissRequest = { composePost = false }, title = { Text("Comparte tu experiencia") }, text = { OutlinedTextField(draft, { if (it.length <= 500) draft = it }, label = { Text("¿Qué tal tu nuevo estilo?") }, supportingText = { Text("${draft.length}/500 · publicación local") }, minLines = 3) }, confirmButton = { TextButton({ state.publish(draft.trim()); draft = ""; composePost = false }, enabled = draft.isNotBlank()) { Text("Publicar") } }, dismissButton = { TextButton({ composePost = false }) { Text("Cancelar") } })
}
