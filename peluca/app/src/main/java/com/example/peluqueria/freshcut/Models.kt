package com.example.peluqueria.freshcut

import android.content.Context
import androidx.compose.runtime.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.random.Random

private val spanish = Locale.forLanguageTag("es-MX")
fun dayLabel(millis: Long, pattern: String = "EEE, d 'de' MMM"): String = SimpleDateFormat(pattern, spanish).format(millis)
fun today(): Long = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
fun nextDay(millis: Long, count: Int): Long = Calendar.getInstance().apply { timeInMillis = millis; add(Calendar.DAY_OF_MONTH, count) }.timeInMillis
fun appointmentTime(day: Long, time: String): Long = Calendar.getInstance().apply { timeInMillis = day; set(Calendar.HOUR_OF_DAY, time.substringBefore(':').toInt()); set(Calendar.MINUTE, time.substringAfter(':').toInt()) }.timeInMillis
fun weekday(millis: Long): Int = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.DAY_OF_WEEK)
fun money(value: Int): String = "$" + NumberFormat.getIntegerInstance(spanish).format(value)

// Horario de atención (lo edita el dueño). Los horarios disponibles se calculan a partir de él.
var openHour by mutableIntStateOf(10)
var closeHour by mutableIntStateOf(19)
/** Días cerrados, con los valores de Calendar.DAY_OF_WEEK (1 = domingo … 7 = sábado). */
val closedWeekdays = mutableStateListOf<Int>()
val timeSlots: List<String> get() = (openHour until closeHour).map { String.format(Locale.US, "%02d:00", it) }
fun isClosedDay(millis: Long): Boolean = weekday(millis) in closedWeekdays

data class Cut(val id: Int, val name: String, val category: String, val description: String, val photo: Int, val likes: String)
val cuts = listOf(
    Cut(0, "Fade Medio", "Fade", "Un degradado limpio en los laterales con volumen y textura en la parte superior. Un estilo versátil que se adapta a tu día a día.", 0, "2.3k"),
    Cut(1, "Taper Classic", "Clásicos", "Contornos suaves y un acabado natural. El equilibrio perfecto entre un look clásico y un toque contemporáneo.", 1, "1.8k"),
    Cut(2, "Pompadour", "Clásicos", "Volumen hacia atrás, laterales definidos y mucha personalidad. Ideal para quienes buscan un acabado elegante.", 2, "1.2k"),
    Cut(3, "French Crop", "Modernos", "Flequillo corto y textura natural con laterales degradados. Práctico, fresco y fácil de peinar.", 3, "980"),
    Cut(4, "Curly Fade", "Fade", "Tus rizos como protagonistas. Un degradado suave realza la textura y mantiene una silueta definida.", 4, "1.6k"),
    Cut(5, "Buzz Cut", "Modernos", "Corto, preciso y sin complicaciones. Un corte uniforme con un degradado que aporta definición.", 5, "860")
)

// Servicios y barberos son editables por el dueño. No se eliminan (las citas guardadas los
// referencian por posición): se desactivan para ocultarlos a los clientes.
data class Service(val name: String, val minutes: Int, val price: Int, val photo: Int, val active: Boolean = true)
val services = mutableStateListOf(Service("Corte de cabello", 30, 150, 0), Service("Corte + barba", 45, 220, 1), Service("Arreglo de barba", 20, 100, 6), Service("Diseño de ceja", 15, 50, 2))
data class Barber(val name: String, val specialty: String, val photo: Int, val rating: String, val active: Boolean = true)
val barbers = mutableStateListOf(Barber("Jason Miller", "Especialista en fades", 6, "4.9"), Barber("Carlos Ramírez", "Clásicos y barba", 2, "4.8"), Barber("Diego Torres", "Textura y estilo urbano", 4, "4.9"))
fun firstActiveService(): Int = services.indexOfFirst { it.active }.coerceAtLeast(0)
fun firstActiveBarber(): Int = barbers.indexOfFirst { it.active }.coerceAtLeast(0)

/** status: "" pendiente, "done" atendida, "noshow" no asistió. clientEmail vacío = cita creada por el negocio. */
data class Appointment(
    val id: String, val service: Int, val date: Long, val time: String, val barber: Int, val cut: Int, val cancelled: Boolean = false,
    val client: String = "", val phone: String = "", val clientEmail: String = "", val status: String = ""
) {
    val start: Long get() = appointmentTime(date, time)
    /** Cuenta para ingresos: no cancelada y el cliente sí asistió (o aún no ocurre). */
    val billable: Boolean get() = !cancelled && status != "noshow"
}

class FreshCutState(context: Context) {
    private val prefs = context.getSharedPreferences("freshcut_local", Context.MODE_PRIVATE)
    var signedIn by mutableStateOf(prefs.getBoolean("signedIn", false)); private set
    var name by mutableStateOf(prefs.getString("name", "David Iram") ?: "David Iram"); private set
    var email by mutableStateOf(prefs.getString("email", "david@ejemplo.com") ?: "david@ejemplo.com"); private set
    var reminders by mutableStateOf(prefs.getBoolean("reminders", true)); private set
    var ownerSignedIn by mutableStateOf(prefs.getBoolean("ownerSignedIn", false)); private set
    var ownerEmail by mutableStateOf(prefs.getString("ownerEmail", "dueno@freshcut.mx") ?: "dueno@freshcut.mx"); private set
    val favorites = mutableStateListOf<Int>().apply { addAll(prefs.getStringSet("favorites", emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }) }
    val likedPosts = mutableStateListOf<Int>()
    val savedPosts = mutableStateListOf<Int>()
    val appointments = mutableStateListOf<Appointment>()
    val publications = mutableStateListOf<String>()
    /** Citas del cliente con sesión iniciada (excluye las que agenda el negocio para otros clientes). */
    val myAppointments: List<Appointment> get() = appointments.filter { it.clientEmail == email }
    init {
        runCatching { loadCatalog() }
        runCatching {
            val array = JSONArray(prefs.getString("appointments", "[]"))
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                appointments.add(Appointment(
                    o.getString("id"), o.getInt("service").coerceIn(0, services.lastIndex), o.getLong("date"), o.getString("time"), o.getInt("barber").coerceIn(0, barbers.lastIndex), o.optInt("cut", 0), o.optBoolean("cancelled"),
                    // Las citas guardadas antes de existir el panel del dueño pertenecen al cliente local.
                    o.optString("client", name), o.optString("phone", ""), o.optString("clientEmail", email), o.optString("status", "")
                ))
            }
            val posts = JSONArray(prefs.getString("publications", "[]"))
            for (i in 0 until posts.length()) publications.add(posts.getString(i))
        }
        likedPosts.addAll(prefs.getStringSet("likes", emptySet()).orEmpty().mapNotNull { it.toIntOrNull() })
        savedPosts.addAll(prefs.getStringSet("saved", emptySet()).orEmpty().mapNotNull { it.toIntOrNull() })
    }
    fun login(userName: String, userEmail: String) {
        name = userName.ifBlank { "David Iram" }; email = userEmail; signedIn = true
        prefs.edit().putBoolean("signedIn", true).putString("name", name).putString("email", email).apply()
    }
    fun logout() { signedIn = false; prefs.edit().putBoolean("signedIn", false).apply() }
    fun updateProfile(userName: String, userEmail: String) { name = userName; email = userEmail; prefs.edit().putString("name", name).putString("email", email).apply() }
    fun toggleFavorite(id: Int) { if (!favorites.remove(id)) favorites.add(id); prefs.edit().putStringSet("favorites", favorites.map { it.toString() }.toSet()).apply() }
    fun togglePost(id: Int, saved: Boolean = false) {
        val list = if (saved) savedPosts else likedPosts
        if (!list.remove(id)) list.add(id)
        prefs.edit().putStringSet(if (saved) "saved" else "likes", list.map { it.toString() }.toSet()).apply()
    }
    fun setReminder(value: Boolean) { reminders = value; prefs.edit().putBoolean("reminders", value).apply() }
    fun publish(text: String) { publications.add(0, text); prefs.edit().putString("publications", JSONArray(publications.toList()).toString()).apply() }
    /** Reserva hecha por el cliente desde la app: se asocia a su nombre y correo. */
    fun saveAppointment(appointment: Appointment) = saveOwnerAppointment(appointment.copy(client = name, clientEmail = email, status = ""))
    /** Reserva hecha por el negocio (cliente en mostrador, por teléfono…): se guarda tal cual. */
    fun saveOwnerAppointment(appointment: Appointment) {
        appointments.removeAll { it.id == appointment.id }; appointments.add(appointment); persistAppointments()
    }
    fun cancel(id: String) { val i = appointments.indexOfFirst { it.id == id }; if (i >= 0) { appointments[i] = appointments[i].copy(cancelled = true); persistAppointments() } }
    fun setStatus(id: String, status: String) { val i = appointments.indexOfFirst { it.id == id }; if (i >= 0) { appointments[i] = appointments[i].copy(status = status); persistAppointments() } }
    fun slotAvailable(date: Long, time: String, barber: Int, service: Int, except: String = ""): Boolean {
        if (isClosedDay(date) || !barbers[barber].active) return false
        val start = appointmentTime(date, time)
        val end = start + services[service].minutes * 60_000L
        return start > System.currentTimeMillis() && appointments.none {
            val other = appointmentTime(it.date, it.time)
            !it.cancelled && it.id != except && it.barber == barber && start < other + services[it.service].minutes * 60_000L && end > other
        }
    }
    private fun persistAppointments() {
        val array = JSONArray()
        appointments.forEach {
            array.put(JSONObject().put("id", it.id).put("service", it.service).put("date", it.date).put("time", it.time).put("barber", it.barber).put("cut", it.cut).put("cancelled", it.cancelled)
                .put("client", it.client).put("phone", it.phone).put("clientEmail", it.clientEmail).put("status", it.status))
        }
        prefs.edit().putString("appointments", array.toString()).apply()
    }

    // ---------- Panel del dueño ----------
    fun ownerLogin(userEmail: String) { ownerEmail = userEmail; ownerSignedIn = true; prefs.edit().putBoolean("ownerSignedIn", true).putString("ownerEmail", userEmail).apply() }
    fun ownerLogout() { ownerSignedIn = false; prefs.edit().putBoolean("ownerSignedIn", false).apply() }
    fun saveService(index: Int, service: Service) { if (index in services.indices) services[index] = service else services.add(service); persistCatalog() }
    fun saveBarber(index: Int, barber: Barber) { if (index in barbers.indices) barbers[index] = barber else barbers.add(barber); persistCatalog() }
    fun saveHours(open: Int, close: Int, closed: List<Int>) {
        openHour = open; closeHour = close; closedWeekdays.clear(); closedWeekdays.addAll(closed.distinct().sorted()); persistCatalog()
    }
    private fun loadCatalog() {
        prefs.getString("services", null)?.let { raw ->
            val a = JSONArray(raw)
            val loaded = (0 until a.length()).map { a.getJSONObject(it) }.map { Service(it.getString("name"), it.getInt("minutes"), it.getInt("price"), it.getInt("photo"), it.optBoolean("active", true)) }
            if (loaded.isNotEmpty()) { services.clear(); services.addAll(loaded) }
        }
        prefs.getString("barbers", null)?.let { raw ->
            val a = JSONArray(raw)
            val loaded = (0 until a.length()).map { a.getJSONObject(it) }.map { Barber(it.getString("name"), it.getString("specialty"), it.getInt("photo"), it.getString("rating"), it.optBoolean("active", true)) }
            if (loaded.isNotEmpty()) { barbers.clear(); barbers.addAll(loaded) }
        }
        openHour = prefs.getInt("openHour", 10)
        closeHour = prefs.getInt("closeHour", 19)
        closedWeekdays.clear(); closedWeekdays.addAll(prefs.getStringSet("closedWeekdays", emptySet()).orEmpty().mapNotNull { it.toIntOrNull() })
    }
    private fun persistCatalog() {
        val s = JSONArray(); services.forEach { s.put(JSONObject().put("name", it.name).put("minutes", it.minutes).put("price", it.price).put("photo", it.photo).put("active", it.active)) }
        val b = JSONArray(); barbers.forEach { b.put(JSONObject().put("name", it.name).put("specialty", it.specialty).put("photo", it.photo).put("rating", it.rating).put("active", it.active)) }
        prefs.edit().putString("services", s.toString()).putString("barbers", b.toString()).putInt("openHour", openHour).putInt("closeHour", closeHour)
            .putStringSet("closedWeekdays", closedWeekdays.map { it.toString() }.toSet()).apply()
    }
    val hasDemoData: Boolean get() = appointments.any { it.id.startsWith("demo-") }
    /** Genera citas ficticias de la última semana y los próximos días para explorar el panel. */
    fun seedDemo() {
        val random = Random(7)
        val clients = listOf("Luis Hernández" to "662 118 4502", "Miguel Ángel Soto" to "662 247 9031", "Andrés Navarro" to "662 390 1187", "Jorge Castillo" to "662 455 6720", "Fernando Ruiz" to "662 501 3349",
            "Ricardo Medina" to "662 612 0985", "Emilio Vargas" to "662 734 2268", "Sebastián Ortiz" to "662 829 5514", "Pablo Reyes" to "662 903 7742", "Iván Morales" to "662 145 8836")
        val now = System.currentTimeMillis()
        val generated = mutableListOf<Appointment>()
        for (offset in -6..4) {
            val day = nextDay(today(), offset)
            if (isClosedDay(day)) continue
            barbers.indices.filter { barbers[it].active }.forEach { barber ->
                timeSlots.filter { random.nextInt(100) < (if (offset < 0) 55 else 40) }.forEach { slot ->
                    val (client, phone) = clients[random.nextInt(clients.size)]
                    val service = services.indices.filter { services[it].active }.let { if (it.isEmpty()) 0 else it[random.nextInt(it.size)] }
                    val past = appointmentTime(day, slot) < now
                    val roll = random.nextInt(100)
                    generated += Appointment("demo-$offset-$barber-$slot", service, day, slot, barber, random.nextInt(cuts.size),
                        cancelled = roll < 8, client = client, phone = phone, status = if (past && roll >= 8) (if (roll < 14) "noshow" else "done") else "")
                }
            }
        }
        // Respeta las citas reales: no agrega una de ejemplo donde ya hay otra con el mismo barbero.
        generated.filter { g -> appointments.none { it.id == g.id || !it.cancelled && it.barber == g.barber && it.date == g.date && it.time == g.time } }.forEach { appointments.add(it) }
        persistAppointments()
    }
    fun clearDemo() { appointments.removeAll { it.id.startsWith("demo-") }; persistAppointments() }
}
