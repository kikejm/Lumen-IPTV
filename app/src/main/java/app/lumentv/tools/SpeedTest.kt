package app.lumentv.tools

import app.lumentv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class SpeedResult(val downloadMbps: Double, val latencyMs: Long?, val iptvLatencyMs: Long?)

/**
 * Test de velocidad sencillo: descarga durante ~8 s desde [BuildConfig.SPEEDTEST_URL] y mide
 * la latencia HTTP al servidor IPTV. Con la VPN en modo "solo Lumen", mide a través del túnel.
 */
class SpeedTest(http: OkHttpClient) {
    private val client = http.newBuilder().readTimeout(15, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build()

    suspend fun run(iptvServer: String?, onProgress: (Double) -> Unit): SpeedResult = withContext(Dispatchers.IO) {
        val latency = measureLatency("https://speed.cloudflare.com/__down?bytes=0")
        val iptvLatency = iptvServer?.takeIf { it.isNotBlank() }?.let { measureLatency(it) }

        var bytes = 0L
        val start = System.nanoTime()
        val maxNanos = TimeUnit.SECONDS.toNanos(8)
        client.newCall(Request.Builder().url(BuildConfig.SPEEDTEST_URL).build()).execute().use { resp ->
            val stream = resp.body?.byteStream() ?: return@use
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                bytes += n
                val elapsed = System.nanoTime() - start
                onProgress(mbps(bytes, elapsed))
                if (elapsed > maxNanos) break
            }
        }
        SpeedResult(mbps(bytes, System.nanoTime() - start), latency, iptvLatency)
    }

    private fun measureLatency(url: String): Long? = runCatching {
        (1..3).minOf {
            val t = System.nanoTime()
            client.newCall(Request.Builder().url(url).head().build()).execute().close()
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t)
        }
    }.getOrNull()

    private fun mbps(bytes: Long, nanos: Long): Double =
        if (nanos <= 0) 0.0 else bytes * 8.0 / (nanos / 1e9) / 1_000_000.0
}
