package me.thanel.htmltextlayout.span

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Paint.Style.FILL
import android.text.Layout
import android.text.style.LeadingMarginSpan

internal class ReplySpan(
        private val margin: Int,
        private val size: Int,
        private val color: Int
) : LeadingMarginSpan {
    override fun getLeadingMargin(first: Boolean) = margin

    override fun drawLeadingMargin(c: Canvas, p: Paint, x: Int, dir: Int, top: Int, baseline: Int,
            bottom: Int, text: CharSequence, start: Int, end: Int, first: Boolean, layout: Layout) {
        val style = p.style
        val color = p.color

        p.style = FILL
        p.color = this.color

        c.drawRect(x.toFloat(), top.toFloat(), (x + dir * size).toFloat(), bottom.toFloat(), p)

        p.style = style
        p.color = color
    }
}
