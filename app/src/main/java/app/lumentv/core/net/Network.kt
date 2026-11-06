package app.lumentv.core.net

import android.content.Context
import android.net.ConnectivityManager

/**
 * ¿La conexión activa es de pago por uso (datos móviles, punto de acceso del móvil)? Android lo
 * decide por la red, no por el tipo: un wifi marcado como «de uso medido» también cuenta.
 */
fun Context.isMeteredNetwork(): Boolean = runCatching {
    (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).isActiveNetworkMetered
}.getOrDefault(false)
