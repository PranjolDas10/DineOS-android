package dev.dineos.app.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.dineos.api.AuthApi
import dev.dineos.api.model.LoginIn
import dev.dineos.api.model.RefreshIn
import dev.dineos.app.R
import dev.dineos.app.data.ChecklistRepository
import dev.dineos.app.data.ItemRow
import dev.dineos.app.data.Session
import dev.dineos.app.data.Tokens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(private val auth: AuthApi, private val session: Session) : ViewModel() {
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var busy by mutableStateOf(false)
        private set

    @get:StringRes
    var error by mutableStateOf<Int?>(null)
        private set

    fun signIn() {
        if (busy) return
        busy = true
        error = null
        viewModelScope.launch {
            try {
                val reply = auth.login(LoginIn(email = email.trim(), password = password))
                val pair = reply.body()
                when {
                    pair != null -> session.set(Tokens(pair.accessToken, pair.refreshToken))
                    reply.code() == 401 || reply.code() == 422 -> error = R.string.login_wrong
                    reply.code() == 429 -> error = R.string.login_slow_down
                    else -> error = R.string.login_failed
                }
            } catch (_: IOException) {
                error = R.string.login_offline
            }
            busy = false
        }
    }
}

/** One checklist run with its lines, as the screen shows it. */
data class RunUi(val id: String, val name: String, val dueAt: Instant, val items: List<ItemRow>) {
    val done get() = items.count { it.checked }
    val complete get() = done == items.size
}

/** What the sync chip says. Offline wins over pending: it explains why things are pending. */
sealed interface SyncState {
    data object Synced : SyncState
    data class Pending(val count: Int) : SyncState
    data class Offline(val count: Int) : SyncState
}

data class TodayUi(val runs: List<RunUi> = emptyList(), val sync: SyncState = SyncState.Synced, val refreshing: Boolean = true)

/** Rows arrive sorted by due time, run, position; consecutive rows of one run form the run. */
fun groupRuns(rows: List<ItemRow>): List<RunUi> =
    rows.groupBy { it.runId }.map { (id, items) -> RunUi(id, items.first().runName, Instant.parse(items.first().dueAt), items) }

fun syncState(pending: Int, offline: Boolean): SyncState = when {
    offline -> SyncState.Offline(pending)
    pending > 0 -> SyncState.Pending(pending)
    else -> SyncState.Synced
}

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val repository: ChecklistRepository,
    private val auth: AuthApi,
    private val session: Session,
) : ViewModel() {
    private val refreshing = MutableStateFlow(true)

    val ui: StateFlow<TodayUi> =
        combine(repository.items, repository.pendingCount, repository.offline, refreshing) { rows, pending, offline, busy ->
            TodayUi(groupRuns(rows), syncState(pending, offline), busy)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUi())

    fun refresh() {
        viewModelScope.launch {
            refreshing.value = true
            repository.sync()
            refreshing.value = false
        }
    }

    /** Never waits for the network: the tap lands in the local database and the UI follows. */
    fun toggle(row: ItemRow) {
        viewModelScope.launch { repository.check(row, !row.checked) }
    }

    /** One last try to send unsent check-offs, then forget this phone's copy of the data. */
    fun signOut() {
        viewModelScope.launch {
            repository.sync()
            val refresh = session.tokens.value?.refresh
            repository.clear()
            session.set(null)
            // Best effort: revoking server-side needs a network; signing out here must not.
            if (refresh != null) runCatching { auth.logout(RefreshIn(refresh)) }
        }
    }
}
