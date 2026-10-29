package com.jibanilji.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import androidx.appcompat.R as A

// 스토어 출시 전에 실제 개인정보처리방침 주소로 바꿔주세요 (예: 노션·블로그 공개 페이지)
const val PRIVACY_URL = "https://example.com/privacy"

/** 카드형 목록 (모든 페이지 공용) */
class ItemAdapter(private val main: MainActivity) : BaseAdapter() {
    var data: List<Item> = emptyList()
        set(v) { field = v; notifyDataSetChanged() }

    override fun getCount() = data.size
    override fun getItem(p: Int) = data[p]
    override fun getItemId(p: Int) = p.toLong()

    override fun getView(p: Int, convert: View?, parent: ViewGroup): View {
        val v = convert ?: main.layoutInflater.inflate(R.layout.item_row, parent, false)
        val item = data[p]
        val left = ChronoUnit.DAYS.between(LocalDate.now(), item.due())
        val ctx = main

        v.findViewById<View>(R.id.card).apply {
            background = ctx.rounded(ctx.c(R.color.surface), 20)
            setOnClickListener { main.openEdit(item) }
            setOnLongClickListener { main.options(item); true }
        }
        Thumb.bind(v.findViewById<ImageView>(R.id.thumbImg), v.findViewById(R.id.thumbIcon), item)
        v.findViewById<View>(R.id.thumb).setOnClickListener { Thumb.showBig(ctx, item) }
        v.findViewById<TextView>(R.id.name).text = item.name
        v.findViewById<TextView>(R.id.sub).text = item.subText()

        val (fg, bg) = when {
            left < 0 -> R.color.danger to R.color.danger_soft
            left == 0L -> R.color.danger to R.color.danger_soft
            left <= 3 -> R.color.warn to R.color.warn_soft
            else -> R.color.brand to R.color.brand_soft
        }
        v.findViewById<TextView>(R.id.dday).apply {
            text = when {
                left > 0 -> "D-$left"
                left == 0L -> if (item.kind == HOME) "오늘" else "D-DAY"
                item.kind == HOME -> "${-left}일 밀림"
                else -> "${-left}일 지남"
            }
            setTextColor(ctx.c(fg)); background = ctx.rounded(ctx.c(bg), 12)
        }
        v.findViewById<TextView>(R.id.btnDone).apply {
            visibility = if (item.kind == HOME) View.VISIBLE else View.GONE
            setTextColor(ctx.c(R.color.brand))
            background = ctx.rounded(ctx.c(R.color.surface), 18, stroke = ctx.c(R.color.brand))
            setOnClickListener { main.complete(item) }
        }
        return v
    }
}

/** 공통: 헤더 + 카드 목록 + 빈 화면 안내 */
abstract class ListPageBase(protected val main: MainActivity, title: String) : FrameLayout(main) {
    protected val list = ListView(main).apply {
        divider = null; dividerHeight = 0; clipToPadding = false
        setPadding(0, 0, 0, main.dp(96)); selector = android.graphics.drawable.ColorDrawable(0)
    }
    protected val adapter = ItemAdapter(main)
    protected val headerBox = LinearLayout(main).apply { orientation = LinearLayout.VERTICAL }
    protected val subtitle: TextView
    private val empty = TextView(main).apply {
        gravity = Gravity.CENTER; textSize = 15f; setTextColor(main.c(R.color.muted))
        setPadding(main.dp(24), main.dp(40), main.dp(24), main.dp(40)); setLineSpacing(0f, 1.3f)
    }

    init {
        val (h, sub) = main.pageHeader(title)
        subtitle = sub
        headerBox.addView(h)
        list.addHeaderView(headerBox, null, false)
        list.addFooterView(empty, null, false)
        list.adapter = adapter
        addView(list)
    }

    protected fun show(items: List<Item>, emptyText: String) {
        adapter.data = items
        empty.text = emptyText
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    abstract fun refresh()
}

/** 📅 캘린더 */
class CalendarPage(main: MainActivity) : ListPageBase(main, "캘린더") {
    val cal = MonthCalendar(main)
    private val dayLabel = TextView(main).apply {
        textSize = 16f; setTypeface(typeface, Typeface.BOLD); setTextColor(main.c(R.color.text))
        setPadding(main.dp(20), main.dp(18), main.dp(20), main.dp(6))
    }

    init {
        headerBox.addView(FrameLayout(main).apply {
            setPadding(main.dp(16), main.dp(4), main.dp(16), 0); addView(cal)
        })
        headerBox.addView(dayLabel)
        cal.onSelect = { refresh() }
    }

    override fun refresh() {
        val all = main.all
        val today = LocalDate.now()
        val todayCount = all.count { it.due() <= today }
        subtitle.text = if (todayCount > 0) "오늘까지 챙길 게 ${todayCount}개 있어요" else "오늘은 여유로운 날이에요 ☕"
        cal.items = all; cal.render()
        val items = all.filter { it.due() == cal.selected }.sortedBy { it.kind }
        dayLabel.text = cal.selected.format(DateTimeFormatter.ofPattern("M월 d일 (E)")) +
            if (items.isNotEmpty()) "  ·  ${items.size}개" else ""
        show(items, "이 날은 챙길 게 없어요")
    }
}

/** 🧊 식품 / 🧹 집안일 */
class KindPage(main: MainActivity, val kind: String) :
    ListPageBase(main, if (kind == FOOD) "식품" else "집안일") {
    private var storage = ""

    init {
        if (kind == FOOD) {
            val g = ChipGroup(main).apply {
                isSingleSelection = true; isSelectionRequired = true
                setPadding(main.dp(16), 0, main.dp(16), main.dp(4))
            }
            (listOf("" to "전체") + STORAGES.map { it to "${storageEmoji(it)} $it" }).forEach { (key, label) ->
                g.addView(Chip(main).apply {
                    text = label; isCheckable = true; isChecked = key == storage
                    setOnClickListener { storage = key; refresh() }
                })
            }
            headerBox.addView(g)
        } else {
            // 추천 루틴 카드
            headerBox.addView(LinearLayout(main).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                background = main.rounded(main.c(R.color.brand_soft), 20)
                setPadding(main.dp(18), main.dp(14), main.dp(18), main.dp(14))
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                    setMargins(main.dp(16), main.dp(4), main.dp(16), main.dp(8))
                }
                addView(TextView(main).apply { text = "⭐"; textSize = 22f })
                addView(LinearLayout(main).apply {
                    orientation = LinearLayout.VERTICAL; setPadding(main.dp(12), 0, 0, 0)
                    addView(TextView(main).apply {
                        text = "추천 루틴 둘러보기"; textSize = 15f
                        setTypeface(typeface, Typeface.BOLD); setTextColor(main.c(R.color.text))
                    })
                    addView(TextView(main).apply {
                        text = "청소·빨래·필터 교체 등 권장 주기로 한 번에 추가"
                        textSize = 12f; setTextColor(main.c(R.color.muted))
                    })
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(TextView(main).apply { text = "›"; textSize = 22f; setTextColor(main.c(R.color.brand)) })
                setOnClickListener { main.showRoutines() }
            })
        }
    }

    override fun refresh() {
        val today = LocalDate.now()
        val mine = main.all.filter { it.kind == kind }
        if (kind == FOOD) {
            val over = mine.count { it.due() < today }
            val soon = mine.count { !it.due().isBefore(today) && it.due() <= today.plusDays(3) }
            subtitle.text = "총 ${mine.size}개 · 임박 ${soon}개" + if (over > 0) " · 지난 것 ${over}개" else ""
            val shown = mine.filter { storage.isEmpty() || it.storage == storage }.sortedBy { it.due() }
            show(shown, if (storage.isNotEmpty()) "$storage 보관 중인 식품이 없어요"
                else "냉장고 속 식품을 추가해 보세요\n사진을 찍으면 소비기한을 읽어줘요 📷")
        } else {
            val todo = mine.count { it.due() <= today }
            subtitle.text = if (todo > 0) "오늘 할 일 ${todo}개 · 전체 ${mine.size}개" else "전체 ${mine.size}개 · 오늘은 다 했어요 ✨"
            show(mine.sortedBy { it.due() }, "위의 추천 루틴에서 골라보세요\n직접 추가는 아래 + 버튼")
        }
    }
}

/** ⚙️ 설정: 함께 쓰기 · 계정 · 앱 정보 */
class SettingsPage(private val main: MainActivity) : FrameLayout(main) {
    private val houseName: TextView
    private val members: TextView
    private val codeView: TextView
    private val status: TextView
    private var code = ""

    init {
        val col = LinearLayout(main).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(main.dp(16), 0, main.dp(16), main.dp(32))
        }
        val (h, sub) = main.pageHeader("설정")
        h.setPadding(main.dp(4), h.paddingTop, main.dp(4), h.paddingBottom)
        sub.text = "함께 쓰기와 계정"
        col.addView(h)

        // 우리 집
        val house = col.card(8)
        houseName = house.text("불러오는 중…", 19f, bold = true)
        members = house.text("", 13f, top = 4, color = R.color.muted)
        house.text("초대 코드", 12f, top = 16, color = R.color.muted)
        codeView = house.text("······", 30f, bold = true, color = R.color.brand)
        house.text("가족·친구가 이 코드를 입력하면 같은 목록과 알림을 함께 써요", 12f, top = 2, color = R.color.muted)
        house.button("📤  초대 메시지 보내기", top = 14) {
            if (code.isEmpty()) return@button
            if (!Premium.unlocked()) return@button Premium.upsell(main, "같이 쓰기는 프리미엄 기능이에요.")
            main.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT,
                    "집안일지 같이 써요! 🏠\n앱의 [설정 > 다른 집에 참여하기]에 아래 코드를 입력해 주세요\n\n$code")
            }, "초대하기"))
        }
        val row = LinearLayout(main).also { house.addView(it) }
        row.addView(textButton("코드 복사") {
            if (code.isEmpty()) return@textButton
            main.getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("초대 코드", code))
            main.toast("복사했어요")
        }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(textButton("집 이름 바꾸기") { rename() }, LinearLayout.LayoutParams(0, -2, 1f))

        // 다른 집 참여
        val join = col.card()
        join.text("다른 집에 참여하기", 16f, bold = true)
        join.text("받은 초대 코드를 입력하면 그 집으로 옮겨가요", 12f, top = 2, color = R.color.muted)
        val input = join.input("초대 코드 6자리", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS)
        val msg = join.text("", 13f, top = 4, color = R.color.danger)
        join.button("참여하기", top = 6) {
            if (!Premium.unlocked()) return@button Premium.upsell(main, "같이 쓰기는 프리미엄 기능이에요.")
            msg.text = "확인 중…"
            Repo.join(main, input.text.toString()) { ok, m ->
                if (ok) { main.toast(m); msg.text = ""; input.setText(""); main.reconnect() } else msg.text = m
            }
        }

        // 계정
        val acc = col.card()
        acc.text("내 계정", 16f, bold = true)
        acc.text("${Repo.nick(main)} · ${Repo.auth.currentUser?.email ?: ""}", 13f, top = 4, color = R.color.muted)
        val accRow = LinearLayout(main).also { acc.addView(it) }
        accRow.addView(textButton("로그아웃") { logout() }, LinearLayout.LayoutParams(0, -2, 1f))
        accRow.addView(textButton("계정 삭제", R.color.danger) { deleteAccount() }, LinearLayout.LayoutParams(0, -2, 1f))

        // 앱 정보
        val info = col.card()
        info.text("앱 정보", 16f, bold = true)
        status = info.text("", 13f, top = 6, color = R.color.muted)
        info.addView(textButton("개인정보처리방침") {
            main.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL)))
        })

        addView(ScrollView(main).apply { addView(col) })
    }

    private fun textButton(s: String, color: Int = R.color.brand, onClick: () -> Unit) =
        com.google.android.material.button.MaterialButton(main, null, A.attr.borderlessButtonStyle).apply {
            text = s; setTextColor(main.c(color)); setOnClickListener { onClick() }
        }

    fun update(h: Map<String, Any?>) {
        houseName.text = "🏠 " + (h["name"] as? String ?: "우리 집")
        code = h["code"] as? String ?: ""
        codeView.text = code.ifEmpty { "······" }
        val names = Repo.members.values.toList()
        members.text = "함께 쓰는 사람 ${names.size}명 · ${names.joinToString(", ")}"
    }

    fun refreshStatus() {
        status.text = "동기화: " + (Repo.lastError?.let { "⚠️ $it" } ?: "정상 ✓") +
            "\n등록된 항목: 식품 ${main.all.count { it.kind == FOOD }}개 · 집안일 ${main.all.count { it.kind == HOME }}개"
    }

    private fun rename() {
        val hid = Repo.cachedHid(main) ?: return
        val et = EditText(main).apply { setText(houseName.text.removePrefix("🏠 ")) }
        AlertDialog.Builder(main).setTitle("집 이름").setView(FrameLayout(main).apply {
            setPadding(main.dp(20), main.dp(8), main.dp(20), 0); addView(et)
        }).setPositiveButton("저장") { _, _ ->
            val n = et.text.toString().trim()
            if (n.isNotEmpty()) Repo.renameHousehold(hid, n)
        }.setNegativeButton("취소", null).show()
    }

    private fun logout() {
        AlertDialog.Builder(main).setMessage("로그아웃할까요? 이 폰에서는 알림도 꺼져요")
            .setPositiveButton("로그아웃") { _, _ -> Repo.signOut(main); main.toLogin() }
            .setNegativeButton("취소", null).show()
    }

    private fun deleteAccount() {
        AlertDialog.Builder(main).setTitle("계정을 삭제할까요?")
            .setMessage("계정과 내 정보가 지워져요. 혼자 쓰던 집이면 모든 목록과 기록도 함께 지워지고, 되돌릴 수 없어요.")
            .setPositiveButton("삭제") { _, _ ->
                Repo.deleteAccount(main) { ok, m ->
                    main.toast(m)
                    if (ok || Repo.uid() == null) main.toLogin()
                }
            }.setNegativeButton("취소", null).show()
    }
}
