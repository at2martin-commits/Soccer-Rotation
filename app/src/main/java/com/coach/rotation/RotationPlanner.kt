package com.coach.rotation

import kotlin.random.Random

enum class Line { GOAL, DEFENSE, MIDFIELD, FORWARD }

enum class Position(val label: String, val short: String, val line: Line) {
    GK("Goalie", "G", Line.GOAL),
    LD("Left Defense", "LD", Line.DEFENSE),
    CD("Center Defense", "CD", Line.DEFENSE),
    RD("Right Defense", "RD", Line.DEFENSE),
    LM("Left Mid", "LM", Line.MIDFIELD),
    CM("Center Mid", "CM", Line.MIDFIELD),
    RM("Right Mid", "RM", Line.MIDFIELD),
    LF("Left Forward", "LF", Line.FORWARD),
    CF("Center Forward", "CF", Line.FORWARD),
    RF("Right Forward", "RF", Line.FORWARD),
}

/**
 * Positions on the field. [fillOrder] is the order spots are filled when not enough
 * players show up, so the last entries are the first left empty.
 */
enum class Formation(val label: String, val fillOrder: List<Position>) {
    SIX_2_3("6v6 (2-3)", listOf(Position.GK, Position.LD, Position.RD, Position.CF, Position.LF, Position.RF)),
    SEVEN_2_3_1("7v7 (2-3-1)", listOf(Position.GK, Position.LD, Position.RD, Position.CM, Position.CF, Position.LM, Position.RM)),
    NINE_3_3_2("9v9 (3-3-2)", listOf(Position.GK, Position.CD, Position.LD, Position.RD, Position.CM, Position.LF, Position.RF, Position.LM, Position.RM)),
    NINE_3_2_3("9v9 (3-2-3)", listOf(Position.GK, Position.CD, Position.LD, Position.RD, Position.LM, Position.RM, Position.CF, Position.LF, Position.RF)),
    NINE_2_3_3("9v9 (2-3-3)", listOf(Position.GK, Position.LD, Position.RD, Position.CM, Position.CF, Position.LM, Position.RM, Position.LF, Position.RF));

    val size get() = fillOrder.size
    val positions get() = Position.entries.filter { it in fillOrder }
}

/** How a game is split into rotation periods. */
enum class GameFormat(val label: String, val periods: Int) {
    QUARTERS("4 quarters", 4),
    HALVES("2 halves", 2),
    HALVES_SPLIT("2 halves, swap mid-half", 4);

    fun periodName(index: Int): String = when (this) {
        QUARTERS -> "Quarter ${index + 1}"
        HALVES -> if (index == 0) "1st half" else "2nd half"
        HALVES_SPLIT -> (if (index < 2) "1st half" else "2nd half") + if (index % 2 == 0) " – start" else " – mid"
    }

    /** Plural period name, for totals like "3/4 quarters". */
    val unit get() = when (this) {
        QUARTERS -> "quarters"
        HALVES -> "halves"
        HALVES_SPLIT -> "segments"
    }
}

data class Player(
    val id: Long,
    val name: String,
    val canPlayGoalie: Boolean = true,
    val preferred: Set<Position> = emptySet(),
)

data class Period(val positions: Map<Position, Long>, val bench: List<Long>)

data class GamePlan(val periods: List<Period>)

/** Totals from games already played, so sitting and goalie turns even out across the season. */
data class SeasonTotals(val played: Int = 0, val sat: Int = 0, val goalie: Int = 0, val games: Int = 0)

data class PlayerSummary(
    val playerId: Long,
    val periodsPlayed: Int,
    val periodsSat: Int,
    val positions: List<Position?>, // one entry per period, null = bench
)

object RotationPlanner {
    private const val ATTEMPTS = 400

    /**
     * Builds a lineup. Bench time is always spread as evenly as possible; among many random
     * candidates, the one with the best goalie spread, position variety and preferred
     * positions is kept. Ties go against players who have sat least this season.
     */
    fun generate(
        players: List<Player>,
        formation: Formation = Formation.SIX_2_3,
        periods: Int = 4,
        season: Map<Long, SeasonTotals> = emptyMap(),
        random: Random = Random.Default,
    ): GamePlan {
        if (players.isEmpty()) return GamePlan(List(periods) { Period(emptyMap(), emptyList()) })
        var best: GamePlan? = null
        var bestScore = Int.MAX_VALUE
        repeat(ATTEMPTS) {
            val plan = candidate(players, formation, periods, season, random)
            val s = score(plan, players, season)
            if (s < bestScore) {
                best = plan
                bestScore = s
            }
        }
        return best!!
    }

    private fun candidate(
        players: List<Player>,
        formation: Formation,
        periods: Int,
        season: Map<Long, SeasonTotals>,
        random: Random,
    ): GamePlan {
        val onField = formation.fillOrder.take(minOf(formation.size, players.size))
        val benchSize = players.size - onField.size
        val sits = players.associate { it.id to 0 }.toMutableMap()
        val goalieTurns = players.associate { it.id to 0 }.toMutableMap()
        val posCount = mutableMapOf<Pair<Long, Position>, Int>()
        var lastBench = emptySet<Long>()
        var lastGoalie: Long? = null
        val result = mutableListOf<Period>()

        repeat(periods) {
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

            // Goalie: eligible players with the fewest goalie turns, kids who like goal first,
            // not the previous goalie.
            val goalie = playing
                .sortedWith(compareBy(
                    { if (it.canPlayGoalie) 0 else 1 },
                    { goalieTurns.getValue(it.id) * 2 - if (Position.GK in it.preferred) 1 else 0 },
                    { if (it.id == lastGoalie) 1 else 0 },
                    { season[it.id]?.goalie ?: 0 },
                ))
                .first()
            assignment[Position.GK] = goalie.id
            goalieTurns[goalie.id] = goalieTurns.getValue(goalie.id) + 1
            playing.remove(goalie)
            lastGoalie = goalie.id

            // Field spots: each spot goes to whoever likes it, or has played it least.
            for (pos in onField.filter { it != Position.GK }.shuffled(random)) {
                val pick = playing.minBy { p ->
                    posCount.getOrDefault(p.id to pos, 0) * 10 +
                        lineCount(posCount, p.id, pos.line) * 3 -
                        if (pos in p.preferred) 25 else 0
                }
                assignment[pos] = pick.id
                posCount[pick.id to pos] = posCount.getOrDefault(pick.id to pos, 0) + 1
                playing.remove(pick)
            }

            lastBench = bench.toSet()
            result += Period(assignment, bench)
        }
        return GamePlan(result)
    }

    private fun lineCount(posCount: Map<Pair<Long, Position>, Int>, id: Long, line: Line) =
        Position.entries.filter { it.line == line }.sumOf { posCount.getOrDefault(id to it, 0) }

    /** Lower is better. */
    fun score(plan: GamePlan, players: List<Player>, season: Map<Long, SeasonTotals> = emptyMap()): Int {
        val summaries = summarize(plan, players)
        var score = 0
        val sits = summaries.map { it.periodsSat }
        if (sits.isNotEmpty()) score += (sits.max() - sits.min()) * 1000

        val eligibleGoalies = players.count { it.canPlayGoalie }
        val fewestSeasonGoalie = players.filter { it.canPlayGoalie }.minOfOrNull { season[it.id]?.goalie ?: 0 } ?: 0
        for (s in summaries) {
            val player = players.first { it.id == s.playerId }
            val likesGoal = Position.GK in player.preferred
            val gk = s.positions.count { it == Position.GK }
            if (gk > 0 && !player.canPlayGoalie && eligibleGoalies > 0) score += 500 * gk
            if (gk > 1 && !likesGoal) score += (gk - 1) * (gk - 1) * 60
            if (!likesGoal) score += gk * ((season[player.id]?.goalie ?: 0) - fewestSeasonGoalie) * 30

            // Sitting back-to-back periods.
            s.positions.zipWithNext().forEach { (a, b) -> if (a == null && b == null) score += 40 }
            // Time in a preferred spot is a plus.
            score -= s.positions.count { it != null && it in player.preferred } * 15
            // Playing the exact same (non-preferred) spot again.
            s.positions.filterNotNull().filter { it !in player.preferred }.groupingBy { it }.eachCount().values
                .forEach { if (it > 1) score += (it - 1) * 8 }
            // Only ever playing one line, for kids with no preference.
            val lines = s.positions.filterNotNull().map { it.line }.filter { it != Line.GOAL }
            if (player.preferred.isEmpty() && lines.size >= 2 && lines.toSet().size == 1) score += 5
        }
        return score
    }

    fun summarize(plan: GamePlan, players: List<Player>): List<PlayerSummary> = players.map { p ->
        val positions = plan.periods.map { q -> q.positions.entries.firstOrNull { it.value == p.id }?.key }
        PlayerSummary(
            playerId = p.id,
            periodsPlayed = positions.count { it != null },
            periodsSat = positions.count { it == null },
            positions = positions,
        )
    }

    /** Adds a finished game to the season totals. */
    fun record(plan: GamePlan, players: List<Player>, season: Map<Long, SeasonTotals>): Map<Long, SeasonTotals> {
        val updated = season.toMutableMap()
        for (s in summarize(plan, players)) {
            val t = updated[s.playerId] ?: SeasonTotals()
            updated[s.playerId] = t.copy(
                played = t.played + s.periodsPlayed,
                sat = t.sat + s.periodsSat,
                goalie = t.goalie + s.positions.count { it == Position.GK },
                games = t.games + 1,
            )
        }
        return updated
    }

    /** Where a player sits within a period: a field position, or the bench. */
    sealed interface Slot {
        data class Field(val position: Position) : Slot
        data class Bench(val index: Int) : Slot
    }

    fun playerAt(period: Period, slot: Slot): Long? = when (slot) {
        is Slot.Field -> period.positions[slot.position]
        is Slot.Bench -> period.bench.getOrNull(slot.index)
    }

    /** Swaps whoever is in [a] and [b] within one period. */
    fun swap(plan: GamePlan, periodIndex: Int, a: Slot, b: Slot): GamePlan {
        val q = plan.periods[periodIndex]
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
        val updated = plan.periods.toMutableList()
        updated[periodIndex] = Period(positions, bench)
        return GamePlan(updated)
    }

    fun asText(plan: GamePlan, players: List<Player>, format: GameFormat, title: String): String {
        val names = players.associate { it.id to it.name }
        return buildString {
            appendLine(title)
            plan.periods.forEachIndexed { i, q ->
                appendLine()
                appendLine(format.periodName(i))
                for (pos in Position.entries) {
                    q.positions[pos]?.let { appendLine("  ${pos.short}: ${names[it]}") }
                }
                if (q.bench.isNotEmpty()) appendLine("  Sits: ${q.bench.joinToString { names[it].orEmpty() }}")
            }
            appendLine()
            appendLine("Playing time (${format.unit})")
            summarize(plan, players).forEach {
                appendLine("  ${names[it.playerId]}: ${it.periodsPlayed}/${plan.periods.size}")
            }
        }.trimEnd()
    }
}
