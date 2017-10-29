package me.thanel.htmltextlayout.html

import android.content.Context
import android.text.Spanned
import org.ccil.cowan.tagsoup.HTMLSchema
import org.ccil.cowan.tagsoup.Parser
import org.xml.sax.SAXNotRecognizedException
import org.xml.sax.SAXNotSupportedException

/**
 * A copy of the framework's HTML class, stripped down and extended for our use cases.
 */
object Html {
    private val schema by lazy { HTMLSchema() }

    fun fromHtml(context: Context, source: String,
            imageGetter: android.text.Html.ImageGetter?): Spanned {
        val parser = Parser()
        try {
            parser.setProperty(Parser.schemaProperty, schema)
        } catch (e: SAXNotRecognizedException) {
            // Should not happen.
            throw RuntimeException(e)
        } catch (e: SAXNotSupportedException) {
            throw RuntimeException(e)
        }

        val converter = HtmlToSpannedConverter(context, source,
                imageGetter, parser)
        return converter.convert()
    }
}

