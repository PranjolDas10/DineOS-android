package dev.dineos.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import java.security.SecureRandom
import java.util.UUID

/** One line of a checklist run, as shown on screen. The run's own fields are repeated on each
 * row: a restaurant has one or two runs a day, so a second table would only add a join. */
@Entity(tableName = "checklist_items", primaryKeys = ["runId", "itemId"])
data class ItemRow(
    val runId: String,
    val itemId: String,
    val runName: String,
    val dueAt: String, // ISO-8601 UTC, as the API sends it
    val position: Int,
    val label: String,
    val checked: Boolean,
)

/** A check or uncheck made on this phone that the server hasn't confirmed yet. `id` is a
 * UUIDv7 made here: the server ignores an id it has already stored, so sending the same op
 * twice (a timeout, a retried job) changes nothing. */
@Entity(tableName = "pending_checks")
data class PendingCheck(
    @PrimaryKey val id: String,
    val runId: String,
    val itemId: String,
    val checked: Boolean,
    val checkedAt: String, // when it happened on the phone, not when it synced
)

/** The server's list with this phone's unsent ops applied on top, oldest first, so a tap made
 * offline doesn't flicker back when a refresh lands before the op has synced. Ops for items
 * the server no longer lists are ignored. */
fun merge(server: List<ItemRow>, pending: List<PendingCheck>): List<ItemRow> {
    val latest = pending.sortedBy { it.id }.associate { (it.runId to it.itemId) to it.checked }
    return server.map { row -> latest[row.runId to row.itemId]?.let { row.copy(checked = it) } ?: row }
}

@Dao
abstract class ChecklistDao {
    @Query("SELECT * FROM checklist_items ORDER BY dueAt, runId, position")
    abstract fun items(): Flow<List<ItemRow>>

    @Query("SELECT COUNT(*) FROM pending_checks")
    abstract fun pendingCount(): Flow<Int>

    @Query("SELECT * FROM pending_checks ORDER BY id")
    abstract suspend fun pending(): List<PendingCheck>

    @Query("DELETE FROM pending_checks WHERE id IN (:ids)")
    abstract suspend fun delete(ids: List<String>)

    @Query("UPDATE checklist_items SET checked = :checked WHERE runId = :runId AND itemId = :itemId")
    protected abstract suspend fun setChecked(runId: String, itemId: String, checked: Boolean)

    @Insert
    protected abstract suspend fun insert(op: PendingCheck)

    @Insert
    protected abstract suspend fun insert(rows: List<ItemRow>)

    @Query("DELETE FROM checklist_items")
    protected abstract suspend fun clearItems()

    /** The tap: the visible state and the op to send change together or not at all. */
    @Transaction
    open suspend fun check(op: PendingCheck) {
        setChecked(op.runId, op.itemId, op.checked)
        insert(op)
    }

    /** Swap in what the server says, keeping unsent taps on top. One transaction, so the
     * screen never observes the half-way state (or a tap made mid-refresh being lost). */
    @Transaction
    open suspend fun replace(server: List<ItemRow>) {
        clearItems()
        insert(merge(server, pending()))
    }

    @Transaction
    open suspend fun clearAll() {
        clearItems()
        delete(pending().map { it.id })
    }
}

@Database(entities = [ItemRow::class, PendingCheck::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun checklist(): ChecklistDao
}

private val random = SecureRandom()

/** A UUIDv7: 48 bits of millisecond time, then random bits. Ids made later sort later, which is
 * how [merge] and the server order ops, and offline phones can make them without asking. */
fun uuid7(nowMillis: Long = System.currentTimeMillis()): UUID {
    val high = (nowMillis shl 16) or 0x7000L or (random.nextLong() and 0x0FFFL)
    val low = (random.nextLong() and 0x3FFFFFFFFFFFFFFFL) or Long.MIN_VALUE // variant bits "10"
    return UUID(high, low)
}
