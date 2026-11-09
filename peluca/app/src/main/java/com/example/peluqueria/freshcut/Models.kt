package com.example.peluqueria.freshcut

import android.content.Context
import androidx.compose.runtime.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val spanish = Locale.forLanguageTag("es-MX")
fun dayLabel(millis: Long, pattern: String = "EEE, d 'de' MMM"): String = SimpleDateFormat(pattern, spanish).format(millis)
fun today(): Long = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
fun nextDay(millis: Long, count: Int): Long = Calendar.getInstance().apply { timeInMillis = millis; add(Calendar.DAY_OF_MONTH, count) }.timeInMillis
fun appointmentTime(day: Long, time: String): Long = Calendar.getInstance().apply { timeInMillis = day; set(Calendar.HOUR_OF_DAY, time.substringBefore(':').toInt()); set(Calendar.MINUTE, time.substringAfter(':').toInt()) }.timeInMillis
val timeSlots = listOf("10:00", "11:00", "12:00", "13:00", "14:00", "15:00", "16:00", "17:00", "18:00")

data class Cut(val id: Int, val name: String, val category: String, val description: String, val photo: Int, val likes: String)
val cuts = listOf(
    Cut(0, "Fade Medio", "Fade", "Un degradado limpio en los laterales con volumen y textura en la parte superior. Un estilo versátil que se adapta a tu día a día.", 0, "2.3k"),
    Cut(1, "Taper Classic", "Clásicos", "Contornos suaves y un acabado natural. El equilibrio perfecto entre un look clásico y un toque contemporáneo.", 1, "1.8k"),
    Cut(2, "Pompadour", "Clásicos", "Volumen hacia atrás, laterales definidos y mucha personalidad. Ideal para quienes buscan un acabado elegante.", 2, "1.2k"),
    Cut(3, "French Crop", "Modernos", "Flequillo corto y textura natural con laterales degradados. Práctico, fresco y fácil de peinar.", 3, "980"),
    Cut(4, "Curly Fade", "Fade", "Tus rizos como protagonistas. Un degradado suave realza la textura y mantiene una silueta definida.", 4, "1.6k"),
    Cut(5, "Buzz Cut", "Modernos", "Corto, preciso y sin complicaciones. Un corte uniforme con un degradado que aporta definición.", 5, "860")
)
data class Service(val name: String, val minutes: Int, val price: Int, val photo: Int)
val services = listOf(Service("Corte de cabello", 30, 150, 0), Service("Corte + barba", 45, 220, 1), Service("Arreglo de barba", 20, 100, 6), Service("Diseño de ceja", 15, 50, 2))
data class Barber(val name: String, val specialty: String, val photo: Int, val rating: String)
val barbers = listOf(Barber("Jason Miller", "Especialista en fades", 6, "4.9"), Barber("Carlos Ramírez", "Clásicos y barba", 2, "4.8"), Barber("Diego Torres", "Textura y estilo urbano", 4, "4.9"))
data class Appointment(val id: String, val service: Int, val date: Long, val time: String, val barber: Int, val cut: Int, val cancelled: Boolean = false)

class FreshCutState(context: Context) {
    private val prefs = context.getSharedPreferences("freshcut_local", Context.MODE_PRIVATE)
    var signedIn by mutableStateOf(prefs.getBoolean("signedIn", false)); private set
    var name by mutableStateOf(prefs.getString("name", "David Iram") ?: "David Iram"); private set
    var email by mutableStateOf(prefs.getString("email", "david@ejemplo.com") ?: "david@ejemplo.com"); private set
    var reminders by mutableStateOf(prefs.getBoolean("reminders", true)); private set
    val favorites = mutableStateListOf<Int>().apply { addAll(prefs.getStringSet("favorites", emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }) }
    val likedPosts = mutableStateListOf<Int>()
    val savedPosts = mutableStateListOf<Int>()
    val appointments = mutableStateListOf<Appointment>()
    val publications = mutableStateListOf<String>()
    init {
        runCatching {
            val array = JSONArray(prefs.getString("appointments", "[]"))
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                appointments.add(Appointment(o.getString("id"), o.getInt("service"), o.getLong("date"), o.getString("time"), o.getInt("barber"), o.optInt("cut", 0), o.optBoolean("cancelled")))
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
    fun saveAppointment(appointment: Appointment) {
        appointments.removeAll { it.id == appointment.id }; appointments.add(appointment); persistAppointments()
    }
    fun cancel(id: String) { val i = appointments.indexOfFirst { it.id == id }; if (i >= 0) { appointments[i] = appointments[i].copy(cancelled = true); persistAppointments() } }
    fun slotAvailable(date: Long, time: String, barber: Int, service: Int, except: String = ""): Boolean {
        val start = appointmentTime(date, time)
        val end = start + services[service].minutes * 60_000L
        return start > System.currentTimeMillis() && appointments.none {
            val other = appointmentTime(it.date, it.time)
            !it.cancelled && it.id != except && it.barber == barber && start < other + services[it.service].minutes * 60_000L && end > other
        }
    }
    private fun persistAppointments() {
        val array = JSONArray()
        appointments.forEach { array.put(JSONObject().put("id", it.id).put("service", it.service).put("date", it.date).put("time", it.time).put("barber", it.barber).put("cut", it.cut).put("cancelled", it.cancelled)) }
        prefs.edit().putString("appointments", array.toString()).apply()
    }
}
