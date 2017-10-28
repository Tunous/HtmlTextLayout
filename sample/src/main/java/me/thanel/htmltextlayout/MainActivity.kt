package me.thanel.htmltextlayout

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val rawResource = resources.openRawResource(R.raw.github_syntax)
        val reader = BufferedReader(InputStreamReader(rawResource))
        val rawText = reader.readText()

        val htmlTextLayout = findViewById<HtmlTextLayout>(R.id.html_text_layout)
        htmlTextLayout.setHtmlText(rawText)
    }
}
