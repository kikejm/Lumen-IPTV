# Plan — versión para móvil Android

Plan elaborado el 31 oct 2026, antes de escribir código. Objetivo: tener la app IPTV funcionando en un teléfono Android con las mismas funciones que en el Fire TV (Xtream/M3U, EPG, VOD, series, favoritos, historial, control parental, VPN WireGuard de Proton), con una UI táctil propia que conserve la identidad visual actual.

Diseño en Google Stitch: proyecto **«IPTV — Android móvil»** (id `11739808551864426596`), sistema de diseño «IPTV Móvil» generado desde un DESIGN.md adaptado al móvil. Capturas exportadas en `design/stitch-mobile/`.

## Diagnóstico: qué se reaprovecha y qué no

El código tiene ~8 700 líneas Kotlin; ~5 300 son UI.

| Capa | Estado | Qué hay que hacer |
|---|---|---|
| `data/*` (Room, Xtream, M3U, XMLTV, listas, catálogo, biblioteca) | Independiente de la TV | **Nada.** Se comparte tal cual. |
| `vpn/*` (GoBackend, parser, repositorio) | Independiente | Nada. El ámbito «Solo esta app» ya usa `context.packageName`. |
| `player/` (`PlayerFactory`, `StreamResolver`, `PlaybackSession`) | Independiente | Añadir `MediaSession` para controles del sistema y audio en segundo plano. |
| `update/`, `core/`, `di/AppGraph` | Independiente | `AppGraph` se comparte; solo cambia lo que arranca la UI. |
| ViewModels (`LiveViewModel`, `VodViewModels`) | Reutilizables | Mover fuera de los paquetes de pantalla de TV para que ambas UIs los usen. |
| `ui/theme` (tokens) | Reutilizable | Compartir `LumenColors`/`LumenType`; añadir `LumenDimens` de móvil (márgenes 16 dp, táctil ≥ 48 dp, tipos un punto más grandes). |
| `ui/components` + `ui/screens` | **Específico de TV** | 49 usos de `FocusSurface`, 41 `focusRequester`, 14 `onMenuKey`, `KeyHints`, `TvTextField`, 13 ficheros con `androidx.tv.material3`. **Se reescriben** para táctil. |
| `remote/ImportServer` | Pensado para TV sin teclado | En móvil no hace falta: importación directa (selector de ficheros, compartir `.conf`). |
| `MainActivity` | Fija `landscape`, PiP con `onUserLeaveHint` | Activity propia del móvil: vertical, reproductor en horizontal, PiP automático (Android 12+). |

Conclusión: **~40 % del código (todo lo que no es UI) se comparte sin cambios**. El trabajo real es una UI táctil nueva sobre los mismos repositorios.

## Decisión de estructura (recomendada)

**Sabores de producto (`productFlavors`) en el módulo `:app`**: `tv` y `mobile`.

- `src/main` — datos, VPN, reproductor, DI, tema, ViewModels (compartido).
- `src/tv` — la UI actual (Compose for TV), `MainActivity` actual, banner, manifiesto con `LEANBACK_LAUNCHER`.
- `src/mobile` — UI táctil nueva, `MobileActivity`, icono adaptativo, manifiesto con `touchscreen` requerido.
- `applicationId`: `tv` conserva **`app.lumentv`** (no rompe las actualizaciones del Fire TV); `mobile` usa **`app.lumentv.mobile`**. Mismo keystore.

Por qué así y no otra cosa:

- *Una sola APK que detecte TV o móvil*: arrastraría `tv-material` y todo el código de foco al teléfono, y cada cambio de UI tendría que probarse en los dos sitios dentro del mismo binario. Descartado.
- *Módulos separados (`:core`, `:tv`, `:mobile`)*: es lo más limpio a largo plazo, pero obliga a mover toda la capa de datos de golpe. Se puede hacer después; los sabores no lo impiden.
- Mantiene la regla de «un único módulo `:app`» de CLAUDE.md.

## Diseño de pantallas (Stitch)

Reglas de traducción TV → móvil, que ya están en el DESIGN.md del proyecto Stitch:

| En la TV | En el móvil |
|---|---|
| Barra superior con pestañas (*segmented control*) | **Barra inferior** con 5 destinos: Inicio · Directo · Cine · Series · Guía. Arriba: título, píldora VPN, buscar, ajustes. |
| Foco: escala ×1.06, anillo y halo | Estado **pulsado**: superficie aclarada + ripple sutil. Sin escala. |
| Tecla ☰ (`onMenuKey`) | **Pulsación larga → hoja inferior** con las mismas opciones, y botón «⋯» visible como alternativa. |
| Overscan 48/27 dp, lienzo 960×540 | Márgenes de 16 dp, *edge-to-edge* con *insets* del sistema, vertical 360×800 dp. |
| Categorías plegadas a la izquierda | **Chips horizontales** desplazables. |
| Reproductor con mando (◀ ▶ zapping) | Horizontal a pantalla completa; **gestos**: brillo (izq.), volumen (dcha.), doble toque ±10 s, deslizar = canal siguiente/anterior. |
| Previsualización 16:9 junto a la lista | Previsualización fija arriba de la lista de canales; al salir del reproductor, **mini reproductor** sobre la barra inferior. |
| `TvTextField` + teclado en pantalla | Campos de texto normales con teclado del sistema. |

Pantallas generadas en Stitch (`design/stitch-mobile/`):

1. `01-inicio` — Sigue viendo, accesos 2×3, favoritos, emitiendo ahora.
2. `02-directo` — previsualización fija + chips de categoría/calidad + lista de canales con programa actual.
3. `03-reproductor` — horizontal, controles superpuestos, EPG, gestos.
4. `04-cine` — *hero*, chips de categoría, rejilla de pósters 3 columnas.
5. `05-ficha-serie` — temporadas en píldoras, episodios con progreso.
6. `06-guia` — parrilla EPG con columna fija de canales y línea de «ahora».
7. `07-vpn` — estado, IP pública, «Solo esta app», países con bandera.
8. `08-ajustes` — listas agrupadas (Listas, Reproductor, EPG, Parental, VPN, Herramientas, Acerca de).
9. `09-alta-lista` — alta Xtream/M3U (onboarding).
10. `10-opciones` — hoja inferior de opciones de canal (sustituye a ☰).

Pendientes de diseñar en Stitch en la fase 1: ficha de película, búsqueda, multipantalla (2 vídeos en horizontal), PIN parental, catch-up, test de velocidad, estados vacíos/errores y la variante tableta.

Correcciones ya aplicadas en Stitch tras revisar la primera generación (`edit_screens`): nombre «IPTV» en una línea (salía «IPTV Stream»), acceso «VPN» en lugar de «Seguridad», fuera las píldoras «EN DIRECTO/LIVE» (las prohíbe DESIGN.md) y el reproductor rehecho en horizontal de móvil (~2,2:1). Stitch no tiene Alan Sans: en código se usa la fuente empaquetada (`res/font`). El DESIGN.md subido a Stitch está en `design/stitch-mobile/DESIGN.md`.

## Fases

### Fase 0 — Preparar el proyecto (½–1 día)

- Añadir `flavorDimensions("device")` con `tv` y `mobile` en `app/build.gradle.kts`; mover `ui/` actual, `MainActivity`, banner y manifiesto de TV a `src/tv`.
- Mover ViewModels y tema a `src/main`; `AppGraph` no cambia.
- `mobile`: `minSdk 26`, `targetSdk 35` (exige *edge-to-edge*), dependencias `compose-material3` (solo en `mobileImplementation`) para `NavigationBar`, `ModalBottomSheet`, `TextField`, `Switch`.
- Actualizar `CLAUDE.md`/`ARCHITECTURE.md`/`DESIGN.md`: la regla «nunca Material 3 / nunca `clickable`» pasa a ser **solo para `src/tv`**.
- Comprobar que `./gradlew assembleTvDebug testTvDebugUnitTest` sigue igual que hoy (el Fire TV no debe notar nada).

### Fase 1 — Diseño cerrado en Stitch (1–2 días)

- Generar las pantallas pendientes (ficha de película, búsqueda, multipantalla, PIN, catch-up, estados vacíos, tableta) y revisarlas en Stitch.
- Exportar capturas a `design/stitch-mobile/` y escribir `DESIGN-MOBILE.md` (tokens compartidos + reglas táctiles).

### Fase 2 — Esqueleto móvil (2–3 días)

- `MobileActivity` (vertical, `enableEdgeToEdge`, `ensureVpnPermission` equivalente).
- `MobileRoot`: `NavHost` con las mismas `Routes`, `Scaffold` con barra inferior y barra superior (píldora VPN, buscar, ajustes).
- Componentes táctiles: `TapSurface` (equivalente a `FocusSurface` con ripple), `PillButton`, `Chip`, `Artwork`, `ProgressLine`, `OptionsSheet`, `PinSheet`, `ConfirmDialog`.
- Onboarding (alta de lista) e Inicio funcionando con datos reales.

### Fase 3 — Contenido (4–6 días)

- TV en directo: previsualización fija + lista (respetando `PlayerFactory.claimStream`/`handOff`: una sola conexión por cuenta).
- Cine y Series: rejilla `LazyVerticalGrid` adaptable (3 columnas en vertical, más en tableta), fichas, Mi lista, Recientes.
- Guía EPG táctil (scroll en dos ejes sincronizado), búsqueda, favoritos, catch-up.

### Fase 4 — Reproductor móvil (3–4 días)

- Pantalla completa horizontal (`requestedOrientation` al entrar/salir), ocultar barras del sistema.
- Gestos (brillo, volumen, doble toque, zapping), bloqueo de pantalla, relación de aspecto, pistas de audio/subtítulos.
- Reconexión automática igual que en TV (5 intentos, 3 s).
- `MediaSessionService` de Media3: controles en notificación y pantalla de bloqueo, audio con la pantalla apagada (opcional en Ajustes).
- PiP automático (`setAutoEnterEnabled` en Android 12+) y mini reproductor interno.
- Permiso `POST_NOTIFICATIONS` (Android 13+).

### Fase 5 — VPN, ajustes y extras (2–3 días)

- Pantalla VPN táctil (misma lógica de `VpnManager`; «Conectado» solo tras handshake).
- Importar `.conf` con el selector de ficheros (SAF) y recibirlos desde «Compartir» (`ACTION_SEND`/`ACTION_VIEW`). El `ImportServer` no se incluye en `mobile`.
- Aviso de datos móviles (calidad/bufer al usar red móvil, `ConnectivityManager`).
- Ajustes, control parental, test de velocidad. Multipantalla: 2 vídeos en horizontal (baja prioridad).

### Fase 6 — Publicación (1 día)

- `release.yml`: compilar `assembleTvRelease assembleMobileRelease` y publicar dos APK con su `update.json` (`update.json` para TV, `update-mobile.json` para móvil). `UpdateManager` elige el manifiesto según el sabor (`BuildConfig.FLAVOR`).
- Icono adaptativo en `src/mobile/res/mipmap-*` (la restricción de `drawable` es solo del lanzador de Fire OS).
- Instalación en el móvil: descargar el APK de la Release y permitir «Instalar apps desconocidas».

**Estimación total: 15–20 días de trabajo** para paridad funcional.

## Riesgos y cosas a vigilar

- **Una VPN a la vez en Android**: si el móvil ya tiene Proton u otra VPN activa (o «VPN siempre activa»), la de la app no podrá levantarse. Mostrar el error claro.
- **Memoria**: los móviles tienen más que el Fire Stick, pero se mantienen las reglas de streaming (`JsonReader`, descarga a disco).
- **Batería y datos**: no sincronizar catálogo/EPG con datos móviles salvo que el usuario lo permita.
- **Rotación**: el reproductor no debe recrearse al girar (`configChanges` o reproductor fuera de la composición, como ya hace `handOff`).
- **Tráfico HTTP en claro**: muchos proveedores IPTV usan `http://`; se mantiene `usesCleartextTraffic`.
- **Base de datos**: no hay cambio de esquema previsto; si lo hubiera, `Migration` obligatoria (regla de CLAUDE.md).

## Decisiones abiertas (para confirmar)

1. ¿Paquete distinto para el móvil (`app.lumentv.mobile`, recomendado) o el mismo?
2. ¿Audio en segundo plano y controles en la pantalla de bloqueo desde la primera versión?
3. ¿Chromecast? Requiere Google Play Services en el móvil (sí lo tiene) y añade dependencia; propuesto para después de la paridad.
4. ¿Soporte de tableta en la primera versión o después?
