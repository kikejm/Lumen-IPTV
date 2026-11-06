---
version: alpha
name: IPTV móvil (Lumen, sabor mobile)
colors:
  background: '#000000'
  surface: '#1C1C1E'
  surface-elevated: '#2C2C2E'
  surface-pressed: '#3A3A3C'
  text-primary: '#FFFFFF'
  text-secondary: '#EBEBF599'
  text-tertiary: '#EBEBF54D'
  accent: '#0A84FF'
  on-accent: '#FFFFFF'
  success: '#30D158'
  warning: '#FF9F0A'
  danger: '#FF453A'
  hairline: '#FFFFFF14'
  scrim: '#000000CC'
typography:
  large-title: {fontFamily: Alan Sans, fontWeight: 800, fontSize: 30sp, letterSpacing: -0.02em}
  title: {fontFamily: Alan Sans, fontWeight: 800, fontSize: 22sp, letterSpacing: -0.01em}
  headline: {fontFamily: Alan Sans, fontWeight: 800, fontSize: 17sp}
  body: {fontFamily: Alan Sans, fontWeight: 400, fontSize: 15sp, lineHeight: 21sp}
  callout: {fontFamily: Alan Sans, fontWeight: 400, fontSize: 13sp}
  caption: {fontFamily: Alan Sans, fontWeight: 400, fontSize: 11sp, letterSpacing: 0.04em, textTransform: uppercase}
rounded: {row: 10dp, card: 14dp, panel: 20dp, pill: 999dp}
spacing: {gutter: 16dp, section: 24dp, touch-min: 48dp, rail: 72dp, rail-expanded: 80dp, bottom-bar: 64dp, mini-player: 64dp, poster-min: 100dp}
---

# DESIGN-MOBILE.md — IPTV para móvil y tableta

Diseño de la UI táctil (sabor `mobile`). Fuente en **Google Stitch**: proyecto «IPTV — Android móvil» (id `11739808551864426596`), capturas en `design/stitch-mobile/` (sin prefijo = vertical, `L` = horizontal, `T` = tableta). Los colores y la tipografía son **los mismos tokens que la TV** (`app/src/main/java/app/lumentv/ui/theme/Theme.kt`); este documento añade las reglas táctiles, de orientación y de tamaño. Para la UI de TV, ver `DESIGN.md`; para el porqué, `docs/PLAN-MOVIL.md`.

## Overview

Es la misma app que en el Fire TV, con la misma identidad (referencia: app Apple TV), pero se usa **con el dedo, a 30 cm y a menudo con una mano**:

- **El contenido manda.** Fondo negro puro, carátulas y logos generosos, cromo mínimo.
- **Un solo acento** (azul de sistema). Verde = VPN conectada, naranja = conectando/advertencia, rojo = error. El rojo no se usa para «en directo»: nunca hay etiquetas «EN DIRECTO/LIVE»; lo que se emite ahora se marca con «Ahora» en azul o con la superficie aclarada. Única excepción: la línea vertical de la hora actual en la Guía.
- **Sin foco visible.** El anillo, la escala y el halo de la TV desaparecen. La respuesta al toque es la superficie aclarada (`surface-pressed`) y un *ripple* sutil.
- **Español** en toda la UI. Mayúsculas solo en `caption`.
- No hay tema claro.

## Colors

- Mismos tokens que la TV. `surface-pressed` = `surfaceFocused` de la TV, usado aquí como estado pulsado.
- Superficies: `surface` (tarjetas, filas), `surface-elevated` (campos, seleccionado, programa en emisión), `surface-pressed` (pulsado).
- Texto en tres opacidades (100 %, 60 %, 30 %), nunca grises inventados.
- Barras de progreso siempre en acento sobre pista al 35 % del terciario.
- Sobre vídeo, blanco/negro con degradados.

## Typography

Alan Sans empaquetada (`res/font`): 800 para títulos, 400 para el resto. Sobre la escala de la TV, `large-title` baja a 30 sp y `body` sube a 15 sp (lectura a corta distancia). Nada por debajo de 11 sp. Se respeta el tamaño de fuente del sistema (sp); las filas crecen en alto, no se cortan.

## Layout

**El diseño lo decide el ancho de ventana (`WindowSizeClass`), no la orientación.** Así sirve igual para móvil, plegable, tableta y multiventana.

| Ancho | Caso típico | Navegación | Columnas de contenido |
|---|---|---|---|
| *Compact* < 600 dp | móvil vertical | barra inferior (64 dp) | 1 |
| *Medium* 600–840 dp | móvil horizontal, plegable | raíl lateral (72 dp) | 2 paneles |
| *Expanded* > 840 dp | tableta horizontal | raíl lateral (80 dp) | 2–3 paneles |

Un móvil en horizontal suele pasar de 840 dp de ancho (cae en *Expanded*), pero solo tiene unos 340 dp de alto. Por eso cuenta también la **altura compacta** (< 480 dp, `isCompactHeight`): el raíl va sin la cabecera «IPTV» para que quepan las cinco pestañas, y TV en directo usa su maquetación de móvil horizontal (abajo).

- Destinos principales (barra o raíl): **Inicio · Directo · Cine · Series · Guía**. Arriba, barra compacta con título, píldora VPN, buscar y ajustes.
- Márgenes laterales 16 dp; *edge-to-edge* respetando *insets* del sistema y el recorte de cámara (en horizontal, a la izquierda o a la derecha).
- Objetivos táctiles ≥ 48 dp aunque el elemento visible sea menor.
- Pantallas de detalle (fichas, VPN, Ajustes, catch-up, test de velocidad) ocultan la barra inferior/raíl y muestran flecha atrás; el gesto atrás del sistema siempre vuelve.

### Pantalla por pantalla

| Pantalla | Vertical | Horizontal | Tableta |
|---|---|---|---|
| Inicio | Sigue viendo → accesos 2×3 → favoritos → emitiendo ahora | Sigue viendo · accesos 3×2 + favoritos | Fila de 3 «sigue viendo», accesos en una fila, rejilla «emitiendo ahora» |
| TV en directo | Previsualización arriba, chips de categoría y calidad, lista | Barra, chips y lista (≥ 300 dp) · previsualización desde arriba, lo más grande que quepa, + EPG | Categorías · lista · previsualización + EPG |
| Reproductor | Vídeo 16:9 arriba + canal, EPG y canales para zapear | Pantalla completa con gestos | Igual que horizontal |
| Cine / Series | *Hero* + rejilla 3 columnas | *Hero* compacto + 6 columnas | *Hero* + 8 columnas |
| Fichas | Arte arriba, datos y episodios debajo | Arte y datos (40 %) · episodios (60 %) | Igual que horizontal |
| Guía | ~1 h visible | ~2,5 h visibles | ~3 h visibles |
| VPN | Estado → países → servidores (pantalla aparte) | Estado · países · servidores | Igual que horizontal |
| Ajustes | Lista de secciones → detalle | Maestro-detalle | Maestro-detalle |
| Opciones (☰ en TV) | Hoja inferior | Hoja lateral derecha (360 dp) | Hoja lateral |

- **Inicio**: si hay una versión nueva, un aviso en acento al 18 % bajo la barra superior (como en la TV); tocarlo descarga e instala el APK, con su barra de progreso. La ✕ lo oculta hasta la próxima vez que se entre en la app; sigue saliendo hasta que se instale.
- **TV en directo**: al entrar, la previsualización arranca sola con el primer canal salvo que haya algo en el mini reproductor (la cuenta solo admite una conexión) o se esté con datos móviles; entonces muestra «Toca un canal para verlo aquí». Tocar otro canal lo pasa a la previsualización; tocar el que ya suena (o la previsualización) lo abre a pantalla completa sin recargar. Reconecta como la multipantalla (`StreamReconnect`); agotados los intentos, «No disponible · Toca para reintentar».
- **TV en directo con el móvil en horizontal** (altura compacta): no hay barra superior a todo el ancho. La barra («Directo», píldora VPN, buscar, ajustes) va solo sobre la columna de canales, junto a las píldoras de categoría y calidad; la lista no muestra el número del canal. La previsualización ocupa la derecha desde arriba con el mayor tamaño 16:9 que deje a la vista la línea de lo que se emite y al menos 300 dp a la lista (≈ 430 dp de ancho en un móvil de 6,7″); debajo, la EPG desplazable.
- **Teclado**: las pantallas con campos de texto (alta de lista, búsqueda) se encogen solo lo que ocupa el teclado (`imePadding` tras `consumeWindowInsets`) y desplazan el campo a la vista; los *insets* de la raíz no incluyen el teclado. Mientras está abierto se oculta el mini reproductor.
- **Pestañas**: cada destino principal guarda su pila y su estado (categoría, desplazamiento) al cambiar de pestaña. Volver de una ficha o del reproductor deja las listas donde estaban.
- **Barra superior en vertical**: título a 22 sp (`title`) y píldora VPN sin el nombre del servidor, para que el título no se corte.

Rejillas de pósters: `GridCells.Adaptive(100.dp)`; el número de columnas sale solo (3 en vertical, 6 en horizontal, 8–10 en tableta).

### Gestos e interacción

- **Toque** = OK de la TV. **Pulsación larga** = ☰ de la TV, y **siempre** hay también un botón «⋯» visible con las mismas opciones.
- Reproductor: toque muestra/oculta controles; deslizar vertical a la izquierda = brillo, a la derecha = volumen; doble toque a los lados = ±10 s (VOD); deslizar horizontal en directo = canal anterior/siguiente; candado para bloquear toques.
- **Girar el móvil** en el reproductor entra o sale de pantalla completa **sin cortar el vídeo**. El botón de pantalla completa fuerza horizontal y lo libera al salir.
- Al salir del reproductor con algo sonando: **mini reproductor** de 64 dp sobre la barra inferior (vídeo, canal, pausa, cerrar). Al salir de la app: PiP automático (Android 12+) si está activo en Ajustes; si no hay PiP, todo se pausa y se reanuda al volver. Cerrar la ventana PiP deja el vídeo en pausa. La ventana PiP toma la proporción del vídeo y lleva un botón de pausa/reproducir. Al cerrar el mini reproductor (o si otro vídeo ocupa la conexión) se guarda el minuto de la película o el episodio.
- Botón de pantalla completa: fuerza horizontal; el de salir fuerza vertical y libera la orientación cuando el móvil se pone de pie.
- Foco de audio: el vídeo se pausa con una llamada y no suena encima de otras apps.
- Reconexión del reproductor igual que en TV: 5 intentos cada 3 s, última imagen congelada y píldora «Error de reproducción, vuelve a conectar en 3 s (1/5)»; agotados, panel con «Reintentar» / «Otro canal» / «Volver».
- Multipantalla: 2 vídeos en móvil (apilados en vertical, lado a lado en horizontal) y 4 en tableta. Tocar un vídeo le da el audio (anillo blanco); pulsación larga o «⋯» = cambiar canal, ampliar, quitar. Reconexión como en la TV (`StreamReconnect`).
- VPN: los `.conf` se importan con el selector de ficheros, una URL o «Abrir con»/«Compartir» desde otra app; tras importar se muestra el resultado en la pantalla VPN.
- Salir: en Inicio, el gesto atrás sale de la app directamente (en móvil no hace falta el «pulsa de nuevo para salir» de la TV).

## Elevation & Depth

Sin sombras duras ni bordes. Profundidad con paneles *glass* (`surface` al 82 % + hairline 8 %) sobre arte oscurecido por degradados, y con el *scrim* (80 %) detrás de hojas y diálogos. Las hojas tienen asa de arrastre de 36×4 dp.

## Shapes

10 dp filas y chips; 14 dp tarjetas, pósters y previsualización; 20 dp paneles, *hero* y esquinas superiores de hojas; píldora para botones, estados y la píldora VPN.

## Components

Viven en `app/src/mobile/java/app/lumentv/ui/components/`. Pueden usar Material 3 por debajo, pero con los tokens de Lumen.

- `TapSurface` — base de todo lo pulsable (equivalente táctil de `FocusSurface`): forma, color en reposo/pulsado, *ripple*, `onClick` y `onLongClick` opcionales.
- `PillButton` — primario (blanco, texto negro) y secundario (`surface-elevated`).
- `FilterChip` — filtros horizontales desplazables (categorías, calidad, temporadas); el activo en acento. 40 dp a la vista y 48 dp de zona táctil. `Chip` es la etiqueta no pulsable.
- `VpnPill` — punto de estado + texto; abre la pantalla VPN.
- `Artwork` — logo/póster con iniciales solo si no hay imagen.
- `ProgressLine` — 3 dp, acento.
- `OptionsSheet` — opciones contextuales: hoja inferior en *Compact*, lateral en *Medium/Expanded*.
- `PinSheet` — PIN parental con teclado numérico propio (no el del sistema).
- `ConfirmDialog` y `EmptyState` (icono, título, texto, acción). El error del reproductor es su `ErrorPanel` (icono naranja, mensaje en español, «Reintentar» + acciones alternativas; en vertical, compacto y desplazable).
- `GroupCard` + `SettingRow` / `SwitchRow` (`LumenSwitch`) — listas agrupadas de Ajustes, separadas por `RowDivider`. Valores de una lista corta se eligen en una hoja con opciones de radio, no pasando valores como en la TV.
- `TextInputSheet` (un campo + «Guardar»), `NoticePill` (aviso de 4 s abajo al centro) y `ReconnectPill` (sobre vídeos pequeños).
- `MiniPlayer` (`ui/player`), los controles y gestos del reproductor (dentro de `PlayerScreen`) y `VideoSurface` (fuera de cualquier superficie con capa propia, igual que en TV; la pantalla solo se mantiene encendida mientras el vídeo suena o carga).
- Una columna / dos o tres paneles según `LocalWidthClass` (`ui/adaptive`): cada pantalla monta los suyos con `when (widthClass)`.

## Do's and Don'ts

- ✅ Cada acción de pulsación larga tiene también un botón visible («⋯»).
- ✅ Prueba cada pantalla en vertical, horizontal y tableta; las que tienen vídeo, girando el móvil mientras suena.
- ✅ Listas largas en `LazyColumn`/`LazyVerticalGrid` con `key` estable.
- ✅ Estados vacíos y de error con texto que diga qué hacer, nunca una pantalla negra.
- ❌ No uses rojo para «en directo» ni etiquetas «EN DIRECTO/LIVE».
- ❌ No fijes la orientación salvo el botón de pantalla completa del reproductor.
- ❌ No pongas nada interactivo debajo de las barras del sistema ni del recorte de cámara.
- ❌ No uses `Modifier.blur` (el mínimo es Android 8).
- ❌ No añadas un segundo acento ni gradientes de colores vivos; el color lo ponen las carátulas.
- ❌ No hay grabación ni descargas: no las muestres en la UI.
