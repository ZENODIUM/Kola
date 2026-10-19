package com.vizeno.kola

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassicalVisionTest {
    @Test fun staticFramesRemainStable() {
        val engine = ClassicalVisionEngine(8, 6)
        engine.analyze(IntArray(48) { 90 })
        val result = engine.analyze(IntArray(48) { 90 })
        assertEquals(0f, result.motion, .001f)
        assertEquals(1f, result.stability, .001f)
        assertFalse(result.activityPresent)
    }

    @Test fun movingPatchProducesCentroidAndArea() {
        val engine = ClassicalVisionEngine(8, 6)
        engine.analyze(IntArray(48) { 40 })
        val changed = IntArray(48) { index -> if (index % 8 >= 5 && index / 8 in 2..4) 220 else 40 }
        val result = engine.analyze(changed)
        assertTrue(result.motion > .05f)
        assertTrue(result.motionArea > .1f)
        assertNotNull(result.center)
        assertTrue(result.center!!.x > .6f)
        assertTrue(result.activityPresent)
    }

    @Test fun wholeFrameChangeIsCameraShift() {
        val engine = ClassicalVisionEngine(8, 6)
        engine.analyze(IntArray(48) { 30 })
        assertTrue(engine.analyze(IntArray(48) { 180 }).cameraShift)
    }
}
