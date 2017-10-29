package me.thanel.htmltextlayout.span

import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.LineBackgroundSpan

internal class HorizontalLineSpan(
        private val height: Float,
        private val color: Int
) : LineBackgroundSpan {
    override fun drawBackground(c: Canvas, p: Paint, left: Int, right: Int, top: Int, baseline: Int,
            bottom: Int, text: CharSequence, start: Int, end: Int, lnum: Int) {
        val paintColor = p.color
        val centerY = ((top + bottom) / 2).toFloat()
        p.color = color
        c.drawRect(left.toFloat(), centerY - height / 2, right.toFloat(), centerY + height / 2, p)
        p.color = paintColor
    }
}
