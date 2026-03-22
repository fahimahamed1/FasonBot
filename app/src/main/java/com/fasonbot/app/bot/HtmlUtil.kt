package com.fasonbot.app.bot

object HtmlUtil {
    fun escape(input: String): String = input
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    fun escapeCode(input: String): String = "<code>${escape(input)}</code>"

    fun bold(input: String): String = "<b>${escape(input)}</b>"

    fun italic(input: String): String = "<i>${escape(input)}</i>"
}
