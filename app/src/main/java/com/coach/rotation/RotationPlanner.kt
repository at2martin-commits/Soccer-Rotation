package com.coach.rotation

import kotlin.random.Random

enum class Line { GOAL, DEFENSE, FORWARD }

enum class Position(val label: String, val short: String, val line: Line) {
    GK("Goalie", "G", Line.GOAL),
    LD("Left Defense", "LD", Line.DEFENSE),
    RD("Right Defense", "RD", Line.DEFENSE),
    LF("Left Forward", "LF", Line.FORWARD),
    CF("Center Forward", "CF", Line.FORWARD),
    RF("Right Forward", "RF", Line.FORWARD);

    companion object {
        /** Order positions are filled when fewer than six players show up. */
        val FILL_ORDER = listOf(GK, LD, RD, CF, LF, RF)
    }
}

data class Player(val id: Long, val name: String, val canPlayGoalie: Boolean = true)

data class Quarter(val positions: Map<Position, Long>, val bench: List<Long>)

data class GamePlan(val quarters: List<Quarter>)

/** Totals from games already played, so sitting and goalie turns even out across the season. */
data class SeasonTotals(val played: Int = 0, val sat: Int = 0, val goalie: Int = 0, val games: Int = 0)

data class PlayerSummary(
    val playerId: Long,
    val quartersPlayed: Int,
    val quartersSat: Int,
    val positions: List<Position?>, // one entry per quarter, null = bench
)

object RotationPlanner {
    const val QUARTERS = 4
    private const val ATTEMPTS = 400

    /**
     * Builds a lineup for [quarters] quarters. Bench time is always spread as evenly as
     * possible; among many random candidates, the one with the best goalie spread and
     * position variety is kept. Ties go against players who have sat least this season.
     */
    fun generate(
        players: List<Player>,
        season: Map<Long, SeasonTotals> = emptyMap(),
        quarters: Int = QUARTERS,
        random: Random = Random.Default,
    ): GamePlan {
        if (players.isEmpty()) return GamePlan(List(quarters) { Quarter(emptyMap(), emptyList()) })
        var best: GamePlan? = null
        var bestScore = Int.MAX_VALUE
        repeat(ATTEMPTS) {
            val plan = candidate(players, season, quarters, random)
            val s = score(plan, players, season)
            if (s < bestScore) {
                best = plan
                bestScore = s
            }
        }
        return best!!
    }

    private fun candidate(players: List<Player>, season: Map<Long, SeasonTotals>, quarters: Int, random: Random): GamePlan {
        val onField = Position.FILL_ORDER.take(minOf(6, players.size))
        val benchSize = players.size - onField.size
        val sits = players.associate { it.id to 0 }.toMutableMap()
        val goalieTurns = players.associate { it.id to 0 }.toMutableMap()
        val posCount = mutableMapOf<Pair<Long, Position>, Int>()
        var lastBench = emptySet<Long>()
        var lastGoalie: Long? = null
        val result = mutableListOf<Quarter>()

        repeat(quarters) {
            // Bench: fewest sits this game, then not twice in a row, then fewest sits this season.
            val bench = players
                .shuffled(random)
                .sortedWith(compareBy(
                    { sits.getValue(it.id) },
                    { if (it.id in lastBench) 1 else 0 },
                    { season[it.id]?.sat ?: 0 },
                ))
                .take(benchSize)
                .map { it.id }
            bench.forEach { sits[it] = sits.getValue(it) + 1 }

            val playing = players.filter { it.id !in bench }.shuffled(random).toMutableList()
            val assignment = mutableMapOf<Position, Long>()

            // Goalie: prefer eligible players with the fewest goalie turns, not the previous goalie.
            val goalie = playing
                .sortedWith(compareBy(
                    { if (it.canPlayGoalie) 0 else 1 },
                    { goalieTurns.getValue(it.id) },
                    { if (it.id == lastGoalie) 1 else 0 },
                    { season[it.id]?.goalie ?: 0 },
                ))
                .first()
            assignment[Position.GK] = goalie.id
            goalieTurns[goalie.id] = goalieTurns.getValue(goalie.id) + 1
            playing.remove(goalie)
            lastGoalie = goalie.id

            // Field spots: each spot goes to whoever has played it least.
            for (pos in onField.filter { it != Position.GK }.shuffled(random)) {
                val pick = playing.minBy { p ->
                    posCount.getOrDefault(p.id to pos, 0) * 10 +
                        lineCount(posCount, p.id, pos.line) * 3
                }
                assignment[pos] = pick.id
                posCount[pick.id to pos] = posCount.getOrDefault(pick.id to pos, 0) + 1
                playing.remove(pick)
            }

            lastBench = bench.toSet()
            result += Quarter(assignment, bench)
        }
        return GamePlan(result)
    }

    private fun lineCount(posCount: Map<Pair<Long, Position>, Int>, id: Long, line: Line) =
        Position.entries.filter { it.line == line }.sumOf { posCount.getOrDefault(id to it, 0) }

    /** Lower is better. */
    fun score(plan: GamePlan, players: List<Player>, season: Map<Long, SeasonTotals> = emptyMap()): Int {
        val summaries = summarize(plan, players)
        var score = 0
        val sits = summaries.map { it.quartersSat }
        if (sits.isNotEmpty()) score += (sits.max() - sits.min()) * 1000

        val eligibleGoalies = players.count { it.canPlayGoalie }
        val fewestSeasonGoalie = players.filter { it.canPlayGoalie }.minOfOrNull { season[it.id]?.goalie ?: 0 } ?: 0
        for (s in summaries) {
            val player = players.first { it.id == s.playerId }
            val gk = s.positions.count { it == Position.GK }
            if (gk > 0 && !player.canPlayGoalie && eligibleGoalies > 0) score += 500 * gk
            if (gk > 1) score += (gk - 1) * (gk - 1) * 60
            score += gk * ((season[player.id]?.goalie ?: 0) - fewestSeasonGoalie) * 30

            // Sitting back-to-back quarters.
            s.positions.zipWithNext().forEach { (a, b) -> if (a == null && b == null) score += 40 }
            // Playing the exact same spot again.
            s.positions.filterNotNull().groupingBy { it }.eachCount().values
                .forEach { if (it > 1) score += (it - 1) * 8 }
            // Only ever playing one line (forward or defense).
            val lines = s.positions.filterNotNull().map { it.line }.filter { it != Line.GOAL }
            if (lines.size >= 2 && lines.toSet().size == 1) score += 5
        }
        return score
    }

    fun summarize(plan: GamePlan, players: List<Player>): List<PlayerSummary> = players.map { p ->
        val positions = plan.quarters.map { q -> q.positions.entries.firstOrNull { it.value == p.id }?.key }
        PlayerSummary(
            playerId = p.id,
            quartersPlayed = positions.count { it != null },
            quartersSat = positions.count { it == null },
            positions = positions,
        )
    }

    /** Adds a finished game to the season totals. */
    fun record(plan: GamePlan, players: List<Player>, season: Map<Long, SeasonTotals>): Map<Long, SeasonTotals> {
        val updated = season.toMutableMap()
        for (s in summarize(plan, players)) {
            val t = updated[s.playerId] ?: SeasonTotals()
            updated[s.playerId] = t.copy(
                played = t.played + s.quartersPlayed,
                sat = t.sat + s.quartersSat,
                goalie = t.goalie + s.positions.count { it == Position.GK },
                games = t.games + 1,
            )
        }
        return updated
    }

    /** Where a player sits within a quarter: a field position, or the bench. */
    sealed interface Slot {
        data class Field(val position: Position) : Slot
        data class Bench(val index: Int) : Slot
    }

    fun playerAt(quarter: Quarter, slot: Slot): Long? = when (slot) {
        is Slot.Field -> quarter.positions[slot.position]
        is Slot.Bench -> quarter.bench.getOrNull(slot.index)
    }

    /** Swaps whoever is in [a] and [b] within one quarter. */
    fun swap(plan: GamePlan, quarterIndex: Int, a: Slot, b: Slot): GamePlan {
        val q = plan.quarters[quarterIndex]
        val pa = playerAt(q, a)
        val pb = playerAt(q, b)
        val positions = q.positions.toMutableMap()
        val bench = q.bench.toMutableList()
        fun put(slot: Slot, id: Long?) {
            when (slot) {
                is Slot.Field -> if (id == null) positions.remove(slot.position) else positions[slot.position] = id
                is Slot.Bench -> if (id != null) bench[slot.index] = id
            }
        }
        put(a, pb)
        put(b, pa)
        val updated = plan.quarters.toMutableList()
        updated[quarterIndex] = Quarter(positions, bench)
        return GamePlan(updated)
    }

    fun asText(plan: GamePlan, players: List<Player>, title: String): String {
        val names = players.associate { it.id to it.name }
        return buildString {
            appendLine(title)
            plan.quarters.forEachIndexed { i, q ->
                appendLine()
                appendLine("Quarter ${i + 1}")
                for (pos in Position.entries) {
                    q.positions[pos]?.let { appendLine("  ${pos.short}: ${names[it]}") }
                }
                if (q.bench.isNotEmpty()) appendLine("  Sits: ${q.bench.joinToString { names[it].orEmpty() }}")
            }
            appendLine()
            appendLine("Quarters played")
            summarize(plan, players).forEach {
                appendLine("  ${names[it.playerId]}: ${it.quartersPlayed}")
            }
        }.trimEnd()
    }
}
