---
name: IPTV Móvil (Lumen)
colors:
  background: '#000000'
  surface: '#1C1C1E'
  surface-elevated: '#2C2C2E'
  surface-pressed: '#3A3A3C'
  text-primary: '#FFFFFF'
  text-secondary: '#EBEBF599'
  text-tertiary: '#EBEBF54D'
  accent: '#0A84FF'
  success: '#30D158'
  warning: '#FF9F0A'
  danger: '#FF453A'
  hairline: '#FFFFFF14'
typography:
  large-title: {fontFamily: Alan Sans, fontWeight: 800, fontSize: 30px, letterSpacing: -0.5px}
  title: {fontFamily: Alan Sans, fontWeight: 800, fontSize: 22px}
  headline: {fontFamily: Alan Sans, fontWeight: 800, fontSize: 17px}
  body: {fontFamily: Alan Sans, fontWeight: 400, fontSize: 15px}
  callout: {fontFamily: Alan Sans, fontWeight: 400, fontSize: 13px}
  caption: {fontFamily: Alan Sans, fontWeight: 400, fontSize: 11px, textTransform: uppercase, letterSpacing: 0.6px}
rounded: {row: 10px, card: 14px, panel: 20px, pill: 999px}
spacing: {gutter: 16px, section: 24px, touch-min: 48px}
---

# IPTV — versión móvil Android

Versión para teléfono Android (vertical 360×800 dp, y horizontal solo en el reproductor) de una app IPTV personal con VPN WireGuard integrada (Proton). Es la misma app que la de Fire TV: mismos datos, mismas funciones, misma identidad visual (inspirada en la app Apple TV / tvOS), pero adaptada a pantalla táctil y a una mano.

## Overview
- El contenido manda: fondo negro puro, carátulas y logos generosos, cromo mínimo.
- Un solo acento azul de sistema (#0A84FF). Verde, naranja y rojo SOLO para estados: VPN conectada, conectando/advertencia, directo/error.
- Toda la UI en español. Mayúsculas solo en caption (etiquetas de sección, chips).
- No hay tema claro.

## Colors
Superficies en tres niveles: surface (tarjetas), surface-elevated (campos, seleccionado), surface-pressed (estado pulsado). Texto en tres opacidades (100 %, 60 %, 30 %). Sobre vídeo, blanco/negro con degradados.

## Typography
Alan Sans: 800 para títulos, 400 para el resto. Nada por debajo de 11 sp. Cuerpo 15 sp.

## Layout
- Barra de navegación inferior con 5 destinos: Inicio · Directo · Cine · Series · Guía. Arriba, barra con título grande, píldora de estado VPN (punto verde/naranja/gris + texto), lupa de búsqueda y engranaje de ajustes.
- Márgenes laterales de 16 dp. Objetivos táctiles de al menos 48 dp.
- El foco con anillo y escala de la TV se sustituye por estado pulsado (superficie aclarada) y ripple sutil.
- Menú contextual (☰ en la TV) = pulsación larga → hoja inferior (bottom sheet) con opciones.
- Reproductor: siempre a pantalla completa en horizontal, controles que aparecen al tocar, gestos: deslizar vertical a la izquierda = brillo, a la derecha = volumen, doble toque = ±10 s, deslizar horizontal en directo = canal anterior/siguiente.
- Mini reproductor flotante sobre la barra inferior cuando se sale del reproductor (como Picture-in-Picture interno).

## Elevation & Depth
Sin sombras duras ni bordes. Paneles glass: surface al 82 % + hairline 8 % sobre fondos con imagen oscurecida por degradados.

## Shapes
10 dp filas y chips, 14 dp tarjetas y pósters, 20 dp paneles y hojas inferiores, píldora para botones y estados.

## Components
PillButton primario (blanco con texto negro) y secundario (surface-elevated). Chip en mayúsculas sobre color al 16 %. ProgressLine de 3 dp. Pósters 2:3. Banderas de país en la VPN. Bottom sheet para opciones.

## Do's and Don'ts
- ✅ Un único acento; el color lo ponen las carátulas.
- ✅ Pulsación larga siempre con alternativa visible (botón ⋯).
- ❌ No usar gradientes de colores vivos ni un segundo acento.
- ❌ No marcar «EN DIRECTO» sobre el vídeo.
