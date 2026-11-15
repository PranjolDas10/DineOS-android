package dev.dineos.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Tokens(val access: String, val refresh: String)

/** Where the token pair lives between launches. An interface because the real one needs the
 * Android Keystore, which a JVM unit test doesn't have. */
interface TokenVault {
    suspend fun read(): Tokens?
    suspend fun write(tokens: Tokens?)
}

/** Who is signed in. The tokens are held in memory so the network layer can read them without
 * touching disk on every request; the vault is only read once at start and written on change. */
class Session(private val vault: TokenVault) {
    private val _tokens = MutableStateFlow<Tokens?>(null)
    private val _loaded = MutableStateFlow(false)

    /** null = signed out (or not loaded yet: check [loaded] before showing the sign-in screen). */
    val tokens: StateFlow<Tokens?> = _tokens
    val loaded: StateFlow<Boolean> = _loaded

    suspend fun load() {
        _tokens.value = vault.read()
        _loaded.value = true
    }

    suspend fun set(tokens: Tokens?) {
        vault.write(tokens)
        _tokens.value = tokens
    }
}
