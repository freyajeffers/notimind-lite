package com.jeffers.notimindlite.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

fun highlightSearchText(text: String, query: String): AnnotatedString {
    val tokens = query.trim().split(Regex("\\s+"))
        .map { it.trim().trim { ch -> !ch.isLetterOrDigit() } }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }
    if (tokens.isEmpty()) return AnnotatedString(text)
    val ranges = tokens.flatMap { token ->
        Regex(Regex.escape(token), RegexOption.IGNORE_CASE)
            .findAll(text).map { it.range }.toList()
    }.sortedWith(compareBy({ it.first }, { it.last }))
    val merged: List<IntRange> = buildList {
        for (range in ranges) {
            if (isEmpty() || range.first > this[lastIndex].last + 1) add(range)
            else if (range.last > this[lastIndex].last) set(lastIndex, this[lastIndex].first..range.last)
        }
    }
    return buildAnnotatedString {
        var cursor = 0
        merged.forEach { range ->
            if (cursor < range.first) append(text.substring(cursor, range.first))
            withStyle(SpanStyle(background = Color(0xFFFFD54F), color = Color.Black)) {
                append(text.substring(range.first, range.last + 1))
            }
            cursor = range.last + 1
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
}
