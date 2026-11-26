# FreshCut Barbería

Aplicación Android Kotlin/Jetpack Compose para una barbería, conectada a un backend propio local con Node.js y SQLite. Mantiene el módulo `app`, el paquete `com.example.peluqueria`, Gradle y SDK existentes.

## Uso actual

1. Inicia el backend siguiendo [backend/README.md](backend/README.md).
2. Abre FreshCut en el teléfono y revisa la barra **Conectado**.
3. Crea una cuenta de cliente o entra como dueño con las credenciales privadas de `backend/data/owner-access.txt`.
4. Usa **Conexión** para elegir USB (`http://127.0.0.1:8080`, con adb reverse) o la dirección de la PC por Wi-Fi.

El registro y acceso ahora se validan en el servidor. Las cuentas de demostración antiguas no se convierten automáticamente en usuarios reales. No se almacenan contraseñas en la app.

## Funciones

- Cliente: catálogo, favoritos por cuenta, citas sincronizadas, reprogramación, cancelación y perfil.
- Dueño: agenda, estados de citas, clientes, servicios, barberos, horarios y publicaciones.
- Reservas protegidas por validación atómica en servidor, con precio/duración guardados por cita.
- Feed **Para ti** personalizado por preferencias e interacciones, con diversidad, razones, ocultamiento y borrado de actividad.
- Publicaciones de texto asociadas a los cortes/fotos incluidos, comentarios, likes y guardados reales.
- Token cifrado en Android; permisos y separación entre cuentas en el backend.

La PC debe permanecer encendida con el backend ejecutándose. La app no confirma cambios sin respuesta del servidor. Los datos quedan en `backend/data/`; la demo anterior permanece separada.

## Compilar

```sh
./gradlew :app:assembleDebug --offline --console=plain
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. El SDK de este entorno es `/home/simi/Android/Sdk`.

Se revisa sintaxis, compilación e inicio; no se ejecutan suites, pruebas de instrumentación ni operaciones Git. Las pruebas de interfaz quedan al usuario.

## Límites de esta etapa

Servidor de desarrollo local, sin Firebase ni despliegue en la nube. No hay correo saliente, notificaciones push, pagos, subida de fotos/videos ni modelo neuronal entrenado. El recomendador es un ranking de contenido basado en señales y puntuaciones explicables. La recuperación de contraseña se hace con el comando administrativo documentado. HTTP está habilitado únicamente en debug; la publicación requiere HTTPS e infraestructura adecuada.
