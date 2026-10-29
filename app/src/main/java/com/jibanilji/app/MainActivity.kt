package com.jibanilji.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.tabs.TabLayout
import com.google.firebase.firestore.ListenerRegistration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class MainActivity : AppCompatActivity() {
    private var tab = 0 // 0 모아보기, 1 식품, 2 집안일
    private var storageFilter = "" // "" = 전체
    private var hid: String? = null
    private var reg: ListenerRegistration? = null
    private var regHouse: ListenerRegistration? = null
    private var all: List<Item> = emptyList()
    private var shown: List<Item> = emptyList()

    private lateinit var list: ListView
    private lateinit var empty: TextView
    private lateinit var dayLabel: TextView
    private lateinit var cal: MonthCalendar

    private val askNotify = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Repo.uid() == null) {
            startActivity(Intent(this, LoginActivity::class.java)); finish(); return
        }
        setContentView(R.layout.activity_main)
        list = findViewById(R.id.list)
        empty = findViewById(R.id.empty)
        dayLabel = findViewById(R.id.dayLabel)
        cal = MonthCalendar(this)
        findViewById<FrameLayout>(R.id.calHost).addView(cal)
        list.adapter = adapter

        tab = savedInstanceState?.getInt("tab") ?: 0
        storageFilter = savedInstanceState?.getString("storage") ?: ""
        val tabs = findViewById<com.google.android.material.tabs.TabLayout>(R.id.tabs)
        listOf("📅 모아보기", "🧊 식품", "🧹 집안일").forEachIndexed { i, t ->
            tabs.addTab(tabs.newTab().setText(t), i == tab)
        }
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(t: TabLayout.Tab) { tab = t.position; refresh() }
            override fun onTabUnselected(t: TabLayout.Tab) {}
            override fun onTabReselected(t: TabLayout.Tab) {}
        })
        setupStorageFilter()
        cal.onSelect = { refresh() }
        findViewById<View>(R.id.fab).setOnClickListener { add() }

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        SyncWorker.start(this)
        refresh()
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putInt("tab", tab); out.putString("storage", storageFilter)
    }

    override fun onStart() {
        super.onStart()
        if (Repo.uid() == null) return
        val h = Repo.cachedHid(this)
        if (h == null) {
            Repo.ensureHousehold(this, null) { got, err ->
                if (got != null) { hid = got; listen() } else toast("불러오지 못했어요: $err")
            }
            return
        }
        if (h != hid) { stopListening(); hid = h } // 다른 집에 참여한 경우
        listen()
    }

    override fun onStop() {
        super.onStop()
        stopListening()
    }

    private fun stopListening() {
        reg?.remove(); reg = null
        regHouse?.remove(); regHouse = null
    }

    private fun listen() {
        val h = hid ?: return
        if (regHouse == null) regHouse = Repo.listenHousehold(h) {
            Alarms.scheduleAll(this, all) // 담당자 정보가 바뀌었을 수 있음
        }
        if (reg == null) reg = Repo.listen(h) { items ->
            all = items
            Mirror.save(this, items)
            Alarms.scheduleAll(this, items)
            refresh()
        }
    }

    // 상단 메뉴: 함께 쓰기
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "👥 함께 쓰기").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 1) { startActivity(Intent(this, ShareActivity::class.java)); return true }
        return super.onOptionsItemSelected(item)
    }

    private fun setupStorageFilter() {
        val g = findViewById<ChipGroup>(R.id.storageFilter)
        (listOf("" to "전체") + STORAGES.map { it to "${storageEmoji(it)} $it" }).forEach { (key, label) ->
            g.addView(Chip(this).apply {
                text = label; isCheckable = true; isChecked = key == storageFilter
                setOnClickListener { storageFilter = key; refresh() }
            })
        }
    }

    private fun refresh() {
        val isCal = tab == 0
        findViewById<View>(R.id.calHost).visibility = if (isCal) View.VISIBLE else View.GONE
        dayLabel.visibility = if (isCal) View.VISIBLE else View.GONE
        findViewById<View>(R.id.filterBox).visibility = if (tab == 1) View.VISIBLE else View.GONE
        cal.items = all
        cal.render()

        shown = when (tab) {
            0 -> all.filter { it.due() == cal.selected }.sortedBy { it.kind }
            1 -> all.filter { it.kind == FOOD && (storageFilter.isEmpty() || it.storage == storageFilter) }
                .sortedBy { it.due() }
            else -> all.filter { it.kind == HOME }.sortedBy { it.due() }
        }
        dayLabel.text = cal.selected.format(DateTimeFormatter.ofPattern("M월 d일 (E)")) +
            if (shown.isNotEmpty()) " · ${shown.size}개" else ""
        empty.text = when (tab) {
            0 -> "이 날은 챙길 게 없어요"
            1 -> if (storageFilter.isNotEmpty()) "${storageFilter} 보관 중인 식품이 없어요"
                 else "아직 등록된 식품이 없어요\n아래 + 버튼으로 추가해 보세요"
            else -> "청소·빨래·필터 교체처럼 반복되는 집안일을\n+ 버튼 → 추천 루틴에서 골라보세요"
        }
        empty.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
        adapter.notifyDataSetChanged()
    }

    // ---------- 추가 ----------
    private fun add() {
        val h = hid ?: return
        fun open(kind: String) {
            if (!Premium.canAdd(all, kind)) {
                val what = if (kind == HOME) "집안일" else "식품"
                return Premium.upsell(this, "무료로는 ${what}을 ${Premium.FREE_LIMIT}개까지 등록할 수 있어요.")
            }
            val i = Intent(this, EditActivity::class.java).putExtra("kind", kind)
            if (tab == 0 && kind == FOOD) i.putExtra("date", cal.selected.toEpochDay())
            startActivity(i)
        }
        when (tab) {
            1 -> open(FOOD)
            2 -> AlertDialog.Builder(this).setTitle("집안일 추가")
                .setItems(arrayOf("⭐ 추천 루틴에서 고르기", "✏️ 직접 추가")) { _, w ->
                    if (w == 0) Routines.showPicker(this, h, all) else open(HOME)
                }.show()
            else -> AlertDialog.Builder(this).setTitle("무엇을 추가할까요?")
                .setItems(arrayOf("🧊 식품 (소비기한)", "🧹 집안일 (직접 추가)", "⭐ 추천 집안일 루틴")) { _, w ->
                    when (w) { 0 -> open(FOOD); 1 -> open(HOME); else -> Routines.showPicker(this, h, all) }
                }.show()
        }
    }

    // ---------- 행 동작 ----------
    private fun complete(item: Item) {
        val h = hid ?: return
        Repo.complete(this, h, item)
        val next = item.due().format(DateTimeFormatter.ofPattern("M.d (E)"))
        toast("✅ 완료! 다음은 $next" + (item.qty?.let { " · 재고 ${it}개 남음" } ?: ""))
    }

    private fun options(item: Item) {
        val isHome = item.kind == HOME
        val labels = if (isHome) arrayOf("✅ 완료 기록하기", "📦 재고 수정", "✏️ 수정 · 기록 보기", "🗑️ 삭제")
        else arrayOf("📦 남은 수량 · 용량 수정", "✏️ 수정", "🗑️ 삭제")
        AlertDialog.Builder(this).setTitle("${item.icon} ${item.name}")
            .setItems(labels) { _, w ->
                when (labels[w].substringAfter(' ').substringBefore(' ')) {
                    "완료" -> complete(item)
                    "재고", "남은" -> updateQty(item)
                    "수정" -> edit(item)
                    "삭제" -> confirmDelete(item)
                }
            }.show()
    }

    private fun edit(item: Item) =
        startActivity(Intent(this, EditActivity::class.java).putExtra("id", item.id))

    /** 일부만 사용했을 때: 남은 수량(재고) · 용량 빠르게 수정 */
    private fun updateQty(item: Item) {
        val h = hid ?: return
        val isHome = item.kind == HOME
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
        }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val num = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER; gravity = Gravity.CENTER
            setText((item.qty ?: if (isHome) 0 else 1).toString()); minWidth = dp(72)
        }
        fun stepBtn(label: String, delta: Int) = MaterialButton(this).apply {
            text = label; minWidth = 0; minimumWidth = 0
            setOnClickListener {
                val cur = num.text.toString().toIntOrNull() ?: 0
                num.setText((cur + delta).coerceIn(0, 9999).toString())
            }
        }
        row.addView(stepBtn("−1", -1)); row.addView(num); row.addView(stepBtn("+1", 1))
        if (isHome) row.addView(stepBtn("+5", 5))
        col.addView(TextView(this).apply {
            text = if (isHome) "남은 재고 (새로 사면 + 버튼)" else "남은 수량"
            setTextColor(getColor(R.color.muted))
        })
        col.addView(row)
        val vol = if (isHome) null else EditText(this).apply {
            hint = "남은 용량 (예: 500ml, 반 통)"; setText(item.volume)
        }.also {
            col.addView(TextView(this).apply {
                text = "남은 용량"; setTextColor(getColor(R.color.muted)); setPadding(0, dp(12), 0, 0)
            })
            col.addView(it)
        }

        AlertDialog.Builder(this).setTitle("${item.icon} ${item.name}")
            .setView(col)
            .setPositiveButton("저장") { _, _ ->
                val q = num.text.toString().toIntOrNull() ?: 0
                item.qty = q
                vol?.let { item.volume = it.text.toString().trim() }
                Repo.save(h, item)
                if (!isHome && q == 0) {
                    AlertDialog.Builder(this).setMessage("다 먹었나요? 목록에서 지울까요?")
                        .setPositiveButton("지우기") { _, _ -> Repo.delete(h, item.id) }
                        .setNegativeButton("남겨두기", null).show()
                }
            }
            .setNegativeButton("취소", null).show()
    }

    private fun confirmDelete(item: Item) {
        val h = hid ?: return
        AlertDialog.Builder(this)
            .setMessage("'${item.name}'을(를) 삭제할까요? 함께 쓰는 사람 목록에서도 지워져요")
            .setPositiveButton("삭제") { _, _ -> Repo.delete(h, item.id) }
            .setNegativeButton("취소", null).show()
    }

    private val adapter = object : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(p: Int) = shown[p]
        override fun getItemId(p: Int) = p.toLong()

        override fun getView(p: Int, convert: View?, parent: ViewGroup): View {
            val v = convert ?: layoutInflater.inflate(R.layout.item_row, parent, false)
            val item = shown[p]
            val left = ChronoUnit.DAYS.between(LocalDate.now(), item.due())

            Thumb.bind(v.findViewById<ImageView>(R.id.thumbImg), v.findViewById(R.id.thumbIcon), item)
            v.findViewById<View>(R.id.thumb).setOnClickListener { Thumb.showBig(this@MainActivity, item) }
            v.findViewById<TextView>(R.id.name).text = item.name
            v.findViewById<TextView>(R.id.sub).text = item.subText()
            v.findViewById<TextView>(R.id.dday).apply {
                text = when {
                    left > 0 -> "D-$left"
                    left == 0L -> if (item.kind == HOME) "오늘" else "D-DAY"
                    item.kind == HOME -> "밀림"
                    else -> "지남"
                }
                setTextColor(getColor(when {
                    left <= 0 -> R.color.danger
                    left <= 3 -> R.color.warn
                    else -> R.color.brand
                }))
            }
            v.findViewById<View>(R.id.btnDone).apply {
                visibility = if (item.kind == HOME) View.VISIBLE else View.GONE
                setOnClickListener { complete(item) }
            }
            v.setOnClickListener { edit(item) }
            v.setOnLongClickListener { options(item); true }
            return v
        }
    }
}
