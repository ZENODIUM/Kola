package com.vizeno.kola

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoneMathTest {
    private val square = listOf(Offset(.1f, .1f), Offset(.9f, .1f), Offset(.9f, .9f), Offset(.1f, .9f))

    @Test fun detectsPointInsidePolygon() = assertTrue(ZoneMath.contains(Offset(.5f, .5f), square))
    @Test fun detectsPointOutsidePolygon() = assertFalse(ZoneMath.contains(Offset(.95f, .5f), square))
    @Test fun incompletePolygonDoesNotAlert() = assertTrue(ZoneMath.contains(Offset(.99f, .99f), square.take(2)))

    @Test fun supportsConcaveArbitraryPolygons() {
        val concave = listOf(
            Offset(.1f, .1f), Offset(.9f, .1f), Offset(.9f, .9f),
            Offset(.5f, .55f), Offset(.1f, .9f),
        )
        assertTrue(ZoneMath.contains(Offset(.5f, .3f), concave))
        assertFalse(ZoneMath.contains(Offset(.5f, .8f), concave))
    }

    @Test fun supportsMoreThanFourVertices() {
        val hexagon = listOf(
            Offset(.25f, .1f), Offset(.75f, .1f), Offset(.95f, .5f),
            Offset(.75f, .9f), Offset(.25f, .9f), Offset(.05f, .5f),
        )
        assertTrue(ZoneMath.contains(Offset(.5f, .5f), hexagon))
        assertFalse(ZoneMath.contains(Offset(.98f, .5f), hexagon))
    }
}
