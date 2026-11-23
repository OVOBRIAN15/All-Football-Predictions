package com.example.domain.engine

import com.example.domain.model.CorrectScoreProb
import com.example.domain.model.FixtureItem
import com.example.domain.model.MarketGroup
import com.example.domain.model.MarketOutcomeProb
import com.example.domain.model.MatchPredictionReport
import com.example.domain.model.MatchResultRecord
import com.example.domain.model.ModelParameters
import com.example.domain.model.PeriodMarketSuite
import kotlin.math.min
import kotlin.math.roundToInt

object MarketCalculator {

    fun generatePrediction(
        fixture: FixtureItem,
        homeHistory: List<MatchResultRecord>,
        awayHistory: List<MatchResultRecord>,
        h2hHistory: List<MatchResultRecord>,
        params: ModelParameters = ModelParameters(),
        generatedEpochMs: Long = System.currentTimeMillis()
    ): MatchPredictionReport {
        val (lambdaHome, lambdaAway) = PoissonModel.estimateExpectedGoals(
            homeTeamId = fixture.homeTeamId,
            homeTeamName = fixture.homeTeam,
            awayTeamId = fixture.awayTeamId,
            awayTeamName = fixture.awayTeam,
            homeHistory = homeHistory,
            awayHistory = awayHistory,
            h2hHistory = h2hHistory,
            params = params
        )

        val fullMatrix = PoissonModel.buildScoreMatrix(lambdaHome, lambdaAway, params.dixonColesRho)
        val fullTimeSuite = buildPeriodSuite(
            title = "Full-Time Markets",
            matrix = fullMatrix,
            includeTeamTotals = true,
            includeDnbAndBtts = true
        )

        // 1X2 normalized exactly to 100%
        val raw1X2 = extract1X2Raw(fullMatrix)
        val norm1X2 = normalizeTo100(listOf(raw1X2.first, raw1X2.second, raw1X2.third))

        // First half & Second half suites ONLY when halftime data exists for >= 3 matches across histories
        val htMatches = (homeHistory + awayHistory).filter {
            it.halftimeHomeGoals != null && it.halftimeAwayGoals != null
        }
        val firstHalfSuite: PeriodMarketSuite
        val secondHalfSuite: PeriodMarketSuite
        val missingStats = mutableListOf<String>()

        if (htMatches.size >= 3) {
            val totalGoalsInHtSample = htMatches.sumOf { it.homeGoals + it.awayGoals }.coerceAtLeast(1)
            val htGoalsInSample = htMatches.sumOf { (it.halftimeHomeGoals ?: 0) + (it.halftimeAwayGoals ?: 0) }
            val htShare = (htGoalsInSample.toDouble() / totalGoalsInHtSample).coerceIn(0.32, 0.56)
            val shShare = 1.0 - htShare

            val htMatrix = PoissonModel.buildScoreMatrix(lambdaHome * htShare, lambdaAway * htShare, params.dixonColesRho * 0.5)
            val shMatrix = PoissonModel.buildScoreMatrix(lambdaHome * shShare, lambdaAway * shShare, params.dixonColesRho * 0.5)

            firstHalfSuite = buildPeriodSuite(
                title = "First-Half Markets (0-45')",
                matrix = htMatrix,
                includeTeamTotals = true,
                includeDnbAndBtts = false
            )
            secondHalfSuite = buildPeriodSuite(
                title = "Second-Half Markets (45-90')",
                matrix = shMatrix,
                includeTeamTotals = true,
                includeDnbAndBtts = false
            )
        } else {
            missingStats.add("Half-time goal split history (< 3 verified matches)")
            firstHalfSuite = PeriodMarketSuite(
                periodTitle = "First-Half Markets (0-45')",
                available = false,
                unavailableReason = "Insufficient data — half-time score splits unavailable for these teams.",
                groups = emptyList()
            )
            secondHalfSuite = PeriodMarketSuite(
                periodTitle = "Second-Half Markets (45-90')",
                available = false,
                unavailableReason = "Insufficient data — second-half score splits unavailable for these teams.",
                groups = emptyList()
            )
        }

        // First 10 minutes: NEVER calculate from final scores alone!
        // Require >= 5 matches with minute-level goal timestamps across the team histories.
        val minuteMatches = (homeHistory + awayHistory).filter {
            it.homeGoalMinutes.isNotEmpty() || it.awayGoalMinutes.isNotEmpty() || (it.homeGoals == 0 && it.awayGoals == 0 && it.sourceName.contains("OpenLigaDB", ignoreCase = true))
        }
        val firstTenSuite = if (minuteMatches.size >= 5) {
            buildFirstTenMinuteSuite(
                fixture = fixture,
                homeHistory = homeHistory,
                awayHistory = awayHistory,
                lambdaHome = lambdaHome,
                lambdaAway = lambdaAway
            )
        } else {
            missingStats.add("Minute-level goal timestamps (< 5 event-logged matches)")
            PeriodMarketSuite(
                periodTitle = "First 10 Minutes (0-10')",
                available = false,
                unavailableReason = "Insufficient data — minute-level goal event history is unavailable from the connected data source.",
                groups = emptyList()
            )
        }

        if (fixture.officialProviderHomeXg == null || fixture.officialProviderAwayXg == null) {
            missingStats.add("Official provider xG feed (using deterministic Poisson model-estimated xG)")
        }

        val combinedSample = homeHistory.size + awayHistory.size
        val warnings = mutableListOf<String>()
        if (combinedSample < 6) {
            warnings.add("Small historical sample ($combinedSample matches); predictions shrink toward league baseline.")
        }
        if (h2hHistory.isEmpty()) {
            warnings.add("No recent head-to-head meetings found in connected datasets.")
        }
        if (fixture.officialProviderHomeXg == null) {
            warnings.add("Official provider xG is unavailable; displayed expected goals are model-estimated.")
        }

        val confidence = calculateConfidence(
            homeSample = homeHistory.size,
            awaySample = awayHistory.size,
            h2hSample = h2hHistory.size,
            hasHt = htMatches.size >= 3,
            hasMinutes = minuteMatches.size >= 5,
            pMax = maxOf(norm1X2[0], norm1X2[1], norm1X2[2])
        )

        val facts = listOf(
            "Fixture: ${fixture.homeTeam} vs ${fixture.awayTeam} (${fixture.competition}, ${fixture.country})",
            "Historical sample analysed: ${homeHistory.size} home-side matches, ${awayHistory.size} away-side matches, ${h2hHistory.size} H2H meetings",
            "Data sources: ${fixture.sourceName}"
        )

        return MatchPredictionReport(
            fixture = fixture,
            modelVersion = params.versionTag,
            generatedEpochMs = generatedEpochMs,
            modelEstimatedHomeXg = (lambdaHome * 100.0).roundToInt() / 100.0,
            modelEstimatedAwayXg = (lambdaAway * 100.0).roundToInt() / 100.0,
            officialProviderHomeXg = fixture.officialProviderHomeXg,
            officialProviderAwayXg = fixture.officialProviderAwayXg,
            probHomeWin = norm1X2[0],
            probDraw = norm1X2[1],
            probAwayWin = norm1X2[2],
            fullTimeSuite = fullTimeSuite,
            firstHalfSuite = firstHalfSuite,
            secondHalfSuite = secondHalfSuite,
            firstTenMinutesSuite = firstTenSuite,
            fullTimeCorrectScores = fullTimeSuite.topCorrectScores,
            confidenceScore = confidence,
            historicalSampleSize = combinedSample,
            h2hSampleSize = h2hHistory.size,
            missingStatistics = missingStats,
            reliabilityWarnings = warnings,
            dataSources = listOf(fixture.sourceName),
            factsSummary = facts
        )
    }

    private fun buildPeriodSuite(
        title: String,
        matrix: Array<DoubleArray>,
        includeTeamTotals: Boolean,
        includeDnbAndBtts: Boolean
    ): PeriodMarketSuite {
        val (pHome, pDraw, pAway) = extract1X2Raw(matrix)
        val norm1X2 = normalizeTo100(listOf(pHome, pDraw, pAway))
        val groups = mutableListOf<MarketGroup>()

        groups.add(
            MarketGroup(
                marketName = "1X2 Result",
                outcomes = listOf(
                    MarketOutcomeProb("Home Win (1)", norm1X2[0], pHome),
                    MarketOutcomeProb("Draw (X)", norm1X2[1], pDraw),
                    MarketOutcomeProb("Away Win (2)", norm1X2[2], pAway)
                )
            )
        )

        val dc1X = (pHome + pDraw).coerceIn(0.0, 1.0)
        val dc12 = (pHome + pAway).coerceIn(0.0, 1.0)
        val dcX2 = (pDraw + pAway).coerceIn(0.0, 1.0)
        groups.add(
            MarketGroup(
                marketName = "Double Chance",
                outcomes = listOf(
                    MarketOutcomeProb("1X (Home or Draw)", (dc1X * 100).roundToInt(), dc1X),
                    MarketOutcomeProb("12 (Home or Away)", (dc12 * 100).roundToInt(), dc12),
                    MarketOutcomeProb("X2 (Draw or Away)", (dcX2 * 100).roundToInt(), dcX2)
                )
            )
        )

        if (includeDnbAndBtts) {
            val nonDraw = (pHome + pAway).coerceAtLeast(1e-6)
            val dnbH = pHome / nonDraw
            val dnbA = pAway / nonDraw
            val dnbNorm = normalizeTo100(listOf(dnbH, dnbA))
            groups.add(
                MarketGroup(
                    marketName = "Draw No Bet",
                    outcomes = listOf(
                        MarketOutcomeProb("Home DNB", dnbNorm[0], dnbH),
                        MarketOutcomeProb("Away DNB", dnbNorm[1], dnbA)
                    )
                )
            )

            var bttsYes = 0.0
            for (h in 1 until matrix.size) {
                for (a in 1 until matrix[h].size) {
                    bttsYes += matrix[h][a]
                }
            }
            val bttsNo = (1.0 - bttsYes).coerceIn(0.0, 1.0)
            val bttsNorm = normalizeTo100(listOf(bttsYes, bttsNo))
            groups.add(
                MarketGroup(
                    marketName = "Both Teams to Score",
                    outcomes = listOf(
                        MarketOutcomeProb("BTTS Yes", bttsNorm[0], bttsYes),
                        MarketOutcomeProb("BTTS No", bttsNorm[1], bttsNo)
                    )
                )
            )
        }

        // Total Goals Over/Under lines
        val lines = if (includeDnbAndBtts) listOf(0.5, 1.5, 2.5, 3.5) else listOf(0.5, 1.5)
        for (line in lines) {
            var over = 0.0
            for (h in matrix.indices) {
                for (a in matrix[h].indices) {
                    if (h + a > line) over += matrix[h][a]
                }
            }
            val under = (1.0 - over).coerceIn(0.0, 1.0)
            val norm = normalizeTo100(listOf(over, under))
            groups.add(
                MarketGroup(
                    marketName = "Total Goals O/U $line",
                    outcomes = listOf(
                        MarketOutcomeProb("Over $line", norm[0], over),
                        MarketOutcomeProb("Under $line", norm[1], under)
                    )
                )
            )
        }

        if (includeTeamTotals) {
            val teamLine = if (includeDnbAndBtts) 1.5 else 0.5
            var homeOver = 0.0
            var awayOver = 0.0
            for (h in matrix.indices) {
                for (a in matrix[h].indices) {
                    if (h > teamLine) homeOver += matrix[h][a]
                    if (a > teamLine) awayOver += matrix[h][a]
                }
            }
            val normH = normalizeTo100(listOf(homeOver, (1.0 - homeOver).coerceIn(0.0, 1.0)))
            val normA = normalizeTo100(listOf(awayOver, (1.0 - awayOver).coerceIn(0.0, 1.0)))
            groups.add(
                MarketGroup(
                    marketName = "Home Team Goals O/U $teamLine",
                    outcomes = listOf(
                        MarketOutcomeProb("Home Over $teamLine", normH[0], homeOver),
                        MarketOutcomeProb("Home Under $teamLine", normH[1], 1.0 - homeOver)
                    )
                )
            )
            groups.add(
                MarketGroup(
                    marketName = "Away Team Goals O/U $teamLine",
                    outcomes = listOf(
                        MarketOutcomeProb("Away Over $teamLine", normA[0], awayOver),
                        MarketOutcomeProb("Away Under $teamLine", normA[1], 1.0 - awayOver)
                    )
                )
            )
        }

        val scores = mutableListOf<CorrectScoreProb>()
        for (h in 0..5) {
            for (a in 0..5) {
                scores.add(
                    CorrectScoreProb(
                        homeGoals = h,
                        awayGoals = a,
                        probabilityPct = ((matrix[h][a] * 1000.0).roundToInt() / 10.0)
                    )
                )
            }
        }
        val topScores = scores.sortedByDescending { it.probabilityPct }.take(8)

        return PeriodMarketSuite(
            periodTitle = title,
            available = true,
            groups = groups,
            topCorrectScores = topScores
        )
    }

    private fun buildFirstTenMinuteSuite(
        fixture: FixtureItem,
        homeHistory: List<MatchResultRecord>,
        awayHistory: List<MatchResultRecord>,
        lambdaHome: Double,
        lambdaAway: Double
    ): PeriodMarketSuite {
        // Empirical minute-level intensity in 1..10 minutes across verified minute-tagged matches
        val homeEvents = homeHistory.filter { it.sourceName.contains("OpenLigaDB", ignoreCase = true) || it.homeGoalMinutes.isNotEmpty() }
        val awayEvents = awayHistory.filter { it.sourceName.contains("OpenLigaDB", ignoreCase = true) || it.awayGoalMinutes.isNotEmpty() }

        val homeEarlyRate = if (homeEvents.isNotEmpty()) {
            val earlyGoals = homeEvents.sumOf { m ->
                val isHome = PoissonModel.matchesTeam(m.homeTeamId, m.homeTeam, fixture.homeTeamId, fixture.homeTeam)
                val mins = if (isHome) m.homeGoalMinutes else m.awayGoalMinutes
                mins.count { it in 1..10 }
            }
            (earlyGoals.toDouble() / homeEvents.size).coerceIn(0.03, 0.35)
        } else {
            (lambdaHome / 9.0).coerceIn(0.05, 0.25)
        }

        val awayEarlyRate = if (awayEvents.isNotEmpty()) {
            val earlyGoals = awayEvents.sumOf { m ->
                val isHome = PoissonModel.matchesTeam(m.homeTeamId, m.homeTeam, fixture.awayTeamId, fixture.awayTeam)
                val mins = if (isHome) m.homeGoalMinutes else m.awayGoalMinutes
                mins.count { it in 1..10 }
            }
            (earlyGoals.toDouble() / awayEvents.size).coerceIn(0.03, 0.35)
        } else {
            (lambdaAway / 9.0).coerceIn(0.05, 0.25)
        }

        val matrix10 = PoissonModel.buildScoreMatrix(homeEarlyRate, awayEarlyRate, 0.0)
        val (p1, pX, p2) = extract1X2Raw(matrix10)
        val norm1X2 = normalizeTo100(listOf(p1, pX, p2))
        val pNoGoal = matrix10[0][0].coerceIn(0.0, 1.0)
        val pGoal = (1.0 - pNoGoal).coerceIn(0.0, 1.0)
        val normGoal = normalizeTo100(listOf(pGoal, pNoGoal))

        return PeriodMarketSuite(
            periodTitle = "First 10 Minutes (0-10')",
            available = true,
            groups = listOf(
                MarketGroup(
                    marketName = "First 10-Min 1X2",
                    outcomes = listOf(
                        MarketOutcomeProb("Home Ahead at 10'", norm1X2[0], p1),
                        MarketOutcomeProb("Draw at 10'", norm1X2[1], pX),
                        MarketOutcomeProb("Away Ahead at 10'", norm1X2[2], p2)
                    )
                ),
                MarketGroup(
                    marketName = "First 10-Min Goal",
                    outcomes = listOf(
                        MarketOutcomeProb("Goal in 0-10'", normGoal[0], pGoal),
                        MarketOutcomeProb("No Goal in 0-10'", normGoal[1], pNoGoal)
                    )
                )
            )
        )
    }

    private fun extract1X2Raw(matrix: Array<DoubleArray>): Triple<Double, Double, Double> {
        var home = 0.0
        var draw = 0.0
        var away = 0.0
        for (h in matrix.indices) {
            for (a in matrix[h].indices) {
                when {
                    h > a -> home += matrix[h][a]
                    h == a -> draw += matrix[h][a]
                    else -> away += matrix[h][a]
                }
            }
        }
        return Triple(home, draw, away)
    }

    /**
     * Largest-remainder integer percentage normalization so mutually exclusive outcomes always sum to 100%.
     */
    fun normalizeTo100(probs: List<Double>): List<Int> {
        if (probs.isEmpty()) return emptyList()
        val sum = probs.sum().takeIf { it > 0.0 } ?: return List(probs.size) { 100 / probs.size }
        val scaled = probs.map { (it / sum) * 100.0 }
        val floors = scaled.map { it.toInt() }.toMutableList()
        var remainder = 100 - floors.sum()
        val indicesByFrac = scaled.indices.sortedByDescending { scaled[it] - floors[it] }
        var idx = 0
        while (remainder > 0 && indicesByFrac.isNotEmpty()) {
            floors[indicesByFrac[idx % indicesByFrac.size]] += 1
            remainder--
            idx++
        }
        return floors
    }

    private fun calculateConfidence(
        homeSample: Int,
        awaySample: Int,
        h2hSample: Int,
        hasHt: Boolean,
        hasMinutes: Boolean,
        pMax: Int
    ): Int {
        val sampleScore = min(40, (homeSample + awaySample) * 2)
        val h2hBonus = min(15, h2hSample * 3)
        val periodBonus = (if (hasHt) 10 else 0) + (if (hasMinutes) 10 else 0)
        val signalStrength = ((pMax - 33).coerceIn(0, 50) * 0.5).roundToInt()
        return (20 + sampleScore + h2hBonus + periodBonus + signalStrength).coerceIn(25, 92)
    }
}
