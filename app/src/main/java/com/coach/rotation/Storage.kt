package com.coach.rotation

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class AppState(
    val players: List<Player> = emptyList(),
    val absent: Set<Long> = emptySet(),
    val plan: GamePlan? = null,
    val planSaved: Boolean = false,
    val season: Map<Long, SeasonTotals> = emptyMap(),
    val nextId: Long = 1,
) {
    val present: List<Player> get() = players.filter { it.id !in absent }
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
        put("nextId", s.nextId)
        put("players", JSONArray(s.players.map {
            JSONObject().put("id", it.id).put("name", it.name).put("goalie", it.canPlayGoalie)
        }))
        put("absent", JSONArray(s.absent.toList()))
        put("planSaved", s.planSaved)
        s.plan?.let { plan ->
            put("plan", JSONArray(plan.quarters.map { q ->
                JSONObject()
                    .put("positions", JSONObject().apply { q.positions.forEach { (pos, id) -> put(pos.name, id) } })
                    .put("bench", JSONArray(q.bench))
            }))
        }
        put("season", JSONObject().apply {
            s.season.forEach { (id, t) ->
                put(id.toString(), JSONObject()
                    .put("played", t.played).put("sat", t.sat).put("goalie", t.goalie).put("games", t.games))
            }
        })
    }

    private fun fromJson(o: JSONObject): AppState {
        val players = o.getJSONArray("players").objects().map {
            Player(it.getLong("id"), it.getString("name"), it.optBoolean("goalie", true))
        }
        val absent = o.optJSONArray("absent")?.longs()?.toSet() ?: emptySet()
        val plan = o.optJSONArray("plan")?.let { arr ->
            GamePlan(arr.objects().map { q ->
                val pos = q.getJSONObject("positions")
                Quarter(
                    positions = pos.keys().asSequence().associate { Position.valueOf(it) to pos.getLong(it) },
                    bench = q.getJSONArray("bench").longs(),
                )
            })
        }
        val seasonObj = o.optJSONObject("season") ?: JSONObject()
        val season = seasonObj.keys().asSequence().associate { key ->
            val t = seasonObj.getJSONObject(key)
            key.toLong() to SeasonTotals(t.optInt("played"), t.optInt("sat"), t.optInt("goalie"), t.optInt("games"))
        }
        return AppState(
            players = players,
            absent = absent,
            plan = plan,
            planSaved = o.optBoolean("planSaved"),
            season = season,
            nextId = o.optLong("nextId", (players.maxOfOrNull { it.id } ?: 0) + 1),
        )
    }

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    private fun JSONArray.longs() = (0 until length()).map { getLong(it) }

    private companion object {
        const val KEY = "state"
    }
}
