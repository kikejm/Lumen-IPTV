package app.lumentv.remote

import android.util.Log
import app.lumentv.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.security.SecureRandom

/** Lo que el móvil puede enviar a la tele. */
interface ImportHandler {
    suspend fun onVpnConfigs(files: List<Pair<String?, String>>): String
    suspend fun onXtream(name: String, server: String, username: String, password: String): String
    suspend fun onM3u(name: String, url: String, epgUrl: String): String
}

data class ImportServerInfo(val url: String, val token: String)

/**
 * Mini servidor HTTP en la red local para configurar la app desde el móvil
 * (escribir con el mando es muy lento). Solo está activo mientras la pantalla de
 * importación está abierta y exige un token aleatorio en cada petición.
 */
class ImportServer(private val scope: CoroutineScope, private val json: Json) {

    private var socket: ServerSocket? = null
    private var job: Job? = null
    private val _info = MutableStateFlow<ImportServerInfo?>(null)
    val info: StateFlow<ImportServerInfo?> = _info.asStateFlow()
    private val _lastEvent = MutableStateFlow<String?>(null)
    val lastEvent: StateFlow<String?> = _lastEvent.asStateFlow()

    fun start(handler: ImportHandler) {
        if (job?.isActive == true) return
        val token = (100000 + SecureRandom().nextInt(900000)).toString()
        val ip = localIpv4() ?: "0.0.0.0"
        job = scope.launch(Dispatchers.IO) {
            try {
                val server = ServerSocket(BuildConfig.IMPORT_SERVER_PORT).also { socket = it }
                _info.value = ImportServerInfo("http://$ip:${server.localPort}/?t=$token", token)
                while (isActive) {
                    val client = server.accept()
                    launch { handle(client, token, handler) }
                }
            } catch (e: SocketException) {
                // cerrado con stop()
            } catch (e: Exception) {
                Log.e(TAG, "Servidor de importación caído", e)
                _lastEvent.value = "Error: ${e.message}"
            }
        }
    }

    fun stop() {
        runCatching { socket?.close() }
        socket = null
        job?.cancel()
        job = null
        _info.value = null
    }

    private suspend fun handle(client: Socket, token: String, handler: ImportHandler) {
        client.use { s ->
            s.soTimeout = 15_000
            val input = BufferedInputStream(s.getInputStream())
            val out = s.getOutputStream()
            val requestLine = readLine(input) ?: return
            val (method, target) = requestLine.split(' ').let { (it.getOrNull(0) ?: "") to (it.getOrNull(1) ?: "/") }
            val headers = HashMap<String, String>()
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                headers[line.substringBefore(':').trim().lowercase()] = line.substringAfter(':').trim()
            }
            val path = target.substringBefore('?')
            val query = target.substringAfter('?', "")
            val reqToken = query.split('&').firstOrNull { it.startsWith("t=") }?.removePrefix("t=")

            if (reqToken != token) return respond(out, 403, "text/plain", "Token no válido. Escanea de nuevo el QR de la tele.")
            when {
                method == "GET" && path == "/" -> respond(out, 200, "text/html; charset=utf-8", PAGE.replace("__TOKEN__", token))
                method == "POST" && path.startsWith("/api/") -> {
                    val length = headers["content-length"]?.toIntOrNull() ?: 0
                    if (length > MAX_BODY) return respond(out, 413, "text/plain", "Demasiado grande")
                    val body = String(readExactly(input, length), Charsets.UTF_8)
                    val result = runCatching { dispatch(path, body, handler) }
                    val ok = result.isSuccess
                    val message = result.getOrElse { it.message ?: "Error" }
                    _lastEvent.value = message
                    respond(out, if (ok) 200 else 400, "application/json; charset=utf-8", json.encodeToString(JsonObject.serializer(),
                        JsonObject(mapOf("ok" to JsonPrimitive(ok), "message" to JsonPrimitive(message)))))
                }
                else -> respond(out, 404, "text/plain", "No encontrado")
            }
        }
    }

    private suspend fun dispatch(path: String, body: String, handler: ImportHandler): String {
        val obj = json.parseToJsonElement(body) as JsonObject
        fun s(k: String) = (obj[k] as? JsonPrimitive)?.contentOrNull.orEmpty()
        return when (path) {
            "/api/vpn" -> {
                val files = (obj["files"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.map { f ->
                    (f["name"] as? JsonPrimitive)?.contentOrNull to ((f["content"] as? JsonPrimitive)?.contentOrNull.orEmpty())
                }
                handler.onVpnConfigs(files)
            }
            "/api/xtream" -> handler.onXtream(s("name"), s("server"), s("username"), s("password"))
            "/api/m3u" -> handler.onM3u(s("name"), s("url"), s("epg"))
            else -> error("Ruta desconocida")
        }
    }

    private fun respond(out: OutputStream, code: Int, type: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val status = when (code) { 200 -> "OK"; 400 -> "Bad Request"; 403 -> "Forbidden"; 404 -> "Not Found"; else -> "Error" }
        out.write("HTTP/1.1 $code $status\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n".toByteArray())
        out.write(bytes)
        out.flush()
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(b.toChar())
            if (sb.length > 8192) return sb.toString()
        }
    }

    private fun readExactly(input: InputStream, length: Int): ByteArray {
        val buf = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(buf, read, length - read)
            if (n == -1) break
            read += n
        }
        return buf
    }

    companion object {
        private const val TAG = "ImportServer"
        private const val MAX_BODY = 2 * 1024 * 1024

        /** IPv4 privada del Fire TV (Wi-Fi o Ethernet), ignorando la interfaz del túnel VPN. */
        fun localIpv4(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback && !it.name.startsWith("tun") && !it.name.startsWith("lumen") }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }?.hostAddress
        }.getOrNull()

        private val PAGE = """
<!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="preconnect" href="https://fonts.gstatic.com" crossorigin><link href="https://fonts.googleapis.com/css2?family=Alan+Sans:wght@400;800&display=swap" rel="stylesheet">
<title>IPTV · Configurar</title>
<style>
:root{color-scheme:light dark;--bg:#f2f2f7;--card:#fff;--text:#000;--muted:#3c3c4399;--accent:#007aff;--ok:#34c759;--err:#ff3b30}
@media (prefers-color-scheme:dark){:root{--bg:#000;--card:#1c1c1e;--text:#fff;--muted:#ebebf599;--accent:#0a84ff;--ok:#30d158;--err:#ff453a}}
*{box-sizing:border-box}body{margin:0;font:400 17px 'Alan Sans',-apple-system,system-ui,sans-serif;background:var(--bg);color:var(--text);padding:24px 16px 48px}
h1{font-size:34px;font-weight:800;margin:8px 0 4px}p.sub{color:var(--muted);margin:0 0 24px}
section{background:var(--card);border-radius:18px;padding:20px;margin:0 0 20px}
h2{font-size:22px;font-weight:800;margin:0 0 6px}small{color:var(--muted);display:block;margin-bottom:14px}
label{display:block;font-size:14px;color:var(--muted);margin:12px 0 6px}
input,textarea{width:100%;font:inherit;padding:12px 14px;border-radius:12px;border:1px solid #8884;background:transparent;color:inherit}
textarea{min-height:120px;font-family:ui-monospace,monospace;font-size:13px}
button{margin-top:16px;width:100%;font-family:inherit;font-weight:400;font-size:17px;padding:14px;border:0;border-radius:14px;background:var(--accent);color:#fff}
.seg{display:flex;gap:8px;margin-bottom:8px}.seg button{margin:0;background:#8882;color:inherit}.seg button.on{background:var(--accent);color:#fff}
.msg{margin-top:12px;font-weight:400}.ok{color:var(--ok)}.err{color:var(--err)}
</style></head><body>
<h1>IPTV</h1><p class="sub">Configura la tele desde aquí. Los datos van directos a tu Fire TV por tu Wi‑Fi.</p>
<section><h2>Servidores VPN (Proton)</h2>
<small>En account.protonvpn.com → Descargas → Configuración WireGuard, genera un fichero por servidor (plataforma Android/Router) y súbelos aquí. Puedes seleccionar varios.</small>
<input type="file" id="files" accept=".conf,text/plain" multiple>
<label>…o pega el contenido de un .conf</label><textarea id="paste" placeholder="[Interface]&#10;PrivateKey = …"></textarea>
<button onclick="sendVpn()">Enviar a la tele</button><div id="vpnMsg" class="msg"></div></section>
<section><h2>Lista IPTV</h2>
<div class="seg"><button id="tx" class="on" onclick="mode('x')">Xtream Codes</button><button id="tm" onclick="mode('m')">M3U</button></div>
<label>Nombre</label><input id="name" placeholder="Mi lista">
<div id="xf"><label>Servidor</label><input id="server" placeholder="http://servidor.com:8080" autocapitalize="off">
<label>Usuario</label><input id="user" autocapitalize="off"><label>Contraseña</label><input id="pass" type="password"></div>
<div id="mf" hidden><label>URL M3U</label><input id="m3u" autocapitalize="off"><label>URL EPG (XMLTV, opcional)</label><input id="epg" autocapitalize="off"></div>
<button onclick="sendList()">Guardar en la tele</button><div id="listMsg" class="msg"></div></section>
<script>
const T="__TOKEN__";let m='x';
function mode(x){m=x;document.getElementById('xf').hidden=x!=='x';document.getElementById('mf').hidden=x!=='m';
document.getElementById('tx').className=x==='x'?'on':'';document.getElementById('tm').className=x==='m'?'on':'';}
async function post(path,data,el){el.className='msg';el.textContent='Enviando…';
try{const r=await fetch(path+'?t='+T,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(data)});
const j=await r.json();el.className='msg '+(j.ok?'ok':'err');el.textContent=j.message;}catch(e){el.className='msg err';el.textContent='Sin conexión con la tele';}}
async function sendVpn(){const files=[];for(const f of document.getElementById('files').files){files.push({name:f.name,content:await f.text()});}
const p=document.getElementById('paste').value.trim();if(p)files.push({name:null,content:p});
if(!files.length){document.getElementById('vpnMsg').textContent='Selecciona o pega al menos una configuración';return;}
post('/api/vpn',{files},document.getElementById('vpnMsg'));}
function v(id){return document.getElementById(id).value.trim();}
function sendList(){const el=document.getElementById('listMsg');
if(m==='x')post('/api/xtream',{name:v('name'),server:v('server'),username:v('user'),password:document.getElementById('pass').value},el);
else post('/api/m3u',{name:v('name'),url:v('m3u'),epg:v('epg')},el);}
</script></body></html>
"""
    }
}
