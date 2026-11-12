package com.amjadoh.sirfari

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * إشعارات أندرويد بدون أي خدمة جديدة ولا طرف ثالث: كل ~15 دقيقة (WorkManager، وأندرويد ممكن يأخرها)
 * يقرا /api/overview و/api/admin عن طريق Tailscale، ويقارن بآخر اللي شافه.
 * - التطبيق: «تنتظر موافقتك»، و«خلصت/فشلت»، وطلبات root. تلقرام يبقى لمشاكل السيرفر الحرجة (ما نكررها هنا).
 * - كل حدث له مفتاح ثابت (مثل await:MSR-0002)، فما يطلع مرتين. وأول تشغيل يحفظ الموجود بدون ما يزعجك.
 */
object Inbox {
    data class Event(val key: String, val channel: String, val title: String, val text: String, val taskId: String?)

    /** صافية (تنختبر): الأحداث الحالية من ردود السيرفر */
    fun events(overview: JSONObject, admin: JSONObject?, now: Long): List<Event> {
        val out = mutableListOf<Event>()
        val tasks = overview.optJSONArray("tasks")
        for (i in 0 until (tasks?.length() ?: 0)) {
            val t = tasks!!.optJSONObject(i) ?: continue
            val id = t.optString("taskId")
            val title = t.optString("title").ifBlank { id }
            when (t.optString("status")) {
                "awaiting" -> if (t.optString("planState") == "ready" && !t.optBoolean("planPending"))
                    out += Event("await:$id:${t.optInt("revisions")}", CH_APPROVALS, "تنتظر موافقتك: $id", title, id)
                "done", "failed" -> {
                    val at = if (t.isNull("finishedAt")) 0L else t.optLong("finishedAt")
                    if (now - at < 24 * 3600_000L) {
                        val ok = t.optString("status") == "done"
                        out += Event("end:$id", CH_TASKS, if (ok) "✅ خلصت: $id" else "❌ فشلت: $id", title, id)
                    }
                }
            }
        }
        val reqs = admin?.optJSONArray("requests")
        for (i in 0 until (reqs?.length() ?: 0)) {
            val r = reqs!!.optJSONObject(i) ?: continue
            if (r.optString("state") == "pending") out += Event("admin:${r.optString("id")}", CH_APPROVALS, "طلب root ينتظر بصمتك", r.optString("summary"), null)
        }
        return out
    }

    /** صافية: الجديد بس، والمحفوظ بعد التحديث (آخر 300 مفتاح) */
    fun diff(current: List<Event>, seen: Set<String>, firstRun: Boolean): Pair<List<Event>, Set<String>> {
        val fresh = if (firstRun) emptyList() else current.filter { it.key !in seen }
        val keep = (seen + current.map { it.key }).toList().takeLast(300).toSet()
        return fresh to keep
    }

    const val CH_APPROVALS = "approvals"
    const val CH_TASKS = "tasks"
    const val EXTRA_TASK = "task"

    fun schedule(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<InboxWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("inbox", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_APPROVALS, "تنتظر موافقتك", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_TASKS, "المهام", NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun show(ctx: Context, e: Event) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        e.taskId?.let { open.putExtra(EXTRA_TASK, it) }
        val pi = PendingIntent.getActivity(ctx, e.key.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, e.channel)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(e.title).setContentText(e.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(e.text))
            .setContentIntent(pi).setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(e.key.hashCode(), n)
    }
}

class InboxWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val api = Api()
        val overview = runCatching { api.get("/api/overview") }.getOrElse { return Result.success() } // Tailscale طافي: نحاول المرة الجاية
        val admin = runCatching { api.get("/api/admin") }.getOrNull()
        val prefs = applicationContext.getSharedPreferences("inbox", Context.MODE_PRIVATE)
        val first = !prefs.getBoolean("started", false)
        val (fresh, keep) = Inbox.diff(Inbox.events(overview, admin, System.currentTimeMillis()), prefs.getStringSet("seen", emptySet()) ?: emptySet(), first)
        prefs.edit().putStringSet("seen", keep).putBoolean("started", true).apply()
        Inbox.channels(applicationContext)
        fresh.take(5).forEach { Inbox.show(applicationContext, it) }
        return Result.success()
    }
}
