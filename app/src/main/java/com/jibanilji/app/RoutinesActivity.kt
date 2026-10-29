package com.jibanilji.app

import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.time.LocalDate
import androidx.appcompat.R as A

/** 추천 루틴: 체크해서 한 번에 추가 + 루틴 고치기 · 새로 만들기 · 기본값 복원 */
class RoutinesActivity : AppCompatActivity() {
    private val UNITS = listOf("일", "주", "개월", "년")
    private var routines = mutableListOf<Routines.Routine>()
    private val picked = mutableSetOf<Routines.Routine>()
    private lateinit var listBox: LinearLayout
    private lateinit var addBtn: MaterialButton
    private var hid = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hid = Repo.cachedHid(this) ?: return finish()
        edgeToEdge()

        listBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(16))
        }
        val scroll = ScrollView(this).apply { addView(listBox) }

        val bottom = LinearLayout(this).apply {
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setBackgroundColor(c(R.color.surface))
        }
        bottom.addView(MaterialButton(this, null, A.attr.borderlessButtonStyle).apply {
            text = "＋ 새 루틴"; setOnClickListener { edit(null) }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addBtn = MaterialButton(this).apply { setOnClickListener { addPicked() } }
        bottom.addView(addBtn, LinearLayout.LayoutParams(0, -2, 1.4f))

        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        content.addView(bottom)
        setContentView(screen("추천 루틴", content))

        listBox.addView(TextView(this).apply { text = "불러오는 중…"; setTextColor(c(R.color.muted)) })
        Repo.loadRoutines { routines = it.toMutableList(); render() }
    }

    private fun render() {
        listBox.removeAllViews()
        val have = Repo.latest.values.filter { it.kind == HOME }.map { it.name }.toSet()
        listBox.text("체크해서 '${Repo.houseName(this, hid)}'에 추가하고, ✎로 이름·주기를 고칠 수 있어요", 13f, top = 8, color = R.color.muted)

        val groups = routines.groupBy { it.group }
        val order = Routines.GROUP_ORDER + groups.keys.filter { it !in Routines.GROUP_ORDER }
        for (g in order) {
            val rs = groups[g] ?: continue
            listBox.text(g, 14f, bold = true, top = 18, color = R.color.muted)
            val card = listBox.card(6).apply { setPadding(dp(8), dp(4), dp(4), dp(4)) }
            rs.forEach { r ->
                val already = r.name in have
                val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
                row.addView(CheckBox(this).apply {
                    text = "${r.icon}  ${r.name}"
                    textSize = 15f; setTextColor(c(if (already) R.color.muted else R.color.text))
                    isEnabled = !already; isChecked = r in picked
                    setOnCheckedChangeListener { _, on -> if (on) picked.add(r) else picked.remove(r); updateButton() }
                }, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(TextView(this).apply {
                    text = if (already) "등록됨" else r.freqText()
                    textSize = 12f; setTextColor(c(R.color.brand))
                    background = rounded(c(R.color.brand_soft), 10)
                    setPadding(dp(8), dp(3), dp(8), dp(3))
                })
                row.addView(TextView(this).apply {
                    text = "✎"; textSize = 18f; gravity = Gravity.CENTER
                    setTextColor(c(R.color.muted)); contentDescription = "${r.name} 고치기"
                    setOnClickListener { edit(r) }
                }, LinearLayout.LayoutParams(dp(44), dp(44)))
                card.addView(row)
            }
        }

        listBox.addView(MaterialButton(this, null, A.attr.borderlessButtonStyle).apply {
            text = "기본 추천 목록으로 되돌리기"; setTextColor(c(R.color.muted))
            setOnClickListener {
                AlertDialog.Builder(this@RoutinesActivity)
                    .setMessage("고치거나 만든 루틴이 사라지고 처음 추천 목록으로 돌아가요")
                    .setPositiveButton("되돌리기") { _, _ ->
                        routines = Routines.DEFAULTS.toMutableList(); picked.clear()
                        Repo.saveRoutines(null); render()
                    }.setNegativeButton("취소", null).show()
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) })
        updateButton()
    }

    private fun updateButton() {
        addBtn.text = if (picked.isEmpty()) "골라서 추가" else "${picked.size}개 추가"
    }

    /** 루틴 고치기 (r == null 이면 새로 만들기) */
    private fun edit(r: Routines.Routine?) {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
        }
        val name = col.input("이름 (예: 베란다 청소)").apply { setText(r?.name ?: "") }

        col.text("아이콘", 13f, top = 12, color = R.color.muted)
        var icon = r?.icon ?: "🧹"
        val icons = Thumb.HOME_GROUPS.flatMap { it.second }.distinct()
        val iconRow = LinearLayout(this)
        fun paintIcons() {
            for (i in 0 until iconRow.childCount) {
                val v = iconRow.getChildAt(i)
                v.background = if (v.tag == icon) rounded(c(R.color.brand_soft), 12) else null
            }
        }
        icons.forEach { ic ->
            iconRow.addView(TextView(this).apply {
                text = ic; textSize = 22f; gravity = Gravity.CENTER; tag = ic
                setOnClickListener { icon = ic; paintIcons() }
            }, LinearLayout.LayoutParams(dp(44), dp(44)))
        }
        paintIcons()
        col.addView(HorizontalScrollView(this).apply { addView(iconRow); isHorizontalScrollBarEnabled = false })

        col.text("얼마나 자주", 13f, top = 12, color = R.color.muted)
        val freqRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val cycle = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER; gravity = Gravity.CENTER
            setText((r?.cycle ?: 1).toString())
        }
        val unit = Spinner(this).apply {
            adapter = ArrayAdapter(this@RoutinesActivity, android.R.layout.simple_spinner_dropdown_item, UNITS)
            setSelection(UNITS.indexOf(r?.unit ?: "주").coerceAtLeast(0))
        }
        freqRow.addView(cycle, LinearLayout.LayoutParams(dp(72), -2))
        freqRow.addView(unit)
        freqRow.addView(TextView(this).apply { text = "마다"; setTextColor(c(R.color.text)) })
        col.addView(freqRow)

        col.text("분류", 13f, top = 12, color = R.color.muted)
        val groupNames = (Routines.GROUP_ORDER + routines.map { it.group }).distinct()
        val group = Spinner(this).apply {
            adapter = ArrayAdapter(this@RoutinesActivity, android.R.layout.simple_spinner_dropdown_item, groupNames)
            setSelection(groupNames.indexOf(r?.group ?: "기타").coerceAtLeast(0))
        }
        col.addView(group)

        val dlg = AlertDialog.Builder(this)
            .setTitle(if (r == null) "새 루틴 만들기" else "루틴 고치기")
            .setView(ScrollView(this).apply { addView(col) })
            .setPositiveButton("저장", null)
            .setNegativeButton("취소", null)
        if (r != null) dlg.setNeutralButton("삭제") { _, _ ->
            routines.remove(r); picked.remove(r); persist()
        }
        val d = dlg.show()
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val n = name.text.toString().trim()
            val cy = cycle.text.toString().toIntOrNull()
            if (n.isEmpty()) { name.error = "이름을 입력해 주세요"; return@setOnClickListener }
            if (cy == null || cy <= 0) { cycle.error = "1 이상"; return@setOnClickListener }
            val nr = Routines.Routine(groupNames[group.selectedItemPosition], n, icon, cy, UNITS[unit.selectedItemPosition])
            if (r == null) routines.add(nr) else {
                routines[routines.indexOf(r)] = nr
                if (picked.remove(r)) picked.add(nr)
            }
            persist(); d.dismiss()
        }
    }

    private fun persist() {
        Repo.saveRoutines(routines)
        render()
    }

    private fun addPicked() {
        if (picked.isEmpty()) return toast("추가할 루틴을 체크해 주세요")
        if (!Premium.canAdd(Repo.latest.values, HOME, picked.size)) {
            return Premium.upsell(this, "무료로는 집안일을 ${Premium.FREE_LIMIT}개까지 등록할 수 있어요.")
        }
        val today = LocalDate.now().toEpochDay()
        picked.forEach { r ->
            Repo.save(hid, ctx = this, item = Item(
                kind = HOME, name = r.name, icon = r.icon, color = Thumb.colorFor(HOME, r.icon),
                base = today, cycle = r.cycle, unit = r.unit, repeat = "interval",
                hour = 9, minute = 0, offsets = listOf(1, 0), by = Repo.nick(this),
            ))
        }
        toast("${picked.size}개 추가했어요. 눌러서 주기·알림을 바꿀 수 있어요")
        finish()
    }
}
