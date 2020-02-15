package com.homan.huang.cameraxfacecheckin

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager


class BoxView(context: Context, attrs: AttributeSet) : View(context, attrs) {
    var mColor = Color.BLACK
    var paint = Paint()
    var rectW = 0
    var rectH = 0
    var rect: Rect? = null

    var mLeft = 0
    var mRight = 0
    var mTop = 0
    var mBottom = 0

    init {
        context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.BoxView,
            0, 0).apply {

            try {
                rectW = getInteger(R.styleable.BoxView_boxWidth, 400)
                rectH = getInteger(R.styleable.BoxView_boxHeight, 500)
            } finally {
                recycle()
            }
        }
    }

    private fun setRectangle(w: Int, h: Int) {
        val canvasW = w / 2
        val canvasH = h / 2
        val centerOfCanvas = Point(canvasW, canvasH)

        val x = rectW / 2
        val y = rectH / 2

        mLeft = centerOfCanvas.x - x
        mRight = centerOfCanvas.x + x

        mTop = centerOfCanvas.y - y
        mBottom = centerOfCanvas.y + y

        rect = Rect(mLeft, mTop, mRight, mBottom)

        lgi("rect: l=$mLeft, t=$mTop,     r=$mRight, b=$mBottom")
    }

    override fun onDraw(canvas: Canvas) {
        lgi ("canvas = height: ${canvas.height} vs width: ${canvas.width}")

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 10F
        paint.color = mColor

        val height = canvas.height
        val width = canvas.width

        if (height > width)
            setRectangle(width, height)
        else
            setRectangle(height, width)

        canvas.drawRect(rect!!, paint)
    }

    fun setColor(color: Int) {
        mColor = color
        invalidate()
    }

    fun setWidth(w: Int) {
        rectW = w
    }

    fun setHeight(h: Int) {
        rectH = h
    }

    companion object {
        val TAG = "MYLOG " + BoxView::class.java.simpleName
        fun lgi(s: String) {
            Log.i(TAG, s)
        }
    }
}