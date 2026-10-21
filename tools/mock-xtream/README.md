# Servidor Xtream de demostración

Panel Xtream Codes falso con datos ficticios (40 canales, 30 películas, 12 series, EPG XMLTV,
catch-up) para probar Lumen en el emulador sin usar una lista real. Requiere Python 3 y Pillow.

```bash
python tools/mock-xtream/mock_xtream.py
```

- Escucha en `0.0.0.0:8099`. Desde el emulador Android el PC es `http://10.0.2.2:8099`
  (usuario y contraseña: cualquiera, p. ej. `demo` / `demo`).
- La primera vez descarga ~7 MB de vídeo de prueba (Big Buck Bunny, CC-BY) y genera logos y pósters en `img/`.
- Todos los streams sirven el mismo vídeo.

Emulador recomendado: imagen `system-images;android-30;android-tv;x86` (Android 11 ≈ Fire OS 8).
