package com.jibanilji.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.android.gms.tasks.Tasks
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** 알림용 로컬 사본 (사진 제외). 앱이 꺼져 있어도 알림 내용을 만들 수 있게 */
object Mirror {
    fun save(ctx: Context, items: List<Item>) {
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject(it.toMap(withPhoto = false)).put("id", it.id)) }
        ctx.getSharedPreferences("mirror", 0).edit().putString("items", arr.toString()).apply()
    }

    fun all(ctx: Context): List<Item> {
        val arr = JSONArray(ctx.getSharedPreferences("mirror", 0).getString("items", "[]")!!)
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val m = o.keys().asSequence().associateWith { k ->
                when (val v = o.get(k)) {
                    is JSONArray -> List(v.length()) { v.get(it) }
                    JSONObject.NULL -> null
                    else -> v
                }
            }
            Item.from(o.getString("id"), m)
        }
    }
}

object Alarms {
    private fun code(id: String, d: Int) = id.hashCode() * 16 + d

    /** 전체 목록 기준으로 알림을 다시 예약 (다른 가구원이 바꾼 내용도 반영) */
    fun scheduleAll(ctx: Context, items: List<Item>) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val prefs = ctx.getSharedPreferences("alarms", 0)
        prefs.getStringSet("codes", emptySet())!!.forEach { c ->
            am.cancel(pending(ctx, c.toInt(), null, 0))
        }
        val now = System.currentTimeMillis()
        val codes = mutableSetOf<String>()
        val me = Repo.uid()
        for (item in items) for (d in item.offsets) {
            if (item.assignee.isNotEmpty() && item.assignee != me) continue // 담당자에게만
            val at = item.due().minusDays(d.toLong()).atTime(item.hour, item.minute)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (at <= now) continue
            val c = code(item.id, d)
            val pi = pending(ctx, c, item.id, d)
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms())
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            codes += c.toString()
        }
        prefs.edit().putStringSet("codes", codes).apply()
    }

    private fun pending(ctx: Context, code: Int, id: String?, d: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, code,
            Intent(ctx, ReminderReceiver::class.java).putExtra("id", id).putExtra("d", d),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun notify(ctx: Context, item: Item, d: Int) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("expiry", "집안일지 알림", NotificationManager.IMPORTANCE_HIGH)
        )
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val date = item.due().format(DateTimeFormatter.ofPattern("M월 d일"))
        var text = if (item.kind == HOME) when (d) {
            0 -> "오늘 할 차례예요 ✅ 하고 나면 '완료'를 눌러주세요"
            1 -> "내일($date) 할 차례예요"
            else -> "${d}일 남았어요 ($date)"
        } else when (d) {
            0 -> "오늘($date)까지예요! 얼른 드세요 🍽️"
            1 -> "내일($date)까지예요"
            else -> "${d}일 남았어요 (${date}까지)"
        }
        if (item.kind == HOME && item.qty == 0) text += "\n⚠️ 재고가 없어요. 미리 사두세요"

        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, "expiry")
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("${item.icon} ${item.name}")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(ctx).notify(code(item.id, d), n)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val item = Mirror.all(ctx).firstOrNull { it.id == id } ?: return
        Alarms.notify(ctx, item, intent.getIntExtra("d", 0))
    }
}

/** 재부팅/앱 업데이트 시 알림이 사라지므로 다시 등록 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Alarms.scheduleAll(ctx, Mirror.all(ctx))
        SyncWorker.start(ctx)
    }
}

/** 앱을 안 열어도 몇 시간마다 공유 목록을 받아와 알림을 맞춤 */
class SyncWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val hid = Repo.cachedHid(applicationContext) ?: return Result.success()
        if (Repo.uid() == null) return Result.success()
        return try {
            val snap = Tasks.await(Repo.itemsRef(hid).get(), 60, TimeUnit.SECONDS)
            val items = snap.documents.map { Item.from(it.id, it.data ?: emptyMap()) }
            Mirror.save(applicationContext, items)
            Alarms.scheduleAll(applicationContext, items)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        fun start(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx)
                .enqueueUniquePeriodicWork("sync", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
