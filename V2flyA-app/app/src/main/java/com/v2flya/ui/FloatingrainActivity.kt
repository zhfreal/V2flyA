package com.v2flya.ui

import android.os.IBinder
import android.view.MenuItem
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

/**
 * @author floatingrain
 * 该类用于完善Activity应有的现代化功能，提升用户体验
 */
abstract class FloatingrainActivity : AppCompatActivity() {
    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        android.R.id.home -> {
            onBackPressed()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    /**
     * 点击空白处隐藏键盘。
     * 该方法不应该被主动调用。
     */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN && currentFocus != null && currentFocus!!.windowToken != null) {
            val v = currentFocus
            val shoudHideKeyboard = when {
                v != null && v is EditText -> {
                    val l = intArrayOf(0, 0)
                    v.getLocationOnScreen(l)
                    val left = l[0]
                    val top = l[1]
                    val bottom = top + v.getHeight()
                    val right = left + v.getWidth()
                    !(event.rawX > left && event.rawX < right && event.rawY > top
                            && event.rawY <
                            bottom)
                }
                else -> false
            }
            if (shoudHideKeyboard) hideKeyboard(v?.windowToken)
        }
        return super.dispatchTouchEvent(event)
    }


    /**
     * 获取InputMethodManager，隐藏软键盘。
     */
    open fun hideKeyboard(token: IBinder?) {
        if (token != null) {
            val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(token, InputMethodManager.HIDE_NOT_ALWAYS)
        }
    }
}