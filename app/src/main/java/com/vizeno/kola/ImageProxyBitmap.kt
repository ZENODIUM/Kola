package com.vizeno.kola

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

/** Converts CameraX YUV frames only when the throttled object detector needs one. */
fun ImageProxy.toDetectorBitmap(): Bitmap? {
    if (format != ImageFormat.YUV_420_888 || planes.size < 3) return null
    val nv21 = ByteArray(width * height * 3 / 2)
    copyPlane(planes[0], width, height, nv21, 0, 1)
    // NV21 chroma is interleaved V,U.
    copyPlane(planes[2], width / 2, height / 2, nv21, width * height, 2)
    copyPlane(planes[1], width / 2, height / 2, nv21, width * height + 1, 2)
    return ByteArrayOutputStream().use { output ->
        if (!YuvImage(nv21, ImageFormat.NV21, width, height, null).compressToJpeg(Rect(0, 0, width, height), 72, output)) return null
        val bytes = output.toByteArray()
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}

private fun copyPlane(plane: ImageProxy.PlaneProxy, planeWidth: Int, planeHeight: Int, output: ByteArray, offset: Int, outputStride: Int) {
    val buffer = plane.buffer.duplicate()
    var destination = offset
    for (row in 0 until planeHeight) {
        val rowStart = row * plane.rowStride
        for (column in 0 until planeWidth) {
            val index = rowStart + column * plane.pixelStride
            if (index < buffer.limit() && destination < output.size) output[destination] = buffer.get(index)
            destination += outputStride
        }
    }
}
