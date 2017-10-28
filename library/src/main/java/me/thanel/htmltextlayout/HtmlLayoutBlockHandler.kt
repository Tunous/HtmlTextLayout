package me.thanel.htmltextlayout

import org.jsoup.Jsoup

object HtmlLayoutBlockHandler {
    fun parseHtmlLayoutBlocks(htmlText: String): List<HtmlLayoutBlock> {
        val body = Jsoup.parse(htmlText).body()
        val textBlocks = mutableListOf<HtmlLayoutBlock>()

        var blockText = ""
        for (child in body.children()) {
            if (child.tagName() != "table" && child.tagName() != "pre") {
                blockText += child.outerHtml()
                continue
            }

            if (blockText.isNotEmpty()) {
                textBlocks.add(
                        HtmlLayoutBlock.RegularLayoutBlock(blockText))
                blockText = ""
            }

            if (child.tagName() == "table") {
                textBlocks.add(HtmlLayoutBlock.TableLayoutBlock(
                        child.outerHtml()))
            } else {
                // Replace new line characters with HTML break tag to fix new line issues in
                // preformatted text blocks
                val outerHtml = child.outerHtml().replace("\n", "<br/>")
                textBlocks.add(
                        HtmlLayoutBlock.PreformattedLayoutBlock(outerHtml))
            }
        }

        if (blockText.isNotEmpty()) {
            textBlocks.add(HtmlLayoutBlock.RegularLayoutBlock(blockText))
        }

        return textBlocks
    }
}
