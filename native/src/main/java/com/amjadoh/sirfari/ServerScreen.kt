package com.amjadoh.sirfari

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val GROUPS = listOf("orch" to "المنظّم وسيرفري", "bots" to "البوتات", "infra" to "البنية")

@Composable
fun ServerScreen(r: Repo.Result<ServerView>?, now: Long) {
    FreshnessBar(r, now)
    val s = r?.data ?: return
    if (s.at == null) { Panel { Text("ما فيه بيانات من جامع السيرفر للحين") }; return }

    Panel(s.hostName ?: "السيرفر") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Ring("المعالج", s.cpuPct)
            Ring("الذاكرة", s.mem?.usedPct)
            Ring("القرص", s.disks.firstOrNull { it.mount == "/" }?.pct ?: s.disks.firstOrNull()?.pct)
            Ring("swap", s.mem?.swapPct)
        }
        val extra = buildList {
            if (s.load.isNotEmpty()) add("التحميل ${s.load.joinToString(" · ") { "%.2f".format(it) }}")
            s.mem?.let { add("متاح ${Math.round(it.availMb)} من ${Math.round(it.totalMb)} ميجا") }
            s.uptimeSec?.let { add("شغّال من ${it / 86400} يوم") }
            s.tailscaleOnline?.let { add(if (it) "Tailscale متصل" else "Tailscale مو متصل") }
        }
        Text(extra.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (s.alerts.isNotEmpty()) Panel("التنبيهات") { s.alerts.forEach { StatusLine(Tone.level(it.level), it.text) } }

    if (s.consumers.isNotEmpty()) Panel("أكثر شي ياخذ ذاكرة") {
        s.consumers.take(6).forEach { StatusLine(Tone.idle, (if (it.protected) "🛡️ " else "") + it.label, "${Math.round(it.rssMb)} ميجا") }
    }

    for ((g, title) in GROUPS) {
        val list = s.services.filter { it.group == g }
        if (list.isNotEmpty()) Panel(title) {
            list.forEach { StatusLine(Tone.state(it.state), (if (it.protected) "🛡️ " else "") + it.label, stateText(it.state)) }
        }
    }

    if (s.containers.isNotEmpty()) Panel("الحاويات") {
        s.containers.forEach { c ->
            val st = if (c.onDemand && c.state != "running") "عند الطلب" else c.status.ifBlank { c.state }
            StatusLine(if (c.onDemand && c.state != "running") Tone.idle else Tone.state(c.state), c.label, st)
        }
    }

    if (s.timers.isNotEmpty()) Panel("النسخ الاحتياطي والمراقبة") {
        s.timers.forEach { t -> StatusLine(Tone.state(t.result), t.label, "آخر تشغيل ${ago(t.lastAt, now)}") }
    }

    s.disks.filter { it.mount != "/" }.takeIf { it.isNotEmpty() }?.let { ds ->
        Panel("الأقراص") { ds.forEach { StatusLine(Tone.idle, it.mount, "${pct(it.pct)} من ${Math.round(it.totalGb)} GB") } }
    }
}

private fun stateText(s: String) = when (s) {
    "active" -> "شغّالة"; "failed" -> "فاشلة"; "inactive" -> "واقفة"; "missing" -> "غير موجودة"; "changing" -> "تتغيّر"; else -> "غير معروف"
}

@Composable
private fun Ring(label: String, value: Double?) {
    val v = value?.takeIf { !it.isNaN() }
    val color = when { v == null -> Tone.idle; v >= 90 -> Tone.bad; v >= 75 -> Tone.warn; else -> Tone.ok }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { ((v ?: 0.0) / 100).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.size(64.dp),
                color = color, trackColor = MaterialTheme.colorScheme.surfaceVariant, strokeWidth = 6.dp, strokeCap = StrokeCap.Round,
            )
            Text(pct(v), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
