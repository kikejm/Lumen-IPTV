# ARCHITECTURE.md — Lumen TV

Este documento describe la forma del sistema a alto nivel: qué piezas hay, dónde viven y qué reglas no deben romperse. Para detalles de un fichero concreto, lee el fichero. Para cómo trabajar en el repo, ver `CLAUDE.md`; para la UI, `DESIGN.md`.

## Vista de pájaro

Lumen es una única app Android (`:app`, paquete `app.lumentv`) que corre en Fire OS 6–8 (Android 7.1–11). Hace dos cosas:

1. **Cliente IPTV**: descarga el catálogo de un proveedor (API Xtream Codes o lista M3U + XMLTV), lo guarda en SQLite (Room) y lo reproduce con Media3/ExoPlayer.
2. **Cliente VPN**: levanta un túnel WireGuard en el propio proceso (librería oficial `com.wireguard.android:tunnel`) usando configuraciones de Proton VPN que el usuario importa.

Flujo de datos:

```
Proveedor IPTV ──HTTP──▶ XtreamClient / M3uParser / XmltvParser
                                  │ (streaming, lotes de 500–1000)
                                  ▼
                             Room (LumenDatabase)  ◀── LibraryRepository (favoritos, historial)
                                  │ Flow
                                  ▼
             ViewModels / Composables (ui/screens) ──▶ PlaybackSession ──▶ PlayerScreen (ExoPlayer)
                                                                        │
Proton .conf ──▶ VpnServerRepository (cifrado) ──▶ VpnManager ──▶ GoBackend (túnel "lumen")
                                                                        ▲
                         todo el tráfico HTTP de Lumen (OkHttp/ExoPlayer) pasa por él si está activo
```

## Mapa del código

El módulo `:app` tiene dos sabores (`productFlavors`, dimensión `device`):

- `src/main` — núcleo compartido: todo lo de abajo salvo la UI, más los tokens del tema (`ui/theme/Theme.kt`), las rutas (`ui/navigation`) y los ViewModels (`LiveViewModel`, `VodViewModels`).
- `src/tv` — `MainActivity` y la UI de Compose for TV (`ui/components`, `ui/screens`, `LumenRoot`, `Launch.kt`, `LumenTheme`). `applicationId` `app.lumentv`.
- `src/mobile` — `MobileActivity` y la UI táctil con Material 3 (en desarrollo, `docs/PLAN-MOVIL.md`; diseño en `DESIGN-MOBILE.md`). `MobileRoot` (NavHost + barra inferior o raíl según `ui/adaptive/WidthClass`), `ui/components` (táctiles: `TapSurface`, hojas, campos) y `ui/screens`. `applicationId` `app.lumentv.mobile`.

Los paquetes Kotlin son los mismos en los tres; lo de abajo cuelga de `app/src/<sabor>/java/app/lumentv/`.

- `LumenApp` (main), `MainActivity` (tv) / `MobileActivity` (mobile) — punto de entrada. Cada sabor tiene una sola Activity. La de TV aloja Compose, pide el permiso de VPN (`ensureVpnPermission`) y gestiona Picture-in-Picture.
- `di/AppGraph` — el grafo de dependencias, construido a mano una sola vez. También orquesta el arranque (`onAppStart`: importar `.conf` empaquetados, refrescar catálogo/EPG caducados, buscar actualizaciones) y `syncPlaylist`.
- `core/` — utilidades sin dependencias de dominio: `SecretBox` (AES-GCM con clave del Android Keystore), formateo de horas, tamaños, banderas.
- `data/settings` — `SettingsRepository` (DataStore Preferences → `StateFlow<AppSettings>`) y `ParentalControl` (PIN con hash + categorías bloqueadas).
- `data/db` — Room: entidades, DAOs y `LumenDatabase`. Tablas de caché (`categories`, `channels`, `movies`, `series`, `programs`) y de usuario (`playlists`, `favorites`, `history`, `vpn_servers`).
- `data/xtream` — `XtreamClient`: autenticación, listas en streaming, fichas de VOD/series, EPG corto/archivo y constructores de URL (`live`, `movie`, `series`, `timeshift`).
- `data/m3u`, `data/epg` — parsers en streaming de M3U y XMLTV (con gzip), y `EpgRepository` (sync XMLTV + fallback `get_short_epg` cacheado en memoria).
- `data/playlist` — `PlaylistRepository`: alta/edición/borrado de listas, lista activa, credenciales.
- `data/catalog` — `CatalogRepository`: sincronización del catálogo y consultas/búsqueda para la UI.
- `data/library` — favoritos, historial y posiciones de reanudación.
- `player/` — `PlayerFactory` (ExoPlayer configurado según Ajustes: búfer, User-Agent, decoder fallback), `StreamResolver` (entidad → `PlayItem` con URL) y `PlaybackSession` (cola compartida para zapping y auto-siguiente episodio).
- `vpn/` — `ProtonConfigParser` (valida `.conf`, deduce servidor/país, aplica ámbito), `VpnServerRepository` (catálogo cifrado, importación desde assets/URL/móvil) y `VpnManager` (estado, conexión, estadísticas, IP pública).
- `remote/ImportServer` — servidor HTTP mínimo en la LAN con página web para subir `.conf` y dar de alta listas desde el móvil.
- `update/UpdateManager` — OTA: GitHub Releases o manifiesto JSON, descarga con verificación SHA-256 e instalación vía `FileProvider`.
- `tools/SpeedTest` — test de descarga y latencia.
- `ui/` — `theme/Theme.kt` y `navigation/` compartidos; el resto es del sabor. En `tv`: Compose for TV, `theme/` (tokens), `components/` (piezas reutilizables, todas enfocables), `navigation/` (rutas, `Navigator`, `LocalGraph`), `LumenRoot` (NavHost), `Launch.kt` (arrancar reproducción, reanudar historial, tecla ☰) y `screens/<pantalla>/`.

Fuera del código:

- `gradle/libs.versions.toml` — catálogo de versiones.
- `app/build.gradle.kts` — lee `.env` y lo expone como `BuildConfig`; copia `vpn-configs/*.conf` a assets si se pide.
- `.github/workflows/release.yml` — build firmado + GitHub Release con `update.json`.
- `design/stitch/` — capturas de los diseños de Stitch; `design/screenshots/` — capturas de la app real en el emulador.
- `tools/mock-xtream/` — panel Xtream falso (Python) para probar sin una lista real.

## Invariantes

- **Room es la fuente de verdad del catálogo.** La UI nunca guarda listas de canales descargadas de red en memoria propia; observa DAOs. Excepciones deliberadas, por ser bajo demanda y efímeras: ficha de película/serie y EPG corto.
- **Nada carga una lista completa del proveedor en memoria.** Canales, películas y series se leen con `android.util.JsonReader` e insertan en lotes. Un panel con 50 000 películas no debe superar unas pocas decenas de MB de heap.
- **Descargar y parsear son pasos separados.** Las respuestas grandes se vuelcan a un temporal en `cacheDir` (`core/net/withDownloadedFile`) y se parsean desde disco: así la conexión HTTP dura lo mínimo y no depende de la velocidad de Room.
- **"Conectado" significa handshake.** La UI de la VPN solo muestra verde tras el primer handshake WireGuard; antes es "Negociando…" y a los 15 s "El servidor no responde".
- **Los secretos nunca salen en claro del dispositivo ni a logs.** Contraseñas y `.conf` se guardan con `SecretBox`. Las URLs de stream contienen credenciales: no se loguean ni se muestran.
- **Una sola conexión de vídeo por cuenta.** Muchas cuentas IPTV solo admiten una: antes de `prepare()`, la previsualización y el reproductor llaman a `PlayerFactory.claimStream`, que para los demás reproductores. Multipantalla crea los suyos con `exclusive = false`. Al abrir a pantalla completa el canal que ya se ve en la previsualización, su reproductor se traspasa (`PlayerFactory.handOff`/`takeHandOff`) y el vídeo sigue sin recargar; al volver con Atrás a TV en directo, el reproductor regresa a la previsualización con el canal que sonaba (también tras hacer zapping).
- **Un enlace M3U `get.php?username=…&password=…` se guarda como Xtream** (`XtreamCredentials.fromM3uUrl`): así hay fichas, series y EPG. Al arrancar se convierten también las listas ya guardadas así.
- **Un solo túnel VPN** (`lumen`) y una sola instancia de `GoBackend`. Conectar a otro servidor reemplaza el túnel; no hay túneles paralelos.
- **El ámbito "Solo Lumen" se aplica en la configuración WireGuard** (`IncludedApplications`), no con proxies ni rutas propias. Por eso el resto del Fire TV no se ve afectado.
- **No hay login contra Proton.** Lumen no conoce la cuenta del usuario; solo tiene los `.conf`. Esto es intencionado: la API de Proton exige SRP + captcha y no es pública.
- **El servidor de importación solo vive mientras su pantalla está abierta** y exige un token aleatorio en cada petición.
- **Toda la UI es navegable con D-pad.** No existe ninguna acción que requiera táctil o ratón.
- **Las actualizaciones requieren la misma firma.** El keystore release no cambia nunca.

## Preocupaciones transversales

- **Concurrencia**: corrutinas. `AppGraph.appScope` (SupervisorJob) para trabajo que debe sobrevivir a una pantalla (sync, VPN, servidor de importación); `viewModelScope`/`rememberCoroutineScope` para el resto. Las sincronizaciones de catálogo y EPG están serializadas con `Mutex`.
- **Errores**: los repositorios lanzan excepciones con mensajes en español listos para mostrar; la UI los presenta tal cual. Las sincronizaciones en segundo plano publican su estado en `StateFlow` (`SyncState`, `EpgSyncState`, `VpnState`).
- **Red**: un único `OkHttpClient` con interceptor de User-Agent (configurable; por defecto `IPTVSmartersPro`, el más aceptado por proveedores). ExoPlayer usa el mismo cliente vía `OkHttpDataSource`.
- **Configuración de build**: `.env` → `BuildConfig`. Nada de `.env` es imprescindible.
- **Tests**: unitarios JVM en `app/src/test` para parsers y lógica pura. Lo dependiente de Android (Keystore, GoBackend, ExoPlayer) se verifica en emulador Android TV o en el Fire TV.

## Decisiones descartadas (y por qué)

- **Controlar la app oficial de Proton VPN**: no expone intents para elegir servidor; además Android solo permite una VPN activa.
- **Lista de servidores en vivo de Proton**: `api.protonvpn.ch/vpn/logicals` requiere sesión autenticada (401).
- **Hilt/Koin**: el grafo es pequeño; DI manual arranca más rápido en hardware modesto.
- **Leanback (Views)**: obsoleto frente a Compose for TV, que es la recomendación actual de Google.
