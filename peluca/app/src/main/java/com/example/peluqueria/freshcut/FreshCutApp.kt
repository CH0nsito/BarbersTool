package com.example.peluqueria.freshcut

import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.Alignment

@Composable
fun FreshCutApp() {
    val context = LocalContext.current
    val state = remember { FreshCutState(context.applicationContext) }
    DisposableEffect(state) { onDispose { state.close() } }
    val lifecycle = (context as ComponentActivity).lifecycle
    LaunchedEffect(state) {
        state.refresh(silent = true)
        while (true) {
            delay(15000)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) state.refresh(silent = true)
        }
    }
    Column(Modifier.fillMaxSize().background(Paper).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(state.connectionMessage, Modifier.weight(1f), color = if (state.connected) Navy else Red, fontSize = 10.sp, maxLines = 1)
            TextButton({ state.showConnection = true }) { Text("Conexión", fontSize = 11.sp) }
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Box(Modifier.weight(1f)) {
            key(state.userId, state.ownerSignedIn) {
                if (state.ownerSignedIn) OwnerApp(state) else ClientApp(state)
            }
        }
    }
    if (state.error != null) AlertDialog(onDismissRequest = { state.error = null }, title = { Text("No se completó la operación") }, text = { Text(state.error.orEmpty()) }, confirmButton = { TextButton({ state.error = null }) { Text("Entendido") } })
    if (state.showConnection) ConnectionDialog(state)
}

@Composable
fun ClientApp(state: FreshCutState) {
    val context = LocalContext.current
    var history by rememberSaveable { mutableStateOf(if (state.signedIn) "home" else "welcome") }
    val route = history.substringAfterLast('|')
    DisposableEffect(route == "welcome") {
        (context as? ComponentActivity)?.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = if (route == "welcome") SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        onDispose { }
    }
    val rootTabs = listOf("home", "cuts", "popular", "appointments", "profile")
    val showTabs = route in rootTabs
    val auth = route in listOf("welcome", "login", "register", "recover", "owner-login")
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val message: (String) -> Unit = { text -> scope.launch { snackbar.showSnackbar(text, withDismissAction = true) } }
    val navigate: (String) -> Unit = { target ->
        when {
            !state.signedIn && (target == "booking" || target.startsWith("booking/") || target in listOf("appointments", "favorites", "profile", "popular", "saved")) -> history += "|login"
            target in rootTabs -> history = target
            target == "booking" -> history += "|booking/-1/0/"
            target != route -> history += "|$target"
        }
    }
    val back: () -> Unit = { history = if ('|' in history) history.substringBeforeLast('|') else if (state.signedIn) "home" else "welcome" }
    val selectCut: (Int) -> Unit = { state.event(null, it, "open"); navigate("detail/$it") }
    BackHandler(enabled = route != "welcome" && route != "home") { back() }
    Scaffold(
        modifier = Modifier.fillMaxSize(), containerColor = Paper,
        contentWindowInsets = if (route == "welcome") WindowInsets(0, 0, 0, 0) else WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showTabs) {
                HorizontalDivider(color = Line)
                NavigationBar(containerColor = Color.White, tonalElevation = 0.dp, modifier = Modifier.navigationBarsPadding(), windowInsets = WindowInsets(0, 0, 0, 0)) {
                    val titles = listOf("Inicio", "Cortes", "Popular", "Citas", "Perfil")
                    val icons = listOf("home", "cut", "flame", "calendar", "user")
                    rootTabs.forEachIndexed { index, tab -> NavigationBarItem(
                        selected = route == tab, onClick = { navigate(tab) },
                        icon = { Glyph(icons[index], if (route == tab) Red else Muted, 22.dp, filled = route == tab && tab == "profile") },
                        label = { Text(titles[index], fontSize = 10.sp, fontWeight = if (route == tab) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(selectedTextColor = Red, unselectedTextColor = Muted, indicatorColor = Color(0xFFFFF0F0))
                    ) }
                }
            }
        }
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner).background(if (auth && route != "welcome") Color.White else Paper)) {
            when {
                route == "welcome" -> WelcomeScreen({ navigate("login") }, { history = "home" }, { navigate("owner-login") })
                route == "owner-login" -> OwnerLoginScreen(state, back)
                auth -> AuthScreen(route, state, navigate, back, { history = "home" }, message)
                route == "home" -> HomeScreen(state, navigate, selectCut)
                route == "cuts" || route == "favorites" -> CatalogScreen(state, route == "favorites", selectCut, back)
                route.startsWith("detail/") -> {
                    val id = route.substringAfter('/').toIntOrNull()?.coerceIn(0, cuts.lastIndex) ?: 0
                    DetailScreen(cuts[id], state, back, { navigate("booking/$id/0/") }, selectCut)
                }
                route == "popular" || route == "saved" -> PopularScreen(state, selectCut, message, route == "saved", back)
                route.startsWith("booking/") -> {
                    val args = route.split('/')
                    BookingScreen(state, args.getOrNull(1)?.toIntOrNull() ?: -1, args.getOrNull(2)?.toIntOrNull() ?: 0, args.getOrNull(3).orEmpty(), back, { history = "appointments|success" }, message)
                }
                route == "success" -> BookingSuccess({ history = "appointments" }, { history = "home" })
                route == "appointments" -> AppointmentScreen(state, { navigate("booking") }, { item -> navigate("booking/${item.cut}/${item.barber}/${item.id}") })
                route == "profile" -> ProfileScreen(state, navigate, { state.logout() })
                route == "barbers" -> BarbersScreen({ navigate("barber/$it") }, back)
                route.startsWith("barber/") -> {
                    val id = route.substringAfter('/').toIntOrNull()?.coerceIn(0, barbers.lastIndex) ?: 0
                    BarberDetailScreen(id, back, { navigate("booking/-1/$id/") }, selectCut)
                }
                route == "settings" -> SettingsScreen(state, back, message)
                else -> AuxiliaryScreen(route, state, back, navigate)
            }
        }
    }
}

@Composable
private fun ConnectionDialog(state: FreshCutState) {
    var url by rememberSaveable { mutableStateOf(state.serverUrl) }
    AlertDialog(onDismissRequest = { state.showConnection = false }, title = { Text("Servidor FreshCut") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("USB: http://127.0.0.1:8080\nWi-Fi: usa la IP de la PC y el puerto 8080. La PC y el servidor deben estar encendidos.", fontSize = 13.sp)
            OutlinedTextField(url, { url = it }, label = { Text("Dirección del servidor") }, singleLine = true)
            Text("Cambiar de servidor cierra la sesión. HTTP solo se permite en esta versión de desarrollo.", fontSize = 11.sp, color = Muted)
        } }, confirmButton = { TextButton({ state.configureServer(url) }, enabled = !state.busy) { Text("Conectar") } },
        dismissButton = { TextButton({ state.showConnection = false }) { Text("Volver") } })
}
