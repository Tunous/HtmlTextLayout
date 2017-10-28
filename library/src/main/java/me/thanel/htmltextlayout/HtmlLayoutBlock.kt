package me.thanel.htmltextlayout

sealed class HtmlLayoutBlock(val text: String) {
    class RegularLayoutBlock(text: String) : HtmlLayoutBlock(text)

    class TableLayoutBlock(text: String) : HtmlLayoutBlock(text)

    class PreformattedLayoutBlock(text: String) : HtmlLayoutBlock(text)
}