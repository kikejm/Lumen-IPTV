package app.lumentv.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import app.lumentv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val notes: String,
    val apkUrl: String,
    val sha256: String,
    /** Si la descarga necesita la cabecera de autenticación de GitHub (repos privados). */
    val viaGithubApi: Boolean,
) {
    val isNewer: Boolean get() = versionCode > BuildConfig.VERSION_CODE
}

/**
 * Actualizaciones OTA para sideload (Fire TV no tiene Play Store para esta app, y el móvil se
 * instala desde la Release).
 *
 * Fuente (por prioridad):
 *  1. GitHub Releases (`LUMEN_UPDATE_GITHUB_REPO`, token opcional para repos privados). La TV y el
 *     móvil comparten Release; cada sabor lee su manifiesto ([ReleaseAssets.manifestName]) y
 *     descarga el APK que nombra (lo genera el workflow `release.yml`).
 *  2. Un manifiesto JSON en cualquier URL (`LUMEN_UPDATE_MANIFEST_URL` en la TV,
 *     `LUMEN_UPDATE_MOBILE_MANIFEST_URL` en el móvil).
 *
 * El APK se verifica con SHA-256 y se instala con el instalador del sistema. Android exige
 * que esté firmado con la misma clave que la versión instalada.
 */
class UpdateManager(private val context: Context, private val http: OkHttpClient, private val json: Json) {

    val isConfigured: Boolean
        get() = BuildConfig.UPDATE_GITHUB_REPO.isNotBlank() || BuildConfig.UPDATE_MANIFEST_URL.isNotBlank()

    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        when {
            BuildConfig.UPDATE_GITHUB_REPO.isNotBlank() -> checkGithub(BuildConfig.UPDATE_GITHUB_REPO)
            BuildConfig.UPDATE_MANIFEST_URL.isNotBlank() -> parseManifest(getText(BuildConfig.UPDATE_MANIFEST_URL, github = false), viaGithub = false)
            else -> null
        }
    }

    private fun checkGithub(repo: String): UpdateInfo {
        val release = json.parseToJsonElement(getText("https://api.github.com/repos/$repo/releases/latest", github = true)) as JsonObject
        val assets = (release["assets"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val manifestName = ReleaseAssets.manifestName(BuildConfig.FLAVOR)
        val manifestAsset = assets.firstOrNull { it.s("name") == manifestName }
            ?: throw IOException(
                if (BuildConfig.FLAVOR == ReleaseAssets.MOBILE) "La última versión publicada no incluye la app del móvil"
                else "La última release no contiene $manifestName",
            )
        val manifest = parseManifest(getText(manifestAsset.s("url"), github = true, binary = true), viaGithub = true)
        // La Release lleva los APK de la TV y del móvil: se baja el que nombra el manifiesto.
        val apkName = ReleaseAssets.apkName(manifest.apkUrl)
        val apkAsset = assets.firstOrNull { it.s("name") == apkName }
            ?: throw IOException("La última release no contiene ${apkName.ifBlank { "el APK" }}")
        return manifest.copy(apkUrl = apkAsset.s("url"), notes = manifest.notes.ifBlank { release.s("body") })
    }

    private fun parseManifest(text: String, viaGithub: Boolean): UpdateInfo {
        val obj = json.parseToJsonElement(text) as JsonObject
        fun s(k: String) = (obj[k] as? JsonPrimitive)?.contentOrNull.orEmpty()
        return UpdateInfo(
            versionCode = (obj["versionCode"] as? JsonPrimitive)?.intOrNull ?: s("versionCode").toIntOrNull() ?: 0,
            versionName = s("versionName"),
            notes = s("notes"),
            apkUrl = s("apkUrl"),
            sha256 = s("sha256").lowercase(),
            viaGithubApi = viaGithub,
        )
    }

    suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val file = File(dir, "lumen-${info.versionName.ifBlank { info.versionCode.toString() }}.apk")
        val request = requestFor(info.apkUrl, github = info.viaGithubApi, binary = true)
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} al descargar el APK")
            val body = resp.body ?: throw IOException("Descarga vacía")
            val total = body.contentLength().takeIf { it > 0 }
            val digest = MessageDigest.getInstance("SHA-256")
            body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        done += n
                        total?.let { onProgress(done.toFloat() / it) }
                    }
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (info.sha256.isNotBlank() && actual != info.sha256) {
                file.delete()
                throw IOException("El APK descargado no coincide con su SHA-256")
            }
        }
        file
    }

    /** `true` si Android permite a Lumen instalar APKs (Fire OS 8 lo pide por app). */
    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Se ha abierto el instalador o sus ajustes: la Activity no debe tomarlo como que el usuario sale de la app. */
    @Volatile
    var openedSystemScreen = false

    fun openInstallPermissionSettings() {
        openedSystemScreen = true
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun install(apk: File) {
        openedSystemScreen = true
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun getText(url: String, github: Boolean, binary: Boolean = false): String =
        http.newCall(requestFor(url, github, binary)).execute().use { resp ->
            if (resp.code == 404) throw IOException("No hay releases publicadas (o el repo es privado y falta el token)")
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            resp.body?.string().orEmpty()
        }

    private fun requestFor(url: String, github: Boolean, binary: Boolean): Request {
        val b = Request.Builder().url(url)
        if (github) {
            b.header("Accept", if (binary) "application/octet-stream" else "application/vnd.github+json")
            b.header("X-GitHub-Api-Version", "2022-11-28")
            BuildConfig.UPDATE_GITHUB_TOKEN.takeIf { it.isNotBlank() }?.let { b.header("Authorization", "Bearer $it") }
        }
        return b.build()
    }
}

/**
 * Ficheros de una Release compartida por la TV y el móvil (ver `.github/workflows/release.yml`):
 * `lumen-X.Y.Z.apk` + `update.json` para la TV y `lumen-mobile-X.Y.Z.apk` + `update-mobile.json`
 * para el móvil. `update.json` no cambia de nombre: lo buscan las TV ya instaladas.
 */
internal object ReleaseAssets {
    const val MOBILE = "mobile"

    fun manifestName(flavor: String): String = if (flavor == MOBILE) "update-mobile.json" else "update.json"

    /** Nombre del fichero al final de la `apkUrl` del manifiesto (sin parámetros). */
    fun apkName(apkUrl: String): String = apkUrl.substringBefore('?').substringBefore('#').substringAfterLast('/')
}
