package com.example.peluqueria.freshcut

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import java.util.UUID

@Composable
fun BookingScreen(state: FreshCutState, cutId: Int, barberId: Int, editId: String, back: () -> Unit, onConfirmed: () -> Unit, message: (String) -> Unit) {
    val existing = remember(editId) { state.appointments.find { it.id == editId } }
    var step by rememberSaveable(editId) { mutableIntStateOf(if (existing != null) 2 else 1) }
    var service by rememberSaveable(editId) { mutableIntStateOf(existing?.service ?: 0) }
    var barber by rememberSaveable(editId) { mutableIntStateOf(existing?.barber ?: barberId) }
    var date by rememberSaveable(editId) { mutableLongStateOf(existing?.date?.coerceAtLeast(today()) ?: nextDay(today(), 1)) }
    var time by rememberSaveable(editId) { mutableStateOf(existing?.time ?: "") }
    var month by rememberSaveable(editId) { mutableLongStateOf(date) }
    BackHandler(enabled = step > 1) { step-- }
    val canContinue = step != 2 || (time.isNotEmpty() && state.slotAvailable(date, time, barber, service, editId))
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (editId.isEmpty()) "Agendar cita" else "Reprogramar cita", { if (step > 1) step-- else back() })
        Row(Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 14.dp), verticalAlignment = Alignment.Top) {
            listOf("Servicio", "Fecha y hora", "Confirmar").forEachIndexed { i, title ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(34.dp).background(if (step >= i + 1) Red else Line, CircleShape), contentAlignment = Alignment.Center) { if (step > i + 1) Glyph("check", Color.White, 18.dp) else Text("${i + 1}", fontSize = 14.sp, color = if (step >= i + 1) Color.White else Muted, fontWeight = FontWeight.Bold) }
                    Text(title, fontSize = 10.sp, color = if (step == i + 1) Ink else Muted, fontWeight = FontWeight.SemiBold)
                }
                if (i < 2) HorizontalDivider(Modifier.width(32.dp).padding(top = 17.dp), color = if (step > i + 1) Red else Line)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            when (step) {
                1 -> {
                    Heading("¿Qué hacemos hoy?", "Elige el servicio que necesitas.")
                    services.forEachIndexed { i, item ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Color.White).border(if (service == i) 1.5.dp else 1.dp, if (service == i) Red else Line, RoundedCornerShape(15.dp)).clickable { service = i; time = "" }.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Photo(item.photo, Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)))
                            Column(Modifier.weight(1f)) { Text(item.name, fontSize = 14.sp, fontWeight = FontWeight.Bold); Text("${item.minutes} min · cuidado a tu medida", color = Muted, fontSize = 10.sp) }
                            Text("$${item.price}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Box(Modifier.size(19.dp).border(1.dp, if (service == i) Red else Muted, CircleShape).background(if (service == i) Red else Color.Transparent, CircleShape), contentAlignment = Alignment.Center) { if (service == i) Glyph("check", Color.White, 13.dp) }
                        }
                    }
                    Heading("Elige a tu barbero")
                    barbers.forEachIndexed { i, person ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (barber == i) SoftBlue else Color.White).clickable { barber = i; time = "" }.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Photo(person.photo, Modifier.size(38.dp).clip(CircleShape)); Column(Modifier.weight(1f)) { Text(person.name, fontSize = 13.sp, fontWeight = FontWeight.Bold); Text(person.specialty, fontSize = 11.sp, color = Muted) }; if (barber == i) Glyph("check", Navy, 20.dp) else Text("★ ${person.rating}", fontSize = 12.sp, color = Navy)
                        }
                    }
                    Text("Precios en MXN · pago en la barbería", color = Muted, fontSize = 12.sp)
                }
                2 -> {
                    Heading("Un espacio para ti", "Selecciona la fecha y el horario.")
                    CardSurface {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            val previous = Calendar.getInstance().apply { timeInMillis = month; add(Calendar.MONTH, -1); set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH)) }.timeInMillis >= today()
                            IconButton({ month = Calendar.getInstance().apply { timeInMillis = month; add(Calendar.MONTH, -1) }.timeInMillis }, enabled = previous) { Glyph("back", if (previous) Navy else Line, 18.dp) }
                            Text(dayLabel(month, "MMMM yyyy").replaceFirstChar { it.uppercase() }, Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            IconButton({ month = Calendar.getInstance().apply { timeInMillis = month; add(Calendar.MONTH, 1) }.timeInMillis }, enabled = month < nextDay(today(), 150)) { Glyph("chevron", Navy, 18.dp) }
                        }
                        Row { listOf("L", "M", "M", "J", "V", "S", "D").forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, color = Muted, fontSize = 11.sp) } }
                        Spacer(Modifier.height(10.dp))
                        val calendar = Calendar.getInstance().apply { timeInMillis = month; set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
                        val offset = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
                        val count = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
                        for (row in 0 until (offset + count + 6) / 7) {
                            Row(Modifier.fillMaxWidth()) {
                                for (col in 0..6) {
                                    val day = row * 7 + col - offset + 1
                                    val dayMillis = Calendar.getInstance().apply { timeInMillis = calendar.timeInMillis; set(Calendar.DAY_OF_MONTH, day.coerceAtLeast(1)) }.timeInMillis
                                    val valid = day in 1..count && dayMillis >= today() && dayMillis <= nextDay(today(), 180)
                                    Box(Modifier.weight(1f).height(42.dp).padding(2.dp).clip(RoundedCornerShape(10.dp)).background(if (day in 1..count && date == dayMillis) Red else Color.Transparent).clickable(enabled = valid) { date = dayMillis; time = "" }, contentAlignment = Alignment.Center) {
                                        if (day in 1..count) Text("$day", fontSize = 12.sp, color = if (date == dayMillis) Color.White else if (valid) Ink else Line)
                                    }
                                }
                            }
                        }
                    }
                    Heading("Horarios disponibles", "Con ${barbers[barber].name} · ${services[service].minutes} minutos")
                    timeSlots.chunked(3).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) { row.forEach { slot ->
                        val available = state.slotAvailable(date, slot, barber, service, editId)
                        OutlinedButton({ time = slot }, Modifier.weight(1f).height(44.dp), enabled = available, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, if (time == slot) Red else Line), colors = ButtonDefaults.outlinedButtonColors(containerColor = if (time == slot) Red else Color.White, contentColor = if (time == slot) Color.White else Ink), contentPadding = PaddingValues(0.dp)) { Text(slot, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    } } }
                    Note("Disponibilidad de demostración. La cita se guardará en este dispositivo.", "calendar")
                }
                else -> {
                    Heading("Todo listo para tu visita", "Revisa los detalles antes de confirmar.")
                    CardSurface(Modifier.fillMaxWidth()) {
                        SummaryRow("cut", "Servicio", services[service].name)
                        SummaryRow("calendar", "Fecha", dayLabel(date, "EEEE, d 'de' MMMM 'de' yyyy"))
                        SummaryRow("clock", "Hora", "$time · ${services[service].minutes} minutos")
                        SummaryRow("user", "Barbero", barbers[barber].name)
                        SummaryRow("pin", "Lugar", "FreshCut Barbería")
                        if (cutId >= 0) SummaryRow("bookmark", "Tu inspiración", cuts[cutId].name)
                        Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("Total", fontWeight = FontWeight.Bold); Text("$${services[service].price} MXN", color = Navy, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp) }
                        Text("Pago en la barbería", color = Muted, fontSize = 11.sp)
                    }
                    Note("Esta es una reserva de demostración. No se enviará a una barbería ni se realizará ningún cobro.")
                }
            }
        }
        Box(Modifier.background(Color.White).padding(20.dp)) {
            PrimaryButton(if (step == 3) "Confirmar cita" else "Siguiente", {
                if (step < 3) step++ else {
                    if (state.slotAvailable(date, time, barber, service, editId)) {
                        state.saveAppointment(Appointment(existing?.id ?: UUID.randomUUID().toString(), service, date, time, barber, cutId.coerceAtLeast(0)))
                        onConfirmed()
                    } else { message("Ese horario ya no está disponible. Elige otro."); step = 2; time = "" }
                }
            }, red = step != 2, enabled = canContinue, icon = if (step == 3) "check" else "arrow")
        }
    }
}
@Composable
fun SummaryRow(icon: String, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Glyph(icon, Navy, 21.dp)
        Column { Text(label.uppercase(), color = Muted, fontSize = 9.sp, letterSpacing = 1.sp); Text(value.replaceFirstChar { it.uppercase() }, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
    }
    HorizontalDivider(color = Line)
}
@Composable
fun AppointmentScreen(state: FreshCutState, book: () -> Unit, reschedule: (Appointment) -> Unit) {
    var tab by rememberSaveable { mutableStateOf("Próximas") }
    var cancelId by remember { mutableStateOf<String?>(null) }
    val now = System.currentTimeMillis()
    val items = state.appointments.filter { (it.cancelled || appointmentTime(it.date, it.time) < now) == (tab == "Historial") }.sortedBy { appointmentTime(it.date, it.time) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Mis citas", action = { IconAction("plus", "Agendar nueva cita", book) })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            ChipRow(listOf("Próximas", "Historial"), tab, { tab = it })
            if (items.isEmpty()) EmptyState(if (tab == "Próximas") "Tu próximo corte te espera" else "Cada corte cuenta", if (tab == "Próximas") "Reserva un momento para ti. Tus próximas citas aparecerán aquí." else "Aquí encontrarás tus citas pasadas y canceladas.", "calendar", if (tab == "Próximas") "Agendar una cita" else null, book)
            items.forEach { item ->
                CardSurface(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().background(if (item.cancelled) Paper else Color(0xFFFFF0F0), RoundedCornerShape(10.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Glyph("calendar", Red, 18.dp); Text("${dayLabel(item.date)} · ${item.time}", color = Red, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    Spacer(Modifier.height(15.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { Photo(barbers[item.barber].photo, Modifier.size(44.dp).clip(CircleShape)); Column { Text(barbers[item.barber].name, fontWeight = FontWeight.Bold, fontSize = 15.sp); Text("Tu barbero · FreshCut", fontSize = 12.sp, color = Muted) } }
                    Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(services[item.service].name, fontSize = 14.sp); Text("$${services[item.service].price}", fontWeight = FontWeight.Bold) }
                    if (tab == "Próximas") Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton({ reschedule(item) }, Modifier.weight(1f), shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(8.dp)) { Text("Reprogramar", fontSize = 12.sp) }
                        OutlinedButton({ cancelId = item.id }, Modifier.weight(1f), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Red), contentPadding = PaddingValues(8.dp)) { Text("Cancelar", fontSize = 12.sp) }
                    } else Text(if (item.cancelled) "Cancelada" else "Finalizada", color = if (item.cancelled) Red else Muted, fontSize = 12.sp)
                    Text("Reserva local de demostración", Modifier.padding(top = 9.dp), color = Muted, fontSize = 10.sp)
                }
            }
        }
    }
    if (cancelId != null) AlertDialog(onDismissRequest = { cancelId = null }, title = { Text("¿Cancelar tu cita?") }, text = { Text("Podrás encontrarla en el historial y reservar otro horario cuando quieras.") }, confirmButton = { TextButton({ state.cancel(cancelId!!); cancelId = null }) { Text("Sí, cancelar", color = Red) } }, dismissButton = { TextButton({ cancelId = null }) { Text("Conservar cita") } })
}
@Composable
fun BookingSuccess(onAppointments: () -> Unit, onHome: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(100.dp).background(SoftBlue, CircleShape), contentAlignment = Alignment.Center) { Glyph("check", Navy, 50.dp) }
        Spacer(Modifier.height(28.dp)); Text("¡Tienes una cita!", fontSize = 29.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp)); Text("Tu reserva de demostración quedó guardada. Puedes verla o cambiarla en Mis citas.", textAlign = TextAlign.Center, color = Muted, fontSize = 15.sp, lineHeight = 23.sp)
        Spacer(Modifier.height(32.dp)); PrimaryButton("Ver mis citas", onAppointments, icon = "calendar"); TextButton(onHome) { Text("Volver al inicio") }
    }
}
