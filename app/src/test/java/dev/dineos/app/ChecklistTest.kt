package dev.dineos.app

import dev.dineos.app.data.ItemRow
import dev.dineos.app.data.PendingCheck
import dev.dineos.app.data.merge
import dev.dineos.app.data.uuid7
import dev.dineos.app.ui.SyncState
import dev.dineos.app.ui.groupRuns
import dev.dineos.app.ui.syncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ChecklistTest {
    private fun row(item: String, checked: Boolean, run: String = "run") =
        ItemRow(run, item, "Closing", "2026-11-15T04:00:00Z", 0, item, checked)

    private fun op(id: String, item: String, checked: Boolean, run: String = "run") =
        PendingCheck(id, run, item, checked, "2026-11-15T03:00:00Z")

    @Test
    fun `unsent taps stay on top of what the server says`() {
        val server = listOf(row("fryer", false), row("floors", true), row("till", false))
        val pending = listOf(
            op("02", "fryer", false), // checked, then unchecked: the later op wins...
            op("01", "fryer", true), // ...whatever order they are listed in
            op("03", "floors", false),
            op("04", "gone", true), // an item the server no longer lists
            op("05", "till", true, run = "other"), // same item id on another run
        )
        assertEquals(listOf(row("fryer", false), row("floors", false), row("till", false)), merge(server, pending))
        assertEquals(server, merge(server, emptyList()))
    }

    @Test
    fun `ids made later sort later`() {
        val ids = (0L until 50L).map { uuid7(nowMillis = 1_700_000_000_000 + it) }
        assertEquals(ids.map { it.toString() }, ids.map { it.toString() }.sorted())
        assertTrue(ids.all { it.version() == 7 && it.variant() == 2 })
        assertEquals(50, ids.toSet().size)
    }

    @Test
    fun `rows group into runs and count what is done`() {
        val rows = listOf(row("a", true), row("b", false), row("c", true, run = "late"))
        val runs = groupRuns(rows, now = Instant.parse("2026-11-15T03:00:00Z"))
        assertEquals(listOf("run" to 1, "late" to 1), runs.map { it.id to it.done })
        assertEquals(listOf(false, true), runs.map { it.complete })
        // Every row here is due at 04:00Z: overdue is read from the clock the caller passes in.
        assertEquals(listOf(false, false), runs.map { it.overdue })
        assertEquals(listOf(true, true), groupRuns(rows, now = Instant.parse("2026-11-15T04:00:01Z")).map { it.overdue })
    }

    @Test
    fun `the chip explains pending work by being offline`() {
        assertEquals(SyncState.Synced, syncState(pending = 0, offline = false))
        assertEquals(SyncState.Pending(2), syncState(pending = 2, offline = false))
        assertEquals(SyncState.Offline(2), syncState(pending = 2, offline = true))
    }
}
