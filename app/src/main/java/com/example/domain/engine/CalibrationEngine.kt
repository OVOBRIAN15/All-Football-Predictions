package com.example.domain.engine

import com.example.domain.model.AccuracyReportSummary
import com.example.domain.model.ModelParameters
import com.example.domain.model.RecalibrationProposal
import com.example.domain.model.TrackedPrediction
import kotlin.math.pow
import kotlin.math.roundToInt

object CalibrationEngine {

    fun summarizeAccuracy(predictions: List<TrackedPrediction>): AccuracyReportSummary {
        val voidCount = predictions.count { it.status == "VOID" }
        val settled = predictions.filter { it.status == "SETTLED" }
        val correct = settled.count { it.outcomeCorrect == true }
        val incorrect = settled.size - correct
        val overallAcc = if (settled.isNotEmpty()) (correct * 100.0) / settled.size else 0.0

        val brier = if (settled.isNotEmpty()) {
            settled.sumOf { pred ->
                val pHome = pred.probHomeWin / 100.0
                val pDraw = pred.probDraw / 100.0
                val pAway = pred.probAwayWin / 100.0
                val yHome = if (pred.settledResult1X2 == "HOME") 1.0 else 0.0
                val yDraw = if (pred.settledResult1X2 == "DRAW") 1.0 else 0.0
                val yAway = if (pred.settledResult1X2 == "AWAY") 1.0 else 0.0
                ((pHome - yHome).pow(2) + (pDraw - yDraw).pow(2) + (pAway - yAway).pow(2)) / 3.0
            } / settled.size
        } else 0.0

        val byComp = settled.groupBy { it.competition }.mapValues { (_, list) ->
            Pair(list.count { it.outcomeCorrect == true }, list.size)
        }

        val bttsCorrect = settled.count { pred ->
            val actualYes = (pred.actualHomeGoals ?: 0) > 0 && (pred.actualAwayGoals ?: 0) > 0
            (pred.probBttsYes >= 50 && actualYes) || (pred.probBttsYes < 50 && !actualYes)
        }
        val over25Correct = settled.count { pred ->
            val actualOver = ((pred.actualHomeGoals ?: 0) + (pred.actualAwayGoals ?: 0)) > 2
            (pred.probOver25 >= 50 && actualOver) || (pred.probOver25 < 50 && !actualOver)
        }

        val byMarket = if (settled.isNotEmpty()) {
            mapOf(
                "Full-Time 1X2" to Pair(correct, settled.size),
                "Both Teams to Score" to Pair(bttsCorrect, settled.size),
                "Over/Under 2.5 Goals" to Pair(over25Correct, settled.size)
            )
        } else emptyMap()

        val byPeriod = if (settled.isNotEmpty()) {
            mapOf("Full-Time (90')" to Pair(correct, settled.size))
        } else emptyMap()

        val byConfidence = settled.groupBy { pred ->
            when {
                pred.confidenceScore >= 75 -> "75% - 100% High"
                pred.confidenceScore >= 60 -> "60% - 74% Moderate"
                else -> "< 60% Lower"
            }
        }.mapValues { (_, list) ->
            Pair(list.count { it.outcomeCorrect == true }, list.size)
        }

        return AccuracyReportSummary(
            totalPredictions = predictions.size,
            settledPredictions = settled.size,
            voidOrPostponedCount = voidCount,
            correctPredictions = correct,
            incorrectPredictions = incorrect,
            overallAccuracyPct = (overallAcc * 10.0).roundToInt() / 10.0,
            brierScore = (brier * 10000.0).roundToInt() / 10000.0,
            accuracyByCompetition = byComp,
            accuracyByMarket = byMarket,
            accuracyByPeriod = byPeriod,
            accuracyByConfidenceRange = byConfidence
        )
    }

    /**
     * Evaluates deterministic parameter candidates on settled historical predictions.
     * Rejects any change that worsens Brier score or accuracy on validation records.
     */
    fun evaluateRecalibration(
        currentParams: ModelParameters,
        settledPredictions: List<TrackedPrediction>
    ): RecalibrationProposal {
        val validSettled = settledPredictions.filter { it.status == "SETTLED" && it.actualHomeGoals != null && it.actualAwayGoals != null }
        if (validSettled.size < 3) {
            return RecalibrationProposal(
                candidateParams = currentParams,
                baselineBrier = 0.0,
                candidateBrier = 0.0,
                baselineAccuracyPct = 0.0,
                candidateAccuracyPct = 0.0,
                sampleSize = validSettled.size,
                approvedImprovement = false,
                evaluationSummary = "At least 3 settled predictions are required to run validation backtesting (currently ${validSettled.size})."
            )
        }

        val baselineSummary = summarizeAccuracy(validSettled)
        val baselineBrier = baselineSummary.brierScore
        val baselineAcc = baselineSummary.overallAccuracyPct

        // Deterministic grid search over Dixon-Coles rho & home advantage scaling on stored xG
        val rhoCandidates = listOf(-0.14, -0.11, -0.08, -0.05)
        val advCandidates = listOf(0.95, 1.00, 1.05, 1.10)

        var bestParams = currentParams
        var bestBrier = baselineBrier
        var bestAcc = baselineAcc

        for (rho in rhoCandidates) {
            for (advScale in advCandidates) {
                var brierSum = 0.0
                var correctCount = 0
                validSettled.forEach { pred ->
                    val adjHomeXg = (pred.homeXg * advScale).coerceIn(0.20, 4.50)
                    val adjAwayXg = pred.awayXg.coerceIn(0.20, 4.50)
                    val matrix = PoissonModel.buildScoreMatrix(adjHomeXg, adjAwayXg, rho)
                    var pH = 0.0
                    var pD = 0.0
                    var pA = 0.0
                    for (h in matrix.indices) {
                        for (a in matrix[h].indices) {
                            when {
                                h > a -> pH += matrix[h][a]
                                h == a -> pD += matrix[h][a]
                                else -> pA += matrix[h][a]
                            }
                        }
                    }
                    val yH = if (pred.settledResult1X2 == "HOME") 1.0 else 0.0
                    val yD = if (pred.settledResult1X2 == "DRAW") 1.0 else 0.0
                    val yA = if (pred.settledResult1X2 == "AWAY") 1.0 else 0.0
                    brierSum += ((pH - yH).pow(2) + (pD - yD).pow(2) + (pA - yA).pow(2)) / 3.0

                    val predictedOutcome = when {
                        pH >= pD && pH >= pA -> "HOME"
                        pD >= pH && pD >= pA -> "DRAW"
                        else -> "AWAY"
                    }
                    if (predictedOutcome == pred.settledResult1X2) correctCount++
                }
                val candBrier = ((brierSum / validSettled.size) * 10000.0).roundToInt() / 10000.0
                val candAcc = ((correctCount * 1000.0) / validSettled.size).roundToInt() / 10.0

                if (candBrier < bestBrier && candAcc >= bestAcc) {
                    bestBrier = candBrier
                    bestAcc = candAcc
                    bestParams = currentParams.copy(
                        versionTag = incrementVersion(currentParams.versionTag),
                        dixonColesRho = rho,
                        homeAdvantageMultiplier = ((currentParams.homeAdvantageMultiplier * advScale) * 100.0).roundToInt() / 100.0
                    )
                }
            }
        }

        val improved = bestBrier < baselineBrier && bestAcc >= baselineAcc
        val summary = if (improved) {
            "Candidate ${bestParams.versionTag} improved Brier score ($baselineBrier -> $bestBrier) and accuracy ($baselineAcc% -> $bestAcc%) across ${validSettled.size} settled matches."
        } else {
            "No candidate improved validation Brier score ($baselineBrier) and accuracy ($baselineAcc%). Existing model ${currentParams.versionTag} retained; candidate rejected."
        }

        return RecalibrationProposal(
            candidateParams = bestParams,
            baselineBrier = baselineBrier,
            candidateBrier = bestBrier,
            baselineAccuracyPct = baselineAcc,
            candidateAccuracyPct = bestAcc,
            sampleSize = validSettled.size,
            approvedImprovement = improved,
            evaluationSummary = summary
        )
    }

    private fun incrementVersion(tag: String): String {
        val regex = Regex("v(\\d+)\\.(\\d+)")
        val match = regex.find(tag)
        return if (match != null) {
            val major = match.groupValues[1].toIntOrNull() ?: 1
            val minor = (match.groupValues[2].toIntOrNull() ?: 0) + 1
            "v$major.$minor-dc-poisson"
        } else {
            "v1.1-dc-poisson"
        }
    }
}
