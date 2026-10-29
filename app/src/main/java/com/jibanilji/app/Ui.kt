package com.jibanilji.app

import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton

/** 간단한 화면을 코드로 만들기 위한 작은 도우미 */
fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

fun Context.toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

fun Context.column(): Pair<ScrollView, LinearLayout> {
    val col = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), dp(24), dp(24), dp(24))
    }
    return ScrollView(this).apply { addView(col) } to col
}

fun LinearLayout.text(s: String, size: Float = 15f, bold: Boolean = false, top: Int = 0, color: Int? = null) =
    TextView(context).apply {
        text = s; textSize = size
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        color?.let { setTextColor(context.getColor(it)) }
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = context.dp(top) }
    }.also { addView(it) }

fun LinearLayout.input(hint: String, type: Int = InputType.TYPE_CLASS_TEXT) =
    EditText(context).apply {
        this.hint = hint; inputType = type; maxLines = 1
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = context.dp(8) }
    }.also { addView(it) }

fun LinearLayout.button(s: String, style: Int = 0, top: Int = 12, onClick: () -> Unit) =
    (if (style == 0) MaterialButton(context)
     else MaterialButton(context, null, style)).apply {
        text = s; gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = context.dp(top) }
        setOnClickListener { onClick() }
    }.also { addView(it) }
