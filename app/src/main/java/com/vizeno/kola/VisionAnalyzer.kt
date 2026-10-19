package com.vizeno.kola

import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

class VisionAnalyzer(context: android.content.Context, private val onResult: (VisionState) -> Unit) : ImageAnalysis.Analyzer {
    private val engine = ClassicalVisionEngine()
    private val objectDetector = AsyncObjectDetector(context)
    private val poseDetector = AsyncPoseDetector(context)
    private var frameCount = 0
    private var fpsWindow = System.currentTimeMillis()
    private var fps = 0

    override fun analyze(image: ImageProxy) {
        val started = SystemClock.elapsedRealtime()
        try {
            val grid = sampleLuma(image)
            val result = engine.analyze(grid)
            if (objectDetector.isDue() || poseDetector.isDue()) image.toDetectorBitmap()?.let { bitmap ->
                if (objectDetector.isDue()) objectDetector.submit(bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, false))
                if (poseDetector.isDue()) poseDetector.submit(bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, false))
                bitmap.recycle()
            }
            val objects = objectDetector.current()
            val pose = poseDetector.current()
            updateFps()
            val transform: (Offset) -> Offset = { rotate(it, image.imageInfo.rotationDegrees) }
            val direction = rotateVector(Offset(result.directionX, result.directionY), image.imageInfo.rotationDegrees)
            val center = result.center?.let(transform)
            val corners = result.bounds?.let { box -> listOf(transform(box.topLeft), transform(box.bottomRight), transform(Offset(box.right, box.top)), transform(Offset(box.left, box.bottom))) }
            val bounds = corners?.let { points -> Rect(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y }) }
            val status = when {
                result.cameraShift -> "Camera view changed"
                result.contrast < .04f -> "Low contrast · details may be hard to see"
                result.brightness < .04f -> "Very dark · view may be hard to see"
                result.motion > .22f -> "A lot of movement in view"
                result.activityPresent && result.direction == "STILL" -> "Recent movement tracked"
                result.activityPresent -> "Activity tracked · ${result.direction.lowercase()}"
                else -> "Quiet scene"
            }
            onResult(VisionState(fps = fps, motion = result.motion, motionArea = result.motionArea,
                brightness = result.brightness, sceneContrast = result.contrast, stability = result.stability,
                directionX = direction.x, directionY = direction.y, direction = directionLabel(direction, result.motion),
                movementSpeed = result.movementSpeed,
                activityPresent = result.activityPresent, status = status, subjectCenter = center, motionBounds = bounds,
                heatPoints = result.heatPoints.map { it.copy(position = transform(it.position)) }, cameraShift = result.cameraShift,
                detectedItems = objects.map { item ->
                    val points = listOf(transform(item.bounds.topLeft), transform(item.bounds.bottomRight),
                        transform(Offset(item.bounds.right, item.bounds.top)), transform(Offset(item.bounds.left, item.bounds.bottom)))
                    item.copy(bounds = Rect(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y }))
                },
                poseJoints = pose.map { it.copy(position = transform(it.position)) },
                processingMs = SystemClock.elapsedRealtime() - started, source = "CAMERA", lastMovementAt = result.lastMovementAt))
        } finally { image.close() }
    }

    private fun sampleLuma(image: ImageProxy): IntArray {
        val plane = image.planes[0]
        return IntArray(32 * 24) { index ->
            val x = index % 32 * (image.width - 1) / 31
            val y = index / 32 * (image.height - 1) / 23
            plane.buffer.get(y * plane.rowStride + x * plane.pixelStride).toInt() and 0xff
        }
    }

    private fun rotate(point: Offset, degrees: Int) = when (degrees) {
        90 -> Offset(1f - point.y, point.x)
        180 -> Offset(1f - point.x, 1f - point.y)
        270 -> Offset(point.y, 1f - point.x)
        else -> point
    }

    private fun rotateVector(vector: Offset, degrees: Int) = when (degrees) {
        90 -> Offset(-vector.y, vector.x)
        180 -> Offset(-vector.x, -vector.y)
        270 -> Offset(vector.y, -vector.x)
        else -> vector
    }

    private fun directionLabel(vector: Offset, motion: Float): String {
        if (motion < .035f || maxOf(kotlin.math.abs(vector.x), kotlin.math.abs(vector.y)) < .006f) return "STILL"
        return if (kotlin.math.abs(vector.x) > kotlin.math.abs(vector.y)) if (vector.x > 0) "RIGHT" else "LEFT" else if (vector.y > 0) "DOWN" else "UP"
    }

    private fun updateFps() {
        frameCount++
        val now = System.currentTimeMillis()
        if (now - fpsWindow >= 1_000) { fps = frameCount; frameCount = 0; fpsWindow = now }
    }

    fun close() { objectDetector.close(); poseDetector.close() }
}
