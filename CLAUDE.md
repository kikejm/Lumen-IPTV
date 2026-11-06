# CLAUDE.md — Lumen TV

App IPTV personal para **Amazon Fire TV** (Android TV, solo mando D-pad) con **VPN WireGuard integrada** para servidores de Proton VPN. Kotlin + Jetpack Compose for TV + Media3. Un único módulo `:app` con dos sabores: **`tv`** (Fire TV, la app publicada) y **`mobile`** (teléfono/tableta, en desarrollo: ver `docs/PLAN-MOVIL.md`). El nombre visible de la app es **IPTV** (`app_name`, banner, textos); «Lumen» queda solo como nombre interno del código y del paquete `app.lumentv`, que no se cambia (cambiarlo rompería las actualizaciones).

Antes de tocar nada lee: @ARCHITECTURE.md (mapa del código e invariantes) y, si es UI, @DESIGN.md (tokens y reglas visuales).

## Comandos

En esta máquina el JDK y el SDK no están en el PATH. Antes de Gradle, en Git Bash:

```bash
export JAVA_HOME="$LOCALAPPDATA/Programs/jdk-17.0.20.1+1"; export PATH="$JAVA_HOME/bin:$PATH"
```

| Tarea | Comando |
|---|---|
| Compilar debug | `./gradlew assembleTvDebug` (móvil: `assembleMobileDebug`) |
| Tests unitarios | `./gradlew testTvDebugUnitTest` |
| APK release firmado | `./gradlew assembleTvRelease` → `app/build/outputs/apk/tv/release/app-tv-release.apk` |
| Instalar en el Fire TV | `adb connect <IP>:5555 && adb install -r app/build/outputs/apk/tv/release/app-tv-release.apk` |
| Logs en el Fire TV | `adb logcat -s VpnManager AppGraph ImportServer ExoPlayerImpl` |

- `applicationId`: `tv` = `app.lumentv` (**no se cambia**), `mobile` = `app.lumentv.mobile`; el build debug añade `.debug` y convive con la release.
- Código: `src/main` es lo compartido (datos, VPN, reproductor, DI, tokens del tema, ViewModels); `src/tv` la UI de Compose for TV y `MainActivity`; `src/mobile` la UI táctil y `MobileActivity`. Nada de `src/main` puede depender de `androidx.tv` ni de Material 3.
- Pruebas sin lista real: `python tools/mock-xtream/mock_xtream.py` + emulador AVD `lumen_tv` (Android TV API 30). En el emulador el servidor es `http://10.0.2.2:8099`, usuario/contraseña `demo`.
- Con el emulador, los toques no funcionan sobre la UI (Compose for TV solo atiende al mando): usa `adb shell input keyevent DPAD_*|DPAD_CENTER|BACK|MENU`.
- `local.properties` (sdk.dir) y `.env` no se versionan.

## Reglas del proyecto

- **Idioma**: textos de UI y documentación en **español**; identificadores y código en inglés.
- **Solo mando (sabor `tv`)**: todo elemento interactivo debe ser enfocable con D-pad. Usa `FocusSurface` (ui/components) — nunca `Modifier.clickable` suelto ni componentes táctiles de Material 3. En `src/mobile` sí se usa Material 3 y táctil.
- **Tecla ☰ (Menu, sabor `tv`)** = opciones contextuales (`Modifier.onMenuKey`). La pulsación larga de OK no es fiable en el mando del Fire TV: si se ofrece (`onLongClick`), que sea un atajo y la misma acción esté también en ☰.
- **Colores y tipografía** solo vía `Lumen.colors` / `Lumen.type`. Nada de `Color(0x…)` en pantallas salvo overlays de vídeo (blanco/negro).
- **Room es la única fuente de verdad** del catálogo: las pantallas observan Flows de los DAOs; la red solo escribe en Room (excepto EPG corto y fichas de VOD/series, que se piden bajo demanda).
- Listas grandes de Xtream **siempre en streaming** (`XtreamClient.streamEach` + `android.util.JsonReader`). Nunca `parseToJsonElement` sobre `get_live_streams`/`get_vod_streams`/`get_series`: el Fire Stick se queda sin memoria.
- Ficheros grandes (listas Xtream, M3U, XMLTV): **primero a disco, luego parsear** (`withDownloadedFile`). Parsear del socket mientras se escribe en Room hace que el servidor corte ("unexpected end of stream").
- Trabajo que debe terminar aunque se cierre la pantalla (guardar historial, sincronizar) va en `graph.appScope`, no en `rememberCoroutineScope`.
- **Secretos**: contraseñas IPTV y `.conf` WireGuard se guardan cifrados con `SecretBox` (Android Keystore). No loguees URLs de stream: llevan usuario y contraseña.
- **Base de datos**: `fallbackToDestructiveMigration` está activo porque el catálogo es caché, pero `playlists`, `vpn_servers`, `favorites` e `history` son datos del usuario. IMPORTANT: si cambias el esquema, sube `version` **y escribe una `Migration`** que preserve esas tablas.
- Inyección de dependencias manual en `di/AppGraph.kt`. No añadas Hilt/Koin.
- Versiones de librerías solo en `gradle/libs.versions.toml`.
- **Icono y banner del sabor `tv` en `res/drawable-*` (PNG), nunca en `mipmap` ni adaptativos**: el lanzador de Fire OS 7/8 no encuentra iconos `mipmap` de apps instaladas fuera de la tienda y muestra una imagen rota. Si cambias el icono, sube `lumen.versionCode`: Fire TV guarda en caché el icono anterior. El menú de Fire TV pinta `ic_launcher` en un cuadrado sobre el rectángulo gris e ignora `android:banner`: el icono debe ser cuadrado (uno 16:9 sale aplastado). Tras `adb install -r` sale la imagen rota (unos 15 s después) hasta reiniciar: espera a que salga y lanza entonces `adb reboot`, porque si reinicias antes el menú conserva el icono anterior. El nuevo aparece más o menos minuto y medio tras reiniciar; el lanzador no se puede cerrar por ADB.

## VPN (lo más delicado)

- Backend: `com.wireguard.android:tunnel` (`GoBackend`, userspace). Un único túnel llamado `lumen`.
- Proton **no tiene API pública** sin login (devuelve 401). El catálogo de servidores son los `.conf` que el usuario genera en account.protonvpn.com → Descargas → WireGuard. No intentes implementar el login SRP de Proton.
- Ámbito "Solo Lumen" = `IncludedApplications = <packageName>` inyectado en el `.conf` (`ProtonConfigParser.withScope`). Usa siempre `context.packageName` (cambia en debug).
- `VpnService.prepare()` necesita una Activity: pide permiso con `MainActivity.ensureVpnPermission`.

## Releases y actualizaciones

- Sube `lumen.versionCode` (+1) y `lumen.versionName` en `gradle.properties`, crea tag `vX.Y.Z` y push: el workflow `.github/workflows/release.yml` compila, firma y publica APK + `update.json`.
- **Nunca cambies el keystore** (`keystore/lumen-release.jks`): Android rechaza actualizaciones con otra firma.

## Verificación antes de dar algo por terminado

1. `./gradlew assembleTvDebug assembleMobileDebug testTvDebugUnitTest` sin errores (el workflow `build.yml` lo comprueba en cada push).
2. Si tocas UI: prueba la navegación solo con flechas/OK/Atrás/☰ (emulador Android TV API 30 o el Fire TV por ADB).
3. Si tocas VPN: conecta, comprueba la IP pública en la pantalla VPN y desconecta.
