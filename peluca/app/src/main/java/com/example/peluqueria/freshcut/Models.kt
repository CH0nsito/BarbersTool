package com.example.peluqueria.freshcut

import android.content.Context
import androidx.compose.runtime.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    val client: String = "", val phone: String = "", val clientEmail: String = "", val status: String = "",
    val serviceName: String = services[service].name,
    val serviceMinutes: Int = services[service].minutes,
    val servicePrice: Int = services[service].price,
    val clientId: String = "", val demo: Boolean = false
) {
    val start: Long get() = appointmentTime(date, time)
    /** Cuenta para ingresos: no cancelada y el cliente sí asistió (o aún no ocurre). */
    val billable: Boolean get() = !cancelled && status != "noshow"
}

data class FeedPost(val id: Int, val cut: Int, val author: String, val body: String, val created: Long, val likes: Int, val comments: Int, val reason: String)

/** The backend is the source of truth. Old demo preferences are intentionally not imported. */
class FreshCutState(context: Context) {
    private val prefs = context.getSharedPreferences("freshcut_backend_v1", Context.MODE_PRIVATE)
    private val vault = SessionVault(context)
    private val api = BackendClient().apply { baseUrl = prefs.getString("url", "http://127.0.0.1:8080")!!; token = vault.read() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lock = Mutex()
    var serverUrl by mutableStateOf(api.baseUrl); private set
    var signedIn by mutableStateOf(api.token.isNotEmpty()); private set
    var ownerSignedIn by mutableStateOf(signedIn && prefs.getString("role", "") == "owner"); private set
    var name by mutableStateOf(prefs.getString("name", "Invitado")!!); private set
    var email by mutableStateOf(prefs.getString("email", "")!!); private set
    var ownerEmail by mutableStateOf(email); private set
    var userId by mutableStateOf(prefs.getString("id", "")!!); private set
    var reminders by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var connected by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null)
    var showConnection by mutableStateOf(false)
    var connectionMessage by mutableStateOf("Conectando con el servidor…"); private set
    val favorites = mutableStateListOf<Int>()
    val likedPosts = mutableStateListOf<Int>()
    val savedPosts = mutableStateListOf<Int>()
    val appointments = mutableStateListOf<Appointment>()
    val publications = mutableStateListOf<String>()
    val feed = mutableStateListOf<FeedPost>()
    private var availabilityJob: Job? = null
    val availableSlots = mutableStateListOf<String>()
    var availabilityBusy by mutableStateOf(false); private set
    private var feedJob: Job? = null
    val postComments = mutableStateListOf<String>()
    var feedMode by mutableStateOf("Para ti"); private set
    var feedBusy by mutableStateOf(false); private set
    val myAppointments: List<Appointment> get() = appointments.filter { it.clientId == userId && userId.isNotEmpty() }
    val hasDemoData: Boolean get() = appointments.any { it.demo }

    private fun handleError(e: Exception, silent: Boolean = false) {
        if (e is CancellationException) throw e
        if ((e as? BackendException)?.status == 401) clearSession()
        if ((e as? BackendException)?.status == 0) { connected = false; connectionMessage = "Sin conexión · no se han guardado cambios" }
        if (!silent) error = e.message ?: "No se pudo completar la operación."
    }
    private fun perform(onSuccess: () -> Unit = {}, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { lock.withLock { block() }; onSuccess() }
            catch (e: Exception) { handleError(e) }
            finally { busy = false }
        }
    }
    private suspend fun loadSnapshot() {
        val data = api.request("GET", if (api.token.isEmpty()) "/v1/catalog" else "/v1/snapshot")
        applyCatalog(data)
        data.optJSONObject("user")?.let { applyUser(it) }
        if (api.token.isNotEmpty()) {
            appointments.clear()
            val array = data.getJSONArray("appointments")
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                appointments.add(Appointment(o.getString("id"), o.getInt("service"), o.getLong("date"), o.getString("time"), o.getInt("barber"), o.getInt("cut"), o.getBoolean("cancelled"), o.getString("client"), o.getString("phone"), o.getString("clientEmail"), o.getString("status"), o.getString("serviceName"), o.getInt("serviceMinutes"), o.getInt("servicePrice"), o.getString("clientId"), o.getBoolean("demo")))
            }
            fun ints(key: String, target: MutableList<Int>) { target.clear(); val a = data.getJSONArray(key); for (i in 0 until a.length()) target.add(a.getInt(i)) }
            ints("favorites", favorites); ints("likedPosts", likedPosts); ints("savedPosts", savedPosts)
            publications.clear(); val posts = data.getJSONArray("publications"); for (i in 0 until posts.length()) publications.add(posts.getString(i))
        }
        connected = true; connectionMessage = "Conectado · ${serverUrl.substringAfter("://")}"
    }
    private fun applyCatalog(data: JSONObject) {
        services.clear(); val s = data.getJSONArray("services")
        for (i in 0 until s.length()) { val o=s.getJSONObject(i); services.add(Service(o.getString("name"),o.getInt("minutes"),o.getInt("price"),o.getInt("photo"),o.getBoolean("active"))) }
        barbers.clear(); val b = data.getJSONArray("barbers")
        for (i in 0 until b.length()) { val o=b.getJSONObject(i); barbers.add(Barber(o.getString("name"),o.getString("specialty"),o.getInt("photo"),o.getString("rating"),o.getBoolean("active"))) }
        openHour=data.getInt("openHour");closeHour=data.getInt("closeHour");closedWeekdays.clear()
        val days=data.getJSONArray("closedWeekdays");for(i in 0 until days.length())closedWeekdays.add(days.getInt(i))
    }
    private fun applyUser(u: JSONObject) {
        userId=u.getString("id");name=u.getString("name");email=u.getString("email");ownerEmail=email
        signedIn=true;ownerSignedIn=u.getString("role")=="owner";reminders=u.getBoolean("reminders")
        prefs.edit().putString("id",userId).putString("name",name).putString("email",email).putString("role",u.getString("role")).apply()
    }
    fun refresh(silent: Boolean = false) {
        if (busy || lock.isLocked) return
        scope.launch { try { lock.withLock { loadSnapshot() } } catch(e: Exception) { handleError(e,silent) } }
    }
    fun configureServer(url: String) {
        val trimmed=url.trim().trimEnd('/')
        val parsed=runCatching { java.net.URI(trimmed) }.getOrNull()
        if(parsed?.scheme !in listOf("http","https") || parsed?.host.isNullOrBlank() || !parsed?.rawUserInfo.isNullOrEmpty() || !parsed?.rawQuery.isNullOrEmpty() || !parsed?.rawFragment.isNullOrEmpty() || !parsed?.path.isNullOrEmpty()) { error="Usa una dirección como http://192.168.1.50:8080, sin rutas ni credenciales."; return }
        if(busy || lock.isLocked) { error="Espera a que termine la operación actual."; return }
        if(trimmed!=serverUrl) {clearSession();api.baseUrl=trimmed;serverUrl=trimmed;prefs.edit().putString("url",trimmed).apply()}
        showConnection=false;refresh()
    }
    fun login(userName: String, userEmail: String, password: String, register: Boolean = false, owner: Boolean = false, onSuccess: () -> Unit = {}) = perform(onSuccess) {
        val response=api.request("POST",if(register)"/v1/auth/register" else "/v1/auth/login",JSONObject().put("name",userName).put("email",userEmail).put("password",password).put("owner",owner))
        api.token=response.getString("token");vault.write(api.token);applyUser(response.getJSONObject("user"));loadSnapshot()
    }
    fun ownerLogin(userEmail: String, password: String) = login("",userEmail,password,owner=true)
    private fun clearSession() {
        feedJob?.cancel();availabilityJob?.cancel();availableSlots.clear();postComments.clear()
        api.token="";vault.write("");signedIn=false;ownerSignedIn=false;userId="";name="Invitado";email="";ownerEmail=""
        appointments.clear();favorites.clear();likedPosts.clear();savedPosts.clear();publications.clear();feed.clear()
        prefs.edit().remove("role").remove("id").remove("name").remove("email").apply()
    }
    fun logout() = perform { api.request("POST","/v1/auth/logout",JSONObject());clearSession();loadSnapshot() }
    fun ownerLogout() = logout()
    fun deleteAccount(onSuccess: () -> Unit) = perform(onSuccess) { api.request("DELETE","/v1/me");clearSession();loadSnapshot() }
    private fun write(method: String, path: String, body: JSONObject, onSuccess: () -> Unit = {}) {
        if (!signedIn) { error = "Inicia sesión o crea una cuenta para guardar cambios."; return }
        perform(onSuccess) { api.request(method,path,body);loadSnapshot() }
    }
    fun updateProfile(userName: String,userEmail: String,onSuccess: () -> Unit = {}) = write("PUT","/v1/me",JSONObject().put("name",userName).put("email",userEmail),onSuccess)
    fun toggleFavorite(id: Int) = write("PUT","/v1/favorites",JSONObject().put("cut",id).put("active",id !in favorites))
    fun togglePost(id: Int,saved: Boolean=false) {
        val active = id !in if(saved)savedPosts else likedPosts
        write("PUT","/v1/reactions",JSONObject().put("post",id).put("kind",if(saved)"save" else "like").put("active",active)) {
            val index=feed.indexOfFirst { it.id==id }
            if(index>=0 && !saved) feed[index]=feed[index].copy(likes=(feed[index].likes+if(active)1 else -1).coerceAtLeast(0))
            if(saved && !active && feedMode=="Guardados")feed.removeAll {it.id==id}
        }
    }
    fun setReminder(value: Boolean) = write("PUT","/v1/preferences",JSONObject().put("reminders",value))
    fun publish(text: String, cut: Int=0, onSuccess: () -> Unit = {}) = write("POST","/v1/posts",JSONObject().put("body",text).put("cut",cut),onSuccess)
    private fun appointmentBody(a: Appointment) = JSONObject().put("id",a.id).put("service",a.service).put("barber",a.barber).put("day",dayLabel(a.date,"yyyy-MM-dd")).put("time",a.time).put("cut",a.cut).put("client",a.client).put("phone",a.phone)
    fun saveAppointment(a: Appointment,onSuccess: () -> Unit = {}) = write(if(appointments.any { it.id==a.id })"PUT" else "POST",if(appointments.any { it.id==a.id })"/v1/appointments/${a.id}" else "/v1/appointments",appointmentBody(a),onSuccess)
    fun saveOwnerAppointment(a: Appointment,onSuccess: () -> Unit = {}) = saveAppointment(a,onSuccess)
    fun cancel(id: String,onSuccess: () -> Unit = {}) = write("PUT","/v1/appointments/$id",JSONObject().put("action","cancel"),onSuccess)
    fun setStatus(id: String,status: String,onSuccess: () -> Unit = {}) = write("PUT","/v1/appointments/$id",JSONObject().put("action","status").put("status",status),onSuccess)
    fun saveService(index: Int,s: Service,onSuccess: () -> Unit = {}) = write("PUT","/v1/owner/services/${if(index in services.indices)index else services.size}",JSONObject().put("name",s.name).put("minutes",s.minutes).put("price",s.price).put("photo",s.photo).put("active",s.active),onSuccess)
    fun saveBarber(index: Int,b: Barber,onSuccess: () -> Unit = {}) = write("PUT","/v1/owner/barbers/${if(index in barbers.indices)index else barbers.size}",JSONObject().put("name",b.name).put("specialty",b.specialty).put("photo",b.photo).put("active",b.active),onSuccess)
    fun saveHours(open: Int,close: Int,closed: List<Int>,onSuccess: () -> Unit = {}) = write("PUT","/v1/owner/hours",JSONObject().put("openHour",open).put("closeHour",close).put("closedWeekdays",JSONArray(closed)),onSuccess)
    fun seedDemo(onSuccess: () -> Unit = {}) = write("POST","/v1/owner/demo",JSONObject(),onSuccess)
    fun clearDemo(onSuccess: () -> Unit = {}) = write("DELETE","/v1/owner/demo",JSONObject(),onSuccess)
    fun loadAvailability(date: Long, barber: Int, service: Int, except: String = "") {
        availabilityJob?.cancel(); availableSlots.clear(); availabilityBusy = true
        val expectedUser = userId
        availabilityJob = scope.launch {
            try {
                val q = "/v1/availability?day=${dayLabel(date, "yyyy-MM-dd")}&barber=$barber&service=$service&except=$except"
                val slots = api.request("GET", q).getJSONArray("slots")
                if (expectedUser == userId) for (i in 0 until slots.length()) availableSlots.add(slots.getString(i))
            } catch (e: Exception) { handleError(e) } finally { availabilityBusy = false }
        }
    }
    fun slotAvailable(date: Long,time: String,barber: Int,service: Int,except: String=""): Boolean {
        if(service !in services.indices || barber !in barbers.indices || isClosedDay(date) || !barbers[barber].active || !services[service].active || time !in timeSlots)return false
        val start=appointmentTime(date,time)
        val duration=appointments.find {it.id==except&&it.service==service}?.serviceMinutes?:services[service].minutes
        val end=start+duration*60_000L
        val closing=Calendar.getInstance().apply {timeInMillis=date;set(Calendar.HOUR_OF_DAY,closeHour);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)}.timeInMillis
        return start>System.currentTimeMillis() && end<=closing && appointments.none { !it.cancelled&&it.id!=except&&it.barber==barber&&start<it.start+it.serviceMinutes*60_000L&&end>it.start }
    }
    // Recommendation requests are independent of catalog synchronization.
    fun loadFeed(mode: String="Para ti") {
        if(!signedIn)return
        feedJob?.cancel()
        val expectedUser=userId
        feedMode=mode;feedBusy=true
        feedJob=scope.launch {
            try {
                val sort=when(mode){"Recientes"->"recent";"Más gustados"->"popular";"Guardados"->"saved";else->"personal"}
                val result=api.request("GET","/v1/feed?sort=$sort")
                val a=result.getJSONArray("posts");val items=mutableListOf<FeedPost>()
                for(i in 0 until a.length()){val o=a.getJSONObject(i);items.add(FeedPost(o.getInt("id"),o.getInt("cut"),o.getString("author"),o.getString("body"),o.getLong("created"),o.getInt("likes"),o.getInt("comments"),o.getString("reason")))}
                if(userId==expectedUser) { feed.clear();feed.addAll(items) }
            }catch(e:Exception){handleError(e)}finally{feedBusy=false}
        }
    }
    fun event(post: Int?,cut: Int,kind: String,value: Double=1.0) {
        if(!signedIn)return
        val tokenAtStart=api.token
        scope.launch { try { if(tokenAtStart==api.token)api.request("POST","/v1/events",JSONObject().put("id",java.util.UUID.randomUUID().toString()).put("post",post?:JSONObject.NULL).put("cut",cut).put("kind",kind).put("value",value)) }catch(_:Exception){ /* Exposure telemetry may be dropped offline; do not interrupt browsing. */ } }
    }
    fun hidePost(post: FeedPost) = perform {api.request("PUT","/v1/reactions",JSONObject().put("post",post.id).put("kind","hide").put("active",true));feed.removeAll {it.id==post.id}}
    fun resetRecommendations(onSuccess: () -> Unit = {}) = perform(onSuccess) {api.request("DELETE","/v1/recommendations/history");feed.clear()}
    fun comment(post: Int,text: String,onSuccess: () -> Unit) = write("POST","/v1/comments",JSONObject().put("post",post).put("body",text)) {
        val i=feed.indexOfFirst {it.id==post};if(i>=0)feed[i]=feed[i].copy(comments=feed[i].comments+1)
        onSuccess()
    }
    fun loadComments(post: Int) {
        postComments.clear()
        scope.launch { try {
            val a=api.request("GET","/v1/comments?post=$post").getJSONArray("comments")
            postComments.clear();for(i in 0 until a.length()){val o=a.getJSONObject(i);postComments.add(o.getString("author")+": "+o.getString("body"))}
        }catch(e:Exception){handleError(e)} }
    }
    fun close() {scope.cancel()}
}
