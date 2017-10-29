package me.thanel.htmltextlayout.html

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.support.v4.content.ContextCompat
import android.text.Editable
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.AlignmentSpan
import android.text.style.BackgroundColorSpan
import android.text.style.BulletSpan
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.text.style.LeadingMarginSpan
import android.text.style.ParagraphStyle
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import me.thanel.htmltextlayout.R
import me.thanel.htmltextlayout.span.HorizontalLineSpan
import me.thanel.htmltextlayout.span.ReplySpan
import org.xml.sax.Attributes
import org.xml.sax.ContentHandler
import org.xml.sax.InputSource
import org.xml.sax.Locator
import org.xml.sax.SAXException
import org.xml.sax.XMLReader
import java.io.IOException
import java.io.StringReader
import java.util.regex.Pattern

internal class HtmlToSpannedConverter(
        private val context: Context,
        private val source: String,
        private val imageGetter: android.text.Html.ImageGetter?,
        private val parser: XMLReader
) : ContentHandler {
    private val dividerHeight = context.resources.getDimension(R.dimen.divider_span_height)
    private val bulletMargin = context.resources.getDimensionPixelSize(R.dimen.bullet_span_margin)
    private val replyMargin = context.resources.getDimensionPixelSize(R.dimen.reply_span_margin)
    private val replyMarkerSize = context.resources.getDimensionPixelSize(R.dimen.reply_span_size)
    private val spannableStringBuilder = SpannableStringBuilder()

    fun convert(): Spanned {
        parser.contentHandler = this

        try {
            parser.parse(InputSource(StringReader(source)))
        } catch (e: IOException) {
            // We are reading from a string. There should not be IO problems.
            throw RuntimeException(e)
        } catch (e: SAXException) {
            // TagSoup doesn't throw parse exceptions.
            throw RuntimeException(e)
        }

        // Replace the placeholders for leading margin spans in reverse order, so the leading
        // margins are drawn in order of tag start
        val needsReversingSpans = spannableStringBuilder.getSpans(0,
                spannableStringBuilder.length, NeedsReversingSpan::class.java)
        for (i in needsReversingSpans.indices.reversed()) {
            val span = needsReversingSpans[i] as NeedsReversingSpan
            val start = spannableStringBuilder.getSpanStart(span)
            val end = spannableStringBuilder.getSpanEnd(span)

            spannableStringBuilder.removeSpan(span)
            spannableStringBuilder.setSpan(span.mActualSpan, start, end,
                    Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
        }

        // Fix flags and range for paragraph-type markup.
        val paragraphStyleSpans = spannableStringBuilder.getSpans<ParagraphStyle>(0,
                spannableStringBuilder.length, ParagraphStyle::class.java)
        for (span in paragraphStyleSpans) {
            val start = spannableStringBuilder.getSpanStart(span)
            var end = spannableStringBuilder.getSpanEnd(span)

            // If the last line of the range is blank, back off by one.
            if (end - 2 >= 0 && end - start >= 2) {
                if (spannableStringBuilder[end - 1] == '\n' && spannableStringBuilder[end - 2] == '\n') {
                    end--
                }
            }

            if (end == start) {
                spannableStringBuilder.removeSpan(span)
            } else {
                spannableStringBuilder.setSpan(span, start, end, Spannable.SPAN_PARAGRAPH)
            }
        }

        // Remove leading newlines
        while (spannableStringBuilder.isNotEmpty() && spannableStringBuilder[0] == '\n') {
            spannableStringBuilder.delete(0, 1)
        }

        // Remove trailing newlines
        var last = spannableStringBuilder.length - 1
        while (last >= 0 && spannableStringBuilder[last] == '\n') {
            spannableStringBuilder.delete(last, last + 1)
            last = spannableStringBuilder.length - 1
        }

        return spannableStringBuilder
    }

    private fun handleStartTag(tag: String, attributes: Attributes) {
        when (tag.toLowerCase()) {
            "br" -> {
                // We don't need to handle this. TagSoup will ensure that there's a </br> for each
                // <br> so we can safely emit the line-breaks when we handle the close tag.
            }
            "p" -> {
                startBlockElement(spannableStringBuilder, attributes)
                startCssStyle(spannableStringBuilder, attributes)
            }
            "ul" -> {
                startBlockElement(spannableStringBuilder, attributes)
                start(spannableStringBuilder, List())
            }
            "ol" -> {
                startBlockElement(spannableStringBuilder, attributes)
                start(spannableStringBuilder, List(parseIntAttribute(attributes, "start", 1)))
            }
            "li" -> startLi(spannableStringBuilder, attributes)
            "input" -> if ("checkbox".equals(attributes.getValue("", "type"), ignoreCase = true)) {
                val drawableResId = if (attributes.getIndex("", "checked") >= 0) {
                    R.drawable.checkbox_checked_small
                } else {
                    R.drawable.checkbox_unchecked_small
                }
                val d = ContextCompat.getDrawable(context, drawableResId)
                // UiUtils.resolveDrawable(context, drawableAttrResId));
                d.setBounds(0, 0, d.intrinsicWidth, d.intrinsicHeight)
                val span = ImageSpan(d, ImageSpan.ALIGN_BOTTOM)

                spannableStringBuilder.append("  ")
                spannableStringBuilder.setSpan(span, spannableStringBuilder.length - 2,
                        spannableStringBuilder.length - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            "div" -> startBlockElement(spannableStringBuilder, attributes)
            "span" -> startCssStyle(spannableStringBuilder, attributes)
            "hr" -> {
                val span = HorizontalLineSpan(dividerHeight, 0x60aaaaaa)
                // enforce the following newlines to be written
                spannableStringBuilder.append(' ')
                appendNewlines(spannableStringBuilder, 2)
                val len = spannableStringBuilder.length
                spannableStringBuilder.setSpan(span, len - 1, len,
                        Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
            }
            "strong" -> start(spannableStringBuilder, Bold())
            "b" -> start(spannableStringBuilder, Bold())
            "em" -> start(spannableStringBuilder, Italic())
            "cite" -> start(spannableStringBuilder, Italic())
            "dfn" -> start(spannableStringBuilder, Italic())
            "i" -> start(spannableStringBuilder, Italic())
            "big" -> start(spannableStringBuilder, Big())
            "small" -> start(spannableStringBuilder, Small())
            "font" -> startFont(spannableStringBuilder, attributes)
            "blockquote" -> startBlockquote(spannableStringBuilder, attributes)
            "tt" -> start(spannableStringBuilder, Monospace())
            "a" -> startA(spannableStringBuilder, attributes)
            "u" -> start(spannableStringBuilder, Underline())
            "del" -> start(spannableStringBuilder, Strikethrough())
            "s" -> start(spannableStringBuilder, Strikethrough())
            "strike" -> start(spannableStringBuilder, Strikethrough())
            "sup" -> start(spannableStringBuilder, Super())
            "sub" -> start(spannableStringBuilder, Sub())
            "img" -> startImg(spannableStringBuilder, attributes, imageGetter)
        }

        if (tag.length == 2 &&
                Character.toLowerCase(tag[0]) == 'h' &&
                tag[1] >= '1' && tag[1] <= '6') {
            startHeading(spannableStringBuilder, attributes, tag[1] - '1')
        }
    }

    private fun handleEndTag(tag: String) {
        when (tag.toLowerCase()) {
            "br" -> handleBr(spannableStringBuilder)
            "p" -> {
                endCssStyle(spannableStringBuilder)
                endBlockElement(spannableStringBuilder)
            }
            "ul" -> {
                endBlockElement(spannableStringBuilder)
                end(spannableStringBuilder, List::class.java, null)
            }
            "ol" -> {
                endBlockElement(spannableStringBuilder)
                end(spannableStringBuilder, List::class.java, null)
            }
            "li" -> endLi(spannableStringBuilder)
            "div" -> endBlockElement(spannableStringBuilder)
            "span" -> endCssStyle(spannableStringBuilder)
            "strong" -> end(spannableStringBuilder, Bold::class.java, StyleSpan(Typeface.BOLD))
            "b" -> end(spannableStringBuilder, Bold::class.java, StyleSpan(Typeface.BOLD))
            "em" -> end(spannableStringBuilder, Italic::class.java, StyleSpan(Typeface.ITALIC))
            "cite" -> end(spannableStringBuilder, Italic::class.java, StyleSpan(Typeface.ITALIC))
            "dfn" -> end(spannableStringBuilder, Italic::class.java, StyleSpan(Typeface.ITALIC))
            "i" -> end(spannableStringBuilder, Italic::class.java, StyleSpan(Typeface.ITALIC))
            "big" -> end(spannableStringBuilder, Big::class.java, RelativeSizeSpan(1.25f))
            "small" -> end(spannableStringBuilder, Small::class.java, RelativeSizeSpan(0.8f))
            "font" -> endFont(spannableStringBuilder)
            "blockquote" -> endBlockquote(spannableStringBuilder)
            "tt" -> end(spannableStringBuilder, Monospace::class.java, TypefaceSpan("monospace"))
            "a" -> endA(spannableStringBuilder)
            "u" -> end(spannableStringBuilder, Underline::class.java, UnderlineSpan())
            "del" -> end(spannableStringBuilder, Strikethrough::class.java, StrikethroughSpan())
            "s" -> end(spannableStringBuilder, Strikethrough::class.java, StrikethroughSpan())
            "strike" -> end(spannableStringBuilder, Strikethrough::class.java, StrikethroughSpan())
            "sup" -> end(spannableStringBuilder, Super::class.java, SuperscriptSpan())
            "sub" -> end(spannableStringBuilder, Sub::class.java, SubscriptSpan())
            "code" -> {
                val code = getLast(spannableStringBuilder, Code::class.java)
                if (code != null) {
                    val backgroundSpan = BackgroundColorSpan(0x30aaaaaa)
                    setSpanFromMark(spannableStringBuilder, code,
                            TypefaceSpan("monospace"), backgroundSpan)
                }
            }
        }

        if (tag.length == 2 &&
                Character.toLowerCase(tag[0]) == 'h' &&
                tag[1] >= '1' && tag[1] <= '6') {
            endHeading(spannableStringBuilder)
        }
    }

    private fun startLi(text: Editable, attributes: Attributes) {
        val item = ListItem(getLast(text, List::class.java), attributes)
        startBlockElement(text, attributes, 1)
        start(text, item)
        if (item.mOrdered) {
            text.insert(text.length, "" + item.mPosition + ". ")
        }
        startCssStyle(text, attributes)
    }

    private fun endLi(text: Editable) {
        endCssStyle(text)
        endBlockElement(text)
        val item = getLast(text, ListItem::class.java)
        if (item != null) {
            if (item.mOrdered) {
                text.removeSpan(item)
            } else {
                setSpanFromMark(text, item, BulletSpan(bulletMargin))
            }
        }
    }

    private fun startBlockquote(text: Editable, attributes: Attributes) {
        startBlockElement(text, attributes)
        start(text, Blockquote())
    }

    private fun endBlockquote(text: Editable) {
        endBlockElement(text)
        end(text, Blockquote::class.java, ReplySpan(replyMargin, replyMarkerSize, -0x222223))
    }

    private fun startHeading(text: Editable, attributes: Attributes, level: Int) {
        startBlockElement(text, attributes)
        start(text, Heading(level))
    }

    private fun startCssStyle(text: Editable, attributes: Attributes) {
        val style = attributes.getValue("", "style")
        if (style != null) {
            var m = foregroundColorPattern.matcher(style)
            if (m.find()) {
                val c = parseColor(m.group(1))
                if (c != null) {
                    start(text, Foreground(c))
                }
            }

            m = backgroundColorPattern.matcher(style)
            if (m.find()) {
                val c = parseColor(m.group(1))
                if (c != null) {
                    start(text, Background(c))
                }
            }

            m = textDecorationPattern.matcher(style)
            if (m.find()) {
                val textDecoration = m.group(1)
                if (textDecoration.equals("line-through", ignoreCase = true)) {
                    start(text, Strikethrough())
                }
            }
        }
    }

    private fun startFont(text: Editable, attributes: Attributes) {
        val color = attributes.getValue("", "color")
        val face = attributes.getValue("", "face")

        if (!TextUtils.isEmpty(color)) {
            val c = parseColor(color)
            if (c != null) {
                start(text, Foreground(c))
            }
        }

        if (!TextUtils.isEmpty(face)) {
            start(text, Font(face))
        }
    }

    override fun setDocumentLocator(locator: Locator) {}

    @Throws(SAXException::class)
    override fun startDocument() {
    }

    @Throws(SAXException::class)
    override fun endDocument() {
    }

    @Throws(SAXException::class)
    override fun startPrefixMapping(prefix: String, uri: String) {
    }

    @Throws(SAXException::class)
    override fun endPrefixMapping(prefix: String) {
    }

    @Throws(SAXException::class)
    override fun startElement(uri: String, localName: String, qName: String,
            attributes: Attributes) {
        handleStartTag(localName, attributes)
    }

    @Throws(SAXException::class)
    override fun endElement(uri: String, localName: String, qName: String) {
        handleEndTag(localName)
    }

    @Throws(SAXException::class)
    override fun characters(ch: CharArray, start: Int, length: Int) {
        val sb = StringBuilder()
        // Ignore whitespace that immediately follows other whitespace,
        // newlines count as spaces.
        for (i in 0 until length) {
            val c = ch[i + start]

            if (c == ' ' || c == '\n') {
                val pred: Char
                var len = sb.length

                if (len == 0) {
                    len = spannableStringBuilder.length

                    pred = if (len == 0) {
                        '\n'
                    } else {
                        spannableStringBuilder[len - 1]
                    }
                } else {
                    pred = sb[len - 1]
                }

                if (pred != ' ' && pred != '\n') {
                    sb.append(' ')
                }
            } else {
                sb.append(c)
            }
        }

        spannableStringBuilder.append(sb)
    }

    @Throws(SAXException::class)
    override fun ignorableWhitespace(ch: CharArray, start: Int, length: Int) {
    }

    @Throws(SAXException::class)
    override fun processingInstruction(target: String, data: String) {
    }

    @Throws(SAXException::class)
    override fun skippedEntity(name: String) {
    }

    private class Bold
    private class Italic
    private class Underline
    private class Strikethrough
    private class Big
    private class Small
    private class Monospace
    private class Blockquote
    private class Super
    private class Sub

    private class NeedsReversingSpan(val mActualSpan: Any)

    private class Code

    private class List {
        val mOrdered: Boolean
        var mPosition = 0

        constructor() {
            mOrdered = false
        }

        constructor(position: Int) {
            mOrdered = true
            mPosition = position
        }
    }

    private class ListItem(list: List?, attrs: Attributes) {
        val mOrdered: Boolean = list != null && list.mOrdered
        val mPosition: Int

        init {
            var position = list?.mPosition ?: -1
            if (mOrdered) {
                position = parseIntAttribute(attrs, "value", position)
            }
            mPosition = position
            if (list != null) {
                list.mPosition = position + 1
            }
        }
    }

    private class Font(val mFace: String)

    private class Href(val mHref: String?)

    private class Foreground(val mForegroundColor: Int)

    private class Background(val mBackgroundColor: Int)

    private class Heading(val mLevel: Int)

    private class Newline(val mNumNewlines: Int)

    private class Alignment(val mAlignment: Layout.Alignment)

    companion object {
        private val HEADING_SIZES = floatArrayOf(1.5f, 1.4f, 1.3f, 1.2f, 1.1f, 1f)

        private var sTextAlignPattern: Pattern? = null
        private var sForegroundColorPattern: Pattern? = null
        private var sBackgroundColorPattern: Pattern? = null
        private var sTextDecorationPattern: Pattern? = null

        private val textAlignPattern: Pattern
            get() {
                if (sTextAlignPattern == null) {
                    sTextAlignPattern = Pattern.compile("(?:\\s+|\\A)text-align\\s*:\\s*(\\S*)\\b")
                }
                return sTextAlignPattern!!
            }

        private val foregroundColorPattern: Pattern
            get() {
                if (sForegroundColorPattern == null) {
                    sForegroundColorPattern = Pattern.compile(
                            "(?:\\s+|\\A)color\\s*:\\s*(\\S*)\\b")
                }
                return sForegroundColorPattern!!
            }

        private val backgroundColorPattern: Pattern
            get() {
                if (sBackgroundColorPattern == null) {
                    sBackgroundColorPattern = Pattern.compile(
                            "(?:\\s+|\\A)background(?:-color)?\\s*:\\s*(\\S*)\\b")
                }
                return sBackgroundColorPattern!!
            }

        private val textDecorationPattern: Pattern
            get() {
                if (sTextDecorationPattern == null) {
                    sTextDecorationPattern = Pattern.compile(
                            "(?:\\s+|\\A)text-decoration\\s*:\\s*(\\S*)\\b")
                }
                return sTextDecorationPattern!!
            }

        private fun appendNewlines(text: Editable, minNewline: Int) {
            val len = text.length

            if (len == 0) {
                return
            }

            var existingNewlines = 0
            var i = len - 1
            while (i >= 0 && text[i] == '\n') {
                existingNewlines++
                i--
            }

            for (j in existingNewlines until minNewline) {
                text.append("\n")
            }
        }

        private fun startBlockElement(text: Editable, attributes: Attributes, newlines: Int = 2) {
            appendNewlines(text, newlines)
            start(text, Newline(newlines))

            val style = attributes.getValue("", "style")
            if (style != null) {
                val m = textAlignPattern.matcher(style)
                if (m.find()) {
                    val alignment = m.group(1)
                    when (alignment.toLowerCase()) {
                        "start" -> start(text, Alignment(Layout.Alignment.ALIGN_NORMAL))
                        "center" -> start(text, Alignment(Layout.Alignment.ALIGN_CENTER))
                        "end" -> start(text, Alignment(Layout.Alignment.ALIGN_OPPOSITE))
                    }
                }
            }
        }

        private fun endBlockElement(text: Editable) {
            val n = getLast(text, Newline::class.java)
            if (n != null) {
                appendNewlines(text, n.mNumNewlines)
                text.removeSpan(n)
            }

            val a = getLast(text, Alignment::class.java)
            if (a != null) {
                setSpanFromMark(text, a, AlignmentSpan.Standard(a.mAlignment))
            }
        }

        private fun handleBr(text: Editable) {
            text.append('\n')
        }

        private fun endHeading(text: Editable) {
            // RelativeSizeSpan and StyleSpan are CharacterStyles
            // Their ranges should not include the newlines at the end
            val h = getLast(text, Heading::class.java)
            if (h != null) {
                setSpanFromMark(text, h, RelativeSizeSpan(HEADING_SIZES[h.mLevel]),
                        StyleSpan(Typeface.BOLD))
            }

            endBlockElement(text)
        }

        private fun <T> getLast(text: Spanned, kind: Class<T>): T? {
            /*
         * This knows that the last returned object from getSpans()
         * will be the most recently added.
         */
            val objs = text.getSpans(0, text.length, kind)

            return if (objs.isEmpty()) {
                null
            } else {
                objs[objs.size - 1]
            }
        }

        private fun setSpanFromMark(text: Spannable, mark: Any, vararg spans: Any?) {
            val where = text.getSpanStart(mark)
            text.removeSpan(mark)
            val len = text.length
            if (where != len) {
                for (span in spans) {
                    val resultSpan = if (span is LeadingMarginSpan) {
                        NeedsReversingSpan(span)
                    } else {
                        span
                    }
                    text.setSpan(resultSpan, where, len, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }

        private fun start(text: Editable, mark: Any) {
            val len = text.length
            text.setSpan(mark, len, len, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
        }

        private fun end(text: Editable, kind: Class<*>, repl: Any?) {
            val obj = getLast(text, kind)
            if (obj != null) {
                setSpanFromMark(text, obj, repl)
            }
        }

        private fun endCssStyle(text: Editable) {
            val s = getLast(text, Strikethrough::class.java)
            if (s != null) {
                setSpanFromMark(text, s, StrikethroughSpan())
            }

            val b = getLast(text, Background::class.java)
            if (b != null) {
                setSpanFromMark(text, b, BackgroundColorSpan(b.mBackgroundColor))
            }

            val f = getLast(text, Foreground::class.java)
            if (f != null) {
                setSpanFromMark(text, f, ForegroundColorSpan(f.mForegroundColor))
            }
        }

        private fun parseColor(colorString: String?): Int? {
            if (colorString == null) {
                return null
            }
            return try {
                val color = Color.parseColor(colorString)
                color or -0x1000000
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        private fun startImg(text: Editable, attributes: Attributes,
                img: android.text.Html.ImageGetter?) {
            val src = attributes.getValue("", "src")
            if (img == null) {
                return
            }
            val d = img.getDrawable(src)

            val len = text.length
            text.append("\uFFFC")

            text.setSpan(ImageSpan(d, src), len, text.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        private fun endFont(text: Editable) {
            val font = getLast(text, Font::class.java)
            if (font != null) {
                setSpanFromMark(text, font, TypefaceSpan(font.mFace))
            }

            val foreground = getLast(text, Foreground::class.java)
            if (foreground != null) {
                setSpanFromMark(text, foreground,
                        ForegroundColorSpan(foreground.mForegroundColor))
            }
        }

        private fun startA(text: Editable, attributes: Attributes) {
            val href = attributes.getValue("", "href")
            start(text, Href(href))
        }

        private fun endA(text: Editable) {
            val h = getLast(text, Href::class.java)
            if (h != null) {
                if (h.mHref != null) {
                    setSpanFromMark(text, h, URLSpan(h.mHref))
                }
            }
        }

        private fun parseIntAttribute(attributes: Attributes, name: String,
                defaultValue: Int): Int {
            val value = attributes.getValue("", name)
            if (value != null) {
                try {
                    return Integer.parseInt(value)
                } catch (e: NumberFormatException) {
                    // fall through
                }
            }
            return defaultValue
        }
    }
}
