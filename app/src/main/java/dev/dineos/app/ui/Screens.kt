package dev.dineos.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dineos.app.R
import dev.dineos.app.data.ItemRow
import dev.dineos.app.ui.theme.LocalTokens
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// Digits of equal width, so "7 / 9" doesn't shift when it becomes "8 / 9".
private const val TABULAR = "tnum"
private val Clock = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

@Composable
fun LoginScreen(vm: LoginViewModel) {
    // Centered while the form fits, scrollable when it doesn't (keyboard up, large font, landscape):
    // the viewport height becomes the scrolling column's minimum, so the weighted spacers still
    // have space to share. safeDrawing keeps the form clear of the status bar, the cutout and the
    // keyboard; imePadding is what the keyboard itself pushes the content up by.
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.login_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = vm.email,
                onValueChange = { vm.email = it },
                label = { Text(stringResource(R.string.login_email)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                // Lets the password manager fill both fields.
                modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.EmailAddress },
            )
            OutlinedTextField(
                value = vm.password,
                onValueChange = { vm.password = it },
                label = { Text(stringResource(R.string.login_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.signIn() }),
                modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Password },
            )
            vm.error?.let {
                // Assertive: a failed sign-in interrupts whatever the screen reader was saying.
                Status(
                    Icons.Rounded.Warning,
                    stringResource(it),
                    MaterialTheme.colorScheme.error,
                    Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
            }
            Button(
                onClick = vm::signIn,
                enabled = !vm.busy && vm.email.isNotBlank() && vm.password.isNotEmpty(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Text(stringResource(if (vm.busy) R.string.login_busy else R.string.login_submit))
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

/** An icon with its words: a state is never told by color alone. */
@Composable
private fun Status(icon: ImageVector, text: String, color: Color, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(text, color = color, style = MaterialTheme.typography.labelLarge, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SyncChip(sync: SyncState) {
    val tokens = LocalTokens.current
    val (icon, text, color) = when (sync) {
        SyncState.Synced -> Triple(Icons.Rounded.Check, stringResource(R.string.sync_synced), tokens.fgMuted)
        is SyncState.Pending -> Triple(Icons.Rounded.Refresh, pluralStringResource(R.plurals.sync_pending, sync.count, sync.count), tokens.fgMuted)
        is SyncState.Offline -> Triple(
            Icons.Rounded.Warning,
            if (sync.count == 0) stringResource(R.string.sync_offline) else pluralStringResource(R.plurals.sync_offline_pending, sync.count, sync.count),
            tokens.warn,
        )
    }
    Status(
        icon,
        text,
        color,
        // Polite: the count changing is worth saying, but never over something else.
        // The cap keeps the longest state ("Offline, 12 unsent") from squeezing the title and the
        // overflow button out of the app bar at a large font scale.
        Modifier
            .semantics { liveRegion = LiveRegionMode.Polite }
            .widthIn(max = 160.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        maxLines = 1,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(vm: TodayViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    // Refresh whenever the screen appears (also after signing in again), not once per ViewModel.
    LaunchedEffect(Unit) { vm.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.today_title), fontWeight = FontWeight.SemiBold) },
                actions = {
                    SyncChip(ui.sync)
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.menu_more)) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_sign_out)) },
                            onClick = {
                                menuOpen = false
                                // Unsent check-offs die with the local data: ask before losing them.
                                if (ui.sync == SyncState.Synced) vm.signOut() else confirmSignOut = true
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(isRefreshing = ui.refreshing, onRefresh = vm::refresh, modifier = Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (ui.runs.isEmpty() && !ui.refreshing) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.today_empty_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                stringResource(if (ui.sync is SyncState.Offline) R.string.today_empty_offline else R.string.today_empty_body),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                for (run in ui.runs) {
                    item(key = run.id) { RunHeader(run) }
                    items(run.items, key = { "${it.runId} ${it.itemId}" }) { row -> ItemLine(row, onToggle = { vm.toggle(row) }) }
                }
            }
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.sign_out_unsent_title)) },
            text = { Text(stringResource(R.string.sign_out_unsent_body)) },
            confirmButton = {
                TextButton(onClick = { confirmSignOut = false; vm.signOut() }) { Text(stringResource(R.string.menu_sign_out)) }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.stay_signed_in)) } },
        )
    }
}

@Composable
private fun RunHeader(run: RunUi) {
    val tokens = LocalTokens.current
    val due = Clock.format(run.dueAt.atZone(ZoneId.systemDefault()))
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(run.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).semantics { heading() })
            Text(
                stringResource(R.string.run_progress, run.done, run.items.size),
                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = TABULAR),
                fontWeight = FontWeight.SemiBold,
            )
        }
        LinearProgressIndicator(
            progress = { if (run.items.isEmpty()) 0f else run.done.toFloat() / run.items.size },
            color = if (run.complete) tokens.ok else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            drawStopIndicator = {},
            modifier = Modifier.fillMaxWidth(),
        )
        when {
            run.complete -> Status(Icons.Rounded.CheckCircle, stringResource(R.string.run_complete), tokens.ok)
            Instant.now().isAfter(run.dueAt) -> Status(Icons.Rounded.Warning, stringResource(R.string.run_overdue, due), tokens.warn)
            else -> Text(stringResource(R.string.run_due, due), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
        }
    }
    HorizontalDivider()
}

/** The whole row is the target (wet hands, one thumb): 64dp tall, edge to edge. */
@Composable
private fun ItemLine(row: ItemRow, onToggle: () -> Unit) {
    val tokens = LocalTokens.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .toggleable(value = row.checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            // The empty circle is outlined in the muted text color: a control's edge needs 3:1
            // against the page, which the hairline colors don't reach.
            Modifier.size(28.dp).clip(CircleShape).then(
                if (row.checked) Modifier.background(tokens.ok) else Modifier.border(2.dp, tokens.fgMuted, CircleShape),
            ),
            contentAlignment = Alignment.Center,
        ) {
            if (row.checked) Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text(
            row.label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (row.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (row.checked) TextDecoration.LineThrough else null,
        )
    }
    HorizontalDivider()
}
