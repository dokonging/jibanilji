package com.jibanilji.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.firebase.firestore.ListenerRegistration
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 하단 탭: 캘린더 / 식품 / 집안일 / 설정 */
class MainActivity : AppCompatActivity() {
    var all: List<Item> = emptyList(); private set
    private var hid: String? = null
    private var reg: ListenerRegistration? = null
    private var regHouse: ListenerRegistration? = null
    private var tab = 0

    private lateinit var calendar: CalendarPage
    private lateinit var food: KindPage
    private lateinit var home: KindPage
    private lateinit var settings: SettingsPage
    private lateinit var pages: List<View>
    private lateinit var fab: ExtendedFloatingActionButton
    private lateinit var banner: TextView
    private val tabViews = mutableListOf<LinearLayout>()

    private val askNotify = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Repo.uid() == null) { toLogin(); return }
        edgeToEdge()
        tab = savedInstanceState?.getInt("tab") ?: 0

        calendar = CalendarPage(this)
        food = KindPage(this, FOOD)
        home = KindPage(this, HOME)
        settings = SettingsPage(this)
        pages = listOf(calendar, food, home, settings)

        val content = FrameLayout(this)
        pages.forEach { content.addView(it) }
        banner = TextView(this).apply {
            textSize = 13f; setTextColor(c(R.color.danger)); visibility = View.GONE
            background = rounded(c(R.color.danger_soft), 14)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setOnClickListener { visibility = View.GONE }
        }
        content.addView(banner, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            setMargins(dp(16), 0, dp(16), dp(88))
        })
        fab = ExtendedFloatingActionButton(this).apply {
            setOnClickListener { add() }
        }
        content.addView(fab, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply {
            setMargins(0, 0, dp(20), dp(20))
        })

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(c(R.color.bg))
        }
        content.applyInsets(top = true, bottom = false)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(bottomBar(), LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
        select(tab)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        SyncWorker.start(this)
    }

    private fun bottomBar(): View {
        val bar = LinearLayout(this).apply {
            setBackgroundColor(c(R.color.surface))
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
        listOf("📅" to "캘린더", "🧊" to "식품", "🧹" to "집안일", "⚙️" to "설정").forEachIndexed { i, (icon, label) ->
            val t = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, dp(6))
                addView(TextView(context).apply { text = icon; textSize = 20f; gravity = Gravity.CENTER })
                addView(TextView(context).apply { text = label; textSize = 12f; gravity = Gravity.CENTER })
                contentDescription = label
                setOnClickListener { select(i) }
            }
            tabViews += t
            bar.addView(t, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(4), 0, dp(4), 0) })
        }
        bar.applyInsets(top = false, bottom = true)
        return bar
    }

    private fun select(i: Int) {
        tab = i
        pages.forEachIndexed { k, v -> v.visibility = if (k == i) View.VISIBLE else View.GONE }
        tabViews.forEachIndexed { k, t ->
            val on = k == i
            t.background = if (on) rounded(c(R.color.brand_soft), 16) else null
            (t.getChildAt(1) as TextView).apply {
                setTextColor(c(if (on) R.color.brand else R.color.muted))
                setTypeface(null, if (on) Typeface.BOLD else Typeface.NORMAL)
            }
            t.getChildAt(0).alpha = if (on) 1f else 0.6f
        }
        fab.visibility = if (i == 3) View.GONE else View.VISIBLE
        fab.text = when (i) { 1 -> "식품 추가"; 2 -> "집안일 추가"; else -> "추가" }
        fab.setIconResource(android.R.drawable.ic_input_add)
        refresh()
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putInt("tab", tab)
    }

    override fun onStart() {
        super.onStart()
        if (Repo.uid() == null) return
        val h = Repo.cachedHid(this)
        if (h == null) {
            Repo.ensureHousehold(this, null) { got, err ->
                if (got != null) { hid = got; listen() } else showError("집 정보를 불러오지 못했어요: $err")
            }
            return
        }
        if (h != hid) { stopListening(); hid = h }
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

    /** 다른 집에 참여한 뒤 다시 연결 */
    fun reconnect() {
        stopListening(); hid = Repo.cachedHid(this); all = emptyList()
        refreshTitles(); refresh(); settings.refreshHouses(); listen()
    }

    private fun listen() {
        val h = hid ?: return
        if (regHouse == null) regHouse = Repo.listenHousehold(this, h, ::showError) { data ->
            settings.update(data)
            refreshTitles()
            Alarms.scheduleAll(this, Mirror.all(this)) // 담당자 정보가 바뀌었을 수 있음
        }
        if (reg == null) reg = Repo.listen(h, ::showError) { items ->
            all = items
            Repo.lastError = null
            banner.visibility = View.GONE
            Mirror.save(this, h, items)
            Alarms.scheduleAll(this, Mirror.all(this))
            refresh()
        }
        Repo.syncOtherHouses(this)
    }

    /** 각 페이지 상단에 지금 보는 집 이름 표시 (집이 여러 개일 때 누르면 전환) */
    private fun refreshTitles() {
        val name = Repo.houseName(this, hid ?: return)
        val multi = Repo.houses(this).size > 1
        listOf(calendar, food, home).forEach { it.setHouse(name, multi) }
    }

    /** 집 전환 */
    fun pickHouse() {
        val hs = Repo.houses(this).entries.toList()
        if (hs.size < 2) { select(3); return toast("설정에서 새 집을 만들거나 다른 집에 참여할 수 있어요") }
        AlertDialog.Builder(this).setTitle("어느 집을 볼까요?")
            .setItems(hs.map { (id, n) -> (if (id == hid) "✓ " else "    ") + n }.toTypedArray()) { _, w ->
                switchHouse(hs[w].key)
            }.show()
    }

    fun switchHouse(id: String) {
        if (id == hid) return
        Repo.switchTo(this, id)
        reconnect()
        toast("'${Repo.houseName(this, id)}'(으)로 바꿨어요")
    }

    private fun showError(msg: String) {
        Repo.lastError = msg
        banner.text = "⚠️ 불러오지 못했어요 · $msg"
        banner.visibility = View.VISIBLE
        settings.refreshStatus()
    }

    private fun refresh() {
        if (!::calendar.isInitialized) return
        when (tab) {
            0 -> calendar.refresh()
            1 -> food.refresh()
            2 -> home.refresh()
            else -> settings.refreshStatus()
        }
    }

    fun toLogin() {
        startActivity(Intent(this, LoginActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }

    // ---------- 추가 ----------
    private fun add() {
        val h = hid ?: return toast("아직 집 정보를 불러오는 중이에요")
        fun open(kind: String) {
            if (!Premium.canAdd(all, kind)) {
                val what = if (kind == HOME) "집안일" else "식품"
                return Premium.upsell(this, "무료로는 ${what}을 ${Premium.FREE_LIMIT}개까지 등록할 수 있어요.")
            }
            val i = Intent(this, EditActivity::class.java).putExtra("kind", kind)
            if (tab == 0 && kind == FOOD) i.putExtra("date", calendar.cal.selected.toEpochDay())
            startActivity(i)
        }
        when (tab) {
            1 -> open(FOOD)
            2 -> open(HOME)
            else -> AlertDialog.Builder(this).setTitle("무엇을 추가할까요?")
                .setItems(arrayOf("🧊 식품", "🧹 집안일", "⭐ 추천 집안일 루틴")) { _, w ->
                    when (w) { 0 -> open(FOOD); 1 -> open(HOME); else -> showRoutines() }
                }.show()
        }
    }

    fun showRoutines() {
        if (hid == null) return toast("아직 집 정보를 불러오는 중이에요")
        startActivity(Intent(this, RoutinesActivity::class.java))
    }

    fun openEdit(item: Item) =
        startActivity(Intent(this, EditActivity::class.java).putExtra("id", item.id))

    // ---------- 행 동작 ----------
    fun complete(item: Item) {
        val h = hid ?: return
        Repo.complete(this, h, item)
        val next = item.due().format(DateTimeFormatter.ofPattern("M.d (E)"))
        toast("✅ 완료! 다음은 $next" + (item.qty?.let { " · 재고 ${it}개 남음" } ?: ""))
    }

    fun options(item: Item) {
        val isHome = item.kind == HOME
        val actions = if (isHome) listOf(
            "✅ 완료 기록하기" to { complete(item) },
            "📦 재고 수정" to { updateQty(item) },
            "✏️ 수정 · 기록 보기" to { openEdit(item) },
            "🗑️ 삭제" to { confirmDelete(item) },
        ) else listOf(
            "📦 남은 수량 · 용량 수정" to { updateQty(item) },
            "✏️ 수정" to { openEdit(item) },
            "🗑️ 삭제" to { confirmDelete(item) },
        )
        AlertDialog.Builder(this).setTitle("${item.icon} ${item.name}")
            .setItems(actions.map { it.first }.toTypedArray()) { _, w -> actions[w].second() }
            .show()
    }

    /** 일부만 사용했을 때: 남은 수량(재고) · 용량 빠르게 수정 */
    private fun updateQty(item: Item) {
        val h = hid ?: return
        val isHome = item.kind == HOME
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
        }
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
        col.text(if (isHome) "남은 재고 (새로 사면 + 버튼)" else "남은 수량", 14f, color = R.color.muted)
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(stepBtn("−1", -1)); row.addView(num); row.addView(stepBtn("+1", 1))
        if (isHome) row.addView(stepBtn("+5", 5))
        col.addView(row)
        val vol = if (isHome) null else {
            col.text("남은 용량", 14f, top = 12, color = R.color.muted)
            col.input("예: 500ml, 반 통").apply { setText(item.volume) }
        }

        AlertDialog.Builder(this).setTitle("${item.icon} ${item.name}")
            .setView(col)
            .setPositiveButton("저장") { _, _ ->
                val q = num.text.toString().toIntOrNull() ?: 0
                item.qty = q
                vol?.let { item.volume = it.text.toString().trim() }
                Repo.save(h, item, this)
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
}
