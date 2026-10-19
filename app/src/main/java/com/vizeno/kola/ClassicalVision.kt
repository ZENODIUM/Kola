package com.vizeno.kola

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import kotlin.math.sqrt

data class CvMetrics(
    val motion: Float,
    val motionArea: Float,
    val brightness: Float,
    val contrast: Float,
    val stability: Float,
    val center: Offset?,
    val bounds: Rect?,
    val directionX: Float,
    val directionY: Float,
    val direction: String,
    val movementSpeed: Float,
    val heatPoints: List<HeatPoint>,
    val cameraShift: Boolean,
    val activityPresent: Boolean,
    val lastMovementAt: Long,
)

/** Deterministic frame differencing. No learned weights, model or remote inference. */
class ClassicalVisionEngine(private val columns: Int = 32, private val rows: Int = 24) {
    private var previous: IntArray? = null
    private var lastCenter: Offset? = null
    private var smoothedDx = 0f
    private var smoothedDy = 0f
    private var lastMovementAt = System.currentTimeMillis()
    private var targetUntil = 0L
    private var lastAnalyzedAt = 0L
    private var smoothedSpeed = 0f

    fun analyze(luma: IntArray, now: Long = System.currentTimeMillis()): CvMetrics {
        require(luma.size == columns * rows)
        val mean = luma.average().toFloat()
        val contrast = (sqrt(luma.map { (it - mean) * (it - mean) }.average()).toFloat() / 128f).coerceIn(0f, 1f)
        val old = previous
        previous = luma.copyOf()
        if (old == null) { lastAnalyzedAt = now; return emptyMetrics(mean / 255f, contrast, now) }
        val elapsedSeconds = ((now - lastAnalyzedAt).coerceIn(16L, 1_000L) / 1_000f)
        lastAnalyzedAt = now

        val diffs = IntArray(luma.size) { abs(luma[it] - old[it]) }
        val rawMotion = diffs.average().toFloat() / 255f
        val broadArea = diffs.count { it >= 14 }.toFloat() / diffs.size
        val threshold = maxOf(14, (rawMotion * 255f * 1.15f).toInt())
        val active = diffs.indices.filter { diffs[it] >= threshold }
        val area = active.size.toFloat() / diffs.size
        val cameraShift = broadArea > .62f && rawMotion > .075f
        val strength = (rawMotion * 5.5f).coerceIn(0f, 1f)

        var center: Offset? = null
        var bounds: Rect? = null
        if (active.size >= 4 && !cameraShift) {
            var total = 0f; var wx = 0f; var wy = 0f
            var minX = columns; var minY = rows; var maxX = 0; var maxY = 0
            active.forEach { index ->
                val x = index % columns; val y = index / columns
                val weight = (diffs[index] - threshold + 1).toFloat()
                total += weight; wx += (x + .5f) * weight; wy += (y + .5f) * weight
                minX = minOf(minX, x); minY = minOf(minY, y); maxX = maxOf(maxX, x); maxY = maxOf(maxY, y)
            }
            center = Offset(wx / total / columns, wy / total / rows)
            bounds = Rect(minX.toFloat() / columns, minY.toFloat() / rows, (maxX + 1f) / columns, (maxY + 1f) / rows)
            val prior = lastCenter
            if (prior != null) {
                val rawDx = center.x - prior.x
                val rawDy = center.y - prior.y
                smoothedDx = smoothedDx * .68f + rawDx * .32f
                smoothedDy = smoothedDy * .68f + rawDy * .32f
                val instantSpeed = sqrt(rawDx * rawDx + rawDy * rawDy) / elapsedSeconds
                smoothedSpeed = smoothedSpeed * .72f + instantSpeed.coerceAtMost(2f) * .28f
            }
            lastCenter = center
        } else {
            smoothedDx *= .72f; smoothedDy *= .72f; smoothedSpeed *= .82f
        }
        if (area > .012f && rawMotion > .006f && !cameraShift) {
            lastMovementAt = now
            targetUntil = now + 4_000L
        }
        val activityPresent = now < targetUntil
        if (center == null && activityPresent && !cameraShift) center = lastCenter
        val direction = directionLabel(smoothedDx, smoothedDy, strength)
        val heat = active.sortedByDescending { diffs[it] }.take(12).map { index ->
            HeatPoint(Offset((index % columns + .5f) / columns, (index / columns + .5f) / rows), (diffs[index] / 100f).coerceIn(.15f, 1f))
        }
        return CvMetrics(strength, area, mean / 255f, contrast, (1f - strength).coerceIn(0f, 1f), center,
            bounds, smoothedDx, smoothedDy, direction, smoothedSpeed, heat, cameraShift, activityPresent, lastMovementAt)
    }

    private fun emptyMetrics(brightness: Float, contrast: Float, now: Long) = CvMetrics(
        0f, 0f, brightness, contrast, 1f, null, null, 0f, 0f, "STILL", 0f, emptyList(), false, false, now,
    )

    private fun directionLabel(dx: Float, dy: Float, strength: Float): String {
        if (strength < .035f || maxOf(abs(dx), abs(dy)) < .006f) return "STILL"
        return if (abs(dx) > abs(dy)) if (dx > 0) "RIGHT" else "LEFT" else if (dy > 0) "DOWN" else "UP"
    }
}
