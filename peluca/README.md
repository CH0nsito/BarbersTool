# FreshCut Barbería

Aplicación Android en español, realizada sobre el proyecto original `Peluqueria` con Kotlin y Jetpack Compose. El módulo, identificador de aplicación (`com.example.peluqueria`), SDK y versiones de Gradle existentes se conservan.

## Ejecutar

Abre esta carpeta en Android Studio, selecciona el módulo **app** y tu teléfono, y pulsa **Run**. El nombre e icono instalados son **FreshCut**.

En la bienvenida, **Explorar demostración** permite entrar directamente. Los formularios de inicio de sesión y registro validan datos ficticios, sin conectarse a un servidor y sin almacenar contraseñas.

## Pantallas y flujos

- Bienvenida, inicio de sesión, registro y recuperación de acceso simulada.
- Inicio con accesos rápidos, catálogo con búsqueda y categorías, detalle de corte e inspiración.
- Favoritos, publicaciones populares, me gusta, publicaciones guardadas y comentarios de demostración.
- Directorio y perfiles de barberos.
- Reserva: servicio y barbero → calendario y horario → resumen y confirmación.
- Próximas citas, historial, reprogramación y cancelación con confirmación.
- Perfil editable, publicaciones locales, preferencias de avisos, pago en barbería y preguntas frecuentes.

Perfil, sesión, favoritos, publicaciones, preferencias y citas se conservan en almacenamiento local. El calendario usa la fecha del teléfono, impide reservar en el pasado y evita solapamientos de las reservas locales de un barbero.

No hay backend, autenticación real, pagos, mensajes enviados, reserva remota ni notificaciones del sistema. Los nombres, reseñas, disponibilidad y precios son de demostración. Las fotografías se muestran desde regiones de las dos referencias visuales aportadas, empaquetadas con la app; no requieren internet.

## Compilación

```sh
./gradlew :app:assembleDebug --offline --console=plain
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

Se verifican compilación, instalación e inicio en el dispositivo. No se ejecutan pruebas automatizadas ni de instrumentación; la revisión de navegación e interfaz queda a cargo del usuario.
