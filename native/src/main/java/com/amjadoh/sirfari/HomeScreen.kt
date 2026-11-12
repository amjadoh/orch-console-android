package com.amjadoh.sirfari

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(r: Repo.Result<Overview>?, now: Long, openServer: () -> Unit) {
    FreshnessBar(r, now)
    val o = r?.data ?: return
    val (color, headline) = when (o.healthLevel) {
        "ok" -> Tone.ok to "السيرفر سليم"
        "watch" -> Tone.warn to "فيه شي تتابعه"
        "act" -> Tone.bad to "يحتاج تدخل"
        else -> Tone.idle to "حالة السيرفر غير معروفة"
    }
    Panel {
        StatusLine(color, headline, if (o.serverBad + o.serverWarn > 0) "${o.serverBad + o.serverWarn} تنبيه" else null)
        if (o.maintenanceUntil != null && o.maintenanceUntil > now) Text("🛠️ وضع الصيانة شغّال", style = MaterialTheme.typography.bodySmall)
        o.problems.forEach { p ->
            Column {
                StatusLine(Tone.level(p.level), p.title)
                if (p.line.isNotBlank()) Text(p.line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (o.problems.isNotEmpty() || o.serverBad + o.serverWarn > 0) {
            Text("التفاصيل في تبويب السيرفر ←", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.clickable(onClick = openServer))
        }
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Stat("تنتظرك", o.awaiting + o.adminPending, if (o.awaiting + o.adminPending > 0) Tone.warn else Tone.idle, Modifier.weight(1f))
        Stat("شغالة", o.running, Tone.run, Modifier.weight(1f))
        Stat("خلصت اليوم", o.doneToday, Tone.ok, Modifier.weight(1f))
        Stat("فشلت", o.failed, if (o.failed > 0) Tone.bad else Tone.idle, Modifier.weight(1f))
    }

    val waiting = o.tasks.filter { it.status == "awaiting" }
    if (waiting.isNotEmpty() || o.adminPending > 0) Panel("تنتظر موافقتك") {
        waiting.forEach { t -> StatusLine(Tone.warn, t.title.ifBlank { t.taskId }, t.projectId) }
        if (o.adminPending > 0) StatusLine(Tone.warn, "طلبات إدارية (root): ${o.adminPending}")
        Text("الموافقة من النسخة الأصلية تجي في المرحلة 2. للحين وافق من التطبيق الحالي.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    val running = o.tasks.filter { it.status == "running" }
    if (running.isNotEmpty()) Panel("شغالة الحين") {
        running.forEach { t -> StatusLine(Tone.run, t.title.ifBlank { t.taskId }, t.projectId) }
    }
}

@Composable
private fun Stat(label: String, value: Int, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Panel(modifier = modifier) {
        Text("$value", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}
