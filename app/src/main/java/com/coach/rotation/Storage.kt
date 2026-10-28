package com.coach.rotation

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Team(
    val id: Long,
    val name: String,
    val formation: Formation = Formation.SIX_2_3,
    val format: GameFormat = GameFormat.QUARTERS,
    val players: List<Player> = emptyList(),
    val absent: Set<Long> = emptySet(),
    val plan: GamePlan? = null,
    val planSaved: Boolean = false,
    val season: Map<Long, SeasonTotals> = emptyMap(),
) {
    val present: List<Player> get() = players.filter { it.id !in absent }

    /** The players in the current lineup, which can differ from [present] if attendance changed. */
    val planPlayers: List<Player>
        get() {
            val ids = plan?.periods?.firstOrNull()?.let { it.positions.values + it.bench }?.toSet() ?: return emptyList()
            return players.filter { it.id in ids }
        }
}

data class AppState(
    val teams: List<Team> = emptyList(),
    val currentTeamId: Long? = null,
    val nextId: Long = 1,
) {
    val team: Team? get() = teams.firstOrNull { it.id == currentTeamId } ?: teams.firstOrNull()

    fun updateTeam(change: (Team) -> Team): AppState {
        val current = team ?: return this
        return copy(teams = teams.map { if (it.id == current.id) change(it) else it })
    }
}

/** Keeps everything in one JSON blob in SharedPreferences; the data is tiny. */
class Storage(context: Context) {
    private val prefs = context.getSharedPreferences("rotation", Context.MODE_PRIVATE)

    fun load(): AppState {
        val raw = prefs.getString(KEY, null) ?: return AppState()
        return try {
            fromJson(JSONObject(raw))
        } catch (e: Exception) {
            AppState()
        }
    }

    fun save(state: AppState) {
        prefs.edit().putString(KEY, toJson(state).toString()).apply()
    }

    private fun toJson(s: AppState) = JSONObject().apply {
        put("version", 2)
        put("nextId", s.nextId)
        s.currentTeamId?.let { put("currentTeamId", it) }
        put("teams", JSONArray(s.teams.map(::teamToJson)))
    }

    private fun teamToJson(t: Team) = JSONObject().apply {
        put("id", t.id)
        put("name", t.name)
        put("formation", t.formation.name)
        put("format", t.format.name)
        put("players", JSONArray(t.players.map {
            JSONObject()
                .put("id", it.id)
                .put("name", it.name)
                .put("goalie", it.canPlayGoalie)
                .put("preferred", JSONArray(it.preferred.map { p -> p.name }))
        }))
        put("absent", JSONArray(t.absent.toList()))
        put("planSaved", t.planSaved)
        t.plan?.let { plan ->
            put("plan", JSONArray(plan.periods.map { q ->
                JSONObject()
                    .put("positions", JSONObject().apply { q.positions.forEach { (pos, id) -> put(pos.name, id) } })
                    .put("bench", JSONArray(q.bench))
            }))
        }
        put("season", JSONObject().apply {
            t.season.forEach { (id, s) ->
                put(id.toString(), JSONObject()
                    .put("played", s.played).put("sat", s.sat).put("goalie", s.goalie).put("games", s.games))
            }
        })
    }

    private fun fromJson(o: JSONObject): AppState {
        // Version 1 stored a single team at the top level.
        if (!o.has("teams")) {
            val team = teamFromJson(o, id = 0, name = "My Team")
            val nextId = maxOf(o.optLong("nextId", 1), (team.players.maxOfOrNull { it.id } ?: 0) + 1)
            return AppState(teams = listOf(team), currentTeamId = 0, nextId = nextId)
        }
        val teams = o.getJSONArray("teams").objects().map { teamFromJson(it, it.getLong("id"), it.getString("name")) }
        return AppState(
            teams = teams,
            currentTeamId = if (o.has("currentTeamId")) o.getLong("currentTeamId") else teams.firstOrNull()?.id,
            nextId = o.optLong("nextId", 1),
        )
    }

    private fun teamFromJson(o: JSONObject, id: Long, name: String): Team {
        val players = o.getJSONArray("players").objects().map {
            Player(
                id = it.getLong("id"),
                name = it.getString("name"),
                canPlayGoalie = it.optBoolean("goalie", true),
                preferred = it.optJSONArray("preferred")?.strings()?.mapNotNull(::position)?.toSet() ?: emptySet(),
            )
        }
        val plan = o.optJSONArray("plan")?.let { arr ->
            GamePlan(arr.objects().map { q ->
                val pos = q.getJSONObject("positions")
                Period(
                    positions = pos.keys().asSequence().mapNotNull { k -> position(k)?.let { it to pos.getLong(k) } }.toMap(),
                    bench = q.getJSONArray("bench").longs(),
                )
            })
        }
        val seasonObj = o.optJSONObject("season") ?: JSONObject()
        val season = seasonObj.keys().asSequence().associate { key ->
            val t = seasonObj.getJSONObject(key)
            key.toLong() to SeasonTotals(t.optInt("played"), t.optInt("sat"), t.optInt("goalie"), t.optInt("games"))
        }
        return Team(
            id = id,
            name = name,
            formation = Formation.entries.firstOrNull { it.name == o.optString("formation") } ?: Formation.SIX_2_3,
            format = GameFormat.entries.firstOrNull { it.name == o.optString("format") } ?: GameFormat.QUARTERS,
            players = players,
            absent = o.optJSONArray("absent")?.longs()?.toSet() ?: emptySet(),
            plan = plan,
            planSaved = o.optBoolean("planSaved"),
            season = season,
        )
    }

    private fun position(name: String) = Position.entries.firstOrNull { it.name == name }
    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    private fun JSONArray.longs() = (0 until length()).map { getLong(it) }
    private fun JSONArray.strings() = (0 until length()).map { getString(it) }

    private companion object {
        const val KEY = "state"
    }
}
