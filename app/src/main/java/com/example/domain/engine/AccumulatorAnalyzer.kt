package com.example.domain.engine

import com.example.domain.model.AccumulatorEvaluation
import com.example.domain.model.AccumulatorSelection
import kotlin.math.roundToInt

object AccumulatorAnalyzer {

    fun evaluate(selections: List<AccumulatorSelection>): AccumulatorEvaluation {
        if (selections.isEmpty()) {
            return AccumulatorEvaluation(
                selections = emptyList(),
                naiveCombinedProbPct = 0.0,
                correlationWarnings = emptyList(),
                independenceJustified = true,
                riskNotice = "Select two or more fixtures to analyse combined probability and correlation."
            )
        }

        val warnings = mutableListOf<String>()

        // 1. Same-fixture correlation check
        val byMatch = selections.groupBy { it.fixture.matchId }
        byMatch.forEach { (_, items) ->
            if (items.size > 1) {
                val matchTitle = "${items.first().fixture.homeTeam} vs ${items.first().fixture.awayTeam}"
                warnings.add(
                    "Correlated same-match selections detected in $matchTitle (${items.joinToString { it.selectionLabel }}). Multiplying these probabilities is mathematically invalid."
                )
            }
        }

        // 2. Same-team overlap check across fixtures
        val teamCounts = mutableMapOf<String, Int>()
        selections.forEach { sel ->
            teamCounts[sel.fixture.homeTeam] = (teamCounts[sel.fixture.homeTeam] ?: 0) + 1
            teamCounts[sel.fixture.awayTeam] = (teamCounts[sel.fixture.awayTeam] ?: 0) + 1
        }
        teamCounts.filter { it.value > 1 }.keys.forEach { team ->
            warnings.add("$team appears in multiple selections; team form or schedule fatigue introduces dependency.")
        }

        // 3. Same-competition cluster check
        val byCompetition = selections.groupBy { it.fixture.competition }
        byCompetition.filter { it.value.size >= 3 }.forEach { (comp, items) ->
            warnings.add("${items.size} selections come from $comp; league-wide scoring variance may correlate outcomes.")
        }

        val independenceJustified = byMatch.values.none { it.size > 1 }
        val naiveProduct = selections.fold(1.0) { acc, sel -> acc * sel.probability.coerceIn(0.01, 0.99) }
        val naivePct = (naiveProduct * 1000.0).roundToInt() / 10.0

        val riskNotice = if (!independenceJustified) {
            "Correlation Warning: Same-match markets are dependent. Combined independent probability ($naivePct%) is suppressed from reliable use."
        } else {
            "Accumulator Risk Notice: Multi-leg accumulators compound variance rapidly. Probability estimates are mathematical projections, not guarantees."
        }

        return AccumulatorEvaluation(
            selections = selections,
            naiveCombinedProbPct = naivePct,
            correlationWarnings = warnings,
            independenceJustified = independenceJustified,
            riskNotice = riskNotice
        )
    }
}
