package com.coach.rotation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
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

private enum class Tab { TEAM, GAME, SEASON }

/** null id = creating a new team. */
private data class TeamEdit(val id: Long?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(storage: Storage, onShare: (String) -> Unit) {
    var state by remember { mutableStateOf(storage.load()) }
    fun update(new: AppState) {
        state = new
        storage.save(new)
    }
    var tab by rememberSaveable { mutableStateOf(if (state.team?.players.isNullOrEmpty()) Tab.TEAM else Tab.GAME) }
    var teamEdit by remember { mutableStateOf<TeamEdit?>(if (state.teams.isEmpty()) TeamEdit(null) else null) }
    var menuOpen by remember { mutableStateOf(false) }
    val team = state.team

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Box {
                    Row(
                        Modifier.clickable { if (state.teams.isEmpty()) teamEdit = TeamEdit(null) else menuOpen = true },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(team?.name ?: "Soccer Rotation", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            team?.let {
                                Text(
                                    "${it.formation.label} · ${it.format.label}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Icon(Icons.Filled.ArrowDropDown, "Switch team")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        state.teams.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t.name) },
                                leadingIcon = { if (t.id == team?.id) Icon(Icons.Filled.Check, null) },
                                onClick = {
                                    update(state.copy(currentTeamId = t.id))
                                    menuOpen = false
                                },
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Edit this team") },
                            leadingIcon = { Icon(Icons.Filled.Edit, null) },
                            onClick = { teamEdit = TeamEdit(team?.id); menuOpen = false },
                        )
                        DropdownMenuItem(
                            text = { Text("New team") },
                            leadingIcon = { Icon(Icons.Filled.Add, null) },
                            onClick = { teamEdit = TeamEdit(null); menuOpen = false },
                        )
                    }
                }
            })
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(tab == Tab.TEAM, { tab = Tab.TEAM }, { Icon(Icons.Filled.Person, null) }, label = { Text("Players") })
                NavigationBarItem(tab == Tab.GAME, { tab = Tab.GAME }, { Icon(Icons.Filled.PlayArrow, null) }, label = { Text("Lineup") })
                NavigationBarItem(tab == Tab.SEASON, { tab = Tab.SEASON }, { Icon(Icons.Filled.DateRange, null) }, label = { Text("Season") })
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (team == null) {
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Create a team to get started.", textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { teamEdit = TeamEdit(null) }) { Text("New team") }
                }
            } else {
                val updateTeam: ((Team) -> Team) -> Unit = { change -> update(state.updateTeam(change)) }
                when (tab) {
                    Tab.TEAM -> PlayersScreen(team, updateTeam) { name ->
                        val id = state.nextId
                        update(state.copy(nextId = id + 1).updateTeam { it.copy(players = it.players + Player(id, name)) })
                    }
                    Tab.GAME -> GameScreen(team, updateTeam, onShare)
                    Tab.SEASON -> SeasonScreen(team, updateTeam)
                }
            }
        }
    }

    teamEdit?.let { edit ->
        val existing = state.teams.firstOrNull { it.id == edit.id }
        TeamDialog(
            existing = existing,
            canCancel = state.teams.isNotEmpty(),
            onDismiss = { teamEdit = null },
            onSave = { name, formation, format ->
                if (existing == null) {
                    val newTeam = Team(id = state.nextId, name = name, formation = formation, format = format)
                    update(state.copy(teams = state.teams + newTeam, currentTeamId = newTeam.id, nextId = state.nextId + 1))
                    tab = Tab.TEAM
                } else {
                    update(state.copy(teams = state.teams.map {
                        if (it.id != existing.id) it
                        else {
                            val lineupStillValid = it.formation == formation && it.format == format
                            it.copy(
                                name = name,
                                formation = formation,
                                format = format,
                                plan = if (lineupStillValid) it.plan else null,
                                planSaved = if (lineupStillValid) it.planSaved else false,
                            )
                        }
                    }))
                }
                teamEdit = null
            },
            onDelete = existing?.let {
                {
                    val remaining = state.teams.filter { t -> t.id != it.id }
                    update(state.copy(teams = remaining, currentTeamId = remaining.firstOrNull()?.id))
                    teamEdit = if (remaining.isEmpty()) TeamEdit(null) else null
                }
            },
        )
    }
}

// ---------------------------------------------------------------- Team settings

@Composable
private fun TeamDialog(
    existing: Team?,
    canCancel: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Formation, GameFormat) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var formation by remember { mutableStateOf(existing?.formation ?: Formation.SIX_2_3) }
    var format by remember { mutableStateOf(existing?.format ?: GameFormat.QUARTERS) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (canCancel) onDismiss() },
        title = { Text(if (existing == null) "New team" else "Edit team") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Team name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text("Players on the field", style = MaterialTheme.typography.titleSmall)
                Formation.entries.forEach { f ->
                    RadioRow(f.label, selected = formation == f) { formation = f }
                }
                Spacer(Modifier.height(8.dp))
                Text("Game", style = MaterialTheme.typography.titleSmall)
                GameFormat.entries.forEach { g ->
                    RadioRow(g.label, selected = format == g) { format = g }
                }
                if (existing != null && (formation != existing.formation || format != existing.format) && existing.plan != null) {
                    Text(
                        "Changing this clears the current lineup.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (onDelete != null) {
                    Spacer(Modifier.height(12.dp))
                    TextButton(
                        onClick = { confirmDelete = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Delete team") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), formation, format) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { if (canCancel) TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (confirmDelete && existing != null && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${existing.name}?") },
            text = { Text("Its players, lineup and season totals will be removed.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

// ---------------------------------------------------------------- Players

@Composable
private fun PlayersScreen(team: Team, updateTeam: ((Team) -> Team) -> Unit, onAdd: (String) -> Unit) {
    var newName by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Player?>(null) }

    fun add() {
        val name = newName.trim()
        if (name.isEmpty()) return
        onAdd(name)
        newName = ""
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("${team.present.size} of ${team.players.size} here today", style = MaterialTheme.typography.titleMedium)
            Text(
                "Uncheck kids who are absent. Tap a name to rename, set preferred positions, or turn off goalie.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(team.players, key = { it.id }) { p ->
            val here = p.id !in team.absent
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = here, onCheckedChange = { checked ->
                        updateTeam { it.copy(absent = if (checked) it.absent - p.id else it.absent + p.id) }
                    })
                    Column(Modifier.weight(1f).clickable { editing = p }.padding(vertical = 10.dp)) {
                        Text(
                            p.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (here) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                        )
                        val notes = buildList {
                            val likes = p.preferred.filter { it in team.formation.fillOrder }.sortedBy { it.ordinal }
                            if (likes.isNotEmpty()) add("Likes " + likes.joinToString { it.short })
                            if (!p.canPlayGoalie) add("No goalie")
                        }
                        if (notes.isNotEmpty()) {
                            Text(notes.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = { editing = p }) { Icon(Icons.Filled.Edit, "Edit ${p.name}") }
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
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(onClick = ::add) { Icon(Icons.Filled.Add, "Add") }
            }
        }
    }

    editing?.let { p ->
        PlayerDialog(
            player = p,
            formation = team.formation,
            onDismiss = { editing = null },
            onSave = { updated ->
                updateTeam { t -> t.copy(players = t.players.map { if (it.id == p.id) updated else it }) }
                editing = null
            },
            onDelete = {
                updateTeam { t ->
                    t.copy(players = t.players.filter { it.id != p.id }, absent = t.absent - p.id, season = t.season - p.id)
                }
                editing = null
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerDialog(player: Player, formation: Formation, onDismiss: () -> Unit, onSave: (Player) -> Unit, onDelete: () -> Unit) {
    var name by remember(player.id) { mutableStateOf(player.name) }
    var goalie by remember(player.id) { mutableStateOf(player.canPlayGoalie) }
    var preferred by remember(player.id) { mutableStateOf(player.preferred) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit player") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Can play goalie", Modifier.weight(1f))
                    Switch(checked = goalie, onCheckedChange = {
                        goalie = it
                        if (!it) preferred = preferred - Position.GK
                    })
                }
                Spacer(Modifier.height(8.dp))
                Text("Preferred positions", style = MaterialTheme.typography.titleSmall)
                Text(
                    "The lineup puts them here whenever they're on the field. Leave empty to rotate everywhere.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    formation.positions.forEach { pos ->
                        val on = pos in preferred
                        FilterChip(
                            selected = on,
                            enabled = pos != Position.GK || goalie,
                            onClick = { preferred = if (on) preferred - pos else preferred + pos },
                            label = { Text(pos.label) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                TextButton(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Remove from team") }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(player.copy(name = name.trim(), canPlayGoalie = goalie, preferred = preferred)) },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove ${player.name}?") },
            text = { Text("They'll be taken off the team and out of season totals.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

// ---------------------------------------------------------------- Lineup

@Composable
private fun GameScreen(team: Team, updateTeam: ((Team) -> Team) -> Unit, onShare: (String) -> Unit) {
    val plan = team.plan
    val playersById = team.players.associateBy { it.id }
    var selected by remember(team.id) { mutableStateOf<Pair<Int, RotationPlanner.Slot>?>(null) }
    var confirmNew by remember { mutableStateOf(false) }
    var confirmSave by remember { mutableStateOf(false) }

    fun makeLineup() {
        selected = null
        updateTeam {
            it.copy(
                plan = RotationPlanner.generate(it.present, it.formation, it.format.periods, it.season),
                planSaved = false,
            )
        }
    }

    val attendanceChanged = plan != null && team.planPlayers.map { it.id }.toSet() != team.present.map { it.id }.toSet()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { if (plan == null || team.planSaved) makeLineup() else confirmNew = true },
                        enabled = team.present.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Refresh, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (plan == null) "Make lineup" else "New lineup", maxLines = 1, softWrap = false)
                    }
                    if (plan != null) {
                        OutlinedButton(onClick = {
                            val title = "${team.name} – " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date())
                            onShare(RotationPlanner.asText(plan, team.planPlayers, team.format, title))
                        }) {
                            Icon(Icons.Filled.Share, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Share", maxLines = 1, softWrap = false)
                        }
                    }
                }
                if (plan != null) {
                    OutlinedButton(
                        onClick = { confirmSave = true },
                        enabled = !team.planSaved && !attendanceChanged,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Check, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (team.planSaved) "Game saved to season" else "Save game", maxLines = 1, softWrap = false)
                    }
                }
            }
        }

        if (plan == null) {
            item {
                Text(
                    if (team.players.isEmpty()) "Add your players on the Players tab first."
                    else "${team.present.size} players here. Tap Make lineup to build the game.",
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
                "Tap two players in the same box to swap them. ★ = preferred spot.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        plan.periods.forEachIndexed { qi, period ->
            item(key = "q$qi") {
                PeriodCard(team.format.periodName(qi), period, team.formation, playersById, selected?.takeIf { it.first == qi }?.second) { slot ->
                    val current = selected
                    selected = when {
                        current == null || current.first != qi -> qi to slot
                        current.second == slot -> null
                        else -> {
                            updateTeam { it.copy(plan = RotationPlanner.swap(plan, qi, current.second, slot)) }
                            null
                        }
                    }
                }
            }
        }

        item { PlayTimeCard(plan, team.planPlayers, team.format) }
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
                    updateTeam { it.copy(season = RotationPlanner.record(plan, it.planPlayers, it.season), planSaved = true) }
                    confirmSave = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { confirmSave = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PeriodCard(
    title: String,
    period: Period,
    formation: Formation,
    players: Map<Long, Player>,
    selected: RotationPlanner.Slot?,
    onTap: (RotationPlanner.Slot) -> Unit,
) {
    @Composable
    fun RowScope.Spot(slot: RotationPlanner.Slot, label: String) {
        val player = RotationPlanner.playerAt(period, slot)?.let { players[it] }
        val isSelected = slot == selected
        val isBench = slot is RotationPlanner.Slot.Bench
        val liked = slot is RotationPlanner.Slot.Field && player != null && slot.position in player.preferred
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
                Text(
                    if (liked) "$label ★" else label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    player?.name ?: "—",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }

    // Laid out like the field: forwards on top, goalie at the bottom.
    val rows = listOf(Line.FORWARD, Line.MIDFIELD, Line.DEFENSE, Line.GOAL).map { line ->
        formation.positions.filter { it.line == line && it in period.positions }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            for (row in rows) {
                if (row.isEmpty()) continue
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val pad = (3 - row.size).coerceAtLeast(0) / 2f
                    if (pad > 0) Spacer(Modifier.weight(pad))
                    row.forEach { Spot(RotationPlanner.Slot.Field(it), it.short) }
                    if (pad > 0) Spacer(Modifier.weight(pad))
                }
            }
            if (period.bench.isNotEmpty()) {
                HorizontalDivider()
                period.bench.indices.chunked(3).forEach { chunk ->
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
private fun PlayTimeCard(plan: GamePlan, players: List<Player>, format: GameFormat) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Playing time (${format.unit})", style = MaterialTheme.typography.titleMedium)
            RotationPlanner.summarize(plan, players).forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(players.first { it.id == s.playerId }.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        s.positions.joinToString(" ") { it?.short?.padEnd(2) ?: "– " },
                        Modifier.padding(horizontal = 8.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("${s.periodsPlayed}/${plan.periods.size}", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Season

@Composable
private fun SeasonScreen(team: Team, updateTeam: ((Team) -> Team) -> Unit) {
    var confirmReset by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(
                "Totals from saved games for ${team.name}. New lineups give extra time to kids who've sat more, and spread goalie turns.",
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
        items(team.players, key = { it.id }) { p ->
            val t = team.season[p.id] ?: SeasonTotals()
            Row {
                Text(p.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                listOf(t.games, t.played, t.sat, t.goalie).forEach {
                    Text("$it", Modifier.width(56.dp), textAlign = TextAlign.End)
                }
            }
        }
        item {
            Text(
                "Played and Sat count ${team.format.unit}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { confirmReset = true }, enabled = team.season.isNotEmpty()) { Text("Reset season") }
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset season totals?") },
            text = { Text("This clears every player's games, playing time and goalie counts for ${team.name}.") },
            confirmButton = {
                TextButton(onClick = { updateTeam { it.copy(season = emptyMap()) }; confirmReset = false }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}
