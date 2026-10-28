package com.coach.rotation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val storage = Storage(this)
        setContent {
            MaterialTheme(colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                App(storage, onShare = ::share)
            }
        }
    }

    private fun share(text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(send, "Share lineup"))
    }
}

private enum class Tab(val label: String) { TEAM("Team"), GAME("Lineup"), SEASON("Season") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(storage: Storage, onShare: (String) -> Unit) {
    var state by remember { mutableStateOf(storage.load()) }
    fun update(new: AppState) {
        state = new
        storage.save(new)
    }
    var tab by rememberSaveable { mutableStateOf(if (state.players.isEmpty()) Tab.TEAM else Tab.GAME) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Soccer Rotation") }) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(tab == Tab.TEAM, { tab = Tab.TEAM }, { Icon(Icons.Filled.Person, null) }, label = { Text("Team") })
                NavigationBarItem(tab == Tab.GAME, { tab = Tab.GAME }, { Icon(Icons.Filled.PlayArrow, null) }, label = { Text("Lineup") })
                NavigationBarItem(tab == Tab.SEASON, { tab = Tab.SEASON }, { Icon(Icons.Filled.DateRange, null) }, label = { Text("Season") })
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                Tab.TEAM -> TeamScreen(state, ::update)
                Tab.GAME -> GameScreen(state, ::update, onShare)
                Tab.SEASON -> SeasonScreen(state, ::update)
            }
        }
    }
}

// ---------------------------------------------------------------- Team

@Composable
private fun TeamScreen(state: AppState, update: (AppState) -> Unit) {
    var newName by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<Player?>(null) }
    var deleting by remember { mutableStateOf<Player?>(null) }

    fun add() {
        val name = newName.trim()
        if (name.isEmpty()) return
        update(state.copy(players = state.players + Player(state.nextId, name), nextId = state.nextId + 1))
        newName = ""
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(
                "${state.present.size} of ${state.players.size} here today",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Uncheck kids who are absent. Turn off \"Goalie\" for anyone who shouldn't play in goal.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(state.players, key = { it.id }) { p ->
            val here = p.id !in state.absent
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = here, onCheckedChange = {
                        update(state.copy(absent = if (it) state.absent - p.id else state.absent + p.id))
                    })
                    Text(
                        p.name,
                        Modifier.weight(1f).clickable { renaming = p }.padding(vertical = 12.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (here) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                    )
                    FilterChip(
                        selected = p.canPlayGoalie,
                        onClick = {
                            update(state.copy(players = state.players.map { if (it.id == p.id) it.copy(canPlayGoalie = !it.canPlayGoalie) else it }))
                        },
                        label = { Text("Goalie") },
                    )
                    IconButton(onClick = { deleting = p }) { Icon(Icons.Filled.Delete, "Remove ${p.name}") }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Add player") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { add() }),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                    ),
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(onClick = ::add) { Icon(Icons.Filled.Add, "Add") }
            }
        }
    }

    renaming?.let { p ->
        var text by remember(p.id) { mutableStateOf(p.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename") },
            text = { OutlinedTextField(text, { text = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    if (text.isNotBlank()) {
                        update(state.copy(players = state.players.map { if (it.id == p.id) it.copy(name = text.trim()) else it }))
                    }
                    renaming = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }

    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Remove ${p.name}?") },
            text = { Text("They'll be taken off the team and out of season totals.") },
            confirmButton = {
                TextButton(onClick = {
                    update(state.copy(
                        players = state.players.filter { it.id != p.id },
                        absent = state.absent - p.id,
                        season = state.season - p.id,
                    ))
                    deleting = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

// ---------------------------------------------------------------- Lineup

@Composable
private fun GameScreen(state: AppState, update: (AppState) -> Unit, onShare: (String) -> Unit) {
    val plan = state.plan
    val names = state.players.associate { it.id to it.name }
    var selected by remember { mutableStateOf<Pair<Int, RotationPlanner.Slot>?>(null) }
    var confirmNew by remember { mutableStateOf(false) }
    var confirmSave by remember { mutableStateOf(false) }

    fun makeLineup() {
        selected = null
        update(state.copy(plan = RotationPlanner.generate(state.present, state.season), planSaved = false))
    }

    val planIds = plan?.quarters?.firstOrNull()?.let { it.positions.values + it.bench }?.toSet()
    val attendanceChanged = planIds != null && planIds != state.present.map { it.id }.toSet()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { if (plan == null || state.planSaved) makeLineup() else confirmNew = true },
                    enabled = state.present.isNotEmpty(),
                ) {
                    Icon(Icons.Filled.Refresh, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (plan == null) "Make lineup" else "New lineup")
                }
                if (plan != null) {
                    OutlinedButton(onClick = {
                        val title = "Lineup – " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date())
                        onShare(RotationPlanner.asText(plan, state.players.filter { it.id in planIds.orEmpty() }, title))
                    }) { Icon(Icons.Filled.Share, "Share") }
                    OutlinedButton(onClick = { confirmSave = true }, enabled = !state.planSaved && !attendanceChanged) {
                        Icon(Icons.Filled.Check, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.planSaved) "Saved" else "Save game")
                    }
                }
            }
        }

        if (plan == null) {
            item {
                Text(
                    if (state.players.isEmpty()) "Add your players on the Team tab first."
                    else "${state.present.size} players here. Tap Make lineup to build the four quarters.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@LazyColumn
        }

        if (attendanceChanged) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        "Attendance changed since this lineup was made. Tap New lineup to include the right kids.",
                        Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }

        item {
            Text(
                "Tap two players in the same quarter to swap them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        plan.quarters.forEachIndexed { qi, quarter ->
            item(key = "q$qi") {
                QuarterCard(qi, quarter, names, selected?.takeIf { it.first == qi }?.second) { slot ->
                    val current = selected
                    selected = when {
                        current == null || current.first != qi -> qi to slot
                        current.second == slot -> null
                        else -> {
                            update(state.copy(plan = RotationPlanner.swap(plan, qi, current.second, slot)))
                            null
                        }
                    }
                }
            }
        }

        item { PlayTimeCard(plan, state.players.filter { it.id in planIds.orEmpty() }) }
    }

    if (confirmNew) {
        AlertDialog(
            onDismissRequest = { confirmNew = false },
            title = { Text("Replace this lineup?") },
            text = { Text("Any swaps you made will be lost.") },
            confirmButton = { TextButton(onClick = { confirmNew = false; makeLineup() }) { Text("Replace") } },
            dismissButton = { TextButton(onClick = { confirmNew = false }) { Text("Cancel") } },
        )
    }
    if (confirmSave && plan != null) {
        AlertDialog(
            onDismissRequest = { confirmSave = false },
            title = { Text("Save this game?") },
            text = { Text("Adds this lineup to the season totals, so future lineups give more time to kids who've sat more.") },
            confirmButton = {
                TextButton(onClick = {
                    val players = state.players.filter { it.id in planIds.orEmpty() }
                    update(state.copy(season = RotationPlanner.record(plan, players, state.season), planSaved = true))
                    confirmSave = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { confirmSave = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun QuarterCard(
    index: Int,
    quarter: Quarter,
    names: Map<Long, String>,
    selected: RotationPlanner.Slot?,
    onTap: (RotationPlanner.Slot) -> Unit,
) {
    @Composable
    fun RowScope.Spot(slot: RotationPlanner.Slot, label: String) {
        val id = RotationPlanner.playerAt(quarter, slot)
        val isSelected = slot == selected
        val isBench = slot is RotationPlanner.Slot.Bench
        Surface(
            onClick = { onTap(slot) },
            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            shape = RoundedCornerShape(10.dp),
            color = when {
                isSelected -> MaterialTheme.colorScheme.primary
                isBench -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.secondaryContainer
            },
            border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    id?.let { names[it] } ?: "—",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }

    val used = quarter.positions.keys
    fun field(vararg ps: Position) = ps.filter { it in used }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Quarter ${index + 1}", style = MaterialTheme.typography.titleMedium)
            // Laid out like the field: forwards on top, goalie at the bottom.
            for (line in listOf(field(Position.LF, Position.CF, Position.RF), field(Position.LD, Position.RD), field(Position.GK))) {
                if (line.isEmpty()) continue
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                    if (line.size == 1) Spacer(Modifier.weight(1f))
                    line.forEach { Spot(RotationPlanner.Slot.Field(it), it.short) }
                    if (line.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            if (quarter.bench.isNotEmpty()) {
                HorizontalDivider()
                quarter.bench.indices.chunked(3).forEach { chunk ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        chunk.forEach { Spot(RotationPlanner.Slot.Bench(it), "SITS") }
                        repeat(3 - chunk.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayTimeCard(plan: GamePlan, players: List<Player>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Play time", style = MaterialTheme.typography.titleMedium)
            RotationPlanner.summarize(plan, players).forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(players.first { it.id == s.playerId }.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        s.positions.joinToString("  ") { it?.short?.padEnd(2) ?: "–" },
                        Modifier.padding(horizontal = 8.dp),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("${s.quartersPlayed}/${plan.quarters.size}", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Season

@Composable
private fun SeasonScreen(state: AppState, update: (AppState) -> Unit) {
    var confirmReset by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(
                "Totals from saved games. New lineups give extra time to kids who've sat more, and spread goalie turns.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Row(Modifier.padding(top = 8.dp)) {
                Text("Player", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                listOf("Games", "Played", "Sat", "Goalie").forEach {
                    Text(it, Modifier.width(56.dp), textAlign = TextAlign.End, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
            HorizontalDivider(Modifier.padding(top = 4.dp))
        }
        items(state.players, key = { it.id }) { p ->
            val t = state.season[p.id] ?: SeasonTotals()
            Row {
                Text(p.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                listOf(t.games, t.played, t.sat, t.goalie).forEach {
                    Text("$it", Modifier.width(56.dp), textAlign = TextAlign.End)
                }
            }
        }
        item {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { confirmReset = true }, enabled = state.season.isNotEmpty()) { Text("Reset season") }
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset season totals?") },
            text = { Text("This clears every player's games, quarters and goalie counts.") },
            confirmButton = { TextButton(onClick = { update(state.copy(season = emptyMap())); confirmReset = false }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}
