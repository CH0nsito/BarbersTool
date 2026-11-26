package com.example.peluqueria.freshcut

import android.net.Uri
import android.util.Patterns
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import java.util.UUID

// =====================================================================
//  Panel del dueño de FreshCut
//  Usa los mismos colores, componentes y datos locales que la app del
//  cliente: lo que reserva un cliente en este teléfono aparece aquí.
// =====================================================================

private val Done = Color(0xFF2E7D4F)
private val Pending = Color(0xFFD08A00)
private fun dayStart(millis: Long): Long = Calendar.getInstance().apply { timeInMillis = millis; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
private fun hour(h: Int) = String.format(Locale.US, "%02d:00", h)
private fun String.cap() = replaceFirstChar { it.uppercase() }
private fun greeting(): String = Calendar.getInstance().get(Calendar.HOUR_OF_DAY).let { if (it < 12) "Buenos días" else if (it < 19) "Buenas tardes" else "Buenas noches" }
private fun initials(name: String) = name.trim().split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "C" }
private fun clientKey(a: Appointment) = if (a.clientEmail.isNotBlank()) a.clientEmail.lowercase() else a.client.trim().lowercase()
private fun price(a: Appointment) = a.servicePrice
private val weekdayNames = mapOf(2 to "lunes", 3 to "martes", 4 to "miércoles", 5 to "jueves", 6 to "viernes", 7 to "sábado", 1 to "domingo")
fun hoursSummary(): String {
    val base = "${hour(openHour)} – ${hour(closeHour)}"
    return if (closedWeekdays.isEmpty()) "$base · todos los días" else "$base · cierra ${listOf(2, 3, 4, 5, 6, 7, 1).filter { it in closedWeekdays }.joinToString(", ") { weekdayNames.getValue(it) }}"
}
/** Ruta para crear una cita, con datos opcionales ya llenos. */
private fun newRoute(date: Long, time: String = "", barber: Int = -1, client: String = "", phone: String = "") = "new/$date/$time/$barber/${Uri.encode(client)}/${Uri.encode(phone)}"
private fun statusInfo(a: Appointment): Pair<String, Color> = when {
    a.cancelled -> "Cancelada" to Muted
    a.status == "done" -> "Atendida" to Done
    a.status == "noshow" -> "No asistió" to Red
    a.start < System.currentTimeMillis() -> "Sin registrar" to Pending
    else -> "Agendada" to Navy
}

// ---------------------------------------------------------------- Acceso
@Composable
fun OwnerLoginScreen(state: FreshCutState, back: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()) {
        IconAction("back", "Volver", back, modifier = Modifier.padding(start = 10.dp))
        Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Spacer(Modifier.height(5.dp)); Brand(); Spacer(Modifier.height(8.dp))
            Surface(color = SoftBlue, shape = RoundedCornerShape(24.dp)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Glyph("store", Navy, 14.dp); Text("PANEL DEL NEGOCIO", color = Navy, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp) }
            }
            Text("Tu barbería, en orden", fontSize = 27.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.8).sp)
            Text("Administra tu agenda, servicios y equipo desde un solo lugar.", color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            InputField(email, { email = it; error = "" }, "Correo del negocio", "mail", email = true)
            InputField(password, { password = it; error = "" }, "Contraseña", "lock", password = true)
            if (error.isNotEmpty()) Text(error, color = Red, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            PrimaryButton("Entrar al panel", {
                error = when {
                    !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "Escribe un correo electrónico válido."
                    password.length < 8 -> "La contraseña debe tener al menos 8 caracteres."
                    else -> ""
                }
                if (error.isEmpty()) state.ownerLogin(email.trim(), password)
            })
            Note("Usa la cuenta del dueño creada desde la PC. Los permisos se verifican en el servidor.")
            TextButton({ state.showConnection = true }) { Text("Configurar conexión", fontWeight = FontWeight.Bold) }
            TextButton(back) { Text("¿Eres cliente? Volver a la app", color = Red, fontSize = 13.sp) }
            Spacer(Modifier.height(20.dp))
        }
    }
}

// ---------------------------------------------------------------- Navegación
@Composable
fun OwnerApp(state: FreshCutState) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        (context as? ComponentActivity)?.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        onDispose { }
    }
    var history by rememberSaveable { mutableStateOf("dashboard") }
    var agendaDay by rememberSaveable { mutableLongStateOf(today()) }
    var agendaBarber by rememberSaveable { mutableIntStateOf(-1) }
    val route = history.substringAfterLast('|')
    val tabs = listOf("dashboard", "agenda", "clients", "business")
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val message: (String) -> Unit = { text -> scope.launch { snackbar.showSnackbar(text, withDismissAction = true) } }
    val navigate: (String) -> Unit = { target ->
        when {
            target in tabs -> history = target
            target != route -> history += "|$target"
        }
    }
    val back: () -> Unit = { history = if ('|' in history) history.substringBeforeLast('|') else "dashboard" }
    BackHandler(enabled = route != "dashboard") { back() }
    Scaffold(
        modifier = Modifier.fillMaxSize(), containerColor = Paper, contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (route in tabs) {
                HorizontalDivider(color = Line)
                NavigationBar(containerColor = Color.White, tonalElevation = 0.dp, modifier = Modifier.navigationBarsPadding(), windowInsets = WindowInsets(0, 0, 0, 0)) {
                    val titles = listOf("Panel", "Agenda", "Clientes", "Negocio")
                    val icons = listOf("chart", "calendar", "users", "store")
                    tabs.forEachIndexed { index, tab -> NavigationBarItem(
                        selected = route == tab, onClick = { history = tab },
                        icon = { Glyph(icons[index], if (route == tab) Red else Muted, 22.dp) },
                        label = { Text(titles[index], fontSize = 10.sp, fontWeight = if (route == tab) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(selectedTextColor = Red, unselectedTextColor = Muted, indicatorColor = Color(0xFFFFF0F0))
                    ) }
                }
            }
        }
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner).background(Paper)) {
            val args = route.split('/')
            when {
                route == "dashboard" -> OwnerDashboard(state, navigate) { agendaDay = today(); navigate("agenda") }
                route == "agenda" -> OwnerAgenda(state, agendaDay, { agendaDay = it }, agendaBarber, { agendaBarber = it }, navigate)
                route == "clients" -> OwnerClients(state, navigate)
                route == "posts" -> AuxiliaryScreen("publications", state, back, navigate)
                route == "business" -> OwnerBusiness(state, navigate, message)
                route.startsWith("appt/") -> OwnerAppointmentDetail(state, route.substringAfter('/'), back, navigate, message)
                route.startsWith("new/") -> OwnerNewAppointment(
                    state, args.getOrNull(1)?.toLongOrNull() ?: today(), args.getOrNull(2).orEmpty(), args.getOrNull(3)?.toIntOrNull() ?: -1,
                    Uri.decode(args.getOrNull(4).orEmpty()), Uri.decode(args.getOrNull(5).orEmpty()), back
                ) { date -> agendaDay = date; back(); message("Cita agendada") }
                route.startsWith("client/") -> OwnerClientDetail(state, Uri.decode(route.substringAfter('/')), back, navigate)
                route == "services" -> OwnerServices(state, navigate, back, message)
                route.startsWith("service/") -> OwnerServiceEditor(state, args.getOrNull(1)?.toIntOrNull() ?: -1, back, message)
                route == "team" -> OwnerTeam(state, navigate, back, message)
                route.startsWith("barber/") -> OwnerBarberEditor(state, args.getOrNull(1)?.toIntOrNull() ?: -1, back, message)
                route == "hours" -> OwnerHours(state, back, message)
                else -> OwnerDashboard(state, navigate) { navigate("agenda") }
            }
        }
    }
}

// ---------------------------------------------------------------- Componentes compartidos del panel
@Composable
fun StatusPill(label: String, color: Color) {
    Text(label, Modifier.background(color.copy(alpha = .1f), RoundedCornerShape(20.dp)).padding(horizontal = 8.dp, vertical = 3.dp), color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
}
@Composable
private fun Avatar(name: String, size: Int = 46) {
    Box(Modifier.size(size.dp).background(SoftBlue, CircleShape), contentAlignment = Alignment.Center) { Text(initials(name), color = Navy, fontWeight = FontWeight.Bold, fontSize = (size * .36f).sp) }
}
@Composable
fun OwnerAppointmentCard(item: Appointment, onClick: () -> Unit, showDate: Boolean = false) {
    val (label, color) = statusInfo(item)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(15.dp)).background(Color.White).border(1.dp, Line, RoundedCornerShape(15.dp)).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(color))
        Column(Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp).width(58.dp)) {
            Text(item.time, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(if (showDate) dayLabel(item.date, "d MMM yy") else "${item.serviceMinutes} min", color = Muted, fontSize = 10.sp)
        }
        Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
            Text(item.client.ifBlank { "Cliente" }, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.serviceName, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Photo(barbers[item.barber].photo, Modifier.size(18.dp).clip(CircleShape)); Text(barbers[item.barber].name, color = Navy, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(money(price(item)), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (item.billable) Ink else Muted, textDecoration = if (item.billable) null else TextDecoration.LineThrough)
            StatusPill(label, color)
        }
    }
}
@Composable
fun DayStrip(days: List<Long>, selected: Long, onSelect: (Long) -> Unit, marked: Set<Long> = emptySet()) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (days.indexOf(selected) - 2).coerceAtLeast(0))
    LazyRow(state = listState, contentPadding = PaddingValues(horizontal = 22.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(days) { d ->
            val chosen = d == selected
            val closed = isClosedDay(d)
            Column(Modifier.width(52.dp).clip(RoundedCornerShape(14.dp)).background(if (chosen) Red else Color.White).border(1.dp, if (chosen) Red else Line, RoundedCornerShape(14.dp)).clickable { onSelect(d) }.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(dayLabel(d, "EEE").take(3).uppercase(), color = if (chosen) Color.White.copy(alpha = .8f) else Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .5.sp)
                Text(dayLabel(d, "d"), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = if (chosen) Color.White else if (closed) Muted.copy(alpha = .5f) else Ink)
                Box(Modifier.size(5.dp).background(if (d in marked) (if (chosen) Color.White else Red) else Color.Transparent, CircleShape))
            }
        }
    }
}
@Composable
fun OwnerField(value: String, onChange: (String) -> Unit, label: String, icon: String, keyboard: KeyboardType = KeyboardType.Text, unit: String? = null) {
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), label = { Text(label, fontSize = 13.sp) }, leadingIcon = { Glyph(icon, Navy, 19.dp) },
        suffix = if (unit != null) { { Text(unit, color = Muted, fontSize = 12.sp) } } else null,
        singleLine = true, shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Line, focusedBorderColor = Navy, unfocusedContainerColor = Color.White, focusedContainerColor = Color.White))
}
@Composable
private fun StatTile(icon: String, value: String, label: String, modifier: Modifier = Modifier, tint: Color = Navy, onClick: () -> Unit = {}) {
    Column(modifier.clip(RoundedCornerShape(15.dp)).background(Color.White).clickable(onClick = onClick).padding(vertical = 14.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Glyph(icon, tint, 21.dp)
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, color = Muted, fontSize = 10.sp, maxLines = 1, textAlign = TextAlign.Center)
    }
}
@Composable
private fun ProgressLine(fraction: Float, color: Color = Red) {
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Paper)) { Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(color, RoundedCornerShape(3.dp))) }
}
@Composable
private fun PhotoPicker(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        (0..6).forEach { id -> Photo(id, Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).border(if (id == selected) 2.5.dp else 1.dp, if (id == selected) Red else Line, RoundedCornerShape(12.dp)).clickable { onSelect(id) }, "Foto ${id + 1}") }
    }
}
@Composable
private fun BottomAction(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color.White).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

// ---------------------------------------------------------------- Panel (inicio)
@Composable
fun OwnerDashboard(state: FreshCutState, navigate: (String) -> Unit, openAgendaToday: () -> Unit) {
    val now = System.currentTimeMillis()
    val day = today()
    val todays = state.appointments.filter { dayStart(it.date) == day && !it.cancelled }.sortedBy { it.start }
    val revenueToday = todays.filter { it.billable }.sumOf { price(it) }
    val week = (-6..0).map { nextDay(day, it) }
    val weekItems = state.appointments.filter { dayStart(it.date) in week }
    val weekValues = week.map { d -> weekItems.filter { dayStart(it.date) == d && it.billable }.sumOf { price(it) } }
    val weekRevenue = weekValues.sum()
    val cancelRate = if (weekItems.isEmpty()) 0 else weekItems.count { it.cancelled || it.status == "noshow" } * 100 / weekItems.size
    val upcoming = state.appointments.filter { !it.cancelled && it.status == "" && it.start >= now }.sortedBy { it.start }.take(3)
    val unregistered = state.appointments.count { !it.cancelled && it.status == "" && it.start < now }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(47.dp).background(Navy, CircleShape).clickable { navigate("business") }, contentAlignment = Alignment.Center) { Glyph("cut", Color.White, 24.dp) }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(greeting(), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Así va FreshCut hoy, ${dayLabel(now, "EEEE d 'de' MMMM")}.", color = Muted, fontSize = 11.sp)
            }
            IconAction("plus", "Nueva cita", { navigate(newRoute(day)) })
        }
        if (state.appointments.isEmpty()) {
            CardSurface(Modifier.fillMaxWidth()) {
                EmptyState("Tu agenda está lista", "Aquí verás las citas que reserven tus clientes y las que agendes tú. Carga datos de ejemplo para probar el panel.", "chart", "Cargar datos de ejemplo") { state.seedDemo() }
            }
            Note("Las reservas hechas desde la app del cliente en este teléfono aparecen aquí automáticamente.", "calendar")
        } else {
            Box(Modifier.fillMaxWidth().height(190.dp).clip(Shape).background(Navy).clickable(onClick = openAgendaToday)) {
                Photo(0, Modifier.fillMaxHeight().fillMaxWidth(.55f).align(Alignment.CenterEnd))
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Navy, Navy.copy(alpha = .95f), Navy.copy(alpha = .5f)))))
                Column(Modifier.padding(22.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                    Text("INGRESOS ESTIMADOS DE HOY", color = Color(0xFFC9D9EF), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
                    Column {
                        Text(money(revenueToday), fontSize = 36.sp, color = Color.White, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
                        Text("MXN · pago en la barbería", fontSize = 12.sp, color = Color.White.copy(alpha = .75f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("${todays.size} citas", "${todays.count { it.status == "done" }} atendidas", "${todays.count { it.status == "" && it.start >= now }} por venir").forEach {
                            Text(it, Modifier.background(Color.White.copy(alpha = .14f), RoundedCornerShape(9.dp)).padding(horizontal = 10.dp, vertical = 6.dp), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("calendar", "${weekItems.count { !it.cancelled }}", "Citas · 7 días", Modifier.weight(1f)) { navigate("agenda") }
                StatTile("money", money(weekRevenue), "Ingresos · 7 días", Modifier.weight(1f))
                StatTile("close", "$cancelRate%", "Cancel. y faltas", Modifier.weight(1f), Red)
            }
            if (unregistered > 0) Row(Modifier.fillMaxWidth().background(Pending.copy(alpha = .1f), RoundedCornerShape(12.dp)).clickable(onClick = openAgendaToday).padding(13.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Glyph("clock", Pending, 18.dp)
                Text("$unregistered ${if (unregistered == 1) "cita pasada sin registrar" else "citas pasadas sin registrar"}. Marca si el cliente asistió para que tus ingresos sean exactos.", Modifier.weight(1f), color = Ink, fontSize = 12.sp, lineHeight = 18.sp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Heading("Próximas citas", "Lo que sigue en tu agenda", "Ver agenda", openAgendaToday)
                if (upcoming.isEmpty()) Note("No hay citas próximas. Comparte tu app o agrega una cita desde el botón +.", "calendar")
                upcoming.forEach { item -> OwnerAppointmentCard(item, { navigate("appt/${item.id}") }, showDate = dayStart(item.date) != day) }
            }
            CardSurface(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Ingresos de la semana", fontSize = 15.sp, fontWeight = FontWeight.Bold); Text("Últimos 7 días · estimado", color = Muted, fontSize = 12.sp) }
                    Text(money(weekRevenue), color = Navy, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                }
                Spacer(Modifier.height(16.dp))
                val max = (weekValues.maxOrNull() ?: 0).coerceAtLeast(1)
                Row(Modifier.fillMaxWidth().height(130.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    weekValues.forEachIndexed { i, value ->
                        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                            if (value > 0) Text(if (value >= 1000) String.format(Locale.US, "%.1fk", value / 1000f) else "$value", color = Muted, fontSize = 9.sp, maxLines = 1)
                            Spacer(Modifier.height(4.dp))
                            Box(Modifier.fillMaxWidth(.72f).fillMaxHeight((value.toFloat() / max * .82f).coerceAtLeast(.03f)).clip(RoundedCornerShape(6.dp)).background(if (i == 6) Red else Navy))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    week.forEachIndexed { i, d -> Text(if (i == 6) "Hoy" else dayLabel(d, "EEE").take(3).cap(), Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 10.sp, color = if (i == 6) Red else Muted, fontWeight = if (i == 6) FontWeight.Bold else FontWeight.Normal) }
                }
            }
            val serviceCounts = services.indices.map { i -> i to weekItems.count { it.service == i && it.billable } }.filter { it.second > 0 }.sortedByDescending { it.second }
            if (serviceCounts.isNotEmpty()) CardSurface(Modifier.fillMaxWidth()) {
                Text("Servicios más pedidos", fontSize = 15.sp, fontWeight = FontWeight.Bold); Text("Esta semana", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                serviceCounts.forEach { (i, count) ->
                    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row { Text(services[i].name, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Medium); Text("$count ${if (count == 1) "cita" else "citas"}", color = Muted, fontSize = 12.sp) }
                        ProgressLine(count.toFloat() / serviceCounts.first().second)
                    }
                }
            }
            CardSurface(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Tu equipo esta semana", fontSize = 15.sp, fontWeight = FontWeight.Bold); Text("Citas e ingresos por barbero", color = Muted, fontSize = 12.sp) }
                    TextButton({ navigate("team") }) { Text("Equipo", color = Red, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                }
                barbers.forEachIndexed { i, person ->
                    val items = weekItems.filter { it.barber == i && it.billable }
                    if (person.active || items.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Photo(person.photo, Modifier.size(40.dp).clip(CircleShape))
                            Column(Modifier.weight(1f)) { Text(person.name, fontSize = 13.sp, fontWeight = FontWeight.Bold); Text("${items.size} ${if (items.size == 1) "cita" else "citas"}", color = Muted, fontSize = 11.sp) }
                            Text(money(items.sumOf { price(it) }), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
            Note("Agenda compartida con los clientes conectados al mismo servidor. Los cambios se sincronizan automáticamente.")
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ---------------------------------------------------------------- Agenda
@Composable
fun OwnerAgenda(state: FreshCutState, day: Long, onDay: (Long) -> Unit, barberFilter: Int, onBarber: (Int) -> Unit, navigate: (String) -> Unit) {
    val now = System.currentTimeMillis()
    val days = (-14..45).map { nextDay(today(), it) }
    val marked = state.appointments.filter { !it.cancelled }.map { dayStart(it.date) }.toSet()
    val filter = if (barberFilter in barbers.indices) barberFilter else -1
    val items = state.appointments.filter { dayStart(it.date) == dayStart(day) && (filter < 0 || it.barber == filter) }.sortedBy { it.start }
    val closed = isClosedDay(day)
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Agenda", action = { IconAction("plus", "Nueva cita", { navigate(newRoute(day, barber = filter)) }) })
        DayStrip(days, day, onDay, marked)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val options = listOf("Todos") + barbers.filter { it.active }.map { it.name }
            ChipRow(options, if (filter < 0) "Todos" else barbers[filter].name) { name -> onBarber(barbers.indexOfFirst { it.name == name }) }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(dayLabel(day, "EEEE d 'de' MMMM").cap(), Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("${items.count { !it.cancelled }} citas · ${money(items.filter { it.billable }.sumOf { price(it) })}", color = Muted, fontSize = 12.sp)
            }
            if (closed) Note("Este día está marcado como cerrado. Los clientes no pueden reservar.", "store")
            var shown = 0
            timeSlots.forEach { slot ->
                val atSlot = items.filter { it.time == slot }
                if (atSlot.isNotEmpty()) atSlot.forEach { item -> OwnerAppointmentCard(item, { navigate("appt/${item.id}") }); shown++ }
                else if (!closed && appointmentTime(day, slot) > now) {
                    shown++
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).border(1.dp, Line, RoundedCornerShape(15.dp)).clickable { navigate(newRoute(day, slot, filter)) }.padding(horizontal = 17.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(slot, Modifier.width(58.dp), color = Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Disponible", Modifier.weight(1f), color = Muted, fontSize = 12.sp)
                        Glyph("plus", Navy, 16.dp)
                    }
                }
            }
            val outside = items.filter { it.time !in timeSlots }
            if (outside.isNotEmpty()) {
                Text("FUERA DEL HORARIO ACTUAL", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                outside.forEach { item -> OwnerAppointmentCard(item, { navigate("appt/${item.id}") }); shown++ }
            }
            if (shown == 0) EmptyState("Sin citas este día", if (day < today()) "No hubo citas registradas en esta fecha." else "No quedan horarios para este día.", "calendar")
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ---------------------------------------------------------------- Detalle de cita
@Composable
fun OwnerAppointmentDetail(state: FreshCutState, id: String, back: () -> Unit, navigate: (String) -> Unit, message: (String) -> Unit) {
    val item = state.appointments.find { it.id == id }
    var confirmCancel by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Detalle de cita", back)
        if (item == null) EmptyState("Cita no encontrada", "Es posible que ya no exista en este dispositivo.", "calendar")
        else {
            val (label, color) = statusInfo(item)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                CardSurface(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Avatar(item.client, 52)
                        Column(Modifier.weight(1f)) {
                            Text(item.client.ifBlank { "Cliente" }, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(if (item.clientEmail.isNotBlank()) "Reservó desde la app" else "Agendada por el negocio", color = Muted, fontSize = 12.sp)
                        }
                        StatusPill(label, color)
                    }
                    Spacer(Modifier.height(8.dp))
                    if (item.phone.isNotBlank()) SummaryRow("phone", "Teléfono", item.phone)
                    if (item.clientEmail.isNotBlank()) SummaryRow("mail", "Correo", item.clientEmail)
                    SummaryRow("cut", "Servicio", "${item.serviceName} · ${item.serviceMinutes} min")
                    SummaryRow("calendar", "Fecha", dayLabel(item.date, "EEEE, d 'de' MMMM 'de' yyyy"))
                    SummaryRow("clock", "Hora", item.time)
                    SummaryRow("user", "Barbero", barbers[item.barber].name)
                    if (item.clientEmail.isNotBlank()) SummaryRow("bookmark", "Inspiración", cuts[item.cut.coerceIn(0, cuts.lastIndex)].name)
                    Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Total", fontWeight = FontWeight.Bold)
                        Text("${money(price(item))} MXN", color = if (item.billable) Navy else Muted, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, textDecoration = if (item.billable) null else TextDecoration.LineThrough)
                    }
                    Text(if (item.billable) "Pago en la barbería" else "No cuenta en tus ingresos", color = Muted, fontSize = 11.sp)
                }
                if (!item.cancelled && item.status == "" && item.start > System.currentTimeMillis()) Note("La cita aún no ocurre. Cuando llegue el cliente, márcala como atendida.", "clock")
                if (item.client.isNotBlank()) CardSurface(Modifier.fillMaxWidth()) { MenuRow("users", "Historial del cliente", "Visitas, gasto y preferencias", { navigate("client/${Uri.encode(clientKey(item))}") }) }
            }
            if (!item.cancelled) BottomAction {
                if (item.status == "") {
                    PrimaryButton("Marcar como atendida", { state.setStatus(item.id, "done") { message("Cita marcada como atendida") } }, icon = "check")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton({ state.setStatus(item.id, "noshow") { message("Registrado: el cliente no asistió") } }, Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) { Text("No asistió", fontSize = 12.sp) }
                        OutlinedButton({ confirmCancel = true }, Modifier.weight(1f), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Red)) { Text("Cancelar cita", fontSize = 12.sp) }
                    }
                } else OutlinedButton({ state.setStatus(item.id, "") { message("La cita volvió a quedar pendiente") } }, Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp)) { Text("Deshacer: marcar como pendiente") }
            }
        }
    }
    if (confirmCancel && item != null) AlertDialog(onDismissRequest = { confirmCancel = false }, title = { Text("¿Cancelar esta cita?") },
        text = { Text("El horario de ${barbers[item.barber].name} quedará libre. ${if (item.clientEmail.isNotBlank()) "El cliente la verá como cancelada en su app." else ""}") },
        confirmButton = { TextButton({ state.cancel(item.id) { confirmCancel = false; message("Cita cancelada") } }) { Text("Sí, cancelar", color = Red) } },
        dismissButton = { TextButton({ confirmCancel = false }) { Text("Conservar") } })
}

// ---------------------------------------------------------------- Nueva cita (mostrador / teléfono)
@Composable
fun OwnerNewAppointment(state: FreshCutState, initialDate: Long, initialTime: String, initialBarber: Int, initialClient: String, initialPhone: String, back: () -> Unit, onSaved: (Long) -> Unit) {
    val reservationId = rememberSaveable { UUID.randomUUID().toString() }
    var client by rememberSaveable { mutableStateOf(initialClient) }
    var phone by rememberSaveable { mutableStateOf(initialPhone) }
    var service by rememberSaveable { mutableIntStateOf(firstActiveService()) }
    var barber by rememberSaveable { mutableIntStateOf(if (initialBarber in barbers.indices && barbers[initialBarber].active) initialBarber else firstActiveBarber()) }
    var date by rememberSaveable { mutableLongStateOf(maxOf(dayStart(initialDate), today())) }
    var time by rememberSaveable { mutableStateOf(initialTime) }
    val days = (0 until 45).map { nextDay(today(), it) }
    LaunchedEffect(date, barber, service) { state.loadAvailability(date, barber, service) }
    val slotOk = !state.availabilityBusy && time in state.availableSlots
    val valid = client.trim().length >= 2 && slotOk
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Nueva cita", back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Heading("Cliente", "Para citas por teléfono o en mostrador.")
                OwnerField(client, { client = it.take(40) }, "Nombre del cliente", "user")
                OwnerField(phone, { value -> phone = value.filter { it.isDigit() || it == ' ' || it == '+' }.take(16) }, "Teléfono (opcional)", "phone", KeyboardType.Phone)
                Spacer(Modifier.height(4.dp))
                Heading("Servicio")
                services.forEachIndexed { i, item ->
                    if (item.active) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Color.White).border(if (service == i) 1.5.dp else 1.dp, if (service == i) Red else Line, RoundedCornerShape(15.dp)).clickable { service = i; time = "" }.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Photo(item.photo, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
                        Column(Modifier.weight(1f)) { Text(item.name, fontSize = 14.sp, fontWeight = FontWeight.Bold); Text("${item.minutes} min", color = Muted, fontSize = 11.sp) }
                        Text(money(item.price), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Heading("Barbero")
                ChipRow(barbers.filter { it.active }.map { it.name }, barbers[barber].name) { name -> barber = barbers.indexOfFirst { it.name == name }.coerceAtLeast(0); time = "" }
                Spacer(Modifier.height(4.dp))
                Heading("Fecha", dayLabel(date, "EEEE d 'de' MMMM").cap())
            }
            DayStrip(days, date, { date = it; time = "" })
            Column(Modifier.padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Heading("Horario", "${barbers[barber].name} · ${services[service].minutes} min")
                if (isClosedDay(date)) Note("Este día está marcado como cerrado. Elige otra fecha o cambia tu horario en Negocio.", "store")
                else timeSlots.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        row.forEach { slot ->
                            val available = !state.availabilityBusy && slot in state.availableSlots
                            OutlinedButton({ time = slot }, Modifier.weight(1f).height(44.dp), enabled = available, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, if (time == slot) Red else Line), colors = ButtonDefaults.outlinedButtonColors(containerColor = if (time == slot) Red else Color.White, contentColor = if (time == slot) Color.White else Ink), contentPadding = PaddingValues(0.dp)) { Text(slot, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Note("Solo puedes elegir horarios libres del barbero seleccionado.", "calendar")
                Spacer(Modifier.height(8.dp))
            }
        }
        BottomAction {
            if (client.isNotEmpty() && client.trim().length < 2) Text("Escribe el nombre del cliente.", color = Red, fontSize = 12.sp)
            PrimaryButton("Guardar cita", {
                state.saveOwnerAppointment(Appointment(reservationId, service, date, time, barber, 0, client = client.trim(), phone = phone.trim())) { onSaved(date) }
            }, red = true, icon = "check", enabled = valid && !state.busy)
        }
    }
}

// ---------------------------------------------------------------- Clientes
private data class ClientSummary(val key: String, val name: String, val phone: String, val email: String, val visits: List<Appointment>) {
    val attended get() = visits.count { it.status == "done" }
    val missed get() = visits.count { it.status == "noshow" || it.cancelled }
    val spent get() = visits.filter { it.status == "done" }.sumOf { price(it) }
    val last get() = visits.filter { !it.cancelled }.maxByOrNull { it.start }
}
private fun clientSummaries(list: List<Appointment>): List<ClientSummary> = list.filter { it.client.isNotBlank() }.groupBy(::clientKey).map { (key, items) ->
    val latest = items.maxBy { it.start }
    ClientSummary(key, latest.client, items.firstOrNull { it.phone.isNotBlank() }?.phone.orEmpty(), latest.clientEmail, items.sortedByDescending { it.start })
}.sortedByDescending { it.last?.start ?: 0L }

@Composable
fun OwnerClients(state: FreshCutState, navigate: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("Todos") }
    val all = clientSummaries(state.appointments)
    val shown = all.filter { (filter == "Todos" || it.attended >= 3) && (it.name.contains(query.trim(), true) || it.phone.replace(" ", "").contains(query.replace(" ", ""))) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Clientes")
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Quién te visita y cuánto vuelve.", color = Muted, fontSize = 13.sp)
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Buscar por nombre o teléfono…", fontSize = 14.sp) }, leadingIcon = { Glyph("search", Muted) }, trailingIcon = if (query.isNotEmpty()) { { IconAction("close", "Limpiar búsqueda", { query = "" }) } } else null, singleLine = true, shape = RoundedCornerShape(14.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Line, unfocusedContainerColor = Color.White, focusedContainerColor = Color.White))
            ChipRow(listOf("Todos", "Frecuentes"), filter) { filter = it }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("${shown.size} ${if (shown.size == 1) "cliente" else "clientes"}", color = Muted, fontSize = 12.sp); Text("CARTERA FRESHCUT", color = Navy, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold) }
            if (shown.isEmpty()) EmptyState(if (all.isEmpty()) "Aún no hay clientes" else "Sin coincidencias", if (all.isEmpty()) "Cuando alguien reserve o agendes una cita, aparecerá aquí." else if (filter == "Frecuentes") "Los clientes con 3 o más visitas atendidas aparecen aquí." else "Prueba con otro nombre o teléfono.", "users")
            shown.forEach { c ->
                CardSurface(Modifier.fillMaxWidth().clickable { navigate("client/${Uri.encode(c.key)}") }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Avatar(c.name)
                        Column(Modifier.weight(1f)) {
                            Text(c.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${c.attended} ${if (c.attended == 1) "visita" else "visitas"}${c.last?.let { " · última: " + dayLabel(it.date, "d MMM") } ?: ""}", color = Muted, fontSize = 12.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) { Text(money(c.spent), fontWeight = FontWeight.Bold, fontSize = 14.sp); Text("gastado", color = Muted, fontSize = 10.sp) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun OwnerClientDetail(state: FreshCutState, key: String, back: () -> Unit, navigate: (String) -> Unit) {
    val c = clientSummaries(state.appointments).find { it.key == key }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Cliente", back)
        if (c == null) EmptyState("Cliente no encontrado", "No hay citas asociadas a este cliente.", "users")
        else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Avatar(c.name, 84)
                    Text(c.name, fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    Text(listOf(c.phone, c.email).filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { "Sin datos de contacto" }, color = Muted, fontSize = 13.sp)
                }
                CardSurface(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth()) {
                        listOf(Triple("check", "Atendidas", "${c.attended}"), Triple("close", "Faltas y canc.", "${c.missed}"), Triple("money", "Gastado", money(c.spent))).forEachIndexed { i, (icon, label, value) ->
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) { Glyph(icon, if (i == 1) Red else Navy); Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text(label, color = Muted, fontSize = 11.sp) }
                            if (i < 2) VerticalDivider(Modifier.height(65.dp), color = Line)
                        }
                    }
                }
                val kept = c.visits.filter { it.billable }
                if (kept.isNotEmpty()) CardSurface(Modifier.fillMaxWidth()) {
                    Text("Preferencias", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    SummaryRow("cut", "Servicio favorito", services[kept.groupingBy { it.service }.eachCount().maxBy { it.value }.key].name)
                    SummaryRow("user", "Barbero de confianza", barbers[kept.groupingBy { it.barber }.eachCount().maxBy { it.value }.key].name)
                }
                Heading("Historial", "${c.visits.size} ${if (c.visits.size == 1) "cita" else "citas"}")
                c.visits.forEach { item -> OwnerAppointmentCard(item, { navigate("appt/${item.id}") }, showDate = true) }
            }
            BottomAction { PrimaryButton("Agendar cita", { navigate(newRoute(today(), client = c.name, phone = c.phone)) }, red = true, icon = "calendar") }
        }
    }
}

// ---------------------------------------------------------------- Negocio
@Composable
fun OwnerBusiness(state: FreshCutState, navigate: (String) -> Unit, message: (String) -> Unit) {
    var confirmLogout by remember { mutableStateOf(false) }
    var demoDialog by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(196.dp)) {
            Box(Modifier.fillMaxWidth().height(135.dp).background(Navy))
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) { Text("Mi negocio", Modifier.weight(1f), color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold); IconAction("clock", "Horario de atención", { navigate("hours") }, Color.White) }
            Canvas(Modifier.fillMaxWidth().height(20.dp).align(Alignment.Center)) { drawRect(Color.White); for (i in -2..20) { drawLine(if (i % 2 == 0) Red else Navy, Offset(i * 42.dp.toPx(), size.height), Offset((i * 42 + 24).dp.toPx(), 0f), 16.dp.toPx()) } }
            Box(Modifier.align(Alignment.BottomCenter).size(106.dp).border(4.dp, Color.White, CircleShape).padding(4.dp).background(Navy, CircleShape), contentAlignment = Alignment.Center) { Glyph("cut", Color.White, 46.dp) }
        }
        Column(Modifier.padding(horizontal = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("FreshCut Barbería", fontSize = 25.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp)); Text(state.ownerEmail, color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(22.dp))
            CardSurface(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth()) {
                    listOf(Triple("cut", "Servicios", "${services.count { it.active }}"), Triple("users", "Barberos", "${barbers.count { it.active }}"), Triple("clock", "Horario", "${openHour}–${closeHour}")).forEachIndexed { i, (icon, label, value) ->
                        Column(Modifier.weight(1f).clickable { navigate(listOf("services", "team", "hours")[i]) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) { Glyph(icon, if (i == 1) Red else Navy); Text(value, fontSize = 19.sp, fontWeight = FontWeight.Bold); Text(label, color = Muted, fontSize = 11.sp) }
                        if (i < 2) VerticalDivider(Modifier.height(65.dp), color = Line)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            CardSurface(Modifier.fillMaxWidth()) {
                MenuRow("cut", "Servicios y precios", "${services.count { it.active }} activos de ${services.size}", { navigate("services") }); HorizontalDivider(color = Line)
                MenuRow("users", "Equipo de barberos", "${barbers.count { it.active }} disponibles para reservar", { navigate("team") }); HorizontalDivider(color = Line)
                MenuRow("clock", "Horario de atención", hoursSummary(), { navigate("hours") }); HorizontalDivider(color = Line)
                MenuRow("edit", "Publicaciones del negocio", "Comparte un corte con tus clientes", { navigate("posts") })
                MenuRow("chart", "Datos de ejemplo", if (state.hasDemoData) "Cargados · toca para quitarlos" else "Llena el panel para probarlo", { demoDialog = true })
            }
            Spacer(Modifier.height(8.dp))
            MenuRow("logout", "Cerrar sesión del panel", onClick = { confirmLogout = true }, red = true)
            Text("FRESHCUT · PANEL DEL NEGOCIO", color = Muted, fontSize = 9.sp, letterSpacing = 1.2.sp, modifier = Modifier.padding(bottom = 22.dp))
        }
    }
    if (demoDialog) AlertDialog(onDismissRequest = { demoDialog = false },
        title = { Text(if (state.hasDemoData) "¿Quitar datos de ejemplo?" else "¿Cargar datos de ejemplo?") },
        text = { Text(if (state.hasDemoData) "Se borrarán las citas ficticias. Las reservas reales de clientes y las que agendaste tú se conservan." else "Se agregarán citas ficticias de la última semana y los próximos días para que veas el panel funcionando. Podrás quitarlas después.") },
        confirmButton = { TextButton({ if (state.hasDemoData) { state.clearDemo { message("Datos de ejemplo eliminados") } } else { state.seedDemo { message("Datos de ejemplo cargados") } }; demoDialog = false }) { Text(if (state.hasDemoData) "Quitar" else "Cargar", color = Red) } },
        dismissButton = { TextButton({ demoDialog = false }) { Text("Volver") } })
    if (confirmLogout) AlertDialog(onDismissRequest = { confirmLogout = false }, title = { Text("¿Cerrar sesión del panel?") }, text = { Text("Tu agenda, servicios y equipo seguirán guardados en este teléfono.") },
        confirmButton = { TextButton({ confirmLogout = false; state.ownerLogout() }) { Text("Cerrar sesión", color = Red) } }, dismissButton = { TextButton({ confirmLogout = false }) { Text("Volver") } })
}

// ---------------------------------------------------------------- Servicios
@Composable
fun OwnerServices(state: FreshCutState, navigate: (String) -> Unit, back: () -> Unit, message: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Servicios y precios", back) { IconAction("plus", "Agregar servicio", { navigate("service/-1") }) }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Lo que ofreces y cuánto cuesta. Los cambios se reflejan al instante en la app del cliente.", color = Muted, fontSize = 13.sp)
            services.forEachIndexed { i, item ->
                CardSurface(Modifier.fillMaxWidth().clickable { navigate("service/$i") }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Photo(item.photo, Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)))
                        Column(Modifier.weight(1f)) {
                            Text(item.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (item.active) Ink else Muted)
                            Text("${item.minutes} min · ${money(item.price)} MXN", color = Muted, fontSize = 12.sp)
                            if (!item.active) Text("Oculto para clientes", color = Red, fontSize = 11.sp)
                        }
                        Switch(item.active, { on ->
                            if (!on && services.count { it.active } <= 1) message("Debe quedar al menos un servicio activo.")
                            else { state.saveService(i, item.copy(active = on)) { message(if (on) "${item.name} ya está visible" else "${item.name} quedó oculto") } }
                        }, colors = SwitchDefaults.colors(checkedTrackColor = Red))
                    }
                }
            }
            Note("Para dejar de ofrecer un servicio, desactívalo. Las citas que ya lo usan se conservan con su precio.")
        }
    }
}

@Composable
fun OwnerServiceEditor(state: FreshCutState, index: Int, back: () -> Unit, message: (String) -> Unit) {
    val original = services.getOrNull(index)
    var name by rememberSaveable { mutableStateOf(original?.name ?: "") }
    var minutes by rememberSaveable { mutableStateOf(original?.minutes?.toString() ?: "30") }
    var cost by rememberSaveable { mutableStateOf(original?.price?.toString() ?: "") }
    var photo by rememberSaveable { mutableIntStateOf(original?.photo ?: 0) }
    var active by rememberSaveable { mutableStateOf(original?.active ?: true) }
    var error by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (original == null) "Nuevo servicio" else "Editar servicio", back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Photo(photo, Modifier.fillMaxWidth().height(150.dp).clip(Shape), name)
            Text("FOTO", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
            PhotoPicker(photo) { photo = it }
            OwnerField(name, { name = it.take(40); error = "" }, "Nombre del servicio", "cut")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { OwnerField(minutes, { value -> minutes = value.filter { it.isDigit() }.take(3); error = "" }, "Duración", "clock", KeyboardType.Number, "min") }
                Box(Modifier.weight(1f)) { OwnerField(cost, { value -> cost = value.filter { it.isDigit() }.take(5); error = "" }, "Precio", "money", KeyboardType.Number, "MXN") }
            }
            Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(12.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Visible para clientes", fontSize = 14.sp, fontWeight = FontWeight.Medium); Text("Si lo apagas, no aparecerá al reservar.", color = Muted, fontSize = 12.sp) }
                Switch(active, { active = it }, colors = SwitchDefaults.colors(checkedTrackColor = Red))
            }
            if (error.isNotEmpty()) Text(error, color = Red, fontSize = 12.sp)
            Note("La duración define qué horarios quedan libres para cada barbero.", "clock")
        }
        BottomAction {
            PrimaryButton(if (original == null) "Agregar servicio" else "Guardar cambios", {
                val m = minutes.toIntOrNull() ?: 0
                val p = cost.toIntOrNull() ?: 0
                error = when {
                    name.trim().length < 3 -> "Escribe un nombre de al menos 3 letras."
                    m !in 5..240 -> "La duración debe estar entre 5 y 240 minutos."
                    p <= 0 -> "Escribe un precio mayor a cero."
                    !active && services.withIndex().none { it.index != index && it.value.active } -> "Debe quedar al menos un servicio activo."
                    else -> ""
                }
                if (error.isEmpty()) { state.saveService(index, Service(name.trim(), m, p, photo, active)) { message(if (original == null) "Servicio agregado" else "Servicio actualizado"); back() } }
            }, red = true, icon = "check")
        }
    }
}

// ---------------------------------------------------------------- Equipo
@Composable
fun OwnerTeam(state: FreshCutState, navigate: (String) -> Unit, back: () -> Unit, message: (String) -> Unit) {
    val upcoming = state.appointments.filter { !it.cancelled && it.status == "" && it.start >= System.currentTimeMillis() }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Equipo de barberos", back) { IconAction("plus", "Agregar barbero", { navigate("barber/-1") }) }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Buenas manos. Grandes resultados. Desactiva a quien no esté disponible para que no reciba reservas.", color = Muted, fontSize = 13.sp)
            barbers.forEachIndexed { i, person ->
                val pending = upcoming.count { it.barber == i }
                CardSurface(Modifier.fillMaxWidth().clickable { navigate("barber/$i") }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Photo(person.photo, Modifier.size(60.dp).clip(CircleShape))
                        Column(Modifier.weight(1f)) {
                            Text(person.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (person.active) Ink else Muted)
                            Text(person.specialty, color = Muted, fontSize = 12.sp)
                            Text(if (person.active) "★ ${person.rating} · $pending ${if (pending == 1) "cita próxima" else "citas próximas"}" else "No recibe reservas", color = if (person.active) Navy else Red, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
                        }
                        Switch(person.active, { on ->
                            if (!on && barbers.count { it.active } <= 1) message("Debe quedar al menos un barbero activo.")
                            else { state.saveBarber(i, person.copy(active = on)) { message(if (on) "${person.name} vuelve a recibir reservas" else "${person.name} ya no recibe reservas nuevas") } }
                        }, colors = SwitchDefaults.colors(checkedTrackColor = Red))
                    }
                }
            }
            Note("Al desactivar a un barbero, sus citas ya agendadas se conservan. Revísalas en la Agenda.")
        }
    }
}

@Composable
fun OwnerBarberEditor(state: FreshCutState, index: Int, back: () -> Unit, message: (String) -> Unit) {
    val original = barbers.getOrNull(index)
    var name by rememberSaveable { mutableStateOf(original?.name ?: "") }
    var specialty by rememberSaveable { mutableStateOf(original?.specialty ?: "") }
    var photo by rememberSaveable { mutableIntStateOf(original?.photo ?: 4) }
    var active by rememberSaveable { mutableStateOf(original?.active ?: true) }
    var error by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (original == null) "Nuevo barbero" else "Editar barbero", back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Photo(photo, Modifier.size(110.dp).border(4.dp, Color.White, CircleShape).padding(4.dp).clip(CircleShape), name)
                if (original != null) Text("★ ${original.rating} · calificación de clientes", color = Navy, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
            Text("FOTO", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
            PhotoPicker(photo) { photo = it }
            OwnerField(name, { name = it.take(40); error = "" }, "Nombre completo", "user")
            OwnerField(specialty, { specialty = it.take(40); error = "" }, "Especialidad", "star")
            Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(12.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Recibe reservas", fontSize = 14.sp, fontWeight = FontWeight.Medium); Text("Si lo apagas, los clientes no podrán elegirlo.", color = Muted, fontSize = 12.sp) }
                Switch(active, { active = it }, colors = SwitchDefaults.colors(checkedTrackColor = Red))
            }
            if (error.isNotEmpty()) Text(error, color = Red, fontSize = 12.sp)
        }
        BottomAction {
            PrimaryButton(if (original == null) "Agregar al equipo" else "Guardar cambios", {
                error = when {
                    name.trim().length < 3 -> "Escribe el nombre del barbero."
                    barbers.withIndex().any { it.index != index && it.value.name.equals(name.trim(), true) } -> "Ya hay un barbero con ese nombre."
                    specialty.trim().length < 3 -> "Escribe su especialidad."
                    !active && barbers.withIndex().none { it.index != index && it.value.active } -> "Debe quedar al menos un barbero activo."
                    else -> ""
                }
                if (error.isEmpty()) { state.saveBarber(index, Barber(name.trim(), specialty.trim(), photo, original?.rating ?: "Nuevo", active)) { message(if (original == null) "Barbero agregado" else "Datos actualizados"); back() } }
            }, red = true, icon = "check")
        }
    }
}

// ---------------------------------------------------------------- Horario
@Composable
private fun HourStepper(label: String, value: Int, onChange: (Int) -> Unit, range: IntRange) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        IconButton({ onChange(value - 1) }, enabled = value - 1 in range) { Glyph("minus", if (value - 1 in range) Navy else Line, 18.dp) }
        Text(hour(value), Modifier.width(72.dp), textAlign = TextAlign.Center, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        IconButton({ onChange(value + 1) }, enabled = value + 1 in range) { Glyph("plus", if (value + 1 in range) Navy else Line, 18.dp) }
    }
}

@Composable
fun OwnerHours(state: FreshCutState, back: () -> Unit, message: (String) -> Unit) {
    var open by rememberSaveable { mutableIntStateOf(openHour) }
    var close by rememberSaveable { mutableIntStateOf(closeHour) }
    var closed by remember { mutableStateOf(closedWeekdays.toList()) }
    val preview = (open until close).map(::hour)
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Horario de atención", back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("Define cuándo pueden reservar tus clientes. Cada horario dura una hora.", color = Muted, fontSize = 13.sp)
            CardSurface(Modifier.fillMaxWidth()) {
                HourStepper("Abre", open, { open = it }, 6 until close)
                HorizontalDivider(color = Line)
                HourStepper("Cierra", close, { close = it }, (open + 1)..23)
            }
            Heading("Días de descanso", "Toca un día para marcarlo como cerrado.")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(2, 3, 4, 5, 6, 7, 1).zip(listOf("L", "M", "M", "J", "V", "S", "D")).forEach { (d, letter) ->
                    val off = d in closed
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (off) Red else Color.White).border(1.dp, if (off) Red else Line, RoundedCornerShape(12.dp)).clickable { closed = if (off) closed - d else closed + d }.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(letter, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (off) Color.White else Ink)
                        Text(if (off) "Cierra" else "Abre", fontSize = 8.sp, color = if (off) Color.White.copy(alpha = .85f) else Muted)
                    }
                }
            }
            Heading("Vista previa", "${preview.size} horarios por barbero cada día abierto")
            preview.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { slot -> Text(slot, Modifier.weight(1f).background(Color.White, RoundedCornerShape(10.dp)).border(1.dp, Line, RoundedCornerShape(10.dp)).padding(vertical = 10.dp), textAlign = TextAlign.Center, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Note("Las citas ya agendadas se conservan aunque cambies el horario; las verás en la Agenda.", "calendar")
        }
        BottomAction {
            PrimaryButton("Guardar horario", {
                if (closed.size >= 7) message("Deja al menos un día abierto.")
                else { state.saveHours(open, close, closed) { message("Horario actualizado"); back() } }
            }, red = true, icon = "check")
        }
    }
}
