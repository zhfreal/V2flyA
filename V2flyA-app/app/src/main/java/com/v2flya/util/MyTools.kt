package com.v2flya.util

import android.app.Activity
import android.content.Context
import android.graphics.Point
import android.util.DisplayMetrics
import android.util.Log
import java.util.regex.Pattern

/**
 * Created by floatingrain.
 */
class MyTools(var activity: Activity, //根据资源ID获取响应的尺寸值//获取status_bar_height资源的ID
              var context: Context) {
    //获取状态栏宽度pixel
    val statusBarSize: Int
        get() {
            var statusBarHeight = -1
            val resourceId = context.resources.getIdentifier("status_bar_height",
                    "dimen", "android") //获取status_bar_height资源的ID
            if (resourceId > 0) {
                //根据资源ID获取响应的尺寸值
                statusBarHeight = context.resources.getDimensionPixelSize(resourceId)
            }
            Log.d("MyTools", " 状态栏高度:" + px2dip(statusBarHeight.toFloat()) + "dp")
            return statusBarHeight
        }//根据资源ID获取响应的尺寸值//获取navigation_bar_height资源的ID

    //获取虚拟导航栏宽度
    val navigationBarSize: Int
        get() {
            var navigationBarHeight = -1
            val resourceId = context.resources.getIdentifier("navigation_bar_height",
                    "dimen", "android") //获取navigation_bar_height资源的ID
            if (resourceId > 0) {
                //根据资源ID获取响应的尺寸值
                navigationBarHeight = context.resources.getDimensionPixelSize(resourceId)
            }
            Log.d("MyTools", " 导航栏高度:" + px2dip(navigationBarHeight.toFloat()) + "dp")
            return navigationBarHeight
        }

    //判断字符串是否为浮点数（Double和Float）
    private fun isDouble(str: String?): Boolean {
        if (null == str || "" == str) {
            return false
        }
        val pattern = Pattern.compile("^[-\\+]?[.\\d]*$")
        return pattern.matcher(str).matches()
    }

    //判断字符串是否为整型
    private fun isInteger(str: String?): Boolean {
        if (null == str || "" == str) {
            return false
        }
        val pattern = Pattern.compile("^[-\\+]?[\\d]*$")
        return pattern.matcher(str).matches()
    }

    //判断字符串是否是数字
    fun isNum(str: String?): Boolean {
        return isInteger(str) || isDouble(str)
    }

    //获取屏幕分辨率（width，height）,更新使用最新方法
    private val resolution: IntArray
        get() {
            val display = activity.windowManager.defaultDisplay
            val point = Point()
            display.getRealSize(point)
            val mScreenH = point.y
            val mScreenW = point.x
            Log.d(TAG, "getResolution: " + "width:" + mScreenW + "height:" + mScreenH)
            return intArrayOf(mScreenH, mScreenW)
        }

    //查看系统当前是否已经显示虚拟导航按键
    val isVitrualButtonOpened: Boolean
        get() {
            val windowManager = activity.windowManager
            val d = windowManager.defaultDisplay
            val realDisplayMetrics = DisplayMetrics()
            d.getRealMetrics(realDisplayMetrics)
            val realHeight = realDisplayMetrics.heightPixels
            val realWidth = realDisplayMetrics.widthPixels
            val displayMetrics = DisplayMetrics()
            d.getMetrics(displayMetrics)
            val displayHeight = displayMetrics.heightPixels
            val displayWidth = displayMetrics.widthPixels
            val result = realWidth - displayWidth > 0 || realHeight - displayHeight > 0
            Log.d(TAG, "isVitrualButtonOpened: $result")
            return result
        }

    //计算PPI
    private fun calculatePpi(width: Int, height: Int, size: Double): Int {
        val ppi = Math.sqrt(Math.pow(width.toDouble(), 2.0) + Math.pow(height.toDouble(), 2.0)) / size
        return if (ppi - ppi.toInt() >= 0.5) {
            Log.d(TAG, "calculatePpi: $ppi")
            ppi.toInt() + 1
        } else {
            Log.d(TAG, "calculatePpi: $ppi")
            ppi.toInt()
        }
    }

    //获取屏幕物理尺寸，标准获取方法
    private val deviceSize: Double
        get() {
            val pixels = resolution
            val dm = activity.resources.displayMetrics
            val x = Math.pow(pixels[0] / dm.xdpi.toDouble(), 2.0)
            val y = Math.pow(pixels[1] / dm.ydpi.toDouble(), 2.0)
            return Math.sqrt(x + y)
        }

    //获取当前屏幕ppi
    val devicePpi: Int
        get() {
            val a = resolution
            val ppi = calculatePpi(a[0], a[1], deviceSize)
            Log.d(TAG, "getDevicePpi: " + a[0] + "+++++" + a[1])
            return ppi
        }

    //pixel转换成dp数
    fun px2dip(pxValue: Float): Float {
        val m = context.resources.displayMetrics.density
        return pxValue / m + 0.5f
    }

    //dp转换成px
    fun dp2px(dpValue: Float): Int {
        val m = context.resources.displayMetrics.density
        return ((dpValue - 0.5f) * m).toInt()
    }

    companion object {
        private const val TAG = "MyTools"
    }
}