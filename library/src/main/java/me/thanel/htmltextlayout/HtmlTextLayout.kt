package me.thanel.htmltextlayout

import android.content.Context
import android.graphics.Typeface
import android.support.v4.content.ContextCompat
import android.support.v4.view.GravityCompat
import android.support.v4.view.ViewCompat
import android.support.v7.widget.AppCompatTextView
import android.support.v7.widget.LinearLayoutCompat
import android.util.AttributeSet
import android.view.Gravity
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import me.thanel.htmltextlayout.block.HtmlLayoutBlock
import me.thanel.htmltextlayout.block.HtmlLayoutBlockHandler
import me.thanel.htmltextlayout.html.Html
import org.jsoup.Jsoup

class HtmlTextLayout : LinearLayoutCompat {
    constructor(context: Context?) : this(context, null)
    constructor(context: Context?, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs,
            defStyleAttr) {
        orientation = VERTICAL
    }

    fun setHtmlText(htmlText: String) {
        removeAllViews()
        val textBlocks = HtmlLayoutBlockHandler.parseHtmlLayoutBlocks(htmlText)
        for (textBlock in textBlocks) {
            when (textBlock) {
                is HtmlLayoutBlock.RegularLayoutBlock -> {
                    addTextView(textBlock.text)
                }
                is HtmlLayoutBlock.PreformattedLayoutBlock -> {
                    addHorizontalScrollView {
                        isFillViewport = true
                        addTextView(textBlock.text) {
                            typeface = Typeface.MONOSPACE
                            val padding = resources.getDimensionPixelSize( R.dimen.table_column_padding)
                            setPaddingRelative(padding, padding, padding, padding)
                            setBackgroundColor(ContextCompat.getColor(context, R.color.block_background))
                        }
                    }
                }
                is HtmlLayoutBlock.TableLayoutBlock -> {
                    addHorizontalScrollView {
                        addTableLayout(textBlock.text)
                    }
                }
            }
        }
    }

    private fun ViewGroup.addTextView(htmlText: String, body: TextView.() -> Unit = {}) {
        val parsedText = Html.fromHtml(context, htmlText, null)
        val textView = AppCompatTextView(context).apply {
            text = parsedText
            setTextIsSelectable(true)
            body()
        }
        addView(textView)
    }

    private fun ViewGroup.addHorizontalScrollView(body: HorizontalScrollView.() -> Unit) {
        val scrollView = HorizontalScrollView(context).apply {
            body()
        }
        val layoutParams = LinearLayoutCompat.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            val verticalMargin = resources.getDimensionPixelSize(R.dimen.table_vertical_margin)
            setMargins(0, verticalMargin, 0, verticalMargin)
        }
        addView(scrollView, layoutParams)
    }

    private fun ViewGroup.addTableLayout(htmlText: String) {
        val tableLayout = TableLayout(context).apply {
            setBackgroundColor(ContextCompat.getColor(context, R.color.block_background))
            val padding = resources.getDimensionPixelSize(R.dimen.table_column_padding)
            ViewCompat.setPaddingRelative(this, padding, padding, padding, padding)
        }
        val htmlDocument = Jsoup.parse(htmlText)
        val htmlTableRows = htmlDocument.getElementsByTag("tr")
        val columnPadding = resources.getDimensionPixelSize(R.dimen.table_column_padding)

        for (htmlTableRow in htmlTableRows) {
            val tableRow = TableRow(context)
            for ((index, htmlTableColumn) in htmlTableRow.children().withIndex()) {
                tableRow.addTextView(htmlTableColumn.html()) {
                    val startPadding = if (index > 0) columnPadding else 0
                    val endPadding = if (index < htmlTableRows.size - 1) columnPadding else 0
                    setPaddingRelative(startPadding, 0, endPadding, 0)

                    val isHeader = htmlTableColumn.tagName().equals("th", ignoreCase = true)
                    if (isHeader) {
                        setTypeface(null, Typeface.BOLD)
                    }

                    gravity = when (htmlTableColumn.attr("align")) {
                        "center" -> Gravity.CENTER_HORIZONTAL
                        "right" -> GravityCompat.END
                        else -> if (isHeader) Gravity.CENTER_HORIZONTAL else GravityCompat.START
                    }
                }
            }

            tableLayout.addView(tableRow, TableLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        addView(tableLayout, LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT))
    }
}