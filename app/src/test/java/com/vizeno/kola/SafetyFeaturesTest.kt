package com.vizeno.kola

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyFeaturesTest {
    private val square = listOf(Offset(.1f,.1f), Offset(.9f,.1f), Offset(.9f,.9f), Offset(.1f,.9f))

    @Test fun boundaryRadarFindsDistanceAndPredictedExit() {
        assertEquals(.3f, ZoneMath.distanceToEdges(Offset(.6f,.5f), square)!!, .001f)
        val eta = ZoneMath.predictedExitSeconds(Offset(.6f,.5f), Offset(1f,0f), .1f, square)
        assertNotNull(eta)
        assertTrue(eta!! in 2.8f..3.2f)
    }

    @Test fun heatmapAccumulatesAndDecaysWithoutRedPosePoints() {
        val heatmap = AreaHeatmap(4,4)
        val first = heatmap.update(listOf(HeatPoint(Offset(.4f,.4f),1f)))
        val second = heatmap.update(emptyList())
        assertEquals(1, first.size)
        assertTrue(second.single().intensity < first.single().intensity)
    }

    @Test fun trackerMarksLatePersistentUnsafeObjectAsIntroduced() {
        val tracker = ObjectTracker(startedAt = 0L)
        val scissors = RawDetection("scissors", .8f, Rect(.2f,.2f,.5f,.5f))
        val first = tracker.update(listOf(scissors), 3_000L).single()
        val later = tracker.update(listOf(scissors), 4_600L).single()
        assertTrue(first.newlyIntroduced)
        assertEquals("DANGER", later.risk)
        assertTrue(later.persistenceMs >= 1_400L)
    }
}
