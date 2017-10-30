package me.thanel.htmltextlayout.block

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

internal object HtmlLayoutBlockHandler {
    fun parseHtmlLayoutBlocks(htmlText: String): List<HtmlLayoutBlock> {
        val body = Jsoup.parse(htmlText).body()
        val textBlocks = mutableListOf<HtmlLayoutBlock>()

        var blockText = ""
        for (child in body.children()) {
            if (!isBlock(child)) {
                blockText += child.outerHtml()
                continue
            }

            if (blockText.isNotEmpty()) {
                textBlocks.add(HtmlLayoutBlock.RegularLayoutBlock(blockText))
                blockText = ""
            }

            when (child.tagName().toLowerCase()) {
                "table" -> textBlocks.add(HtmlLayoutBlock.TableLayoutBlock(child.outerHtml()))
                "pre" -> {
                    val outerHtml = fixNewLines(child.outerHtml())
                    textBlocks.add(HtmlLayoutBlock.PreformattedLayoutBlock(outerHtml))
                }
                else -> {
                    val outerHtml = fixNewLines(child.outerHtml())
                    textBlocks.add(HtmlLayoutBlock.PreformattedLayoutBlock(outerHtml))
                }
            }
        }

        if (blockText.isNotEmpty()) {
            textBlocks.add(HtmlLayoutBlock.RegularLayoutBlock(blockText))
        }

        return textBlocks
    }

    /**
     * Replaces new line characters with HTML break tag to fix new line issues
     * in preformatted text blocks.
     */
    private fun fixNewLines(text: String) = text.replace("\n", "<br/>")

    /**
     * Tells whether the element should be extracted as a separate layout block.
     */
    private fun isBlock(element: Element) = when (element.tagName().toLowerCase()) {
        "table" -> true
        "pre" -> true
        "div" -> {
            val children = element.children()
            children.size == 1 && children[0].tagName().toLowerCase() == "pre"
        }
        else -> false
    }
}
