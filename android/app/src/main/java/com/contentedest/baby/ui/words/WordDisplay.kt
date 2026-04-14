package com.contentedest.baby.ui.words

import java.util.Locale

internal fun String.displayWordTitleCase(): String {
    val t = trim()
    if (t.isEmpty()) return t
    return t.replaceFirstChar { it.titlecase(Locale.getDefault()) }
}
