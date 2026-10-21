# Plan de implementación — Lumen TV

Plan elaborado antes de escribir código (21 oct 2026). Se conserva como registro de las decisiones; el estado real del código está en ARCHITECTURE.md.

## Objetivo

Sustituir IPTV Smarters Pro en un Fire TV Stick por una app propia con:

1. Las funciones de Smarters, incluidas las de pago: EPG externo, búsqueda maestra, PiP, ajustes del reproductor, control parental, formato de hora, test de velocidad, multipantalla, catch-up.
2. **VPN de Proton integrada** para elegir país y servidor dentro de la app, sustituyendo el ciclo actual: abrir Proton → conectar → abrir Smarters → volver a Proton → desconectar.
3. Diseño propio en Google Stitch: moderno, minimalista, claro/oscuro, inspirado en Apple.
4. Instalación y actualizaciones sencillas en el Fire TV.

## Investigación previa (conclusiones)

| Pregunta | Conclusión |
|---|---|
| ¿Hay API de Proton para listar servidores? | `api.protonvpn.ch/vpn/logicals` responde **401** sin sesión. El login es SRP con captcha. **Descartado.** |
| ¿Controlar la app de Proton desde otra app? | No expone intents para elegir servidor. **Descartado.** |
| ¿Cuenta gratuita y WireGuard? | La cuenta gratuita puede generar ficheros WireGuard de servidores FREE en account.protonvpn.com (unos 10 países). **Base de la solución.** |
| ¿Cómo embebemos WireGuard? | Librería oficial `com.wireguard.android:tunnel` (Apache 2.0), `GoBackend` en userspace, declara su propio `VpnService`. Admite `IncludedApplications`, lo que permite poner **solo Lumen** detrás de la VPN. |
| Fire OS | Fire OS 7 = Android 9 y Fire OS 8 = Android 11. Sin Google Play Services, así que las fuentes van empaquetadas y no hay Firebase. `VpnService` funciona (Proton y otras VPN lo usan). |
| UI de TV | Compose for TV (`androidx.tv:tv-material`) es la recomendación actual frente a Leanback. |
| Instalación | ADB por red o Downloader; actualizaciones con instalador del sistema + `REQUEST_INSTALL_PACKAGES`. Requiere la misma firma siempre. |
| CLAUDE.md | Corto (menos de 200 líneas), comandos exactos, reglas no obvias, sin secretos y enlazando al resto. |
| DESIGN.md | Formato abierto de Google Stitch: tokens en YAML front matter y secciones en orden fijo (Overview → Do's and Don'ts). |
| ARCHITECTURE.md | Estilo matklad: vista de pájaro, mapa del código, invariantes (sobre todo los "por ausencia") y aspectos transversales. Estable, sin enlaces frágiles. |

## Decisiones de arquitectura

- Un módulo `:app`, capas por paquete (`data`, `vpn`, `player`, `ui`…), DI manual.
- **Room como fuente única de verdad**; listas grandes en streaming (`JsonReader`) e inserción por lotes.
- Media3 ExoPlayer con OkHttp (User-Agent configurable), HLS y MPEG-TS.
- VPN: catálogo = `.conf` importados (cifrados con Android Keystore), agrupados por país a partir del nombre Proton (`NL-FREE#12`).
- Importación desde el móvil mediante un servidor HTTP local efímero con QR y token, que sirve tanto para listas IPTV como para `.conf`.
- OTA desde GitHub Releases (`update.json` + SHA-256), generado por GitHub Actions al hacer push de un tag.

## Fases

1. **Diseño en Stitch**: sistema de diseño (Inter, acento #0A84FF, radio 12–20) y pantallas Inicio, Directo, VPN, Cine y modo claro.
2. **Base**: Gradle (catálogo de versiones, `.env` → BuildConfig, firma), Room, DataStore, SecretBox, cliente Xtream, parsers M3U/XMLTV.
3. **IPTV**: Inicio, Directo (con previsualización), Cine/Series y sus fichas, reproductor con overlay propio, Guía, Catch-up, Búsqueda, Multipantalla.
4. **VPN**: parser de `.conf`, repositorio cifrado, `VpnManager` (GoBackend), pantalla de países/servidores, ámbito, autoconectar/desconectar.
5. **Extras**: control parental, ajustes del reproductor, fuentes EPG externas, test de velocidad, PiP, servidor de importación, actualizador.
6. **Entrega**: documentación, workflow de release, keystore, tests unitarios, build release y prueba en emulador Android TV (API 30 ≈ Fire OS 8).

## Fuera de alcance (v1)

- Grabación (REC) y radio.
- Perfiles múltiples con avatares (sí hay varias listas).
- Ping por servidor VPN: WireGuard es UDP sin eco y Proton no publica la carga sin login.
