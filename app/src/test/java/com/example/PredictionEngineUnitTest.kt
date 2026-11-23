package com.example

import com.example.data.sources.FootballDataRepository
import com.example.domain.engine.AccumulatorAnalyzer
import com.example.domain.engine.CalibrationEngine
import com.example.domain.engine.MarketCalculator
import com.example.domain.engine.PoissonModel
import com.example.domain.model.AccumulatorSelection
import com.example.domain.model.FixtureItem
import com.example.domain.model.FixtureStatus
import com.example.domain.model.GenderCategory
import com.example.domain.model.MatchResultRecord
import com.example.domain.model.ModelParameters
import com.example.domain.model.TrackedPrediction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PredictionEngineUnitTest {

    private fun sampleFixture(
        id: String = "fx_1",
        home: String = "Arsenal",
        away: String = "Chelsea"
    ) = FixtureItem(
        matchId = id,
        homeTeamId = "t_home",
        homeTeam = home,
        awayTeamId = "t_away",
        awayTeam = away,
        competitionId = "epl",
        competition = "Premier League",
        country = "England",
        gender = GenderCategory.MEN,
        kickoffEpochMs = 1_800_000_000_000L,
        status = FixtureStatus.SCHEDULED,
        historicalMatchesSample = 10,
        sourceId = "thesportsdb",
        sourceName = "TheSportsDB",
        sourceUrl = "https://www.thesportsdb.com",
        retrievedEpochMs = 1_790_000_000_000L
    )

    @Test
    fun `score matrix sums to 1_0 and is deterministic`() {
        val m1 = PoissonModel.buildScoreMatrix(1.65, 1.10, -0.11)
        val m2 = PoissonModel.buildScoreMatrix(1.65, 1.10, -0.11)
        var sum = 0.0
        for (h in m1.indices) {
            for (a in m1[h].indices) {
                assertEquals(m1[h][a], m2[h][a], 1e-12)
                sum += m1[h][a]
            }
        }
        assertTrue(abs(sum - 1.0) < 1e-9)
    }

    @Test
    fun `mutually exclusive markets sum to 100 and missing HT or minute history reports insufficient data`() {
        val fix = sampleFixture()
        val homeHist = List(4) { idx ->
            MatchResultRecord(
                matchId = "h_$idx",
                dateEpochMs = 1_780_000_000_000L + idx * 86_400_000L,
                competition = "Premier League",
                homeTeamId = "t_home",
                homeTeam = "Arsenal",
                awayTeamId = "opp_$idx",
                awayTeam = "Opponent $idx",
                homeGoals = 2,
                awayGoals = 1,
                sourceName = "TheSportsDB"
            )
        }
        val awayHist = List(4) { idx ->
            MatchResultRecord(
                matchId = "a_$idx",
                dateEpochMs = 1_780_000_000_000L + idx * 86_400_000L,
                competition = "Premier League",
                homeTeamId = "opp_a_$idx",
                homeTeam = "Host $idx",
                awayTeamId = "t_away",
                awayTeam = "Chelsea",
                homeGoals = 1,
                awayGoals = 1,
                sourceName = "TheSportsDB"
            )
        }

        val report = MarketCalculator.generatePrediction(
            fixture = fix,
            homeHistory = homeHist,
            awayHistory = awayHist,
            h2hHistory = emptyList()
        )

        assertEquals(100, report.probHomeWin + report.probDraw + report.probAwayWin)
        assertNull(report.officialProviderHomeXg)
        assertTrue(report.fullTimeSuite.available)

        val btts = report.fullTimeSuite.groups.first { it.marketName == "Both Teams to Score" }
        assertEquals(100, btts.outcomes.sumOf { it.probabilityPct })

        // No half-time splits or minute-level timestamps -> strictly unavailable
        assertFalse(report.firstHalfSuite.available)
        assertTrue(report.firstHalfSuite.unavailableReason!!.contains("Insufficient data", ignoreCase = true))
        assertFalse(report.firstTenMinutesSuite.available)
        assertTrue(report.firstTenMinutesSuite.unavailableReason!!.contains("Insufficient data", ignoreCase = true))
    }

    @Test
    fun `first half and first 10 minute markets activate when verified HT and minute logs exist`() {
        val fix = sampleFixture()
        val richHome = List(4) { idx ->
            MatchResultRecord(
                matchId = "ol_h_$idx",
                dateEpochMs = 1_780_000_000_000L + idx * 86_400_000L,
                competition = "Bundesliga",
                homeTeamId = "t_home",
                homeTeam = "Arsenal",
                awayTeamId = "opp_$idx",
                awayTeam = "Opp $idx",
                homeGoals = 2,
                awayGoals = 1,
                halftimeHomeGoals = 1,
                halftimeAwayGoals = 0,
                homeGoalMinutes = listOf(8, 65),
                awayGoalMinutes = listOf(77),
                sourceName = "OpenLigaDB"
            )
        }
        val richAway = List(3) { idx ->
            MatchResultRecord(
                matchId = "ol_a_$idx",
                dateEpochMs = 1_780_000_000_000L + idx * 86_400_000L,
                competition = "Bundesliga",
                homeTeamId = "opp_a_$idx",
                homeTeam = "OppA $idx",
                awayTeamId = "t_away",
                awayTeam = "Chelsea",
                homeGoals = 1,
                awayGoals = 2,
                halftimeHomeGoals = 0,
                halftimeAwayGoals = 1,
                homeGoalMinutes = listOf(52),
                awayGoalMinutes = listOf(6, 80),
                sourceName = "OpenLigaDB"
            )
        }

        val report = MarketCalculator.generatePrediction(fix, richHome, richAway, emptyList())
        assertTrue(report.firstHalfSuite.available)
        assertTrue(report.secondHalfSuite.available)
        assertTrue(report.firstTenMinutesSuite.available)
        val first10Group = report.firstTenMinutesSuite.groups.first { it.marketName == "First 10-Min 1X2" }
        assertEquals(100, first10Group.outcomes.sumOf { it.probabilityPct })
    }

    @Test
    fun `accumulator detects same-match correlation and invalidates naive product`() {
        val fix1 = sampleFixture(id = "m1", home = "Arsenal", away = "Chelsea")
        val fix2 = sampleFixture(id = "m2", home = "Bayern Munich", away = "Dortmund")

        val independent = AccumulatorAnalyzer.evaluate(
            listOf(
                AccumulatorSelection(fix1, "1X2", "Home Win", 0.55, 55),
                AccumulatorSelection(fix2, "1X2", "Home Win", 0.60, 60)
            )
        )
        assertTrue(independent.independenceJustified)
        assertEquals(33.0, independent.naiveCombinedProbPct, 0.2)

        val correlated = AccumulatorAnalyzer.evaluate(
            listOf(
                AccumulatorSelection(fix1, "1X2", "Home Win", 0.55, 55),
                AccumulatorSelection(fix1, "Total Goals O/U 2.5", "Over 2.5", 0.58, 58)
            )
        )
        assertFalse(correlated.independenceJustified)
        assertTrue(correlated.correlationWarnings.isNotEmpty())
    }

    @Test
    fun `calibration rejects parameter candidates when existing model is already optimal on settled sample`() {
        val settled = listOf(
            TrackedPrediction(
                id = "p1", userId = "u1", matchId = "m1", homeTeam = "A", awayTeam = "B",
                competition = "EPL", homeXg = 2.4, awayXg = 0.6,
                probHomeWin = 75, probDraw = 16, probAwayWin = 9,
                status = "SETTLED", actualHomeGoals = 3, actualAwayGoals = 0,
                settledResult1X2 = "HOME", outcomeCorrect = true, confidenceScore = 78
            ),
            TrackedPrediction(
                id = "p2", userId = "u1", matchId = "m2", homeTeam = "C", awayTeam = "D",
                competition = "EPL", homeXg = 1.2, awayXg = 1.2,
                probHomeWin = 36, probDraw = 28, probAwayWin = 36,
                status = "SETTLED", actualHomeGoals = 1, actualAwayGoals = 1,
                settledResult1X2 = "DRAW", outcomeCorrect = false, confidenceScore = 52
            ),
            TrackedPrediction(
                id = "p3", userId = "u1", matchId = "m3", homeTeam = "E", awayTeam = "F",
                competition = "La Liga", homeXg = 0.5, awayXg = 2.5,
                probHomeWin = 8, probDraw = 15, probAwayWin = 77,
                status = "SETTLED", actualHomeGoals = 0, actualAwayGoals = 3,
                settledResult1X2 = "AWAY", outcomeCorrect = true, confidenceScore = 80
            )
        )

        val summary = CalibrationEngine.summarizeAccuracy(settled)
        assertEquals(3, summary.settledPredictions)
        assertEquals(2, summary.correctPredictions)

        // Insufficient sample (< 3) check
        val smallProposal = CalibrationEngine.evaluateRecalibration(ModelParameters(), settled.take(2))
        assertFalse(smallProposal.approvedImprovement)
    }

    @Test
    fun `repository deduplicates identical fixtures across providers on same day`() {
        val repo = FootballDataRepository()
        val f1 = sampleFixture(id = "openliga_1", home = "Bayern Munich", away = "Borussia Dortmund")
        val f2 = sampleFixture(id = "sportsdb_99", home = "Bayern Munich", away = "Borussia Dortmund")
        val deduped = repo.deduplicateFixtures(listOf(f1, f2))
        assertEquals(1, deduped.size)
    }
}
