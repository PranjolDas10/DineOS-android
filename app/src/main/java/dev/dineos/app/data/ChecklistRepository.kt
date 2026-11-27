package dev.dineos.app.data

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.dineos.api.ChecklistsApi
import dev.dineos.api.model.CheckIn
import dev.dineos.api.model.ChecksIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Today's checklists, offline-first: the screen only ever reads the local database. A tap
 * writes there and queues an op; [sync] sends the queue and pulls the server's list. */
@Singleton
class ChecklistRepository @Inject constructor(
    private val api: ChecklistsApi,
    private val dao: ChecklistDao,
    private val work: WorkManager,
) {
    private val lock = Mutex()
    private val _offline = MutableStateFlow(false)

    val items: Flow<List<ItemRow>> = dao.items()
    val pendingCount: Flow<Int> = dao.pendingCount()

    /** True after a sync that couldn't reach the server, until one does. */
    val offline: StateFlow<Boolean> = _offline

    suspend fun check(row: ItemRow, checked: Boolean) {
        dao.check(PendingCheck(uuid7().toString(), row.runId, row.itemId, checked, Instant.now().toString()))
        requestSync()
    }

    /** Hands the queue to WorkManager, which runs it when there is a network, retries with
     * backoff, and survives the app being closed or the phone restarting. */
    fun requestSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        // Append, not KEEP: [sync] reads the queue once when it starts, so a tap made while one
        // is running would never be sent if KEEP dropped the new request. A burst of taps chains
        // a few runs instead, which costs nothing: sync is idempotent and holds a lock, so the
        // extra runs find an empty queue and just re-pull the list.
        work.enqueueUniqueWork("sync", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /** Push, then pull. False when the server couldn't be reached; nothing is lost, the ops
     * stay queued. Safe to run twice or concurrently with itself: the server ignores op ids
     * it has seen, and the lock keeps two runs from interleaving their writes. */
    suspend fun sync(): Boolean = lock.withLock {
        try {
            // Push first, so the list pulled afterwards already includes these ops.
            for ((runId, ops) in dao.pending().groupBy { it.runId }) {
                val body = ChecksIn(ops.map { CheckIn(id = UUID.fromString(it.id), itemId = UUID.fromString(it.itemId), checkedAt = it.checkedAt, checked = it.checked) })
                val reply = api.recordChecks(UUID.fromString(runId), body)
                // 404 or 422: the run or the item is gone on the server. No retry can fix that,
                // so the ops are dropped instead of blocking everything queued behind them.
                if (!reply.isSuccessful && reply.code() != 404 && reply.code() != 422) throw IOException("HTTP ${reply.code()}")
                dao.delete(ops.map { it.id })
            }
            val reply = api.today()
            val today = reply.body() ?: throw IOException("HTTP ${reply.code()}")
            dao.replace(
                today.runs.flatMap { run ->
                    run.items.mapIndexedNotNull { position, item ->
                        item.id?.let { ItemRow(run.id.toString(), it.toString(), run.name, run.dueAt, position, item.label, item.checked) }
                    }
                },
            )
            _offline.value = false
            true
        } catch (_: IOException) {
            _offline.value = true
            false
        }
    }

    suspend fun clear() = dao.clearAll()
}

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: ChecklistRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = if (repository.sync()) Result.success() else Result.retry()
}
