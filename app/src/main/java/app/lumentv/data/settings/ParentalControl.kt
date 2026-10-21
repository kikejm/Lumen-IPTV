package app.lumentv.data.settings

import app.lumentv.core.util.sha256Hex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Control parental: PIN de 4 dígitos (guardado como hash) y categorías bloqueadas.
 * Una vez introducido el PIN, el contenido queda desbloqueado hasta cerrar la app.
 */
class ParentalControl(private val settings: SettingsRepository) {

    private val _unlocked = MutableStateFlow(false)
    val sessionUnlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    fun hasPin(): Boolean = settings.current.hasParentalPin

    fun isLocked(kindName: String, categoryId: String): Boolean =
        !_unlocked.value && settings.current.hasParentalPin && key(kindName, categoryId) in settings.current.lockedCategories

    fun verify(pin: String): Boolean {
        val ok = hash(pin) == settings.current.parentalPinHash
        if (ok) _unlocked.value = true
        return ok
    }

    suspend fun setPin(pin: String) {
        require(pin.length == 4 && pin.all(Char::isDigit)) { "El PIN debe tener 4 dígitos" }
        settings.setParentalPinHash(hash(pin))
        _unlocked.value = false
    }

    suspend fun removePin() {
        settings.setParentalPinHash("")
        settings.setLockedCategories(emptySet())
    }

    suspend fun toggleLock(kindName: String, categoryId: String) {
        val k = key(kindName, categoryId)
        val current = settings.current.lockedCategories
        settings.setLockedCategories(if (k in current) current - k else current + k)
    }

    fun isMarkedLocked(kindName: String, categoryId: String) = key(kindName, categoryId) in settings.current.lockedCategories

    fun lockSession() { _unlocked.value = false }

    private fun key(kindName: String, categoryId: String) = "$kindName:$categoryId"
    private fun hash(pin: String) = sha256Hex("lumen-parental:$pin".toByteArray())
}
