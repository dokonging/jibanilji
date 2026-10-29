package com.jibanilji.app

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton

/** 화면을 코드로 만들기 위한 작은 도우미들 */
fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()
fun Context.toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
fun Context.c(id: Int) = getColor(id)

/** 둥근 배경 */
fun Context.rounded(color: Int, radiusDp: Int = 16, stroke: Int? = null) = GradientDrawable().apply {
    cornerRadius = dp(radiusDp).toFloat()
    setColor(color)
    if (stroke != null) setStroke(dp(1), stroke)
}

/** 상태바·내비게이션바 뒤까지 그리고, 필요한 만큼 안쪽 여백을 줌 (화면 잘림 방지) */
fun Activity.edgeToEdge() = WindowCompat.setDecorFitsSystemWindows(window, false)

fun View.applyInsets(top: Boolean = true, bottom: Boolean = true) {
    val l = paddingLeft; val t = paddingTop; val r = paddingRight; val b = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
        v.setPadding(
            l + sys.left, t + if (top) sys.top else 0, r + sys.right,
            b + if (bottom) maxOf(sys.bottom, ime.bottom) else 0,
        )
        insets
    }
    ViewCompat.requestApplyInsets(this)
}

/** 상단바(뒤로 + 제목) + 내용으로 화면 구성 */
fun Activity.screen(title: String, content: View, back: Boolean = true): View {
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(c(R.color.bg))
    }
    val bar = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), 0, dp(16), 0)
    }
    if (back) bar.addView(TextView(this).apply {
        text = "←"; textSize = 22f; setTextColor(c(R.color.text))
        gravity = Gravity.CENTER; contentDescription = "뒤로"
        setOnClickListener { finish() }
    }, LinearLayout.LayoutParams(dp(48), dp(48)))
    bar.addView(TextView(this).apply {
        text = title; textSize = 19f; setTypeface(typeface, Typeface.BOLD)
        setTextColor(c(R.color.text)); setPadding(dp(if (back) 4 else 12), 0, 0, 0)
    })
    root.addView(bar, LinearLayout.LayoutParams(MATCH_PARENT, dp(56)))
    root.addView(content, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
    root.applyInsets()
    return root
}

/** 페이지 제목 + 부제 */
fun Context.pageHeader(title: String): Pair<LinearLayout, TextView> {
    val box = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(16), dp(20), dp(8))
    }
    box.addView(TextView(this).apply {
        text = title; textSize = 26f; setTypeface(typeface, Typeface.BOLD); setTextColor(c(R.color.text))
    })
    val sub = TextView(this).apply { textSize = 14f; setTextColor(c(R.color.muted)) }
    box.addView(sub)
    return box to sub
}

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
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setTextColor(context.getColor(color ?: R.color.text))
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = context.dp(top) }
    }.also { addView(it) }

fun LinearLayout.input(hint: String, type: Int = InputType.TYPE_CLASS_TEXT) =
    EditText(context).apply {
        this.hint = hint; inputType = type; maxLines = 1
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = context.dp(8) }
    }.also { addView(it) }

fun LinearLayout.button(s: String, style: Int = 0, top: Int = 12, onClick: () -> Unit) =
    (if (style == 0) MaterialButton(context) else MaterialButton(context, null, style)).apply {
        text = s; gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = context.dp(top) }
        setOnClickListener { onClick() }
    }.also { addView(it) }

/** 카드(둥근 흰 상자) */
fun LinearLayout.card(top: Int = 12): LinearLayout = LinearLayout(context).apply {
    orientation = LinearLayout.VERTICAL
    background = context.rounded(context.c(R.color.surface), 20)
    setPadding(context.dp(18), context.dp(16), context.dp(18), context.dp(16))
    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = context.dp(top) }
}.also { addView(it) }
