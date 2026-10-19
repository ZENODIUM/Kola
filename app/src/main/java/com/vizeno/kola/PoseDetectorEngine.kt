package com.vizeno.kola

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.util.concurrent.atomic.AtomicBoolean

/** Throttled bundled pose model. Only 13 useful body anchors are exposed to keep the overlay readable. */
class AsyncPoseDetector(context: Context) : AutoCloseable {
    private val detector = PoseDetection.getClient(
        PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build()
    )
    private val pending = AtomicBoolean(false)
    @Volatile private var latest = emptyList<PoseJoint>()
    @Volatile private var lastRunAt = 0L
    private val selected = intArrayOf(
        PoseLandmark.NOSE,
        PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER,
        PoseLandmark.LEFT_ELBOW, PoseLandmark.RIGHT_ELBOW,
        PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST,
        PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP,
        PoseLandmark.LEFT_KNEE, PoseLandmark.RIGHT_KNEE,
        PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE,
    )

    fun isDue(now: Long = System.currentTimeMillis()) = !pending.get() && now - lastRunAt >= 220L
    fun current() = latest

    fun submit(bitmap: Bitmap) {
        if (!isDue() || !pending.compareAndSet(false, true)) { bitmap.recycle(); return }
        val width = bitmap.width.toFloat(); val height = bitmap.height.toFloat()
        detector.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { pose ->
                latest = selected.toList().mapNotNull { id ->
                    pose.getPoseLandmark(id)?.takeIf { it.inFrameLikelihood >= .45f }?.let { landmark ->
                        PoseJoint(id, Offset(
                            (landmark.position.x / width).coerceIn(0f, 1f),
                            (landmark.position.y / height).coerceIn(0f, 1f),
                        ), landmark.inFrameLikelihood)
                    }
                }
            }
            .addOnFailureListener { latest = emptyList() }
            .addOnCompleteListener {
                lastRunAt = System.currentTimeMillis()
                pending.set(false)
                bitmap.recycle()
            }
    }

    override fun close() = detector.close()
}
