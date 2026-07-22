package com.example.calories.util

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager

/**
 * Hides the soft keyboard for this [View]'s window token.
 * Safe to call when the view is detached or the IME is already hidden.
 */
fun View.hideSoftKeyboard() {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        ?: return
    imm.hideSoftInputFromWindow(windowToken, 0)
}
