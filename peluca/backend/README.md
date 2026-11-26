# Backend local de FreshCut

Backend propio para una barbería. Node.js 24.15+ (rama 24), SQLite incorporado y módulos estándar; no requiere npm install, Docker, Firebase, cuentas de nube ni un servidor de base de datos separado. Android continúa en Kotlin/Compose.

## Arrancar

Desde la raíz del proyecto:

```sh
node backend/src/admin.mjs init-owner
backend/run.sh
```

El primer comando crea el dueño solo si no existe. Su correo inicial es `dueno@freshcut.local`; la contraseña es aleatoria y se guarda en `backend/data/owner-access.txt`, con permisos privados. El registro público crea exclusivamente clientes. No hay acceso administrativo de demostración sin contraseña.

El segundo comando deja el servidor en primer plano: mantén esa terminal abierta. Para detenerlo, Ctrl+C. Reiniciar no elimina los datos.

### USB

Con el servidor ejecutándose:

```sh
/home/simi/Android/Sdk/platform-tools/adb reverse tcp:8080 tcp:8080
```

En FreshCut → **Conexión**, usa `http://127.0.0.1:8080`. El reenvío puede necesitar repetirse tras reconectar o reiniciar el teléfono.

### Wi-Fi

```sh
HOST=0.0.0.0 backend/run.sh
```

En **Conexión**, usa `http://IP_DE_LA_PC:8080`. Ambos dispositivos deben poder comunicarse en la misma red. La PC puede estar por cable y el teléfono por Wi-Fi. El firewall debe permitir el puerto 8080 en la red de confianza. No se modificó el firewall automáticamente.

Durante la implementación la PC tenía `192.168.22.31` por cable y `192.168.22.28` por Wi-Fi; estas direcciones pueden cambiar. `ip -4 -brief address` muestra las actuales. Nunca abras este servidor de desarrollo a internet mediante redirección de puertos.

## Cuentas y almacenamiento

- Clientes: registrarse en la app con nombre, correo y contraseña de 8 a 128 caracteres.
- Dueño: acceso del negocio usando el archivo privado indicado arriba. Puede administrar catálogo, horarios, agenda y publicaciones.
- Identidad permanente mediante UUID; cambiar correo conserva las reservas.
- Contraseñas con scrypt y sal aleatoria; sesiones opacas almacenadas como hashes en el servidor, válidas por 30 días y revocadas al cerrar sesión.
- Android cifra el token con Android Keystore y no guarda contraseñas. Las copias de seguridad de la app están desactivadas.
- Los datos antiguos de la demo se conservan en sus preferencias originales pero no se importan ni se usan como cuentas reales.
- Los datos del servidor están en `backend/data/freshcut.sqlite` (WAL). No borres ni compartas esta carpeta. Para una copia sencilla, detén el servidor y copia la carpeta completa; no copies solo el archivo principal mientras esté escribiendo.

### Recuperación local de contraseña

No existe un proveedor de correo en esta etapa. El administrador de la PC puede ejecutar:

```sh
node backend/src/admin.mjs reset-password correo@ejemplo.com
```

Se genera una contraseña aleatoria y se revocan las sesiones de esa cuenta. Se escribe en `backend/data/owner-access.txt` (el archivo indica el correo afectado y reemplaza su contenido anterior). No se envían correos ni SMS.

## Datos compartidos y reservas

La app consulta su snapshot al entrar y cada 15 segundos mientras está en primer plano. El cliente recibe solo sus citas; el dueño recibe la agenda completa. La disponibilidad se consulta por separado sin revelar datos personales ajenos. Cada cambio se confirma después de la respuesta del servidor; la app no guarda reservas ficticias cuando no hay conexión.

La creación/reprogramación usa `BEGIN IMMEDIATE`, validación de permisos, servicios/barberos activos, días abiertos, horario y solapamiento por intervalo. Guarda precio, nombre y duración del servicio en la cita. Reprogramar el mismo servicio conserva sus condiciones; cambiarlo usa el catálogo vigente. Fechas de negocio: America/Mazatlan, máximo seis meses, inicios cada hora.

La opción Datos de ejemplo del dueño crea un pequeño conjunto de citas ficticias identificadas; eliminarlas no afecta las demás reservas. El catálogo y seis publicaciones iniciales son ejemplos. No se cargaron clientes ficticios automáticamente.

## Feed personalizado

El recomendador se ejecuta en `src/recommendations.mjs`. Es un sistema basado en contenido y señales de comportamiento, no una red neuronal ni una réplica de los algoritmos privados de redes sociales.

- Candidatos: las 200 publicaciones más recientes; entrega hasta 50.
- Afinidad por categoría: favoritos (+4), likes (+3), guardados (+5), reservas (+6) y ocultamientos (-3).
- Aperturas (+1.5), visualización (hasta +2 por exposición) y paso rápido (-0.4), con decaimiento exponencial de 21 días.
- Eventos retenidos hasta 90 días; la limpieza se ejecuta al registrar nuevos eventos. Máximo 20 eventos por categoría de señal/corte/cuenta en 24 horas.
- Se consideran exposición solo con la app en primer plano y al menos 60% del área relevante de la tarjeta visible. La UI limita cada exposición continua a 10 segundos; menos de 2 segundos no cuenta como vista.
- Popularidad basada en interacciones reales del servidor, recencia, novedad y exploración diaria determinista.
- Selección con penalización por repetición de categoría para mantener variedad.
- Inicio sin historial: novedades, popularidad y exploración; cada cuenta tiene señales independientes.
- Los eventos tienen identificador único; likes y guardados son idempotentes.
- «No me interesa» oculta esa publicación. El perfil permite borrar vistas y ocultamientos; conserva preferencias explícitas y reservas.
- «Actualizar» recalcula el orden. Mientras lees no se reordena por cada interacción.
- «Recientes», «Más gustados» y «Guardados» ofrecen alternativas al ranking personalizado.

Las publicaciones usan las fotos y cortes incluidos en la app. Se pueden publicar textos asociados a esos estilos, comentar y guardar. Subida de fotos/video externos, moderación y procesamiento multimedia no forman parte de esta entrega local.

## API principal

Todas las rutas salvo health, catálogo, login y registro requieren `Authorization: Bearer TOKEN`.

| Ruta | Uso |
|---|---|
| GET /health | Comprobar que el servidor está activo |
| GET /v1/catalog | Catálogo público |
| POST /v1/auth/register, /v1/auth/login, /v1/auth/logout | Sesión |
| GET /v1/snapshot | Datos autorizados del usuario |
| PUT, DELETE /v1/me | Editar/eliminar cuenta cliente |
| GET /v1/availability | Horarios libres; day, barber, service, except opcional |
| POST /v1/appointments | Reservar con ID estable para reintentos |
| PUT /v1/appointments/:id | Reprogramar, cancelar o registrar asistencia |
| PUT /v1/owner/services/:id, /v1/owner/barbers/:id, /v1/owner/hours | Catálogo y horario |
| POST, DELETE /v1/owner/demo | Citas ficticias identificadas |
| PUT /v1/favorites, /v1/reactions | Preferencias explícitas |
| POST /v1/posts | Publicar un corte |
| GET /v1/feed | sort=personal, recent, popular o saved |
| POST /v1/events | Señales de exposición/apertura |
| GET, POST /v1/comments | Comentarios por publicación |
| DELETE /v1/recommendations/history | Borrar señales implícitas y ocultamientos |

## Alcance y verificación

La compilación y comprobaciones de sintaxis no ejecutan pruebas automatizadas. La revisión de navegación, reservas simultáneas y aprendizaje de preferencias en el teléfono queda al usuario según su instrucción.

No es un despliegue de producción: HTTP solo se permite en Android debug, no hay correo saliente, push, pagos, carga multimedia ni infraestructura de alta disponibilidad. Publicar requerirá HTTPS, alojamiento estable, operación/respaldos, moderación si se abre contenido al público y validación integral de seguridad y funcionamiento. Las estadísticas actuales son estimaciones de reservas, no contabilidad de cobros.
