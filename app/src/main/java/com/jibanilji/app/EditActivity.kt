package com.jibanilji.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputLayout
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class EditActivity : AppCompatActivity() {
    private val UNITS = listOf("일", "주", "개월", "년")
    private val DAY_LABELS = listOf("월", "화", "수", "목", "금", "토", "일")
    private val STORAGE_IDS = mapOf("냉장" to R.id.stFridge, "냉동" to R.id.stFreezer, "상온" to R.id.stRoom)

    private var existing: Item? = null
    private var hid = ""
    private var kind = FOOD
    private var usePhoto = false
    private var icon = ""
    private var storage = ""
    private var byDays = false
    private var days = mutableSetOf<Int>()
    private var dateTouched = false
    private var newPhoto: String? = null
    private var pendingPath: String? = null
    private var date: LocalDate? = null
    private var hour = 0
    private var minute = 0
    private var memberIds: List<String> = emptyList()

    private lateinit var photo: ImageView
    private lateinit var status: TextView
    private lateinit var name: EditText
    private lateinit var cycle: EditText
    private lateinit var unit: Spinner
    private lateinit var assignee: Spinner
    private lateinit var qty: EditText
    private lateinit var volume: EditText
    private lateinit var stockSwitch: MaterialSwitch
    private lateinit var btnDate: Button
    private lateinit var btnTime: Button
    private lateinit var nextInfo: TextView
    private lateinit var iconGrid: GridLayout
    private lateinit var daysRow: ChipGroup
    private lateinit var checks: Map<Int, CheckBox>

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = pendingPath?.let(::File) ?: return@registerForActivityResult
        if (ok && f.length() > 0) onPhotoTaken(f) else f.delete()
        pendingPath = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hid = Repo.cachedHid(this) ?: return finish()
        val id = intent.getStringExtra("id")
        existing = id?.let { Repo.latest[it] }
        if (id != null && existing == null) { toast("항목을 찾지 못했어요"); finish(); return }
        kind = existing?.kind ?: intent.getStringExtra("kind") ?: FOOD
        val isHome = kind == HOME

        setContentView(R.layout.activity_edit)
        photo = findViewById(R.id.photo)
        status = findViewById(R.id.status)
        name = findViewById(R.id.name)
        cycle = findViewById(R.id.cycle)
        unit = findViewById(R.id.unit)
        assignee = findViewById(R.id.assignee)
        qty = findViewById(R.id.qty)
        volume = findViewById(R.id.volume)
        stockSwitch = findViewById(R.id.stockSwitch)
        btnDate = findViewById(R.id.btnDate)
        btnTime = findViewById(R.id.btnTime)
        nextInfo = findViewById(R.id.nextInfo)
        iconGrid = findViewById(R.id.iconGrid)
        daysRow = findViewById(R.id.daysRow)
        checks = mapOf(7 to R.id.c7, 3 to R.id.c3, 1 to R.id.c1, 0 to R.id.c0)
            .mapValues { findViewById<CheckBox>(it.value) }
        unit.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, UNITS)

        val ex = existing
        if (savedInstanceState != null) {
            usePhoto = savedInstanceState.getBoolean("usePhoto")
            icon = savedInstanceState.getString("icon") ?: Thumb.defaultIcon(kind)
            storage = savedInstanceState.getString("storage") ?: ""
            byDays = savedInstanceState.getBoolean("byDays")
            days = savedInstanceState.getIntArray("days")?.toMutableSet() ?: mutableSetOf()
            dateTouched = savedInstanceState.getBoolean("dateTouched")
            newPhoto = savedInstanceState.getString("newPhoto")
            pendingPath = savedInstanceState.getString("pending")
            date = savedInstanceState.getLong("date", Long.MIN_VALUE)
                .takeIf { it != Long.MIN_VALUE }?.let(LocalDate::ofEpochDay)
            hour = savedInstanceState.getInt("hour"); minute = savedInstanceState.getInt("minute")
        } else if (ex != null) {
            name.setText(ex.name)
            icon = ex.icon; storage = ex.storage
            usePhoto = ex.photo != null
            byDays = ex.byDays; days = ex.days.toMutableSet()
            date = LocalDate.ofEpochDay(ex.base); dateTouched = true
            cycle.setText(ex.cycle.toString())
            unit.setSelection(UNITS.indexOf(ex.unit).coerceAtLeast(0))
            hour = ex.hour; minute = ex.minute
            checks.forEach { (d, cb) -> cb.isChecked = d in ex.offsets }
            ex.qty?.let { qty.setText(it.toString()) }
            volume.setText(ex.volume)
            stockSwitch.isChecked = ex.qty != null
        } else {
            icon = Thumb.defaultIcon(kind)
            val now = LocalTime.now(); hour = now.hour; minute = now.minute // 시간 미설정 시 현재 시간
            if (isHome) {
                date = LocalDate.now()
                cycle.setText("1"); unit.setSelection(UNITS.indexOf("주"))
                listOf(1, 0).forEach { checks[it]!!.isChecked = true }
            } else {
                storage = "냉장"
                date = intent.getLongExtra("date", Long.MIN_VALUE)
                    .takeIf { it != Long.MIN_VALUE }?.let(LocalDate::ofEpochDay)
                listOf(3, 1, 0).forEach { checks[it]!!.isChecked = true }
            }
        }

        // 식품 / 집안일에 맞게 화면 구성
        title = (if (ex != null) "수정" else "추가") + if (isHome) " · 집안일" else " · 식품"
        findViewById<TextInputLayout>(R.id.nameLayout).hint =
            if (isHome) "이름 (예: 화장실 청소, 칫솔 교체)" else "이름 (예: 우유)"
        findViewById<TextView>(R.id.dateLabel).text = if (isHome) "마지막으로 한 날" else "소비기한 (유통기한)"
        listOf(R.id.cycleBox, R.id.assigneeBox).forEach {
            findViewById<View>(it).visibility = if (isHome) View.VISIBLE else View.GONE
        }
        findViewById<View>(R.id.storageBox).visibility = if (isHome) View.GONE else View.VISIBLE
        nextInfo.visibility = if (isHome) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.btnPhoto).text =
            if (isHome) "📷  사진 찍기" else "📷  사진 찍기 (소비기한 글자가 보이게)"

        setupStorage()
        setupQty()
        setupRepeat()
        setupAssignee()
        buildIconGrid()
        val mode = findViewById<MaterialButtonToggleGroup>(R.id.mode)
        mode.check(if (usePhoto) R.id.modePhoto else R.id.modeIcon)
        mode.addOnButtonCheckedListener { _, bid, checked ->
            if (checked) { usePhoto = bid == R.id.modePhoto; applyMode() }
        }
        applyMode()

        (newPhoto?.let { Photos.thumb(it, 800) } ?: Photos.decode(ex?.photo))?.let { photo.setImageBitmap(it) }
        updateButtons()

        val shoot = View.OnClickListener { openCamera() }
        photo.setOnClickListener(shoot)
        findViewById<View>(R.id.btnPhoto).setOnClickListener(shoot)
        btnDate.setOnClickListener { pickDate() }
        btnTime.setOnClickListener { pickTime() }
        cycle.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = updateButtons()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        unit.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = updateButtons()
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        findViewById<View>(R.id.btnSave).setOnClickListener { save() }

        if (ex != null) {
            findViewById<View>(R.id.btnDelete).apply {
                visibility = View.VISIBLE
                setOnClickListener {
                    AlertDialog.Builder(this@EditActivity)
                        .setMessage("삭제할까요? 함께 쓰는 사람 목록에서도 지워져요")
                        .setPositiveButton("삭제") { _, _ -> Repo.delete(hid, ex.id); finish() }
                        .setNegativeButton("취소", null).show()
                }
            }
            if (isHome) loadLogs(ex.id)
        }
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putBoolean("usePhoto", usePhoto); out.putString("icon", icon)
        out.putString("storage", storage); out.putBoolean("byDays", byDays)
        out.putIntArray("days", days.toIntArray()); out.putBoolean("dateTouched", dateTouched)
        out.putString("newPhoto", newPhoto); out.putString("pending", pendingPath)
        date?.let { out.putLong("date", it.toEpochDay()) }
        out.putInt("hour", hour); out.putInt("minute", minute)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) newPhoto?.let { File(it).delete() } // 임시 사진 정리
    }

    // ---------- 식품: 보관 방식 ----------
    private fun setupStorage() {
        val g = findViewById<MaterialButtonToggleGroup>(R.id.storage)
        STORAGE_IDS[storage]?.let { g.check(it) }
        g.addOnButtonCheckedListener { _, bid, checked ->
            if (checked) storage = STORAGE_IDS.entries.first { it.value == bid }.key
            else if (g.checkedButtonId == View.NO_ID) storage = ""
        }
    }

    // ---------- 집안일: 반복 방식 ----------
    private fun setupRepeat() {
        DAY_LABELS.forEachIndexed { i, label ->
            val dow = i + 1
            daysRow.addView(Chip(this).apply {
                text = label; isCheckable = true; isChecked = dow in days
                setOnCheckedChangeListener { _, on -> if (on) days.add(dow) else days.remove(dow); updateButtons() }
            })
        }
        val g = findViewById<MaterialButtonToggleGroup>(R.id.repeat)
        g.check(if (byDays) R.id.repDays else R.id.repInterval)
        g.addOnButtonCheckedListener { _, bid, checked ->
            if (checked) { byDays = bid == R.id.repDays; applyRepeat(); updateButtons() }
        }
        applyRepeat()
    }

    private fun applyRepeat() {
        daysRow.visibility = if (byDays) View.VISIBLE else View.GONE
        findViewById<View>(R.id.intervalRow).visibility = if (byDays) View.GONE else View.VISIBLE
    }

    // ---------- 집안일: 담당자 ----------
    private fun setupAssignee() {
        val members = Repo.members.entries.sortedBy { it.value }
        memberIds = listOf("") + members.map { it.key }
        val labels = listOf("모두 (담당 없음)") + members.map { it.value }
        assignee.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        existing?.assignee?.let { a -> memberIds.indexOf(a).takeIf { it >= 0 }?.let { assignee.setSelection(it) } }
    }

    // ---------- 완료 기록 ----------
    private fun loadLogs(id: String) {
        val box = findViewById<View>(R.id.logBox)
        val tv = findViewById<TextView>(R.id.logs)
        box.visibility = View.VISIBLE
        tv.text = "불러오는 중…"
        val f = DateTimeFormatter.ofPattern("yyyy.M.d (E) a h:mm")
        Repo.logs(hid, id) { logs ->
            tv.text = if (logs.isEmpty()) "아직 기록이 없어요. 목록에서 '완료'를 누르면 남아요"
            else logs.joinToString("\n") {
                val t = Instant.ofEpochMilli(it.at).atZone(ZoneId.systemDefault()).format(f)
                "✅ $t · ${it.by}"
            }
        }
    }

    // ---------- 수량 · 용량 · 재고 ----------
    private fun setupQty() {
        val isHome = kind == HOME
        val label = findViewById<TextView>(R.id.qtyLabel)
        val row = findViewById<View>(R.id.qtyRow)
        val hint = findViewById<TextView>(R.id.qtyHint)
        volume.visibility = if (isHome) View.GONE else View.VISIBLE
        stockSwitch.visibility = if (isHome) View.VISIBLE else View.GONE

        if (isHome) {
            label.text = "재고 수량"
            hint.text = "'완료'를 누르면 재고가 1개씩 줄어요. 청소처럼 재고가 없는 일이면 꺼두세요"
            fun showStock(on: Boolean) {
                listOf(label, row, hint).forEach { it.visibility = if (on) View.VISIBLE else View.GONE }
                if (on && qty.text.isNullOrBlank()) qty.setText("1")
            }
            showStock(stockSwitch.isChecked)
            stockSwitch.setOnCheckedChangeListener { _, on -> showStock(on) }
        } else {
            label.text = "수량 · 용량 (선택)"
            hint.text = "비워두면 표시하지 않아요. 일부만 쓰면 목록에서 길게 눌러 바꿀 수 있어요"
        }

        fun step(delta: Int) {
            val cur = qty.text?.toString()?.toIntOrNull() ?: 0
            qty.setText((cur + delta).coerceIn(0, 9999).toString())
        }
        findViewById<View>(R.id.minus).setOnClickListener { step(-1) }
        findViewById<View>(R.id.plus).setOnClickListener { step(1) }
    }

    // ---------- 아이콘 (카테고리별) ----------
    private fun buildIconGrid() {
        iconGrid.removeAllViews()
        val cols = iconGrid.columnCount
        for ((group, icons) in Thumb.groups(kind)) {
            iconGrid.addView(TextView(this).apply {
                text = group; textSize = 12f; setTypeface(typeface, Typeface.BOLD)
                setTextColor(getColor(R.color.muted))
                setPadding(dp(4), dp(10), 0, dp(2))
                layoutParams = GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(0, cols, 1f)
                ).apply { width = 0 }
            })
            icons.forEach { ic ->
                iconGrid.addView(TextView(this).apply {
                    text = ic; textSize = 24f; gravity = Gravity.CENTER; tag = ic
                    contentDescription = "$group 아이콘"
                    layoutParams = GridLayout.LayoutParams(
                        GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    ).apply { width = 0; height = dp(50); setMargins(3, 3, 3, 3) }
                    setOnClickListener { icon = ic; refreshIconSelection() }
                })
            }
        }
        refreshIconSelection()
    }

    private fun refreshIconSelection() {
        for (i in 0 until iconGrid.childCount) {
            val v = iconGrid.getChildAt(i)
            if (v.tag == null) continue // 카테고리 제목
            val on = v.tag == icon
            v.background = if (on) Thumb.bg(this, Thumb.colorFor(kind, icon)) else null
            v.alpha = if (on) 1f else 0.75f
        }
    }

    private fun applyMode() {
        iconGrid.visibility = if (usePhoto) View.GONE else View.VISIBLE
        findViewById<View>(R.id.photoBox).visibility = if (usePhoto) View.VISIBLE else View.GONE
    }

    // ---------- 사진 ----------
    private fun openCamera() {
        val dir = File(cacheDir, "cam").apply { mkdirs() }
        val f = File(dir, "p_${System.currentTimeMillis()}.jpg")
        pendingPath = f.path
        try {
            takePicture.launch(FileProvider.getUriForFile(this, "$packageName.fileprovider", f))
        } catch (e: Exception) {
            toast("카메라 앱을 열 수 없어요")
        }
    }

    private fun onPhotoTaken(f: File) {
        Photos.normalize(f)
        newPhoto?.let { File(it).delete() }
        newPhoto = f.path
        photo.setImageBitmap(Photos.thumb(f.path, 800))
        if (kind == HOME) { status.text = ""; return }

        status.text = "🔎 글자 인식 중…"
        Ocr.recognize(this, Uri.fromFile(f)) { guessed, d ->
            val msgs = mutableListOf<String>()
            if (d != null) {
                date = d; updateButtons()
                msgs += "📅 날짜 인식: ${d.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))} (맞는지 확인해 주세요)"
            } else msgs += "날짜를 못 찾았어요. 직접 선택해 주세요"
            if (guessed != null && name.text.isNullOrBlank()) {
                name.setText(guessed); msgs += "✏️ 이름 후보를 넣었어요. 필요하면 고쳐 주세요"
            }
            status.text = msgs.joinToString("\n")
        }
    }

    // ---------- 날짜 / 시간 ----------
    private fun pickDate() {
        val d = date ?: LocalDate.now()
        DatePickerDialog(this, { _, y, m, day ->
            date = LocalDate.of(y, m + 1, day); dateTouched = true; updateButtons()
        }, d.year, d.monthValue - 1, d.dayOfMonth).show()
    }

    private fun pickTime() {
        TimePickerDialog(this, { _, h, m -> hour = h; minute = m; updateButtons() },
            hour, minute, false).show()
    }

    private fun cycleValue() = cycle.text?.toString()?.toIntOrNull()?.takeIf { it > 0 }
    private fun unitValue() = UNITS[unit.selectedItemPosition.coerceAtLeast(0)]

    /** 새 요일 반복 항목은 '어제' 기준 → 오늘이 해당 요일이면 오늘부터 잡힘 */
    private fun effectiveBase(d: LocalDate) =
        if (kind == HOME && byDays && existing == null && !dateTouched) d.minusDays(1) else d

    private fun previewItem(): Item? {
        val d = date ?: return null
        if (byDays && days.isEmpty()) return null
        val c = if (byDays) 1 else cycleValue() ?: return null
        return Item(kind = HOME, base = effectiveBase(d).toEpochDay(), cycle = c, unit = unitValue(),
            repeat = if (byDays) "days" else "interval", days = days.sorted())
    }

    private fun updateButtons() {
        btnDate.text = date?.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)")) ?: "날짜 선택"
        btnTime.text = LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern("a h:mm"))
        if (kind == HOME) {
            nextInfo.text = previewItem()?.let {
                "→ 다음 할 날 ${it.due().format(DateTimeFormatter.ofPattern("yyyy.M.d (E)"))}"
            } ?: ""
        }
    }

    // ---------- 저장 ----------
    private fun save() {
        val n = name.text?.toString()?.trim().orEmpty()
        val d = date
        val c = if (kind == HOME && !byDays) cycleValue() else 1
        val offs = checks.filter { it.value.isChecked }.keys.sortedDescending()
        when {
            n.isEmpty() -> return toast("이름을 입력해 주세요")
            d == null -> return toast(if (kind == HOME) "마지막으로 한 날을 선택해 주세요" else "소비기한 날짜를 선택해 주세요")
            c == null -> return toast("반복 주기를 숫자로 입력해 주세요")
            kind == HOME && byDays && days.isEmpty() -> return toast("반복할 요일을 골라 주세요")
            offs.isEmpty() -> return toast("알림 받을 날을 하나 이상 골라 주세요")
        }
        if (existing == null && !Premium.canAdd(Repo.latest.values, kind)) {
            val what = if (kind == HOME) "집안일" else "식품"
            return Premium.upsell(this, "무료로는 ${what}을 ${Premium.FREE_LIMIT}개까지 등록할 수 있어요.")
        }
        val q = qty.text?.toString()?.toIntOrNull()
        val tracked = if (kind == HOME) stockSwitch.isChecked else true
        val photoData = if (!usePhoto) null
        else newPhoto?.let { Photos.toBase64(File(it)) } ?: existing?.photo
        val who = memberIds.getOrNull(assignee.selectedItemPosition).orEmpty()

        val item = (existing?.copy() ?: Item(by = Repo.nick(this))).apply {
            kind = this@EditActivity.kind
            name = n; icon = this@EditActivity.icon; photo = photoData
            color = Thumb.colorFor(kind, icon)
            base = effectiveBase(d!!).toEpochDay(); cycle = c!!; unit = unitValue()
            repeat = if (kind == HOME && byDays) "days" else "interval"
            days = if (kind == HOME && byDays) this@EditActivity.days.sorted() else emptyList()
            hour = this@EditActivity.hour; minute = this@EditActivity.minute
            offsets = offs
            qty = if (tracked) q else null
            volume = if (kind == FOOD) this@EditActivity.volume.text.toString().trim() else ""
            storage = if (kind == FOOD) this@EditActivity.storage else ""
            assignee = if (kind == HOME) who else ""
            assigneeName = if (kind == HOME) Repo.members[who].orEmpty() else ""
        }
        Repo.save(hid, item)
        toast("저장했어요 🔔")
        finish()
    }
}
