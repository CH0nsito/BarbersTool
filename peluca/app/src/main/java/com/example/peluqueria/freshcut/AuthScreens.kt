package com.example.peluqueria.freshcut

import android.util.Patterns
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WelcomeScreen(onStart: () -> Unit, onDemo: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Navy)) {
        Box(Modifier.fillMaxWidth().aspectRatio(.85f).align(Alignment.Center)) {
            Photo(0, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Navy, .18f to Color.Transparent, .68f to Color.Transparent, 1f to Navy)))
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, .48f to Color.Transparent, 1f to Color(0xFF071222))))
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(25.dp)); Brand(light = true)
            Spacer(Modifier.weight(1f))
            Text("TU ESTILO. TU MOMENTO.", color = Color(0xFFD9B89A), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.height(14.dp))
            Text("Un buen corte.\nUna nueva versión.", color = Color.White, fontSize = 35.sp, lineHeight = 39.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, letterSpacing = (-1).sp)
            Spacer(Modifier.height(15.dp))
            Text("Encuentra tu estilo y reserva tu próxima cita.\nNosotros nos encargamos del resto.", color = Color.White.copy(alpha = .8f), textAlign = TextAlign.Center, fontSize = 14.sp, lineHeight = 22.sp)
            Spacer(Modifier.height(28.dp))
            PrimaryButton("Comenzar", onStart, red = true)
            TextButton(onDemo) { Text("Explorar demostración", color = Color.White, fontSize = 13.sp) }
        }
    }
}
@Composable
fun InputField(value: String, onChange: (String) -> Unit, label: String, icon: String, password: Boolean = false, email: Boolean = false) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), label = { Text(label, fontSize = 13.sp) }, leadingIcon = { Glyph(icon, Navy, 19.dp) }, trailingIcon = if (password) { { IconAction("eye", if (visible) "Ocultar contraseña" else "Mostrar contraseña", { visible = !visible }) } } else null,
        singleLine = true, shape = RoundedCornerShape(12.dp), visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else if (email) KeyboardType.Email else KeyboardType.Text),
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Line, focusedBorderColor = Navy))
}
@Composable
fun AuthScreen(mode: String, state: FreshCutState, navigate: (String) -> Unit, back: () -> Unit, onLogin: () -> Unit, message: (String) -> Unit) {
    val register = mode == "register"
    val recover = mode == "recover"
    var name by rememberSaveable(mode) { mutableStateOf("") }
    var email by rememberSaveable(mode) { mutableStateOf("") }
    var password by rememberSaveable(mode) { mutableStateOf("") }
    var repeat by rememberSaveable(mode) { mutableStateOf("") }
    var error by rememberSaveable(mode) { mutableStateOf("") }
    var terms by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()) {
        IconAction("back", "Volver", back, modifier = Modifier.padding(start = 10.dp))
        Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Spacer(Modifier.height(5.dp)); Brand(); Spacer(Modifier.height(16.dp))
            Text(if (recover) "Recupera tu acceso" else if (register) "Crea tu cuenta" else "Qué bueno verte", fontSize = 27.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.8).sp)
            Text(if (recover) "Ingresa tu correo para continuar." else if (register) "Tu próximo gran corte comienza aquí." else "Inicia sesión y encuentra tu próximo estilo.", color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            if (register) InputField(name, { name = it; error = "" }, "Nombre completo", "user")
            InputField(email, { email = it; error = "" }, "Correo electrónico", "mail", email = true)
            if (!recover) InputField(password, { password = it; error = "" }, "Contraseña", "lock", password = true)
            if (register) InputField(repeat, { repeat = it; error = "" }, "Confirmar contraseña", "lock", password = true)
            if (error.isNotEmpty()) Text(error, color = Red, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            PrimaryButton(if (recover) "Solicitar recuperación" else if (register) "Registrarme" else "Iniciar sesión", {
                error = when {
                    register && name.trim().length < 2 -> "Escribe tu nombre completo."
                    !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "Escribe un correo electrónico válido."
                    !recover && password.length < 6 -> "La contraseña debe tener al menos 6 caracteres."
                    register && password != repeat -> "Las contraseñas no coinciden."
                    else -> ""
                }
                if (error.isEmpty()) {
                    if (recover) message("Demostración: no se envió ningún correo. Puedes entrar con Explorar demostración.")
                    else { state.login(if (register) name.trim() else state.name, email.trim()); onLogin() }
                }
            }, red = register, icon = null)
            if (!register && !recover) TextButton({ navigate("recover") }) { Text("¿Olvidaste tu contraseña?", fontSize = 13.sp) }
            if (!recover) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { HorizontalDivider(Modifier.weight(1f), color = Line); Text("o continúa con", color = Muted, fontSize = 12.sp); HorizontalDivider(Modifier.weight(1f), color = Line) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("Google", "Apple").forEach { provider -> OutlinedButton({ message("El acceso con $provider requiere conectar un proveedor. Usa el acceso de demostración.") }, Modifier.weight(1f), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Line)) { Text(provider, color = Ink) } }
                }
                TextButton({ navigate(if (register) "login" else "register") }) { Text(if (register) "¿Ya tienes cuenta? Inicia sesión" else "¿Aún no tienes cuenta? Regístrate", color = Red, fontSize = 13.sp) }
            }
            Note("Modo demostración. Usa datos ficticios; no se autentica en un servidor ni se guarda tu contraseña.")
            TextButton({ state.login("David Iram", "david@ejemplo.com"); onLogin() }) { Text("Explorar demostración", fontWeight = FontWeight.Bold) }
            if (register) TextButton({ terms = true }) { Text("Términos y privacidad", color = Muted, fontSize = 12.sp) }
            Spacer(Modifier.height(20.dp))
        }
    }
    if (terms) AlertDialog(onDismissRequest = { terms = false }, title = { Text("Tu privacidad") }, text = { Text("FreshCut es una demostración local. El perfil, favoritos y citas se guardan en este dispositivo. No se realizan pagos, envíos de correo ni reservas en una barbería real. Evita introducir datos sensibles.") }, confirmButton = { TextButton({ terms = false }) { Text("Entendido") } })
}
