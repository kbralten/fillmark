package com.fillmark.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

object WidgetProgressBarGenerator {

    /**
     * Creates a custom progress bar Bitmap with an optional on-track pacing marker dot
     * and overflow color reflection for > 100% consumption.
     */
    fun createProgressBarBitmap(
        context: Context,
        progress: Float,
        paceRatio: Float?,
        widthDp: Int = 280,
        heightDp: Int = 12
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val widthPx = (widthDp * density).toInt().coerceAtLeast(100)
        val heightPx = (heightDp * density).toInt().coerceAtLeast(10)
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val barHeight = heightPx * 0.7f
        val barTop = (heightPx - barHeight) / 2f
        val cornerRadius = barHeight / 2f

        // 1. Background Track
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#33808080")
        }
        canvas.drawRoundRect(
            RectF(0f, barTop, widthPx.toFloat(), barTop + barHeight),
            cornerRadius,
            cornerRadius,
            trackPaint
        )

        // 2. Base Progress Fill (up to 100%)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#2196F3")
        }
        val baseFillWidth = (widthPx * progress.coerceIn(0f, 1f)).coerceAtLeast(0f)
        if (baseFillWidth > 0) {
            canvas.drawRoundRect(
                RectF(0f, barTop, baseFillWidth, barTop + barHeight),
                cornerRadius,
                cornerRadius,
                fillPaint
            )
        }

        // 3. Overflow Fill for > 100% consumption
        if (progress > 1f) {
            val overflowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.parseColor("#00E5FF")
            }
            val overflowWidth = (widthPx * (progress - 1f).coerceIn(0f, 1f)).coerceAtLeast(0f)
            if (overflowWidth > 0) {
                canvas.drawRoundRect(
                    RectF(0f, barTop, overflowWidth, barTop + barHeight),
                    cornerRadius,
                    cornerRadius,
                    overflowPaint
                )
            }
        }

        // 4. "On track" expected pace marker dot
        if (paceRatio != null) {
            val dotX = (widthPx * paceRatio.coerceIn(0.04f, 0.96f))
            val dotCenterY = heightPx / 2f
            val dotRadius = heightPx * 0.44f

            // Outer ring
            val outerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.parseColor("#FF9800")
            }
            canvas.drawCircle(dotX, dotCenterY, dotRadius, outerDotPaint)

            // Inner white dot
            val innerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
            }
            canvas.drawCircle(dotX, dotCenterY, dotRadius * 0.48f, innerDotPaint)
        }

        return bitmap
    }
}
