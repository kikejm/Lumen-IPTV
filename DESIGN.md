---
version: alpha
name: Lumen TV
description: Interfaz 10-foot para Fire TV inspirada en Apple tvOS. Minimalista, contenido primero, foco como protagonista. Solo modo oscuro.
colors:
  # Tema oscuro (único)
  background: "#000000"
  surface: "#1C1C1E"
  surface-elevated: "#2C2C2E"
  surface-focused: "#3A3A3C"
  text-primary: "#FFFFFF"
  text-secondary: "rgba(235, 235, 245, 0.6)"
  text-tertiary: "rgba(235, 235, 245, 0.3)"
  accent: "#0A84FF"
  on-accent: "#FFFFFF"
  success: "#30D158"
  warning: "#FF9F0A"
  danger: "#FF453A"
  hairline: "rgba(255, 255, 255, 0.08)"
  focus-ring: "#FFFFFF"
  # Tintes de los accesos de Inicio
  tile-live: "#0A84FF"
  tile-movies: "#FF375F"
  tile-series: "#BF5AF2"
  tile-guide: "#30D158"
  tile-multiview: "#5E5CE6"
  tile-catchup: "#FF9F0A"
typography:
  large-title:
    fontFamily: Alan Sans
    fontSize: 32px
    fontWeight: 800
    letterSpacing: -0.02em
  title:
    fontFamily: Alan Sans
    fontSize: 22px
    fontWeight: 800
    letterSpacing: -0.01em
  headline:
    fontFamily: Alan Sans
    fontSize: 17px
    fontWeight: 800
  body:
    fontFamily: Alan Sans
    fontSize: 14px
    fontWeight: 400
    lineHeight: 20px
  callout:
    fontFamily: Alan Sans
    fontSize: 13px
    fontWeight: 400
  caption:
    fontFamily: Alan Sans
    fontSize: 11px
    fontWeight: 400
    letterSpacing: 0.04em
rounded:
  sm: 10px
  md: 14px
  lg: 20px
  pill: 999px
spacing:
  safe-horizontal: 48px
  safe-vertical: 27px
  xs: 4px
  sm: 8px
  md: 14px
  lg: 22px
  xl: 36px
components:
  focus-surface:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.md}"
  focus-surface-focused:
    backgroundColor: "{colors.surface-focused}"
    rounded: "{rounded.md}"
  pill-button-primary:
    backgroundColor: "#FFFFFF"
    textColor: "#000000"
    typography: "{typography.callout}"
    rounded: "{rounded.pill}"
    padding: 10px 20px
  pill-button-secondary:
    backgroundColor: "{colors.surface-elevated}"
    textColor: "{colors.text-primary}"
    typography: "{typography.callout}"
    rounded: "{rounded.pill}"
    padding: 10px 20px
  category-item-selected:
    backgroundColor: "{colors.accent}"
    textColor: "{colors.on-accent}"
    rounded: "{rounded.sm}"
  vpn-pill:
    backgroundColor: "{colors.surface}"
    typography: "{typography.callout}"
    rounded: "{rounded.pill}"
  glass-panel:
    backgroundColor: "rgba(28, 28, 30, 0.82)"
    rounded: "{rounded.lg}"
  chip-live:
    backgroundColor: "{colors.danger}"
    textColor: "#FFFFFF"
    typography: "{typography.caption}"
    rounded: 6px
---

# DESIGN.md — Lumen TV

Diseño fuente en **Google Stitch**: proyecto «Lumen TV — IPTV para Fire TV» (id `16620154609208606606`). Las capturas exportadas están en `design/stitch/`. Los tokens de arriba son la referencia normativa; en código viven en `app/src/main/java/app/lumentv/ui/theme/Theme.kt` (`LumenColors`, `LumenType`, `LumenDimens`). Si cambias uno, cambia el otro.

Este documento describe la UI de **TV** (sabor `tv`). La versión móvil (sabor `mobile`) comparte estos tokens de color y tipografía, pero sus reglas táctiles y de orientación están en `DESIGN-MOBILE.md` (proyecto Stitch «IPTV — Android móvil», id `11739808551864426596`) y en `docs/PLAN-MOVIL.md`.

## Overview

Lumen es una app de TV que se usa **a 3 metros y solo con el mando del Fire TV** (flechas, OK, Atrás, ☰, Inicio). La referencia es Apple tvOS / app Apple TV:

- **El contenido manda.** Fondos negros puros, carátulas y logos a tamaño generoso, cromo mínimo.
- **El foco es el protagonista.** Solo el elemento enfocado "despierta": escala (×1.06 por defecto, ×1.08 en pósters), su superficie se aclara, aparece un anillo de 2 dp y un halo suave. Todo lo demás permanece quieto.
- **Un solo acento** (azul de sistema). Verde, naranja y rojo están reservados para estados: VPN conectada, conectando/advertencia y directo/error.
- **Español** en toda la UI. Mayúsculas solo en `caption` (etiquetas de sección, chips).

## Colors

- `background` negro puro (OLED y menos halo en paneles LED). **No hay tema claro**: se eliminó porque no funcionaba bien en la tele.
- Superficies en tres niveles: `surface` (tarjetas en reposo), `surface-elevated` (seleccionado sin foco, campos), `surface-focused` (foco).
- Texto en tres opacidades (primario 100 %, secundario 60 %, terciario 30 %), nunca grises inventados.
- Los tintes `tile-*` solo se usan como fondo al 18 % del icono de cada acceso de Inicio.
- Sobre vídeo (reproductor, multipantalla) se usa blanco/negro con degradados, independientemente del tema.

## Typography

**Alan Sans** (Google Fonts, SIL OFL 1.1), empaquetada en `res/font` como dos instancias estáticas de la fuente variable: **800 para títulos** (`large-title`, `title`, `headline`, nombres de mosaicos) y **400 para todo el texto**; no se usan otros pesos. Va empaquetada porque Fire TV no tiene Google Play Services para fuentes descargables, y estática porque Fire OS 6 (Android 7.1) no aplica el eje `wght`. Los símbolos del mando (◀ ▶ ☰) no están en Alan Sans y salen de la fuente del sistema. Escala basada en la guía de tipografía de Android TV: nada por debajo de 11 sp, cuerpo a 14 sp. Títulos con tracking ligeramente negativo, como tvOS.

## Layout

- Lienzo de 960×540 dp (1080p a xhdpi). **Overscan seguro: 48 dp horizontal, 27 dp vertical** (5 %); nada interactivo fuera.
- Barra superior común (`TopBar`): marca · pestañas en *segmented control* · píldora VPN · buscar · ajustes · reloj.
- Patrones de pantalla:
  - **Inicio**: "Sigue viendo" (tarjeta ancha) → "Accesos" (6 mosaicos) → fila de favoritos → pie con estado de la lista. Si hay una versión nueva, encima va un aviso en acento al 18 % (OK lleva a Ajustes para instalarla) con un botón «Ocultar» al lado, que lo quita hasta la próxima vez que se abra la app.
  - **TV en directo**: canales con programa actual · previsualización 16:9 + EPG, que se lleva el espacio sobrante. Al entrar no se carga ningún canal: la previsualización muestra «Pulse OK» hasta el primer OK sobre un canal (o sobre ella misma). La previsualización no sigue al foco: moverse por canales o categorías no la cambia; OK sobre otro canal lo pasa a la previsualización (marcado con ▶ en acento en la lista) y OK sobre el que ya suena lo abre a pantalla completa. ▶ desde los canales lleva el foco (anillo) a la previsualización; OK ahí la abre a pantalla completa sin cortar el vídeo, y al volver con Atrás sigue sonando en la previsualización; ◀ vuelve al canal. Las categorías (190 dp) están plegadas: ◀ desde los canales las despliega a la izquierda; ▶ o elegir una las pliega y devuelve el foco a los canales. Entre ambas columnas hay siempre un separador (línea + asa + flecha ‹/›) que indica que hay un panel plegado. Encima de los canales, píldoras de calidad (Todas · FHD · FHD HEVC · HD · SD, solo las presentes en la categoría; cada canal cae en una sola) deducidas del nombre del canal.
  - **Cine / Series**: fondo con el arte del elemento enfocado, *hero* con título/metadatos, raíl de categorías y rejilla de pósters 2:3. Mantener OK (o ☰) sobre un póster abre «Añadir/Quitar de Mi lista». En Cine, la categoría **Recientes** (películas reproducidas, la última primero) solo aparece tras ver la primera película; allí el menú añade «Quitar de Recientes». Al volver a la rejilla (de la ficha o del reproductor) el foco regresa al último póster enfocado, con la rejilla en su sitio.
  - **Ficha de serie**: póster · título, metadatos, sinopsis, botones y, debajo, las temporadas, con su parte baja a ras de la del póster. La lista de episodios llega hasta el borde inferior de la pantalla (el enfocado se detiene antes del margen de overscan). Al bajar a los episodios la cabecera se recoge (como Netflix: queda el título y las temporadas) y la lista ocupa la pantalla; al subir a las temporadas se despliega. ▲ desde el primer episodio y ▼ desde los botones van a la temporada elegida. Al volver del reproductor, el foco está en el episodio que sonaba (tras el auto-siguiente, el último), en su temporada.
  - **Reanudar desde Inicio** («Sigue viendo»): reproduce directamente y apila debajo la sección, como si se hubiera entrado desde ella. Atrás lleva a TV en directo con la previsualización sonando y la categoría del canal, a Cine con la película enfocada en su categoría, o a la ficha de la serie con el episodio enfocado (y de ahí a Series, con la serie enfocada).
  - **Reproductor**: ante un error (o 20 s cargando sin respuesta) reconecta hasta 5 veces, una cada 3 s, con la última imagen congelada y una píldora al pie «Error de reproducción, vuelve a conectar en 3s (1/5)»; películas y episodios vuelven al minuto en que estaban. Agotados los intentos muestra el error con el foco en «Reintentar» (◀ ▶ cambian de botón).
  - **Guía**: pestañas → píldoras de categoría → ‹ Ahora › → parrilla, en ese orden con ▲▼ (rutas de foco explícitas: la búsqueda geométrica saltaba las categorías). Al entrar, el foco está en la pestaña «Guía».
  - **VPN**: panel de estado (330 dp) · países con bandera · servidores del país.
  - **Ajustes**: secciones a la izquierda, filas valor/acción a la derecha.
- Pista de teclas (`KeyHints`) al pie cuando hay gestos no obvios (☰).
- **Multipantalla**: la pantalla elegida solo lleva anillo (sin halo, para no teñir las franjas). OK la amplía (o elige canal si está vacía), ☰ cambia el canal y Atrás vuelve a la disposición.
- **Zoom de vídeo** (TV en directo y multipantalla): 300 ms, reversible a medias, siempre con el mismo `SurfaceView` (`placedAt`, `VideoZoomSpec`). En TV en directo, Atrás reduce a la primera y lleva al canal que sonaba, en su categoría si la lista a la vista no lo contiene.
- **Historial de canales**: los 10 últimos; ☰ «Quitar del historial». Es el mismo en TV en directo y en multipantalla: cuentan los canales vistos a pantalla completa y los elegidos para una pantalla de multipantalla, y el selector de canal de multipantalla tiene su categoría «Historial».
- **Reconexión en vídeos pequeños** (previsualización y multipantalla, `StreamReconnect`): con los mismos límites que el reproductor (5 intentos, uno cada 3 s; también tras 20 s cargando), con la última imagen y una píldora «Reconectando (1/5)». Agotados, «No disponible»: en multipantalla OK reintenta; en la previsualización, OK abre el reproductor.
- **Tecla Inicio**: cierra la app. No hay Picture-in-Picture.
- **Salir de la app**: en la pantalla raíz (Inicio, u onboarding si no hay lista), Atrás no cierra a la primera: muestra una píldora «PULSA DE NUEVO PARA SALIR» abajo al centro durante 1,5 s y solo sale si se pulsa Atrás otra vez en ese tiempo. En el resto de pantallas Atrás vuelve atrás como siempre.

## Elevation & Depth

No hay sombras duras ni bordes. La profundidad se comunica con: escala en foco, halo (`Glow`) del color del anillo al 18 %, y paneles "glass" (`surface` al 82 % + hairline 8 %) sobre fondos con imagen oscurecida por degradados.

## Shapes

Esquinas continuas: 10 dp (filas, chips grandes), 14 dp (tarjetas, previsualización de TV en directo), 20 dp (paneles, hero), píldora para botones, pestañas y estados. Los pósters usan 14 dp.

## Components

- `FocusSurface` — base de todo lo navegable (envuelve `androidx.tv.material3.Surface`). Parámetros: radio, colores reposo/foco, escala, anillo, halo. **Nunca metas un `VideoSurface` dentro**: la capa de la superficie tapa el hueco del SurfaceView y el vídeo se ve negro (se oye). Pon el vídeo debajo y una `FocusSurface` transparente encima, sin escala ni halo. `VideoSurface` vigila además los fotogramas: si sonando con vídeo no se pinta ninguno en 3 s (imagen congelada o negra con audio, visto en Fire TV), reengancha la superficie y, a los 7 s, recarga el stream.
- `PillButton` — primario (blanco con texto negro) y secundario (`surface-elevated`).
- `CategoryColumn` — lista vertical; la categoría activa va en acento, con contador y candado si está bloqueada.
- `VpnPill` — punto de estado + texto; abre la pantalla VPN.
- `Chip` — etiqueta en mayúsculas sobre color al 16 % (o sólido, como «Mi lista»). No se marca «EN DIRECTO» sobre el vídeo: ni en la previsualización, ni en el reproductor, ni en Inicio.
- `ProgressLine` — barra de 3 dp, pista al 35 % del terciario.
- `LoadingSpinner` — anillo blanco que gira (pista al 20 %); solo él en la previsualización mientras carga. `PlayerLoading` — en pantalla completa, «Cargando» con los puntos apareciendo en bucle dentro de un anillo de 150 dp.
- `Artwork` — logo/póster; las iniciales del nombre solo aparecen si no hay imagen o falla al cargar.
- `GlassPanel`, `OptionsDialog` (menú ☰), `PinDialog` (teclado numérico navegable), `ConfirmDialog`, `TvTextField`.

## Do's and Don'ts

- ✅ Haz que cada pantalla tenga un foco inicial claro (`FocusRequester` + `requestFocus` en `LaunchedEffect`).
- ✅ Usa ☰ para acciones secundarias y muéstralo en `KeyHints`.
- ✅ Mantén las listas largas en `LazyColumn`/`LazyVerticalGrid` con `key` estable.
- ❌ No uses componentes táctiles de Material 3 (`Button`, `TextField`, `Card` de `material3`): no gestionan bien el foco de TV.
- ❌ No pongas texto informativo dentro de la zona de overscan ni por debajo de 11 sp.
- ❌ No uses `Modifier.blur`: no funciona por debajo de Android 12 y Fire OS 7/8 son Android 9/11.
- ❌ No añadas un segundo color de acento ni gradientes de colores vivos; el color lo ponen las carátulas.
