package com.example.calories.ui.common

import android.graphics.Rect
import android.view.MotionEvent
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import com.example.calories.ads.FullscreenAdWindowHelper
import com.example.calories.util.hideSoftKeyboard

/**
 * Activity base that dismisses the soft keyboard when the user taps outside a
 * focused [EditText] or [SearchView].
 */
open class BaseActivity : AppCompatActivity() {

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_DOWN) {
            val focused = currentFocus
            if (focused is EditText || focused is SearchView) {
                val hitRect = Rect()
                focused.getGlobalVisibleRect(hitRect)
                if (!hitRect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    focused.clearFocus()
                    focused.hideSoftKeyboard()
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onDestroy() {
        FullscreenAdWindowHelper.exitFullscreenAdMode(this)
        super.onDestroy()
    }
}
