package com.example.peluqueria.freshcut

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.peluqueria.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class BackendException(val status: Int, message: String) : Exception(message)

/** Session encrypted with the device keystore; never persist a password. */
class SessionVault(context: Context) {
    private val prefs = context.getSharedPreferences("freshcut_session_v1", Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("freshcut_session", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("freshcut_session", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun read(): String = runCatching {
        val value = prefs.getString("token", null) ?: return ""
        val pieces = value.split(":")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(pieces[0], Base64.NO_WRAP)))
        String(cipher.doFinal(Base64.decode(pieces[1], Base64.NO_WRAP)), Charsets.UTF_8)
    }.getOrDefault("")
    fun write(token: String) {
        if (token.isEmpty()) { prefs.edit().remove("token").apply(); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val value = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(cipher.doFinal(token.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        prefs.edit().putString("token", value).apply()
    }
}
class BackendClient {
    var baseUrl = "http://127.0.0.1:8080"
    var token = ""
    suspend fun request(method: String, path: String, body: JSONObject? = null): JSONObject {
        val endpoint = baseUrl.trimEnd('/') + path
        val sessionToken = token
        return withContext(Dispatchers.IO) {
        val url = URL(endpoint)
        if (!BuildConfig.DEBUG && url.protocol != "https") throw BackendException(0, "La versión de publicación requiere HTTPS.")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method; connectTimeout = 5000; readTimeout = 10000; instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            if (sessionToken.isNotEmpty()) setRequestProperty("Authorization", "Bearer $sessionToken")
        }
        try {
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val raw = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val response = runCatching { JSONObject(raw) }.getOrElse { throw BackendException(code, "Respuesta no válida del servidor.") }
            if (code !in 200..299) throw BackendException(code, response.optString("error", "No se pudo completar la solicitud."))
            response
        } catch (e: BackendException) { throw e }
        catch (e: Exception) { throw BackendException(0, "No se pudo conectar con el backend. Comprueba que está encendido y revisa la dirección en Conexión.") }
        finally { connection.disconnect() }
    }
}
}
