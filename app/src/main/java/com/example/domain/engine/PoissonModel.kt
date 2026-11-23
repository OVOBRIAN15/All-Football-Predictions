package com.example.domain.engine

import com.example.domain.model.MatchResultRecord
import com.example.domain.model.ModelParameters
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

object PoissonModel {

    private const val MAX_GOALS = 8

    fun factorial(n: Int): Double {
        if (n <= 1) return 1.0
        var res = 1.0
        for (i in 2..n) {
            res *= i
        }
        return res
    }

    fun poissonPmf(lambda: Double, k: Int): Double {
        if (lambda <= 0.0) return if (k == 0) 1.0 else 0.0
        return (lambda.pow(k) * exp(-lambda)) / factorial(k)
    }

    /**
     * Dixon-Coles low-score correlation factor tau(x, y, lambda, mu, rho)
     */
    fun dixonColesTau(x: Int, y: Int, lambda: Double, mu: Double, rho: Double): Double {
        return when {
            x == 0 && y == 0 -> max(0.01, 1.0 - lambda * mu * rho)
            x == 0 && y == 1 -> max(0.01, 1.0 + lambda * rho)
            x == 1 && y == 0 -> max(0.01, 1.0 + mu * rho)
            x == 1 && y == 1 -> max(0.01, 1.0 - rho)
            else -> 1.0
        }
    }

    /**
     * Builds a normalized (MAX_GOALS+1) x (MAX_GOALS+1) bivariate score distribution matrix.
     * Sum of all cells == 1.0.
     */
    fun buildScoreMatrix(
        lambdaHome: Double,
        lambdaAway: Double,
        rho: Double
    ): Array<DoubleArray> {
        val matrix = Array(MAX_GOALS + 1) { DoubleArray(MAX_GOALS + 1) }
        var total = 0.0
        for (h in 0..MAX_GOALS) {
            val pH = poissonPmf(lambdaHome, h)
            for (a in 0..MAX_GOALS) {
                val pA = poissonPmf(lambdaAway, a)
                val tau = dixonColesTau(h, a, lambdaHome, lambdaAway, rho)
                val p = pH * pA * tau
                matrix[h][a] = p
                total += p
            }
        }
        if (total > 0.0) {
            for (h in 0..MAX_GOALS) {
                for (a in 0..MAX_GOALS) {
                    matrix[h][a] /= total
                }
            }
        }
        return matrix
    }

    /**
     * Deterministic estimation of expected goals (lambdaHome, lambdaAway) from historical records.
     * Separates home-venue performance, away-venue performance, recent 5-match form, and up to 5 H2H meetings.
     */
    fun estimateExpectedGoals(
        homeTeamId: String,
        homeTeamName: String,
        awayTeamId: String,
        awayTeamName: String,
        homeHistory: List<MatchResultRecord>,
        awayHistory: List<MatchResultRecord>,
        h2hHistory: List<MatchResultRecord>,
        params: ModelParameters
    ): Pair<Double, Double> {
        val baseline = params.leagueBaselineGoalsPerTeam
        val decay = params.recencyDecay

        val homeAtHome = homeHistory
            .filter { matchesTeam(it.homeTeamId, it.homeTeam, homeTeamId, homeTeamName) }
            .sortedByDescending { it.dateEpochMs }
        val homeAll = homeHistory.sortedByDescending { it.dateEpochMs }.take(5)

        val awayAtAway = awayHistory
            .filter { matchesTeam(it.awayTeamId, it.awayTeam, awayTeamId, awayTeamName) }
            .sortedByDescending { it.dateEpochMs }
        val awayAll = awayHistory.sortedByDescending { it.dateEpochMs }.take(5)

        val (homeAtt, homeDef) = weightedAttackDefense(
            teamId = homeTeamId,
            teamName = homeTeamName,
            primaryMatches = homeAtHome,
            fallbackMatches = homeAll,
            baseline = baseline,
            decay = decay
        )

        val (awayAtt, awayDef) = weightedAttackDefense(
            teamId = awayTeamId,
            teamName = awayTeamName,
            primaryMatches = awayAtAway,
            fallbackMatches = awayAll,
            baseline = baseline,
            decay = decay
        )

        var lambdaHome = baseline * (homeAtt / baseline) * (awayDef / baseline) * params.homeAdvantageMultiplier
        var lambdaAway = baseline * (awayAtt / baseline) * (homeDef / baseline)

        // Blend historical head-to-head (up to last 5 meetings) if available
        val recentH2H = h2hHistory.sortedByDescending { it.dateEpochMs }.take(5)
        if (recentH2H.isNotEmpty()) {
            var h2hHomeGoals = 0.0
            var h2hAwayGoals = 0.0
            recentH2H.forEach { m ->
                val homeIsFirst = matchesTeam(m.homeTeamId, m.homeTeam, homeTeamId, homeTeamName)
                if (homeIsFirst) {
                    h2hHomeGoals += m.homeGoals
                    h2hAwayGoals += m.awayGoals
                } else {
                    h2hHomeGoals += m.awayGoals
                    h2hAwayGoals += m.homeGoals
                }
            }
            val h2hAvgHome = h2hHomeGoals / recentH2H.size
            val h2hAvgAway = h2hAwayGoals / recentH2H.size
            val effectiveH2HWeight = params.h2hWeight * min(1.0, recentH2H.size / 5.0)
            lambdaHome = lambdaHome * (1.0 - effectiveH2HWeight) + h2hAvgHome * effectiveH2HWeight
            lambdaAway = lambdaAway * (1.0 - effectiveH2HWeight) + h2hAvgAway * effectiveH2HWeight
        }

        // Clamp within realistic football bounds [0.20, 4.50]
        return Pair(
            lambdaHome.coerceIn(0.20, 4.50),
            lambdaAway.coerceIn(0.20, 4.50)
        )
    }

    private fun weightedAttackDefense(
        teamId: String,
        teamName: String,
        primaryMatches: List<MatchResultRecord>,
        fallbackMatches: List<MatchResultRecord>,
        baseline: Double,
        decay: Double
    ): Pair<Double, Double> {
        val sample = if (primaryMatches.size >= 3) primaryMatches.take(8) else fallbackMatches
        if (sample.isEmpty()) return Pair(baseline, baseline)

        var weightedScored = 0.0
        var weightedConceded = 0.0
        var totalWeight = 0.0

        sample.forEachIndexed { index, match ->
            val w = decay.pow(index.toDouble())
            val isHome = matchesTeam(match.homeTeamId, match.homeTeam, teamId, teamName)
            val scored = if (isHome) match.homeGoals else match.awayGoals
            val conceded = if (isHome) match.awayGoals else match.homeGoals
            weightedScored += scored * w
            weightedConceded += conceded * w
            totalWeight += w
        }

        val rawAtt = if (totalWeight > 0) weightedScored / totalWeight else baseline
        val rawDef = if (totalWeight > 0) weightedConceded / totalWeight else baseline

        // Bayesian shrinkage toward baseline when sample size is small (prior weight = 3 matches)
        val priorWeight = 3.0
        val effectiveN = sample.size.toDouble()
        val shrunkAtt = (rawAtt * effectiveN + baseline * priorWeight) / (effectiveN + priorWeight)
        val shrunkDef = (rawDef * effectiveN + baseline * priorWeight) / (effectiveN + priorWeight)
        return Pair(shrunkAtt, shrunkDef)
    }

    fun matchesTeam(idA: String, nameA: String, idB: String, nameB: String): Boolean {
        if (idA.isNotBlank() && idB.isNotBlank() && idA.equals(idB, ignoreCase = true)) return true
        val normA = nameA.trim().lowercase()
        val normB = nameB.trim().lowercase()
        return normA == normB || (normA.length > 4 && normB.contains(normA)) || (normB.length > 4 && normA.contains(normB))
    }
}
