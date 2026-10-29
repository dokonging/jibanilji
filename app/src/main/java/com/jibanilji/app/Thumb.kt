package com.jibanilji.app

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.Window
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** 아이콘 모음 · 목록 썸네일(사진/아이콘) · 눌렀을 때 크게 보기 */
object Thumb {
    val FOOD_GROUPS = listOf(
        "유제품·달걀" to listOf("🥛", "🧀", "🥚", "🧈", "🍦", "🥯"),
        "고기·해산물" to listOf("🍖", "🥩", "🍗", "🥓", "🌭", "🐟", "🦐", "🦑", "🦀", "🐙"),
        "채소" to listOf("🥕", "🥬", "🥦", "🥒", "🌽", "🍅", "🧅", "🧄", "🥔", "🍄", "🌶️", "🍆"),
        "과일" to listOf("🍎", "🍌", "🍇", "🍓", "🍊", "🍋", "🍑", "🍉", "🥝", "🍍", "🍒", "🥑"),
        "밥·면·빵" to listOf("🍚", "🍙", "🍜", "🍝", "🍞", "🥐", "🥟", "🍱", "🍕", "🥫", "🥗", "🍲"),
        "간식·음료" to listOf("🍰", "🍫", "🍪", "🍩", "🍶", "🧃", "🥤", "🍺", "🍷", "☕", "🍵", "🧋"),
        "양념·기타" to listOf("🧂", "🍯", "🥜", "💊", "🧊"),
    )
    val HOME_GROUPS = listOf(
        "청소" to listOf("🧹", "🧽", "🪣", "🧼", "🚽", "🪟", "🛋️", "✨"),
        "욕실·교체품" to listOf("🪥", "🧴", "🛁", "🚿", "🪒", "🧻", "🪞", "🧷"),
        "주방" to listOf("🍳", "🔪", "🥢", "🫖", "🧊", "🍽️", "🫧", "🧯"),
        "세탁·침구" to listOf("🧺", "👕", "🧦", "🛏️", "🧸", "🧤", "👟", "🧥"),
        "가전·필터" to listOf("💧", "❄️", "🌀", "💨", "💡", "🔋", "🔌", "🤖"),
        "쓰레기·분리수거" to listOf("🗑️", "♻️", "📦", "🥫", "🍾", "🛍️"),
        "식물·반려동물" to listOf("🪴", "🌱", "🌵", "🐶", "🐱", "🐟", "🐹", "🐦"),
        "관리·납부" to listOf("🔥", "🧾", "💳", "🏠", "🚗", "💊", "😷", "📅"),
    )

    private val BG = intArrayOf(
        0xFFE6F1FB.toInt(), 0xFFFAEEDA.toInt(), 0xFFE1F5EE.toInt(),
        0xFFFAECE7.toInt(), 0xFFEEEDFE.toInt(), 0xFFEAF3DE.toInt(),
    )

    fun groups(kind: String) = if (kind == HOME) HOME_GROUPS else FOOD_GROUPS
    fun defaultIcon(kind: String) = groups(kind)[0].second[0]
    fun colorFor(kind: String, icon: String) =
        groups(kind).indexOfFirst { icon in it.second }.coerceAtLeast(0)

    fun bg(ctx: Context, color: Int, radiusDp: Float = 12f) = GradientDrawable().apply {
        cornerRadius = radiusDp * ctx.resources.displayMetrics.density
        setColor(BG[Math.floorMod(color, BG.size)])
    }

    fun bind(img: ImageView, txt: TextView, item: Item) {
        val bmp = Photos.decode(item.photo)
        img.background = bg(img.context, item.color); img.clipToOutline = true
        txt.background = bg(txt.context, item.color)
        if (bmp != null) {
            img.setImageBitmap(bmp); img.visibility = View.VISIBLE; txt.visibility = View.GONE
        } else {
            img.setImageDrawable(null); img.visibility = View.GONE
            txt.visibility = View.VISIBLE; txt.text = item.icon
        }
    }

    fun showBig(ctx: Context, item: Item) {
        val dm = ctx.resources.displayMetrics
        val size = (dm.widthPixels * 0.8).toInt()
        val dlg = Dialog(ctx)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val bmp = Photos.decode(item.photo)
        val content: View = if (bmp != null) ImageView(ctx).apply {
            setImageBitmap(bmp); adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = bg(ctx, item.color, 16f); clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(size, WRAP_CONTENT)
        } else TextView(ctx).apply {
            text = item.icon; textSize = 110f; gravity = Gravity.CENTER
            background = bg(ctx, item.color, 16f)
            layoutParams = LinearLayout.LayoutParams(size, size)
        }

        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(content)
            addView(TextView(ctx).apply {
                text = item.name; textSize = 18f; setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
                setPadding(0, (12 * dm.density).toInt(), 0, 0)
            })
            addView(TextView(ctx).apply {
                text = item.subText() + if (item.by.isNotBlank()) "\n${item.by} 님이 등록" else ""
                textSize = 13f; setTextColor(0xDDFFFFFF.toInt()); gravity = Gravity.CENTER
            })
            setOnClickListener { dlg.dismiss() }
        }
        content.setOnClickListener { dlg.dismiss() }
        dlg.setContentView(box)
        dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dlg.setCanceledOnTouchOutside(true)
        dlg.show()
    }
}
